package com.kraft.post.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** GIF 메타데이터 제거. 코멘트·XMP 확장은 빼고 애니메이션 확장과 프레임은 남긴다. */
class ImageMetadataStripperGifTest {

    private static final byte[] HEADER = {'G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0, 0, 0, 0};
    private static final byte[] FRAME = {
            0x21, (byte) 0xF9, 0x04, 0x00, 0x0A, 0x00, 0x00, 0x00, // Graphic Control
            0x2C, 0, 0, 0, 0, 1, 0, 1, 0, 0, // Image Descriptor(지역 색 표 없음)
            0x02, 0x02, 0x44, 0x01, 0x00 // LZW 최소 코드 크기 + 데이터 서브블록
    };

    @Test
    @DisplayName("gif: Comment 확장과 XMP Application 확장은 지우고 프레임은 그대로 둔다")
    void removesCommentAndXmpKeepsFrame() {
        byte[] gif = concat(HEADER, comment("secret-location"), application("XMP Data", "XMP"), FRAME, new byte[]{0x3B});

        byte[] stripped = ImageMetadataStripper.strip(gif, "gif");

        assertThat(contains(stripped, "secret-location")).isFalse();
        assertThat(contains(stripped, "XMP Data")).isFalse();
        assertThat(stripped).isEqualTo(concat(HEADER, FRAME, new byte[]{0x3B}));
    }

    @Test
    @DisplayName("gif: NETSCAPE2.0 반복 확장은 애니메이션을 위해 남긴다")
    void keepsNetscapeLoopExtension() {
        byte[] loop = {0x21, (byte) 0xFF, 0x0B, 'N', 'E', 'T', 'S', 'C', 'A', 'P', 'E', '2', '.', '0',
                0x03, 0x01, 0x00, 0x00, 0x00};
        byte[] gif = concat(HEADER, loop, FRAME, new byte[]{0x3B});

        assertThat(ImageMetadataStripper.strip(gif, "gif")).isEqualTo(gif);
    }

    @Test
    @DisplayName("gif: 구조가 깨졌으면 원본을 그대로 돌려준다")
    void malformedReturnsOriginal() {
        byte[] gif = concat(HEADER, new byte[]{0x21, (byte) 0xFE, 0x05, 'a'});

        assertThat(ImageMetadataStripper.strip(gif, "gif")).isEqualTo(gif);
    }

    private static byte[] comment(String text) {
        byte[] body = text.getBytes(StandardCharsets.US_ASCII);
        return concat(new byte[]{0x21, (byte) 0xFE, (byte) body.length}, body, new byte[]{0x00});
    }

    private static byte[] application(String payload, String idSuffix) {
        byte[] id = ("APP" + idSuffix + "ZZZ").substring(0, 8).concat("1.0").getBytes(StandardCharsets.US_ASCII);
        byte[] body = payload.getBytes(StandardCharsets.US_ASCII);
        return concat(new byte[]{0x21, (byte) 0xFF, 0x0B}, id, new byte[]{(byte) body.length}, body, new byte[]{0x00});
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    private static boolean contains(byte[] data, String text) {
        return new String(data, StandardCharsets.ISO_8859_1).contains(text);
    }
}
