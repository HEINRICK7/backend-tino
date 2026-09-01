package com.tino.backend.receiving.application.usecase;

import com.tino.backend.receiving.application.model.PurchaseDocument;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

final class PurchaseDocumentFingerprint {
    private PurchaseDocumentFingerprint() {}

    static String sha256(PurchaseDocument document) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            update(digest, document.source().name());
            update(digest, document.documentType().name());
            update(digest, document.accessKey());
            update(digest, document.issuedAt() == null ? null : document.issuedAt().toString());
            update(digest, document.issuer().name());
            update(digest, document.issuer().taxId());
            update(digest, document.total() == null ? null : document.total().toPlainString());
            for (var item : document.items()) {
                update(digest, Integer.toString(item.lineNumber()));
                update(digest, item.externalCode());
                update(digest, item.gtin());
                update(digest, item.rawDescription());
                update(digest, item.quantity() == null ? null : item.quantity().toPlainString());
                update(digest, item.unit());
                update(digest, item.unitPrice() == null ? null : item.unitPrice().toPlainString());
                update(digest, item.totalPrice() == null ? null : item.totalPrice().toPlainString());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        var encoded = value == null ? "-" : value.length() + ":" + value;
        digest.update(encoded.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }
}
