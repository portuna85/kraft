package com.kraft.post.service;

import java.nio.charset.StandardCharsets;

/**
 * WEBP 파일의 RIFF 컨테이너 구조를 파일 끝까지 검사하고 캔버스 크기를 돌려준다
 * (평가 보고서 2026-09-25 F06).
 * <p>
 * 예전에는 앞 30바이트의 치수 필드만 읽어, 이미지 비트스트림이 전혀 없는 30바이트짜리 헤더도
 * 정상 업로드로 받아들였다. JDK ImageIO에는 WEBP 디코더가 없고, 50메가픽셀까지 허용하는
 * 이미지를 서버에서 전부 디코딩하면 메모리 비용이 크다. 그래서 디코딩 대신 명세(RIFF/WebP
 * 컨테이너, VP8·VP8L 비트스트림 헤더)의 구조 규칙을 끝까지 확인한다.
 * <ul>
 *   <li>RIFF 크기 필드 + 8이 파일 길이와 정확히 같다. 잘리거나 뒤에 다른 데이터가 붙으면 거절한다.</li>
 *   <li>모든 청크가 경계를 넘지 않고 파일 끝에서 정확히 끝난다(홀수 길이는 패딩 1바이트).</li>
 *   <li>이미지 청크가 실제로 있다. 단순 형식은 첫 청크가 VP8/VP8L이다. 확장 형식(VP8X)은
 *       정지 이미지면 VP8/VP8L이 하나 있고 크기가 캔버스와 같으며, 애니메이션이면 ANIM과
 *       ANMF가 하나 이상 있고 각 프레임이 캔버스 안에 든다.</li>
 *   <li>VP8은 키 프레임 태그, 시작 코드 {@code 9d 01 2a}, 0이 아닌 크기, 첫 파티션이 청크 안에
 *       든다는 조건을 만족한다. VP8L은 시그니처 {@code 0x2F}, 버전 0, 헤더 뒤 비트스트림이 있어야 한다.</li>
 * </ul>
 * 지원 범위는 손실(VP8), 무손실(VP8L), 알파(VP8X+ALPH+VP8), 애니메이션(VP8X+ANIM+ANMF)이다.
 * 한계: 엔트로피 코딩된 비트스트림 내부까지 디코딩하지는 않는다. 구조가 온전하지만 압축 데이터가
 * 깨진 파일은 통과할 수 있다(브라우저에서 깨진 이미지로 보인다).
 */
final class WebpStructure {

    static final String INVALID_MESSAGE = "WEBP 이미지가 손상되었거나 이미지 데이터가 없습니다.";

    private WebpStructure() {
    }

    /**
     * @return {@code [width, height]} 캔버스 크기
     * @throws IllegalArgumentException 구조가 명세와 맞지 않으면
     */
    static long[] inspect(byte[] data) {
        if (data.length < 12 + 8 || !fourCc(data, 0).equals("RIFF") || !fourCc(data, 8).equals("WEBP")) {
            throw invalid();
        }
        long riffSize = le32(data, 4);
        if (riffSize + 8 != data.length) {
            throw invalid();
        }

        String first = fourCc(data, 12);
        return switch (first) {
            case "VP8 ", "VP8L" -> {
                // 단순 형식: 이미지 청크 하나가 파일 전체다.
                Chunk image = chunkAt(data, 12, data.length);
                if (image.end() != data.length) {
                    throw invalid();
                }
                yield frameSize(data, image);
            }
            case "VP8X" -> extended(data);
            default -> throw invalid();
        };
    }

    private static long[] extended(byte[] data) {
        Chunk header = chunkAt(data, 12, data.length);
        if (header.size() < 10) {
            throw invalid();
        }
        int flags = data[header.payload()] & 0xFF;
        boolean animated = (flags & 0x02) != 0;
        long canvasWidth = le24(data, header.payload() + 4) + 1;
        long canvasHeight = le24(data, header.payload() + 7) + 1;

        boolean sawAnim = false;
        int frames = 0;
        long[] still = null;
        int offset = header.end();
        while (offset < data.length) {
            Chunk chunk = chunkAt(data, offset, data.length);
            switch (chunk.id()) {
                case "ANIM" -> sawAnim = true;
                case "ANMF" -> {
                    if (!animated) {
                        throw invalid();
                    }
                    animationFrame(data, chunk, canvasWidth, canvasHeight);
                    frames++;
                }
                case "VP8 ", "VP8L" -> {
                    if (animated || still != null) {
                        throw invalid();
                    }
                    still = frameSize(data, chunk);
                }
                default -> {
                    // ICCP·ALPH·EXIF·XMP 및 알 수 없는 청크는 명세상 건너뛴다.
                }
            }
            offset = chunk.end();
        }
        if (offset != data.length) {
            throw invalid();
        }

        if (animated) {
            if (!sawAnim || frames == 0) {
                throw invalid();
            }
        } else if (still == null || still[0] != canvasWidth || still[1] != canvasHeight) {
            throw invalid();
        }
        return new long[] { canvasWidth, canvasHeight };
    }

