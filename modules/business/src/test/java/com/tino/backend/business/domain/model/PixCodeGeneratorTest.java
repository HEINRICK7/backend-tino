package com.tino.backend.business.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PixCodeGeneratorTest {
    @Test
    void generatesStaticBrCodeWithPayloadFieldsAndValidCrc() {
        var payload = PixCodeGenerator.generate(
                PixKey.parse("7e5b2c1a-5c7d-4a4d-8d86-1f4bd4e1c2aa"),
                "Mercadinho João", "Fortaleza");

        assertThat(payload).startsWith("00020126");
        assertThat(payload).contains("0014br.gov.bcb.pix");
        assertThat(payload).contains("5802BR");
        assertThat(payload).contains("5915MERCADINHO JOAO");
        assertThat(payload).contains("6009FORTALEZA");
        assertThat(payload).contains("62070503***6304");
        assertThat(payload).doesNotContain("540");
        assertThat(payload.substring(payload.length() - 4)).isEqualTo(crc16(payload.substring(0, payload.length() - 4)));
    }

    private static String crc16(String value) {
        var crc = 0xFFFF;
        for (var current : value.getBytes(StandardCharsets.US_ASCII)) {
            crc ^= (current & 0xFF) << 8;
            for (var bit = 0; bit < 8; bit++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }
        return String.format("%04X", crc);
    }
}
