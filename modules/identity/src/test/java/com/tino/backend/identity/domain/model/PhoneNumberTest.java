package com.tino.backend.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PhoneNumberTest {
    @Test
    void normalizesBrazilianMobileToE164() {
        assertThat(PhoneNumber.normalize("(86) 99592-2924").e164())
                .isEqualTo("+5586995922924");
        assertThat(PhoneNumber.normalize("0055 86 99592-2924").e164())
                .isEqualTo("+5586995922924");
    }

    @Test
    void normalizesBrazilianMobileWithLocalTrunkPrefix() {
        assertThat(PhoneNumber.normalize("086 99592-2924").e164())
                .isEqualTo("+5586995922924");
    }

    @Test
    void rejectsInvalidPhone() {
        assertThatThrownBy(() -> PhoneNumber.normalize("+1 555 0100"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
