package com.kraft.post.service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 업로드 이미지에서 위치·기기 정보(EXIF의 GPS·기기 모델·촬영 시각 등)를 재인코딩 없이 제거한다. 세그먼트·청크만
 * 걸러 픽셀은 건드리지 않는다. JPEG은 Orientation 태그만 남긴 최소 EXIF를 다시 만든다(없애면 세로 사진이
 * 눕는다). 파싱 중 예상과 다르면 원본을 그대로 돌려준다 — 부가 방어일 뿐 유효성 판정은
 * {@code PostImageService.validate()}의 몫이다.
 */
final class ImageMetadataStripper {

    private ImageMetadataStripper() {
    }

    static byte[] strip(byte[] data, String extension) {
        try {
            return switch (extension) {
                case "jpg", "jpeg" -> stripJpeg(data);
                case "png" -> stripPng(data);
                case "webp" -> stripWebp(data);
                case "gif" -> stripGif(data);
                // (이미 걸러진) HEIC 등은 이 메타데이터를 담는 관례가 없다.
                default -> data;
            };
        } catch (RuntimeException e) {
            return data;
        }
    }

    // ── JPEG ────────────────────────────────────────────────────────────────

    private static final int MARKER_SOI = 0xD8;
    private static final int MARKER_SOS = 0xDA;
    private static final int MARKER_APP1 = 0xE1;
    private static final int MARKER_APP13 = 0xED;