    /** ANMF: 프레임 X/2(3)·Y/2(3)·폭-1(3)·높이-1(3)·지속(3)·플래그(1) 다음에 ALPH?·VP8/VP8L. */
    private static void animationFrame(byte[] data, Chunk anmf, long canvasWidth, long canvasHeight) {
        if (anmf.size() < 16 + 8) {
            throw invalid();
        }
        int p = anmf.payload();
        long x = le24(data, p) * 2;
        long y = le24(data, p + 3) * 2;
        long width = le24(data, p + 6) + 1;
        long height = le24(data, p + 9) + 1;
        if (x + width > canvasWidth || y + height > canvasHeight) {
            throw invalid();
        }
        long[] frame = null;
        int offset = p + 16;
        int end = p + anmf.size();
        while (offset < end) {
            Chunk sub = chunkAt(data, offset, end);
            if (sub.id().equals("VP8 ") || sub.id().equals("VP8L")) {
                if (frame != null) {
                    throw invalid();
                }
                frame = frameSize(data, sub);
            }
            offset = sub.end();
        }
        if (offset != end || frame == null || frame[0] != width || frame[1] != height) {
            throw invalid();
        }
    }

    private static long[] frameSize(byte[] data, Chunk chunk) {
        return chunk.id().equals("VP8 ") ? vp8Size(data, chunk) : vp8lSize(data, chunk);
    }

    /** VP8 키 프레임: 프레임 태그(3) + 시작 코드(3) + 폭(2) + 높이(2) + 첫 파티션. */
    private static long[] vp8Size(byte[] data, Chunk chunk) {
        if (chunk.size() < 10) {
            throw invalid();
        }
        int p = chunk.payload();
        long tag = le24(data, p);
        boolean keyFrame = (tag & 0x01) == 0;
        long firstPartitionSize = (tag >> 5) & 0x7FFFF;
        if (!keyFrame
                || (data[p + 3] & 0xFF) != 0x9D || (data[p + 4] & 0xFF) != 0x01 || (data[p + 5] & 0xFF) != 0x2A
                || firstPartitionSize == 0 || 10 + firstPartitionSize > chunk.size()) {
            throw invalid();
        }
        long width = le16(data, p + 6) & 0x3FFF;
        long height = le16(data, p + 8) & 0x3FFF;
        if (width == 0 || height == 0) {
            throw invalid();
        }
        return new long[] { width, height };
    }

    /** VP8L: 시그니처 0x2F + 폭-1(14) · 높이-1(14) · 알파(1) · 버전(3) + 비트스트림. */
    private static long[] vp8lSize(byte[] data, Chunk chunk) {
        if (chunk.size() <= 5) {
            throw invalid();
        }
        int p = chunk.payload();
        long packed = le32(data, p + 1);
        if ((data[p] & 0xFF) != 0x2F || (packed >>> 29) != 0) {
            throw invalid();
        }
        return new long[] { (packed & 0x3FFF) + 1, ((packed >>> 14) & 0x3FFF) + 1 };
    }

    private record Chunk(String id, int payload, int size, int end) {
    }

    /** offset의 청크 헤더를 읽는다. 페이로드(+패딩)가 limit을 넘으면 거절한다. */
    private static Chunk chunkAt(byte[] data, int offset, int limit) {
        if (offset + 8 > limit) {
            throw invalid();
        }
        long size = le32(data, offset + 4);
        long end = offset + 8 + size + (size & 1);
        if (end > limit) {
            throw invalid();
        }
        return new Chunk(fourCc(data, offset), offset + 8, (int) size, (int) end);
    }

    private static String fourCc(byte[] data, int offset) {
        return new String(data, offset, 4, StandardCharsets.US_ASCII);
    }

    private static long le16(byte[] data, int offset) {
        return (data[offset] & 0xFFL) | (data[offset + 1] & 0xFFL) << 8;
    }

    private static long le24(byte[] data, int offset) {
        return le16(data, offset) | (data[offset + 2] & 0xFFL) << 16;
    }

    private static long le32(byte[] data, int offset) {
        return le24(data, offset) | (data[offset + 3] & 0xFFL) << 24;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException(INVALID_MESSAGE);
    }
}
