package com.tino.backend.fiscal.adapter.out.serpro;

import com.tino.backend.fiscal.domain.model.CanonicalNfeDocument;
import com.tino.backend.fiscal.domain.model.CanonicalNfeIssuer;
import com.tino.backend.fiscal.domain.model.CanonicalNfeItem;
import com.tino.backend.fiscal.domain.model.FiscalStatus;
import com.tino.backend.fiscal.domain.model.NfeAccessKey;
import com.tino.backend.fiscal.application.port.out.NfeParser;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Maps the SERPRO NF-e JSON shape into TINO-owned canonical types. */
public final class SerproNfeParser implements NfeParser {
    public static final String VERSION = "nfe-parser-v1";

    private final ObjectMapper mapper;

    public SerproNfeParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public CanonicalNfeDocument parse(String rawJson, NfeAccessKey requestedKey) {
        try {
            var root = mapper.readTree(rawJson);
            var nfeProc = required(root, "nfeProc");
            var nfe = required(nfeProc, "NFe");
            var infNfe = required(nfe, "infNFe");
            var ide = required(infNfe, "ide");
            var emit = required(infNfe, "emit");
            var protocol = optionalObject(nfeProc, "protNFe");
            var protocolInfo = optionalObject(protocol, "infProt");
            var responseKey = firstText(protocolInfo, "chNFe", infNfe.get("Id"));
            var accessKey = responseKey == null ? requestedKey : new NfeAccessKey(responseKey.replaceFirst("^NFe", ""));
            return new CanonicalNfeDocument(
                    accessKey,
                    text(ide, "nNF"),
                    text(ide, "serie"),
                    instant(text(ide, "dhEmi")),
                    text(ide, "natOp"),
                    integer(ide, "tpNF"),
                    issuer(emit),
                    fiscalStatus(root, protocolInfo),
                    items(infNfe),
                    VERSION);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException) throw exception;
            throw new IllegalArgumentException("SERPRO NF-e payload could not be parsed", exception);
        }
    }

    private static CanonicalNfeIssuer issuer(JsonNode emit) {
        return new CanonicalNfeIssuer(text(emit, "CNPJ", "CPF"), requiredText(emit, "xNome"),
                text(emit, "xFant"), text(emit, "IE"));
    }

    private static List<CanonicalNfeItem> items(JsonNode infNfe) {
        var details = required(infNfe, "det");
        var result = new ArrayList<CanonicalNfeItem>();
        if (details.isArray()) {
            for (var detail : details) result.add(item(detail));
        } else {
            result.add(item(details));
        }
        return result;
    }

    private static CanonicalNfeItem item(JsonNode detail) {
        var product = required(detail, "prod");
        return new CanonicalNfeItem(
                integer(detail, "nItem") == null ? 0 : integer(detail, "nItem"),
                text(product, "cProd"), text(product, "cEAN"), requiredText(product, "xProd"),
                text(product, "NCM"), text(product, "CEST"), text(product, "CFOP"),
                requiredText(product, "uCom"), decimal(product, "qCom"), decimal(product, "vUnCom"),
                decimal(product, "vProd"), text(product, "cEANTrib"), text(product, "uTrib"),
                decimal(product, "qTrib"), decimal(product, "vUnTrib"), decimal(product, "vDesc"),
                decimal(product, "vFrete"), decimal(product, "vSeg"), decimal(product, "vOutro"),
                flag(product, "indTot"));
    }

    private static FiscalStatus fiscalStatus(JsonNode root, JsonNode protocolInfo) {
        var serialized = root.toString().toLowerCase(Locale.ROOT);
        if (serialized.contains("110111") || serialized.contains("cancelamento")
                || serialized.contains("cancelada")) return FiscalStatus.CANCELLED;
        var statusCode = integer(protocolInfo, "cStat");
        if (statusCode != null && (statusCode == 100 || statusCode == 150)) return FiscalStatus.AUTHORIZED;
        if (statusCode != null && (statusCode == 301 || statusCode == 302 || statusCode == 303)) {
            return FiscalStatus.DENIED;
        }
        return FiscalStatus.UNKNOWN;
    }

    private static Instant instant(String value) {
        if (value == null) return null;
        try { return OffsetDateTime.parse(value).toInstant(); }
        catch (Exception ignored) { return Instant.parse(value); }
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        var value = text(node, field);
        return value == null ? null : new BigDecimal(value);
    }

    private static Boolean flag(JsonNode node, String field) {
        var value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) return null;
        return value.isBoolean() ? value.booleanValue() : "1".equals(value.toString());
    }

    private static Integer integer(JsonNode node, String field) {
        var value = text(node, field);
        return value == null ? null : Integer.valueOf(value);
    }

    private static String firstText(JsonNode node, String field, JsonNode fallback) {
        var value = text(node, field);
        if (value != null) return value;
        return scalar(fallback);
    }

    private static String requiredText(JsonNode node, String field) {
        var value = text(node, field);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required in SERPRO payload");
        return value;
    }

    private static String text(JsonNode node, String... fields) {
        if (node == null) return null;
        for (var field : fields) {
            var value = node.get(field);
            var scalar = scalar(value);
            if (scalar != null && !scalar.isBlank()) return scalar;
        }
        return null;
    }

    private static String scalar(JsonNode node) {
        if (node == null || node.isNull() || node.isObject() || node.isArray()) return null;
        return node.isString() ? node.stringValue() : node.toString();
    }

    private static JsonNode required(JsonNode node, String field) {
        var value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) throw new IllegalArgumentException(field + " is required in SERPRO payload");
        return value;
    }

    private static JsonNode optionalObject(JsonNode node, String field) {
        var value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value;
    }
}
