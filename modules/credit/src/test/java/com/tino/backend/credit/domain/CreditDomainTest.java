package com.tino.backend.credit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tino.backend.credit.domain.model.CreditAmount;
import com.tino.backend.credit.domain.model.CreditDirection;
import com.tino.backend.credit.domain.model.CreditLedgerEntry;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreditDomainTest {
    private static final UUID ENTRY_ID = UUID.fromString("00000000-0000-7000-8000-000000000901");
    private static final UUID BUSINESS_ID = UUID.fromString("00000000-0000-7000-8000-00000000090a");
    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-7000-8000-00000000090b");
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-7000-8000-00000000090c");

    @Test
    void amountNormalizesWithoutRoundingAndRejectsMoreThanTwoDecimals() {
        assertThat(CreditAmount.of(new BigDecimal("12.3")).value())
                .isEqualByComparingTo("12.30");
        assertThatThrownBy(() -> CreditAmount.of(new BigDecimal("12.345")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CreditAmount.of(new BigDecimal("0.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void amountRejectsOverflowAndDirectionHasAnOpposite() {
        assertThatThrownBy(() -> CreditAmount.of(new BigDecimal("100000000000000000.00")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(CreditDirection.CREDIT.opposite()).isEqualTo(CreditDirection.DEBIT);
        assertThat(CreditDirection.DEBIT.opposite()).isEqualTo(CreditDirection.CREDIT);
    }

    @Test
    void ledgerEntryRejectsBlankReasonAndSelfCompensation() {
        assertThatThrownBy(() -> new CreditLedgerEntry(ENTRY_ID, new BusinessId(BUSINESS_ID), ACCOUNT_ID,
                CUSTOMER_ID, CreditDirection.CREDIT, CreditAmount.of(new BigDecimal("1.00")), " ", null,
                null, Instant.parse("2026-08-29T12:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreditLedgerEntry(ENTRY_ID, new BusinessId(BUSINESS_ID), ACCOUNT_ID,
                CUSTOMER_ID, CreditDirection.CREDIT, CreditAmount.of(new BigDecimal("1.00")), "MANUAL",
                ENTRY_ID, null, Instant.parse("2026-08-29T12:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
