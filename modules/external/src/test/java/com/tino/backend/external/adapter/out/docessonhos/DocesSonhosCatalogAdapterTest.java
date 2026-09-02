package com.tino.backend.external.adapter.out.docessonhos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tino.backend.external.application.exception.ExternalProviderMalformedException;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class DocesSonhosCatalogAdapterTest {
    private static final UUID CONNECTION = UUID.fromString("01a04d7c-a223-757f-8a96-861ceefd8ec7");

    @Test
    void mapsVersionedPageToCanonicalProductsAndPreservesAllPriceOptions() throws Exception {
        var body = fixture();
        var page = DocesSonhosCatalogAdapter.parsePage(new ObjectMapper(), body, CONNECTION, Instant.parse("2026-08-30T00:00:00Z"));

        assertThat(page.nextCursor()).isEqualTo("page-2");
        assertThat(page.watermark()).isEqualTo(Instant.parse("2026-08-30T12:01:00Z"));
        var product = page.products().getFirst();
        assertThat(product.providerConnectionId()).isEqualTo(CONNECTION);
        assertThat(product.externalId()).isEqualTo("bolo-50");
        assertThat(product.unitRaw()).isEqualTo("P");
        assertThat(product.defaultPrice()).isEqualByComparingTo("50.00");
        assertThat(product.priceOptions()).extracting("externalId").containsExactly("p", "g");
        assertThat(product.priceOptions().get(1).price()).isEqualByComparingTo("85.5");
        assertThat(product.categoryContext()).isEqualTo("Bolos");
    }

    @Test
    void rejectsNonDecimalPriceInsteadOfCoercingIt() {
        var body = """
                {"products":[{"id":"x","name":"Produto","price_options":[{"id":"p","label":"P","quantity":1,"unit":"UN","price":"NaN","is_default":true}]}]}
                """;
        assertThatThrownBy(() -> DocesSonhosCatalogAdapter.parsePage(new ObjectMapper(), body, CONNECTION, Instant.now()))
                .isInstanceOf(ExternalProviderMalformedException.class);
    }

    @Test
    void mapsLiveDocesSonhosPublicSnapshotContract() throws Exception {
        var page = DocesSonhosCatalogAdapter.parsePage(new ObjectMapper(), liveFixture(), CONNECTION,
                Instant.parse("2026-08-30T00:00:00Z"));

        assertThat(page.nextCursor()).isNull();
        assertThat(page.watermark()).isEqualTo(Instant.parse("2026-04-05T11:32:28.508Z"));
        var product = page.products().getFirst();
        assertThat(product.externalId()).isEqualTo("docinho_brownie_recheado_morango_brigadeiro_branco_doces");
        assertThat(product.active()).isTrue();
        assertThat(product.categoryContext()).isEqualTo("infantil");
        assertThat(product.subcategoryContext()).isEqualTo("festa_infantil");
        assertThat(product.priceOptions()).singleElement().satisfies(option -> {
            assertThat(option.externalId()).isEqualTo("b563dc72-e94b-4216-b90e-6c0782d2d818");
            assertThat(option.price()).isEqualByComparingTo("3.5");
            assertThat(option.unit()).isEqualTo("UN");
            assertThat(option.defaultOption()).isTrue();
        });
    }

    private static String fixture() throws Exception {
        try (InputStream stream = DocesSonhosCatalogAdapterTest.class.getResourceAsStream("/docessonhos/products-page.json")) {
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static String liveFixture() throws Exception {
        try (InputStream stream = DocesSonhosCatalogAdapterTest.class.getResourceAsStream("/docessonhos/live-public-products.json")) {
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
