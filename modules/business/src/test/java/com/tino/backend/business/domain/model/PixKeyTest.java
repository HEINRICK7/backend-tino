package com.tino.backend.business.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PixKeyTest {
    @Test
    void normalizesSupportedPixKeysWithoutPersistingCredentials() {
        assertThat(PixKey.parse(" 7e5b2c1a-5c7d-4a4d-8d86-1f4bd4e1c2aa "))
                .isEqualTo(new PixKey(PixKeyType.RANDOM, "7e5b2c1a-5c7d-4a4d-8d86-1f4bd4e1c2aa"));
        assertThat(PixKey.parse(" Loja@Exemplo.COM "))
                .isEqualTo(new PixKey(PixKeyType.EMAIL, "loja@exemplo.com"));
        assertThat(PixKey.parse("+55 (86) 99592-2924"))
                .isEqualTo(new PixKey(PixKeyType.PHONE, "+5586995922924"));
        assertThat(PixKey.parse("529.982.247-25"))
                .isEqualTo(new PixKey(PixKeyType.CPF, "52998224725"));
        assertThat(PixKey.parse("04.252.011/0001-10"))
                .isEqualTo(new PixKey(PixKeyType.CNPJ, "04252011000110"));
    }

    @Test
    void rejectsAmbiguousOrInvalidKeys() {
        assertThatThrownBy(() -> PixKey.parse("12345678901"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PixKey.parse("merchant@example"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PixKey.parse("bank-account-credential"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
