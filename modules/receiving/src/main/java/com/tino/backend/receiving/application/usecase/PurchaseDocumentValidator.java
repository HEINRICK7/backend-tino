package com.tino.backend.receiving.application.usecase;

import com.tino.backend.receiving.application.exception.ReceivingErrorCode;
import com.tino.backend.receiving.application.exception.ReceivingException;
import com.tino.backend.receiving.application.model.PurchaseDocument;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Validates the canonical boundary before any tenant-owned persistence. */
final class PurchaseDocumentValidator {
    private static final String PI_PREFIX = "22";
    private static final String NFCE_MODEL = "65";

    private PurchaseDocumentValidator() {}

    static PurchaseDocument validate(PurchaseDocument document) {
        if (document == null) throw invalid("purchase document is required");
        if (document.source() != PurchaseDocument.Source.NFCE
                || document.documentType() != PurchaseDocument.DocumentType.NFCE) {
            throw invalid("only NFCE documents are supported");
        }
        var accessKey = normalizeAccessKey(document.accessKey());
        if (!accessKey.startsWith(PI_PREFIX)) throw invalid("NFC-e access key is not from Piauí");
        if (!NFCE_MODEL.equals(accessKey.substring(20, 22))) throw invalid("access key is not NFC-e model 65");
        if (!hasValidCheckDigit(accessKey)) throw invalid("NFC-e access key has an invalid check digit");
        if (document.issuer() == null) throw invalid("issuer is required");
        validateText(document.issuer().name(), "issuer name", 500, true);
        validateText(document.issuer().taxId(), "issuer tax id", 32, true);
        if (document.items().isEmpty()) throw invalid("purchase document must contain at least one item");
        if (document.items().size() > 10000) throw invalid("purchase document has too many items");
        Set<Integer> lines = new HashSet<>();
        for (var item : document.items()) {
            if (item == null || item.lineNumber() < 1 || !lines.add(item.lineNumber())) {
                throw invalid("purchase item lines must be positive and unique");
            }
            validateText(item.rawDescription(), "item description", 500, false);
            validateText(item.externalCode(), "item external code", 200, true);
            validateText(item.gtin(), "item gtin", 32, true);
            validateText(item.unit(), "item unit", 32, true);
            validateNonNegative(item.quantity(), "item quantity", true);
            validateNonNegative(item.unitPrice(), "item unit price", true);
            validateNonNegative(item.totalPrice(), "item total price", true);
        }
        validateNonNegative(document.total(), "purchase total", true);
        return new PurchaseDocument(document.source(), document.documentType(), accessKey, document.issuedAt(),
                document.issuer(), document.items(), document.total());
    }

    static String normalizeAccessKey(String input) {
        Objects.requireNonNull(input, "access key");
        var normalized = input.replaceAll("\\D", "");
        if (!normalized.matches("\\d{44}")) throw invalid("NFC-e access key must contain 44 digits");
        return normalized;
    }

    private static void validateText(String value, String field, int maxLength, boolean optional) {
        if (value == null) {
            if (!optional) throw invalid(field + " is required");
            return;
        }
        if (value.isBlank()) throw invalid(field + " must not be blank");
        if (value.length() > maxLength) throw invalid(field + " is too long");
    }

    private static void validateNonNegative(BigDecimal value, String field, boolean optional) {
        if (value == null) {
            if (!optional) throw invalid(field + " is required");
            return;
        }
        if (value.signum() < 0) throw invalid(field + " must not be negative");
    }

    private static boolean hasValidCheckDigit(String key) {
        var sum = 0;
        var weight = 2;
        for (var index = key.length() - 2; index >= 0; index--, weight++) {
            if (weight == 10) weight = 2;
            sum += Character.digit(key.charAt(index), 10) * weight;
        }
        var remainder = sum % 11;
        var check = remainder == 0 || remainder == 1 ? 0 : 11 - remainder;
        return check == Character.digit(key.charAt(key.length() - 1), 10);
    }

    private static ReceivingException invalid(String message) {
        return new ReceivingException(ReceivingErrorCode.INVALID_REQUEST, message, false, 400);
    }
}
