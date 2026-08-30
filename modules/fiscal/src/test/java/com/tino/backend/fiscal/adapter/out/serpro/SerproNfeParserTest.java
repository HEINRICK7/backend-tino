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
        assertThat(document.issuedAt()).isEqualTo(java.time.Instant.parse("2017-06-05T11:31:06Z"));
        assertThat(document.natureOperation()).isEqualTo("VENDA");
        assertThat(document.operationType()).isEqualTo(1);
        assertThat(document.fiscalStatus()).isEqualTo(FiscalStatus.AUTHORIZED);
        assertThat(document.issuer().document()).isEqualTo("56776378000136");
        assertThat(document.issuer().legalName()).isEqualTo("COMERCIO DE TESTE LTDA EPP");
        assertThat(document.issuer().tradeName()).isEqualTo("COMERCIO DE TESTE");
        assertThat(document.issuer().stateRegistration()).isEqualTo("123456789123");
        assertThat(document.items()).hasSize(1);
        var item = document.items().getFirst();
        assertThat(item.lineNumber()).isEqualTo(1);
        assertThat(item.supplierProductCode()).isEqualTo("346");
        assertThat(item.gtin()).isNull();
        assertThat(item.description()).isEqualTo("SULFITE A4 75GR BOREAL (5000FLS)");
        assertThat(item.ncm()).isEqualTo("48025610");
        assertThat(item.cest()).isEqualTo("9999999");
        assertThat(item.cfop()).isEqualTo("5102");
        assertThat(item.commercialUnit()).isEqualTo("RS");
        assertThat(item.commercialQuantity()).isEqualByComparingTo("5");
        assertThat(item.commercialUnitPrice()).isEqualByComparingTo("149");
        assertThat(item.productTotal()).isEqualByComparingTo("745");
        assertThat(item.taxGtin()).isNull();
        assertThat(item.taxUnit()).isEqualTo("RS");
        assertThat(item.taxQuantity()).isEqualByComparingTo("5");
        assertThat(item.taxUnitPrice()).isEqualByComparingTo("149");
        assertThat(item.discount()).isEqualByComparingTo("0");
        assertThat(item.freight()).isEqualByComparingTo("0");
        assertThat(item.insurance()).isEqualByComparingTo("0");
        assertThat(item.otherValue()).isEqualByComparingTo("0");
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
