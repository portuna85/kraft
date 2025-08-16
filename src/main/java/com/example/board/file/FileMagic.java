// src/main/java/com/example/board/file/FileMagic.java
package com.example.board.file;

import java.nio.charset.StandardCharsets;

public final class FileMagic {
    private FileMagic() {}

    /** JPEG/PNG/GIF/WebP 매직바이트만 엄격 허용 */
    public static boolean isSupportedImage(byte[] header) {
        if (header == null) return false;
        int len = header.length;

        // JPEG: FF D8 FF
        if (len >= 3 &&
                (header[0] & 0xFF) == 0xFF &&
                (header[1] & 0xFF) == 0xD8 &&
                (header[2] & 0xFF) == 0xFF) {
            return true;
        }

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        byte[] PNG = new byte[]{(byte)0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A};
        if (len >= 8 && startsWith(header, PNG, 0)) return true;

        // GIF87a / GIF89a
        if (len >= 6) {
            byte[] g87a = "GIF87a".getBytes(StandardCharsets.US_ASCII);
            byte[] g89a = "GIF89a".getBytes(StandardCharsets.US_ASCII);
            if (startsWith(header, g87a, 0) || startsWith(header, g89a, 0)) return true;
        }

        // WebP: "RIFF" + 4바이트 + "WEBP"
        if (len >= 12) {
            byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
            byte[] WEBP = "WEBP".getBytes(StandardCharsets.US_ASCII);
            if (startsWith(header, RIFF, 0) && startsWith(header, WEBP, 8)) return true;
        }

        return false;
    }

    private static boolean startsWith(byte[] data, byte[] prefix, int offset) {
        if (data.length < offset + prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) return false;
        }
        return true;
    }
}
