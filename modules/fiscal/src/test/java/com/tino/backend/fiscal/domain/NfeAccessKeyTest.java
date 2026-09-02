package com.tino.backend.fiscal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tino.backend.fiscal.domain.model.NfeAccessKey;
import org.junit.jupiter.api.Test;

class NfeAccessKeyTest {
    private static final String OFFICIAL_TRIAL_KEY = "53160911510448000171550010000106771000187760";

    @Test
    void normalizesOfficialTrialKeyAndPreservesValue() {
        assertThat(new NfeAccessKey(" 53160911 510448000171550010000106771000187760 ").value())
                .isEqualTo(OFFICIAL_TRIAL_KEY);
    }

    @Test
    void acceptsTheOfficialAlphanumericEvolutionFixture() {
        assertThat(new NfeAccessKey("352605127AUC8B000121558890000000071003360292").value())
                .isEqualTo("352605127AUC8B000121558890000000071003360292");
    }

    @Test
    void rejectsWrongLengthAndNumericCheckDigit() {
        assertThatThrownBy(() -> new NfeAccessKey("123"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NfeAccessKey(OFFICIAL_TRIAL_KEY.substring(0, 43) + "1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
