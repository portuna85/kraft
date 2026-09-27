package com.kraft.post.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ImageMetadataStripper}가 픽셀 데이터는 건드리지 않고 위치·기기 메타데이터만
 * 없애는지 확인한다(전체 리뷰 2026-09-26 A-SEC-10). 실제 카메라 JPEG 대신 명세에 맞는
 * 최소 바이트를 손으로 만든다 — {@link WebpStructure}·{@code PostImageServiceWebpTest}와
 * 같은 이유(재현 가능하고, 어떤 바이트가 왜 있는지 테스트 자체가 설명한다).
 */
class ImageMetadataStripperTest {

    private static final byte[] SCAN_TAIL = {
            (byte) 0xFF, (byte) 0xDA, // SOS
            0x01, 0x02, 0x03, 0x04,   // 임의의 "엔트로피 코딩" 바이트(파싱 대상 아님)
            (byte) 0xFF, (byte) 0xD9, // EOI
    };

    private static final byte[] APP0_JFIF =
            marker(0xE0, new byte[] { 'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1, 0, 1, 0, 0 });

    private static final byte[] APP13_PHOTOSHOP =
            marker(0xED, new byte[] { 'P', 'h', 'o', 't', 'o', 's', 'h', 'o', 'p', 0, 1, 2, 3 });

    @Test
    @DisplayName("Orientation이 있으면 그 값만 남기고 GPS 태그·Photoshop 세그먼트는 없앤다")
    void jpeg_keepsOrientationOnlyAndRemovesGpsAndPhotoshop() {
        byte[] original = jpeg(exifApp1(6, true), APP0_JFIF, APP13_PHOTOSHOP);

        byte[] result = ImageMetadataStripper.strip(original, "jpg");

        assertThat(result[0] & 0xFF).isEqualTo(0xFF);
        assertThat(result[1] & 0xFF).isEqualTo(0xD8);
        // GPSInfoIFDPointer 태그(0x8825, LE로 25 88)가 사라졌다.
        assertThat(indexOf(result, new byte[] { 0x25, (byte) 0x88 })).isEqualTo(-1);
        // Photoshop 문자열이 사라졌다 — APP13 전체가 빠졌다는 뜻이다.
        assertThat(indexOf(result, "Photoshop".getBytes(StandardCharsets.US_ASCII))).isEqualTo(-1);
        // APP0(JFIF)은 EXIF·Photoshop이 아니므로 그대로 남는다.
        assertThat(indexOf(result, "JFIF".getBytes(StandardCharsets.US_ASCII))).isNotNegative();
        // Orientation(0x0112, LE로 12 01) 태그와 값 6이 남아 있다.
        int orientationTag = indexOf(result, new byte[] { 0x12, 0x01 });
        assertThat(orientationTag).isNotNegative();
        assertThat(result[orientationTag + 8]).isEqualTo((byte) 6);
        // 스캔 데이터 + EOI는 손대지 않는다.
        assertThat(result).endsWith(SCAN_TAIL);
    }

    @Test
    @DisplayName("Orientation이 기본값(1)이면 EXIF 세그먼트를 통째로 없앤다")
    void jpeg_withDefaultOrientation_removesExifEntirely() {
        byte[] original = jpeg(exifApp1(1, true));

        byte[] result = ImageMetadataStripper.strip(original, "jpg");

        assertThat(indexOf(result, "Exif".getBytes(StandardCharsets.US_ASCII))).isEqualTo(-1);
    }

    @Test
    @DisplayName("Orientation 태그가 없으면 EXIF 세그먼트를 통째로 없앤다")
    void jpeg_withoutOrientationTag_removesExifEntirely() {
        byte[] original = jpeg(exifApp1(6, false));

        byte[] result = ImageMetadataStripper.strip(original, "jpg");

        assertThat(indexOf(result, "Exif".getBytes(StandardCharsets.US_ASCII))).isEqualTo(-1);
    }

    @Test
    @DisplayName("SOI로 시작하지 않으면 손대지 않고 그대로 돌려준다")
    void jpeg_withoutSoi_returnsOriginalUnchanged() {
        byte[] garbage = { 0x00, 0x01, 0x02, 0x03 };

        assertThat(ImageMetadataStripper.strip(garbage, "jpg")).isEqualTo(garbage);
    }

    @Test
    @DisplayName("PNG의 eXIf·tEXt 청크만 없애고 나머지는 그대로 둔다")
    void png_removesOnlyExifAndTextChunks() {
        byte[] original = png(
                pngChunk("IHDR", new byte[] { 0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0 }),
                pngChunk("eXIf", "GPSLatitude".getBytes(StandardCharsets.US_ASCII)),
                pngChunk("tEXt", "Comment\0hello".getBytes(StandardCharsets.US_ASCII)),
                pngChunk("IDAT", new byte[] { 1, 2, 3 }),
                pngChunk("IEND", new byte[0]));

        byte[] result = ImageMetadataStripper.strip(original, "png");

        assertThat(indexOf(result, "GPSLatitude".getBytes(StandardCharsets.US_ASCII))).isEqualTo(-1);
        assertThat(indexOf(result, "Comment".getBytes(StandardCharsets.US_ASCII))).isEqualTo(-1);
        assertThat(indexOf(result, "IHDR".getBytes(StandardCharsets.US_ASCII))).isNotNegative();
        assertThat(indexOf(result, "IDAT".getBytes(StandardCharsets.US_ASCII))).isNotNegative();
        assertThat(indexOf(result, "IEND".getBytes(StandardCharsets.US_ASCII))).isNotNegative();
    }

    @Test
    @DisplayName("PNG 시그니처가 아니면 손대지 않는다")
    void png_withoutSignature_returnsOriginalUnchanged() {
        byte[] notPng = "not a png".getBytes(StandardCharsets.US_ASCII);

        assertThat(ImageMetadataStripper.strip(notPng, "png")).isEqualTo(notPng);
    }

    @Test
    @DisplayName("WEBP의 EXIF·XMP 청크를 없애고 RIFF 크기 필드를 다시 쓴다")
    void webp_removesExifAndXmpAndFixesSize() {
        byte[] original = webp(
                riffChunk("VP8 ", new byte[] { 1, 2, 3, 4 }),
                riffChunk("EXIF", "GPSLongitude".getBytes(StandardCharsets.US_ASCII)),
                riffChunk("XMP ", "<x:xmpmeta/>".getBytes(StandardCharsets.US_ASCII)));

        byte[] result = ImageMetadataStripper.strip(original, "webp");

        assertThat(indexOf(result, "GPSLongitude".getBytes(StandardCharsets.US_ASCII))).isEqualTo(-1);
        assertThat(indexOf(result, "xmpmeta".getBytes(StandardCharsets.US_ASCII))).isEqualTo(-1);
        assertThat(indexOf(result, "VP8 ".getBytes(StandardCharsets.US_ASCII))).isNotNegative();

        long declaredSize = (result[4] & 0xFFL) | ((result[5] & 0xFFL) << 8)
                | ((result[6] & 0xFFL) << 16) | ((result[7] & 0xFFL) << 24);
        assertThat(declaredSize).isEqualTo(result.length - 8L);
    }

    @Test
    @DisplayName("GIF 등 대상이 아닌 형식은 그대로 돌려준다")
    void unsupportedExtension_returnsOriginalUnchanged() {
        byte[] data = { 'G', 'I', 'F', '8', '9', 'a' };

        assertThat(ImageMetadataStripper.strip(data, "gif")).isSameAs(data);
    }

    // ── JPEG 픽스처 ─────────────────────────────────────────────────────────

    private static byte[] jpeg(byte[]... headerSegments) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] { (byte) 0xFF, (byte) 0xD8 });
        for (byte[] segment : headerSegments) {
            out.writeBytes(segment);
        }
        out.writeBytes(SCAN_TAIL);
        return out.toByteArray();
    }

    private static byte[] marker(int markerId, byte[] payload) {
        int length = payload.length + 2;
        byte[] segment = new byte[4 + payload.length];
        segment[0] = (byte) 0xFF;
        segment[1] = (byte) markerId;
        segment[2] = (byte) (length >> 8);
        segment[3] = (byte) length;
        System.arraycopy(payload, 0, segment, 4, payload.length);
        return segment;
    }

    /**
     * IFD0에 Orientation(선택)과, "GPS 정보가 있다"를 나타내는 GPSInfoIFDPointer 태그를 담은
     * 최소 EXIF APP1. GPS 서브 IFD 자체는 만들지 않는다 — 이 클래스는 포인터가 가리키는 곳을
     * 읽지 않고 태그가 있다는 사실만으로 판단하므로, 포인터 값이 실제로 무엇을 가리키는지는
     * 테스트 목적에 영향이 없다.
     */
    private static byte[] exifApp1(int orientation, boolean includeOrientation) {
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        writeU16(tiff, 0x4949); // "II"
        writeU16(tiff, 0x002A);
        writeU32(tiff, 8);

        int entryCount = includeOrientation ? 2 : 1;
        writeU16(tiff, entryCount);
        if (includeOrientation) {
            writeU16(tiff, 0x0112); // Orientation
            writeU16(tiff, 3);      // SHORT
            writeU32(tiff, 1);
            writeU16(tiff, orientation);
            writeU16(tiff, 0);
        }
        writeU16(tiff, 0x8825); // GPSInfoIFDPointer
        writeU16(tiff, 4);      // LONG
        writeU32(tiff, 1);
        writeU32(tiff, 999);    // 어디를 가리키는지는 이 테스트에서 중요하지 않다.
        writeU32(tiff, 0);      // 다음 IFD 없음

        byte[] tiffBytes = tiff.toByteArray();
        byte[] payload = new byte[6 + tiffBytes.length];
        System.arraycopy("Exif\0\0".getBytes(StandardCharsets.US_ASCII), 0, payload, 0, 6);
        System.arraycopy(tiffBytes, 0, payload, 6, tiffBytes.length);
        return marker(0xE1, payload);
    }

    private static void writeU16(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    private static void writeU32(ByteArrayOutputStream out, long value) {
        writeU16(out, (int) (value & 0xFFFF));
        writeU16(out, (int) ((value >> 16) & 0xFFFF));
    }

    // ── PNG 픽스처 ──────────────────────────────────────────────────────────

    private static byte[] png(byte[]... chunks) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' });
        for (byte[] chunk : chunks) {
            out.writeBytes(chunk);
        }
        return out.toByteArray();
    }

    /** CRC는 이 클래스가 읽지 않으므로 실제 값 대신 0을 채운다. */
    private static byte[] pngChunk(String type, byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int length = data.length;
        out.write((length >> 24) & 0xFF);
        out.write((length >> 16) & 0xFF);
        out.write((length >> 8) & 0xFF);
        out.write(length & 0xFF);
        out.writeBytes(type.getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(data);
        out.writeBytes(new byte[] { 0, 0, 0, 0 }); // CRC(더미)
        return out.toByteArray();
    }

    // ── WEBP 픽스처 ─────────────────────────────────────────────────────────

    private static byte[] webp(byte[]... chunks) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
        for (byte[] chunk : chunks) {
            body.writeBytes(chunk);
        }
        byte[] bodyBytes = body.toByteArray();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        long size = bodyBytes.length;
        out.write((int) (size & 0xFF));
        out.write((int) ((size >> 8) & 0xFF));
        out.write((int) ((size >> 16) & 0xFF));
        out.write((int) ((size >> 24) & 0xFF));
        out.writeBytes(bodyBytes);
        return out.toByteArray();
    }

    private static byte[] riffChunk(String id, byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(id.getBytes(StandardCharsets.US_ASCII));
        long size = data.length;
        out.write((int) (size & 0xFF));
        out.write((int) ((size >> 8) & 0xFF));
        out.write((int) ((size >> 16) & 0xFF));
        out.write((int) ((size >> 24) & 0xFF));
        out.writeBytes(data);
        if (data.length % 2 != 0) {
            out.write(0);
        }
        return out.toByteArray();
    }

    // ── 공용 ────────────────────────────────────────────────────────────────

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