    /** SOS(스캔 시작)를 만나면 그 뒤(픽셀 데이터)를 그대로 복사하고 멈춘다. APP1(EXIF·XMP)과 APP13(IPTC)만 뺀다. */
    private static byte[] stripJpeg(byte[] data) {
        if (data.length < 4 || (data[0] & 0xFF) != 0xFF || (data[1] & 0xFF) != MARKER_SOI) {
            return data;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        out.write(0xFF);
        out.write(MARKER_SOI);
        int pos = 2;

        while (pos + 4 <= data.length) {
            if ((data[pos] & 0xFF) != 0xFF) {
                // 구조를 신뢰할 수 없으니 손대지 않는다.
                return data;
            }
            int marker = data[pos + 1] & 0xFF;
            if (marker == MARKER_SOS) {
                out.write(data, pos, data.length - pos);
                return out.toByteArray();
            }
            int length = ((data[pos + 2] & 0xFF) << 8) | (data[pos + 3] & 0xFF);
            int segmentEnd = pos + 2 + length;
            if (length < 2 || segmentEnd > data.length) {
                return data;
            }

            if (marker == MARKER_APP1) {
                byte[] replacement = orientationOnlyApp1(data, pos + 4, segmentEnd);
                if (replacement != null) {
                    out.write(replacement, 0, replacement.length);
                }
                // replacement == null: EXIF Orientation이 없거나 기본값(1)이라 통째로 뺀다.
            } else if (marker != MARKER_APP13) {
                out.write(data, pos, segmentEnd - pos);
            }
            pos = segmentEnd;
        }
        // SOS 없이 끝났다 — 원본을 그대로 쓴다.
        return data;
    }

    /** EXIF의 Orientation(0x0112)이 기본값(1)이 아니면 그 값만 담은 최소 APP1을 만든다. 그 외에는 null(통째로 뺀다). */
    private static byte[] orientationOnlyApp1(byte[] data, int payloadStart, int payloadEnd) {
        int tiffStart = payloadStart + 6;
        if (payloadEnd - payloadStart < 8
                || data[payloadStart] != 'E' || data[payloadStart + 1] != 'x'
                || data[payloadStart + 2] != 'i' || data[payloadStart + 3] != 'f'
                || data[payloadStart + 4] != 0 || data[payloadStart + 5] != 0
                || tiffStart + 8 > payloadEnd) {
            return null;
        }

        boolean little = data[tiffStart] == 'I' && data[tiffStart + 1] == 'I';
        boolean big = data[tiffStart] == 'M' && data[tiffStart + 1] == 'M';
        if (!little && !big) {
            return null;
        }
        long ifdOffset = u32(data, tiffStart + 4, little);
        int ifdStart = (int) (tiffStart + ifdOffset);
        if (ifdStart < tiffStart || ifdStart + 2 > payloadEnd) {
            return null;
        }
        int entryCount = (int) u16(data, ifdStart, little);
        int entriesEnd = ifdStart + 2 + entryCount * 12;
        if (entriesEnd > payloadEnd) {
            return null;
        }

        for (int i = 0; i < entryCount; i++) {
            int entry = ifdStart + 2 + i * 12;
            int tag = (int) u16(data, entry, little);
            if (tag != 0x0112) {
                continue;
            }
            int orientation = (int) u16(data, entry + 8, little);
            if (orientation <= 1 || orientation > 8) {
                return null;
            }
            return buildMinimalApp1(orientation);
        }
        return null;
    }

    /** Orientation 하나만 담은 IFD0 한 항목짜리 EXIF를 리틀 엔디안으로 새로 짠다. */
    private static byte[] buildMinimalApp1(int orientation) {
        ByteArrayOutputStream tiff = new ByteArrayOutputStream(26);
        writeU16(tiff, 'I' | ('I' << 8)); // "II"
        writeU16(tiff, 0x002A);
        writeU32(tiff, 8); // IFD0가 헤더 바로 뒤(오프셋 8)
        writeU16(tiff, 1); // 항목 1개
        writeU16(tiff, 0x0112); // Orientation
        writeU16(tiff, 3); // SHORT
        writeU32(tiff, 1); // count
        writeU16(tiff, orientation);
        writeU16(tiff, 0); // 4바이트 값 필드의 패딩
        writeU32(tiff, 0); // 다음 IFD 없음
        byte[] tiffBytes = tiff.toByteArray();

        byte[] payload = new byte[6 + tiffBytes.length];
        payload[0] = 'E';
        payload[1] = 'x';
        payload[2] = 'i';
        payload[3] = 'f';
        payload[4] = 0;
        payload[5] = 0;
        System.arraycopy(tiffBytes, 0, payload, 6, tiffBytes.length);

        int length = payload.length + 2;
        byte[] segment = new byte[2 + 2 + payload.length];
        segment[0] = (byte) 0xFF;
        segment[1] = (byte) MARKER_APP1;
        segment[2] = (byte) (length >> 8);
        segment[3] = (byte) length;
        System.arraycopy(payload, 0, segment, 4, payload.length);
        return segment;
    }

    private static long u16(byte[] data, int offset, boolean little) {
        int b0 = data[offset] & 0xFF;
        int b1 = data[offset + 1] & 0xFF;
        return little ? (b0 | (b1 << 8)) : ((b0 << 8) | b1);
    }

    private static long u32(byte[] data, int offset, boolean little) {
        long low = u16(data, offset, little);
        long high = u16(data, offset + 2, little);
        return little ? (low | (high << 16)) : ((low << 16) | high);
    }

    private static void writeU16(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    private static void writeU32(ByteArrayOutputStream out, long value) {
        writeU16(out, (int) (value & 0xFFFF));
        writeU16(out, (int) ((value >> 16) & 0xFFFF));
    }

    // ── PNG ─────────────────────────────────────────────────────────────────

    /** 텍스트·EXIF·XMP·수정 시각을 담을 수 있는 청크. */
    private static final java.util.Set<String> PNG_METADATA_CHUNKS =
            java.util.Set.of("eXIf", "tEXt", "iTXt", "zTXt", "tIME");

    private static final byte[] PNG_SIGNATURE =
            { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };

    /** 메타데이터 청크({@link #PNG_METADATA_CHUNKS})만 뺀다. 남기는 청크는 CRC까지 그대로 복사하고, 색 재현용 {@code iCCP} 등은 남긴다. */
    private static byte[] stripPng(byte[] data) {
        if (data.length < PNG_SIGNATURE.length || !startsWith(data, PNG_SIGNATURE)) {
            return data;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        out.write(PNG_SIGNATURE, 0, PNG_SIGNATURE.length);

        int pos = PNG_SIGNATURE.length;
        while (pos + 8 <= data.length) {
            long chunkDataLength = ((long) (data[pos] & 0xFF) << 24) | ((data[pos + 1] & 0xFF) << 16)
                    | ((data[pos + 2] & 0xFF) << 8) | (data[pos + 3] & 0xFF);
            String type = new String(data, pos + 4, 4, StandardCharsets.US_ASCII);
            long chunkTotal = 12L + chunkDataLength;
            if (chunkDataLength < 0 || pos + chunkTotal > data.length) {
                return data;
            }
            if (!PNG_METADATA_CHUNKS.contains(type)) {
                out.write(data, pos, (int) chunkTotal);
            }
            pos += (int) chunkTotal;
        }
        return out.toByteArray();
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    // ── WEBP ────────────────────────────────────────────────────────────────

    /** RIFF의 {@code EXIF}·{@code XMP }(공백 포함 4글자) 청크를 빼고 RIFF 크기 필드를 다시 쓴다. */
    private static byte[] stripWebp(byte[] data) {
        if (data.length < 12 || !"RIFF".equals(new String(data, 0, 4, StandardCharsets.US_ASCII))
                || !"WEBP".equals(new String(data, 8, 4, StandardCharsets.US_ASCII))) {
            return data;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        out.write(data, 0, 12); // "RIFF" + size(임시) + "WEBP"

        int pos = 12;
        while (pos + 8 <= data.length) {
            String id = new String(data, pos, 4, StandardCharsets.US_ASCII);
            long size = (data[pos + 4] & 0xFFL) | ((data[pos + 5] & 0xFFL) << 8)
                    | ((data[pos + 6] & 0xFFL) << 16) | ((data[pos + 7] & 0xFFL) << 24);
            long padded = size + (size & 1);
            long chunkTotal = 8L + padded;
            if (pos + chunkTotal > data.length) {
                return data;
            }
            if (!id.equals("EXIF") && !id.equals("XMP ")) {
                out.write(data, pos, (int) chunkTotal);
            }
            pos += (int) chunkTotal;
        }

        byte[] result = out.toByteArray();
        long riffSize = result.length - 8L;
        result[4] = (byte) (riffSize & 0xFF);
        result[5] = (byte) ((riffSize >> 8) & 0xFF);
        result[6] = (byte) ((riffSize >> 16) & 0xFF);
        result[7] = (byte) ((riffSize >> 24) & 0xFF);
        return result;
    }

    // ── GIF ─────────────────────────────────────────────────────────────────

    /**
     * Comment Extension(0x21 0xFE)과 애니메이션용이 아닌 Application Extension(0x21 0xFF — XMP 등)을 뺀다.
     * {@code NETSCAPE2.0}/{@code ANIMEXTS1.0}은 남겨 애니메이션을 유지한다. 구조가 예상과 다르면 원본을 돌려준다.
     */
    private static byte[] stripGif(byte[] data) {
        if (data.length < 13 || data[0] != 'G' || data[1] != 'I' || data[2] != 'F') {
            return data;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        int pos = 13;
        if ((data[10] & 0x80) != 0) {
            pos += 3 * (1 << ((data[10] & 0x07) + 1)); // Global Color Table
        }
        if (pos > data.length) {
            return data;
        }
        out.write(data, 0, pos);

        while (pos < data.length) {
            int introducer = data[pos] & 0xFF;
            if (introducer == 0x3B) { // Trailer
                out.write(data, pos, data.length - pos);
                return out.toByteArray();
            }
            if (introducer == 0x2C) { // Image Descriptor
                if (pos + 10 > data.length) {
                    return data;
                }
                int end = pos + 10;
                if ((data[pos + 9] & 0x80) != 0) {
                    end += 3 * (1 << ((data[pos + 9] & 0x07) + 1)); // Local Color Table
                }
                end += 1; // LZW minimum code size
                end = skipSubBlocks(data, end);
                if (end < 0) {
                    return data;
                }
                out.write(data, pos, end - pos);
                pos = end;
            } else if (introducer == 0x21) { // Extension
                if (pos + 2 > data.length) {
                    return data;
                }
                int label = data[pos + 1] & 0xFF;
                int end = skipSubBlocks(data, pos + 2);
                if (end < 0) {
                    return data;
                }
                if (keepGifExtension(data, pos, label)) {
                    out.write(data, pos, end - pos);
                }
                pos = end;
            } else {
                return data;
            }
        }
        // Trailer 없이 끝난 잘린 파일은 손대지 않는다.
        return data;
    }

    /** 데이터 서브블록(길이 1바이트 + 내용, 길이 0이면 끝) 뒤의 위치. 구조가 깨졌으면 -1. */
    private static int skipSubBlocks(byte[] data, int start) {
        int pos = start;
        while (pos < data.length) {
            int size = data[pos] & 0xFF;
            pos += 1 + size;
            if (size == 0) {
                return pos <= data.length ? pos : -1;
            }
        }
        return -1;
    }

    private static boolean keepGifExtension(byte[] data, int pos, int label) {
        if (label == 0xFE) {
            return false; // Comment
        }
        if (label != 0xFF) {
            return true; // Graphic Control, Plain Text 등
        }
        // Application Extension: [0x21][0xFF][0x0B]["NETSCAPE"+"2.0"] ...
        if (pos + 14 > data.length) {
            return false;
        }
        String id = new String(data, pos + 3, 11, StandardCharsets.US_ASCII);
        return id.equals("NETSCAPE2.0") || id.equals("ANIMEXTS1.0");
    }
}
