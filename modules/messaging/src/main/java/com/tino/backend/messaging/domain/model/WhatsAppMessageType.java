package com.tino.backend.messaging.domain.model;

public enum WhatsAppMessageType {
    DEBT_STATEMENT("debt-statement", "Extrato da conta"),
    DEBT_CREATED("debt-created", "Compra fiada"),
    PAYMENT_CONFIRMED("payment-confirmed", "Pagamento recebido"),
    PARTIAL_PAYMENT_CONFIRMED("partial-payment-confirmed", "Pagamento parcial"),
    PAYMENT_REMINDER("payment-reminder", "Lembrete de pagamento"),
    PROMISE_TO_PAY("promise-to-pay", "Promessa de pagamento"),
    PAYMENT_AGREEMENT("payment-agreement", "Acordo de pagamento");

    private final String templateId;
    private final String label;

    WhatsAppMessageType(String templateId, String label) {
        this.templateId = templateId;
        this.label = label;
    }

    public String templateId() {
        return templateId;
    }

    public String label() {
        return label;
    }
}
