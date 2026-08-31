package com.tino.backend.external.adapter.out.docessonhos;

import com.tino.backend.external.application.exception.ExternalProviderAuthenticationException;
import com.tino.backend.external.application.exception.ExternalProviderMalformedException;
import com.tino.backend.external.application.exception.ExternalProviderUnavailableException;
import com.tino.backend.external.application.model.ExternalCatalogPage;
import com.tino.backend.external.application.model.ExternalPriceOption;
import com.tino.backend.external.application.model.ExternalProduct;
import com.tino.backend.external.application.port.out.ExternalCatalogProvider;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** First outer adapter. It converts the versioned provider contract into canonical products. */
public final class DocesSonhosCatalogAdapter implements ExternalCatalogProvider {
    public static final String PROVIDER = "DOCES_SONHOS";
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final URI baseUri;
    private final String path;
    private final String runtimeToken;
    private final Duration timeout;

    public DocesSonhosCatalogAdapter(HttpClient http, ObjectMapper mapper, URI baseUri, String path,
            String runtimeToken, Duration timeout) {
        this.http = http;
        this.mapper = mapper;
        this.baseUri = baseUri;
        this.path = path == null || path.isBlank() ? "/integrations/tino/v1/products" : path;
        this.runtimeToken = runtimeToken == null ? "" : runtimeToken;
        this.timeout = timeout;
    }

    @Override
    public String provider() { return PROVIDER; }

