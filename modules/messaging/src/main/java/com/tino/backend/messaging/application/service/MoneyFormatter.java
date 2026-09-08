package com.tino.backend.messaging.application.service;

import com.tino.backend.messaging.domain.model.MoneyView;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

public final class MoneyFormatter {
    private static final Locale BRAZIL = Locale.forLanguageTag("pt-BR");

    private MoneyFormatter() {}

    public static String format(MoneyView money) {
        var formatter = NumberFormat.getNumberInstance(BRAZIL);
        formatter.setGroupingUsed(true);
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);
        formatter.setRoundingMode(RoundingMode.UNNECESSARY);
        return "R$ " + formatter.format(money.amount());
    }
}
