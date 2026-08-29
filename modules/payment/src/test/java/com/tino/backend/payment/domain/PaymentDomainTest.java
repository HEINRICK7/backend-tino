package com.tino.backend.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tino.backend.payment.domain.model.PaymentAmount;
import com.tino.backend.payment.domain.model.PaymentStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaymentDomainTest {
    @Test
    void moneyIsExactAndNormalizedToCents() {
        assertThat(new PaymentAmount(new BigDecimal("10")).value()).isEqualByComparingTo("10.00");
        assertThatThrownBy(() -> new PaymentAmount(new BigDecimal("10.001")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stateMachineAllowsOnlyForwardProviderTransitions() {
        assertThat(PaymentStatus.CREATED.canTransitionTo(PaymentStatus.AUTHORIZED)).isTrue();
        assertThat(PaymentStatus.AUTHORIZED.canTransitionTo(PaymentStatus.CAPTURED)).isTrue();
        assertThat(PaymentStatus.CAPTURED.canTransitionTo(PaymentStatus.CREATED)).isFalse();
        assertThat(PaymentStatus.REFUNDED.canTransitionTo(PaymentStatus.CAPTURED)).isFalse();
    }
}