    @Override
    public ExternalCatalogPage fetch(UUID connectionId, String cursor, Instant watermark) {
        if (baseUri == null || runtimeToken.isBlank()) throw new ExternalProviderAuthenticationException();
        var request = HttpRequest.newBuilder(uri(cursor, watermark)).timeout(timeout)
                .header("Accept", "application/json").header("Authorization", "Bearer " + runtimeToken).GET().build();
        for (var attempt = 0; attempt < 2; attempt++) {
            try {
                var response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 401 || response.statusCode() == 403) throw new ExternalProviderAuthenticationException();
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parsePage(mapper, response.body(), connectionId, Instant.now());
                }
                if (!retryable(response.statusCode()) || attempt == 1) throw new ExternalProviderUnavailableException();
                pause();
            } catch (ExternalProviderAuthenticationException | ExternalProviderMalformedException exception) {
                throw exception;
            } catch (HttpTimeoutException exception) {
                if (attempt == 1) throw new ExternalProviderUnavailableException(exception);
                pause();
            } catch (IOException exception) {
                if (attempt == 1) throw new ExternalProviderUnavailableException(exception);
                pause();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ExternalProviderUnavailableException(exception);
            }
        }
        throw new ExternalProviderUnavailableException();
    }

    static ExternalCatalogPage parsePage(ObjectMapper mapper, String body, UUID connectionId, Instant fallbackTime) {
        try {
            var root = mapper.readTree(body);
            var productsNode = root.isArray() ? root : first(root, "products", "data");
            if (productsNode == null || !productsNode.isArray()) throw new IllegalArgumentException("products array is required");
            var products = new ArrayList<ExternalProduct>();
            for (var i = 0; i < productsNode.size(); i++) products.add(parseProduct(productsNode.get(i), connectionId, fallbackTime, i));
            var next = text(root, "next_cursor", "nextCursor");
            var watermark = instant(root, "watermark", "updated_at", "updatedAt");
            return new ExternalCatalogPage(products, next, watermark == null ? fallbackTime : watermark);
        } catch (ExternalProviderMalformedException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ExternalProviderMalformedException(exception);
        }
    }

    private static ExternalProduct parseProduct(JsonNode node, UUID connectionId, Instant fallbackTime, int index) {
        var externalId = requiredText(node, "id", "external_id", "externalId");
        var name = requiredText(node, "name");
        var optionsNode = first(node, "price_options", "priceOptions");
        if (optionsNode == null || !optionsNode.isArray() || optionsNode.isEmpty()) throw new IllegalArgumentException("price_options is required");
        var options = new ArrayList<ExternalPriceOption>();
        for (var optionIndex = 0; optionIndex < optionsNode.size(); optionIndex++) {
            var option = optionsNode.get(optionIndex);
            var optionId = text(option, "id", "external_id", "externalId");
            if (optionId == null) optionId = "option-" + index + "-" + optionIndex;
            var rawUnit = requiredText(option, "unit");
            var price = decimal(option, "price");
            var quantity = decimal(option, "quantity");
            options.add(new ExternalPriceOption(optionId, textOr(option, "label", "name", optionId), quantity,
                    rawUnit, rawUnit, price, bool(option, "is_default", "isDefault")));
        }
        if (options.stream().filter(ExternalPriceOption::defaultOption).count() > 1) {
            throw new IllegalArgumentException("multiple default price options are ambiguous");
        }
        var defaultOption = options.stream().filter(ExternalPriceOption::defaultOption).findFirst().orElse(options.getFirst());
        var updated = instant(node, "updated_at", "updatedAt");
        var category = context(node, "category");
        var subcategory = context(node, "subcategory");
        return new ExternalProduct(connectionId, externalId, name, boolOr(node, true, "active", "isActive"),
                updated == null ? fallbackTime : updated, defaultOption.price(), options, defaultOption.quantity(),
                defaultOption.unit(), defaultOption.unitRaw(), category, subcategory);
    }

    private URI uri(String cursor, Instant watermark) {
        var value = baseUri.toString().replaceAll("/$", "") + (path.startsWith("/") ? path : "/" + path);
        var query = new ArrayList<String>();
        if (cursor != null && !cursor.isBlank()) query.add("cursor=" + encode(cursor));
        else if (watermark != null) query.add("updated_since=" + encode(watermark.toString()));
        return URI.create(query.isEmpty() ? value : value + "?" + String.join("&", query));
    }

    private static String context(JsonNode node, String field) {
        var value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) return null;
        return value.isObject() ? text(value, "name", "label") : scalar(value);
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        var value = node == null ? null : node.get(field);
        var serialized = scalar(value);
        if (serialized == null || serialized.isBlank()) throw new IllegalArgumentException(field + " is required");
        try {
            var result = new BigDecimal(serialized.trim());
            if (result.signum() < 0) throw new IllegalArgumentException(field + " cannot be negative");
            return result;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be a decimal", exception);
        }
    }

    private static Instant instant(JsonNode node, String... fields) {
        var value = text(node, fields);
        if (value == null) return null;
        try { return OffsetDateTime.parse(value).toInstant(); }
        catch (RuntimeException ignored) { return Instant.parse(value); }
    }

    private static boolean bool(JsonNode node, String... fields) {
        var value = node == null ? null : first(node, fields);
        if (value == null || value.isNull()) return false;
        if (!value.isBoolean()) throw new IllegalArgumentException("boolean field is malformed");
        return value.booleanValue();
    }

    private static boolean boolOr(JsonNode node, boolean fallback, String... fields) {
        var value = node == null ? null : first(node, fields);
        return value == null || value.isNull() ? fallback : bool(node, fields);
    }

    private static String requiredText(JsonNode node, String... fields) {
        var value = text(node, fields);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fields[0] + " is required");
        return value;
    }

    private static String textOr(JsonNode node, String first, String second, String fallback) {
        var value = text(node, first, second);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String text(JsonNode node, String... fields) {
        if (node == null) return null;
        for (var field : fields) {
            var value = scalar(node.get(field));
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static String scalar(JsonNode node) {
        if (node == null || node.isNull() || node.isObject() || node.isArray()) return null;
        return node.isString() ? node.stringValue() : node.toString();
    }

    private static JsonNode first(JsonNode node, String... fields) {
        if (node == null) return null;
        for (var field : fields) if (node.get(field) != null && !node.get(field).isNull()) return node.get(field);
        return null;
    }

    private static boolean retryable(int status) { return status == 408 || status == 429 || status >= 500; }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static void pause() { try { Thread.sleep(25L); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); } }
}
