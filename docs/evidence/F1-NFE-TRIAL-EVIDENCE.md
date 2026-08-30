# F1 — Fiscal Trial Evidence

**Status:** `PASS — F1 TRIAL IMPLEMENTATION COMPLETE`
**Date:** 2026-08-29
**Authorization:** `APPROVED_FOR_TRIAL_IMPLEMENTATION`
**Production:** `NOT AUTHORIZED — F7 DEFERRED`

## Scope delivered

The implementation is limited to:

```text
NfeAccessKey → OAuth client_credentials → SerproNfeAdapter
→ raw provider response → versioned parser → CanonicalNfeDocument
```

No Product Catalog, Goods Receipt, Inventory, catalog mutation, stock mutation,
Flyway migration, or real SERPRO secret was introduced.

## Files changed

- `settings.gradle.kts` — registers `modules:fiscal`;
- `app/build.gradle.kts` — includes the fiscal module in the application;
- `modules/fiscal/build.gradle.kts` — module dependencies and JUnit launcher;
- `modules/fiscal/src/main/java/...` — F1 domain, application port/use case,
  OAuth client, SERPRO adapter, parser and Spring composition;
- `modules/fiscal/src/test/...` — domain/parser/adapter tests;
- `modules/fiscal/src/test/resources/serpro/consulta-nfe-trial-official-sanitized.json`
  — sanitized fixture using an official Trial-listed fictitious key;
- `docs/TINO-NFE-GOODS-RECEIPT-GOAL.md` — status and P0 decisions updated;
- this evidence file.

## Trial fixture

Official Trial-listed fictitious access key used:

```text
53160911510448000171550010000106771000187760
```

The fixture preserves the official response shape (`nfeProc`, `protNFe`, `NFe`,
`infNFe`, `ide`, `emit`, `det`, `prod`) and contains no Consumer Secret,
Bearer token, or real credential.

## Canonical result proven

The parser produced the following relevant canonical values from the fixture:

```json
{
  "access_key": "53160911510448000171550010000106771000187760",
  "number": "15430",
  "series": "0",
  "fiscal_status": "AUTHORIZED",
  "issuer_document": "56776378000136",
  "item_count": 1,
  "item": {
    "line_number": 1,
    "supplier_product_code": "346",
    "description": "SULFITE A4 75GR BOREAL (5000FLS)",
    "commercial_unit": "RS",
    "commercial_quantity": "5",
    "commercial_unit_price": "149",
    "product_total": "745",
    "ncm": "48025610"
  },
  "parser_version": "nfe-parser-v1"
}
```

Quantities and monetary fields are parsed as `BigDecimal`; identifiers are
preserved as text. The raw response is carried as a separate `RawNfePayload`
and never becomes an HTTP/domain response object.

## Adapter behavior proven

- OAuth2 `client_credentials` with Basic credentials;
- in-memory token reuse;
- one controlled token renewal after `401`;
- at most one retry for `408`, `500` and `504`, using the injected delay policy;
- `404` mapped to `RetrievalStatus.NOT_FOUND`;
- malformed provider payload mapped to `FAILED` without a canonical document;
- timeout mapped to `OUTCOME_UNKNOWN`;
- `X-Request-Tag: tino-nfe` sent without sensitive identifiers;
- no provider response body is copied into failure codes or logs.

The adapter tests use a local JDK HTTP server as a deterministic Trial gateway
double. No network call to SERPRO was made because no real Consumer Key/Secret
is configured or stored in the repository.

## Test evidence

Focused F1 command:

```text
./gradlew :modules:fiscal:test --no-daemon --console=plain
BUILD SUCCESSFUL
18 tests, 0 failures, 0 errors
```

Full repository gates:

```text
./gradlew test architecture migrations --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL in 3m 3s
```

Additional gates:

```text
./scripts/secret-scan.sh
Secret scan passed.

git diff --check
PASS
```

The full test command also passed the existing architecture and empty-database
Flyway verification. There is no new migration in F1.

## Gate decision

F1 is complete and its gates are green. F2 is now eligible for a separate
authorized step, but was not started automatically. F7 SERPRO Production remains
explicitly blocked until the real contracted Swagger/endpoint, retention policy,
and Production retry/billing policy are approved.
