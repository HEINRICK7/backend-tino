package com.tino.backend.receiving.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tino.backend.receiving.application.exception.ReceivingException;
import com.tino.backend.receiving.application.model.PurchaseDocument;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PurchaseDocumentValidatorTest {
    private static final String KEY = "22260831838128000748650120002104021782591975";

    @Test
    void validatesPiNfceAndPreservesExactDecimalsAndEvidence() {
        var input = document("QUEIJO MUSS ISIS 150G FAT", new BigDecimal("10.790"));

        var validated = PurchaseDocumentValidator.validate(input);

        assertThat(validated.accessKey()).isEqualTo(KEY);
        assertThat(validated.items().get(0).rawDescription()).isEqualTo("QUEIJO MUSS ISIS 150G FAT");
        assertThat(validated.items().get(0).unitPrice()).isEqualByComparingTo("10.790");
    }

    @Test
    void rejectsWrongUfModelCheckDigitAndDuplicateLines() {
        assertInvalid(documentWithKey(KEY.replaceFirst("22", "35")));
        assertInvalid(documentWithKey(KEY.substring(0, 20) + "55" + KEY.substring(22)));
        assertInvalid(documentWithKey(KEY.substring(0, 43) + "0"));
        var duplicate = new PurchaseDocument(PurchaseDocument.Source.NFCE, PurchaseDocument.DocumentType.NFCE, KEY,
                null, new PurchaseDocument.Issuer("Fornecedor", "123"), List.of(
                        item(1, "A", BigDecimal.ONE), item(1, "B", BigDecimal.ONE)), BigDecimal.ONE);
        assertInvalid(duplicate);
    }

    @Test
    void allowsUnavailableOptionalEvidenceAsNullButRequiresCoreFields() {
        var valid = new PurchaseDocument(PurchaseDocument.Source.NFCE, PurchaseDocument.DocumentType.NFCE, KEY,
                null, new PurchaseDocument.Issuer(null, null), List.of(
                        new PurchaseDocument.Item(1, null, null, "ITEM", null, null, null, null)), null);
        assertThat(PurchaseDocumentValidator.validate(valid)).isEqualTo(valid);

        assertThatThrownBy(() -> PurchaseDocumentValidator.validate(
                new PurchaseDocument(PurchaseDocument.Source.NFCE, PurchaseDocument.DocumentType.NFCE, KEY,
                        null, null, valid.items(), null)))
                .isInstanceOf(ReceivingException.class);
    }

    private static PurchaseDocument document(String description, BigDecimal price) {
        return documentWithKey(KEY, description, price);
    }

    private static PurchaseDocument documentWithKey(String key) {
        return documentWithKey(key, "ITEM", BigDecimal.ONE);
    }

    private static PurchaseDocument documentWithKey(String key, String description, BigDecimal price) {
        return new PurchaseDocument(PurchaseDocument.Source.NFCE, PurchaseDocument.DocumentType.NFCE, key,
                null, new PurchaseDocument.Issuer("GRUPO VANGUARDA", "31838128000748"),
                List.of(item(1, description, price)), new BigDecimal("10.790"));
    }

    private static PurchaseDocument.Item item(int line, String description, BigDecimal price) {
        return new PurchaseDocument.Item(line, "249886", null, description, BigDecimal.ONE, "UN", price, price);
    }

    private static void assertInvalid(PurchaseDocument document) {
        assertThatThrownBy(() -> PurchaseDocumentValidator.validate(document))
                .isInstanceOf(ReceivingException.class);
    }
}
