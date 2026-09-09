package com.tino.backend.business.domain.model;

import java.text.Normalizer;
import java.util.Locale;

/** Generates the static Pix BR Code (EMV payload), including CRC16-CCITT. */
public final class PixCodeGenerator {
    private PixCodeGenerator() {}

    public static String generate(PixKey key, String merchantName, String merchantCity) {
        var normalizedName = merchantField(merchantName, 25, "merchant name");
        var normalizedCity = merchantField(merchantCity, 15, "merchant city");
        var merchantAccount = field("00", "br.gov.bcb.pix")
                + field("01", key.value());
        var payload = field("00", "01")
                + field("26", merchantAccount)
                + field("52", "0000")
                + field("53", "986")
                + field("58", "BR")
                + field("59", normalizedName)
                + field("60", normalizedCity)
                + field("62", field("05", "***"))
                + "6304";
        return payload + crc16(payload);
    }

    public static String merchantField(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        var ascii = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9 &./-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (ascii.isBlank()) throw new IllegalArgumentException(label + " is required");
        return ascii.substring(0, Math.min(maxLength, ascii.length())).trim();
    }

    private static String field(String id, String value) {
        if (value.length() > 99) throw new IllegalArgumentException("BR Code field is too long");
        return id + String.format(Locale.ROOT, "%02d", value.length()) + value;
    }

    private static String crc16(String value) {
        var crc = 0xFFFF;
        for (var current : value.getBytes(java.nio.charset.StandardCharsets.US_ASCII)) {
            crc ^= (current & 0xFF) << 8;
            for (var bit = 0; bit < 8; bit++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }
        return String.format(Locale.ROOT, "%04X", crc);
    }
}
