package com.tino.backend.messaging.application.service;

import com.tino.backend.messaging.application.model.WhatsAppStatementSnapshot;
import com.tino.backend.messaging.domain.model.WhatsAppMessageType;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class WhatsAppTextComposer {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    public String compose(WhatsAppMessageType type, WhatsAppStatementSnapshot snapshot) {
        var statement = snapshot.statement();
        var customer = statement.customerDisplayName();
        var balance = MoneyFormatter.format(statement.openBalance());
        var count = statement.entries().size();
        var movementLabel = count == 1 ? "1 lançamento" : count + " lançamentos";
        var updatedAt = DATE.format(statement.generatedAt());

        return switch (type) {
            case DEBT_STATEMENT -> "*TINO • Caderneta*\n\nOlá, " + customer + ".\n\n"
                    + "Seu saldo atual é de *" + balance + "*.\n\n"
                    + movementLabel + " estão incluídos nesse valor.\n\n"
                    + "_Extrato atualizado em " + updatedAt + "._";
            case DEBT_CREATED -> "*TINO • Caderneta*\n\nOlá, " + customer + ".\n\n"
                    + "Uma nova compra fiada foi registrada. Seu saldo atual é de *" + balance + "*.";
            case PAYMENT_CONFIRMED -> "*TINO • Caderneta*\n\nOlá, " + customer + ".\n\n"
                    + "Pagamento recebido. Seu saldo atual é de *" + balance + "*.";
            case PARTIAL_PAYMENT_CONFIRMED -> "*TINO • Caderneta*\n\nOlá, " + customer + ".\n\n"
                    + "Pagamento parcial recebido. Seu saldo atual é de *" + balance + "*.";
            case PAYMENT_REMINDER -> "*TINO • Caderneta*\n\nOlá, " + customer + ".\n\n"
                    + "Lembrete: seu saldo atual é de *" + balance + "*.";
            case PROMISE_TO_PAY -> "*TINO • Caderneta*\n\nOlá, " + customer + ".\n\n"
                    + "Registramos sua promessa de pagamento. Seu saldo atual é de *" + balance + "*.";
            case PAYMENT_AGREEMENT -> "*TINO • Caderneta*\n\nOlá, " + customer + ".\n\n"
                    + "Seu acordo de pagamento foi registrado. Saldo atual: *" + balance + "*.";
        };
    }
}
