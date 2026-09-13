package com.kraft.support;

import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * 테스트용 <b>진짜</b> 이미지 바이트를 만든다.
 * <p>
 * 예전에는 테스트가 {@code "fake-image".getBytes()}를 올렸고 서버도 그것을 받아줬다 —
 * 확장자만 검사했기 때문이다(개선 보고서 F06). 이제 내용의 시그니처까지 검사하므로,
 * 업로드 성공을 검증하려면 실제로 디코딩 가능한 이미지가 필요하다.
 */
public final class TestImages {

    private TestImages() {
    }

    public static MockMultipartFile pngFile(String originalFilename) {
        return new MockMultipartFile("file", originalFilename, "image/png", pngBytes(1, 1));
    }

    public static MockMultipartFile jpegFile(String originalFilename) {
        return new MockMultipartFile("file", originalFilename, "image/jpeg", bytesOf("jpg", 1, 1));
    }

    public static byte[] pngBytes(int width, int height) {
        return bytesOf("png", width, height);
    }

    private static byte[] bytesOf(String format, int width, int height) {
        BufferedImage image = new BufferedImage(width, height,
                "png".equals(format) ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, format, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
