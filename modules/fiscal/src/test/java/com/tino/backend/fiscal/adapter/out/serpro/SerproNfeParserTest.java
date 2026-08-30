package com.tino.backend.fiscal.adapter.out.serpro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tino.backend.fiscal.domain.model.FiscalStatus;
import com.tino.backend.fiscal.domain.model.NfeAccessKey;
import java.io.IOException;
import java.io.InputStream;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SerproNfeParserTest {
    private static final NfeAccessKey KEY = new NfeAccessKey("53160911510448000171550010000106771000187760");

    @Test
    void mapsOfficialTrialFixtureToCanonicalDocumentWithExactDecimals() throws IOException {
        var document = new SerproNfeParser(new ObjectMapper()).parse(fixture(), KEY);

        assertThat(document.accessKey().value()).isEqualTo(KEY.value());
        assertThat(document.number()).isEqualTo("15430");
        assertThat(document.series()).isEqualTo("0");
        assertThat(document.fiscalStatus()).isEqualTo(FiscalStatus.AUTHORIZED);
        assertThat(document.issuer().document()).isEqualTo("56776378000136");
        assertThat(document.items()).hasSize(1);
        var item = document.items().getFirst();
        assertThat(item.supplierProductCode()).isEqualTo("346");
        assertThat(item.gtin()).isNull();
        assertThat(item.commercialQuantity()).isEqualByComparingTo("5");
        assertThat(item.commercialUnitPrice()).isEqualByComparingTo("149");
        assertThat(item.productTotal()).isEqualByComparingTo("745");
        assertThat(item.ncm()).isEqualTo("48025610");
        assertThat(item.includedInTotal()).isTrue();
        assertThat(document.parserVersion()).isEqualTo(SerproNfeParser.VERSION);
    }

    @Test
    void acceptsOptionalFieldsAndScientificNotationAsBigDecimal() {
        var json = """
                {"nfeProc":{"protNFe":{"infProt":{"chNFe":"53160911510448000171550010000106771000187760","cStat":100}},
                "NFe":{"infNFe":{"ide":{"nNF":1,"serie":1,"dhEmi":"2026-01-01T00:00:00Z","natOp":"COMPRA","tpNF":1},
                "emit":{"xNome":"Fornecedor"},"det":{"nItem":1,"prod":{"xProd":"Produto","uCom":"UN","qCom":1.2E+2,"vUnCom":3.4E-1,"vProd":40.8}}}}}}
                """;
        var item = new SerproNfeParser(new ObjectMapper()).parse(json, KEY).items().getFirst();
        assertThat(item.commercialQuantity()).isEqualByComparingTo("120");
        assertThat(item.commercialUnitPrice()).isEqualByComparingTo("0.34");
        assertThat(item.productTotal()).isEqualByComparingTo("40.8");
        assertThat(item.taxQuantity()).isNull();
    }

    @Test
    void rejectsMalformedProviderPayload() {
        assertThatThrownBy(() -> new SerproNfeParser(new ObjectMapper()).parse("{}", KEY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String fixture() throws IOException {
        try (InputStream stream = SerproNfeParserTest.class.getResourceAsStream("/serpro/consulta-nfe-trial-official-sanitized.json")) {
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
