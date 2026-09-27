package com.kraft.post.service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 업로드된 이미지에서 위치·기기 정보가 담길 수 있는 메타데이터를 재인코딩 없이 제거한다
 * (전체 리뷰 2026-09-26 A-SEC-10). 휴대폰 JPEG의 EXIF에는 GPS 좌표·기기 모델·촬영 시각이
 * 들어 있는데, 예전에는 검증만 하고 원본 바이트를 그대로 저장·공개해 누구나 다운로드해
 * 확인할 수 있었다.
 * <p>
 * 세그먼트·청크만 걸러내고 픽셀 데이터는 건드리지 않는다 — 재인코딩하면 화질이 떨어지고
 * {@link WebpStructure}처럼 이미 이 저장소가 컨테이너 구조를 직접 읽는 방식과도 맞지 않는다.
 * 다만 JPEG의 EXIF Orientation 태그가 통째로 사라지면 세로로 찍은 사진이 눕혀 보이므로,
 * 그 값만 남긴 최소 EXIF 블록을 다시 만든다.
 * <p>
 * 파싱 중 무엇이든 예상과 다르면(형식이 이상하거나 손상된 경우) 원본을 그대로 돌려준다 —
 * 이 클래스가 하는 일은 부가적인 방어이지, {@code PostImageService.validate()}가 이미 확인한
 * "실제 이미지인가"를 다시 판정하는 것이 아니다. 실패해도 업로드 자체를 막을 이유는 없다.
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
                // GIF·(이미 걸러진) HEIC 등은 이 메타데이터를 담는 관례가 없다.
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

    /**
     * SOI 뒤 세그먼트를 하나씩 읽다가 SOS(스캔 시작)를 만나면 그 뒤 전부(엔트로피 코딩된
     * 픽셀 데이터 + EOI)를 그대로 복사하고 멈춘다 — 그 구간은 파싱할 필요도, 메타데이터가
     * 들어갈 자리도 없다. APP1(대개 EXIF, 가끔 XMP)과 APP13(Photoshop IRB/IPTC)만 없앤다.
     */
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
                // 마커가 아닌 곳에 도달했다 — 구조를 신뢰할 수 없으니 손대지 않는다.
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
        // SOS를 못 찾고 파일이 끝났다 — 잘렸거나 예상과 다른 구조이니 원본을 그대로 쓴다.
        return data;
    }

    /**
     * APP1 페이로드가 EXIF(TIFF)이고 Orientation(태그 0x0112)이 기본값(1)이 아니면, 그 값
     * 하나만 담은 최소 APP1 세그먼트를 새로 만든다. 그 외에는(XMP APP1, Orientation 없음·
     * 기본값) 완전히 없애도 되므로 {@code null}을 돌려준다.
     */
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

    private static final byte[] PNG_SIGNATURE =
            { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };

    /**
     * 청크를 하나씩 훑어 {@code eXIf}·{@code tEXt} 타입만 뺀다. 남기는 청크는 CRC까지
     * 그대로 복사하므로(내용을 바꾸지 않았다) 다시 계산할 필요가 없다.
     */
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
            if (!type.equals("eXIf") && !type.equals("tEXt")) {
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

    /**
     * RIFF 청크 중 {@code EXIF}·{@code XMP }(뒤에 공백 포함, 4글자)만 빼고, 전체 크기가
     * 바뀌었으니 RIFF 헤더의 크기 필드를 다시 쓴다.
     */
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
}
