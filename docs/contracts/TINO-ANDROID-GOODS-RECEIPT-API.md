# TINO — Android Goods Receipt API

Status: `CONTRACT_AUTHORITY — TRIAL/LOCAL`

This document and the runtime OpenAPI at `/openapi` are the Android integration
authority. SERPRO Production/F7 is not authorized by this contract.

## Boundary and authentication

Every endpoint below requires a Keycloak-issued JWT in
`Authorization: Bearer <access_token>`. The backend resolves the authenticated
user, checks active membership and active business, establishes the PostgreSQL
transaction-local tenant context, and relies on RLS. Android never sends a
tenant header and never receives SERPRO credentials, tokens, endpoint URLs,
external DTOs, `nfeProc`, raw fiscal JSON, canonical fiscal JSON, or parser
versions.

The generated OpenAPI declares the `bearerAuth` HTTP bearer/JWT scheme. Swagger
UI is available at `/swagger-ui.html` when the backend is running.

## Endpoint table

| Method | Path | Required input | Success |
|---|---|---|---|
| `POST` | `/api/v1/businesses/{businessId}/nfe-documents` | `Idempotency-Key`, `{access_key}` | `NfeResponse`, including `preview` after an authorized successful retrieval |
| `GET` | `/api/v1/businesses/{businessId}/nfe-documents/{documentId}` | — | persisted `NfeResponse` without fiscal raw/canonical data |
| `GET` | `/api/v1/businesses/{businessId}/nfe-documents/{documentId}/preview` | — | `PreviewResponse` |
| `POST` | `/api/v1/businesses/{businessId}/nfe-documents/{documentId}/reprocess` | `Idempotency-Key` | versioned `NfeResponse` |
| `GET` | `/api/v1/businesses/{businessId}/products?q={text}` | `q` or `gtin` | `ProductSearchItem[]` |
| `GET` | `/api/v1/businesses/{businessId}/products?gtin={gtin}` | `q` or `gtin` | `ProductSearchItem[]` |
| `POST` | `/api/v1/businesses/{businessId}/goods-receipts/{previewId}/confirm` | `Idempotency-Key`, `ConfirmRequest` | authoritative `GoodsReceiptResult` |
| `GET` | `/api/v1/businesses/{businessId}/goods-receipts/{receiptId}` | — | authoritative `GoodsReceiptResult` |

`Idempotency-Key` is non-blank and at most 200 characters. It is required for
retrieval, reprocess, and confirmation. A retrieval key cannot be reused for a
different access key (`IDEMPOTENCY_CONFLICT`). Confirmation is exactly-once by
the preview's unique receipt constraint and inventory movement uniqueness;
retries return the existing result and never add stock a second time.

## Request DTOs

Retrieve NF-e:

```json
{
  "access_key": "53160911510448000171550010000106771000187760"
}
```

Confirm a human-reviewed preview:

```json
{
  "preview_version": 0,
  "items": [
    {
      "line_number": 1,
      "action": "CREATE_PRODUCT",
      "product_id": null,
      "base_unit": "RS",
      "conversion_factor": null
    }
  ]
}
```

`action` is one of `USE_EXISTING`, `CREATE_PRODUCT`, or `IGNORE`.
`USE_EXISTING` requires a real `product_id`. `CREATE_PRODUCT` requires an
explicit human decision and a base unit when the preview has no resolved
unit. If purchase and base units differ, `conversion_factor` is required and
must be positive. The backend never invents a conversion.

## Response DTOs

### NfeResponse

```json
{
  "document_id": "01a04d7c-a223-757f-8a96-861ceefd8ec7",
  "access_key": "53160911510448000171550010000106771000187760",
  "retrieval_status": "SUCCESS",
  "fiscal_status": "AUTHORIZED",
  "item_count": 1,
  "error_code": null,
  "retryable": false,
  "preview": { "preview_id": "uuid", "status": "REVIEW_REQUIRED", "version": 0 }
}
```

`RetrievalStatus` is `PENDING`, `IN_PROGRESS`, `SUCCESS`, `NOT_FOUND`,
`FAILED`, or `OUTCOME_UNKNOWN`. `FiscalStatus` is `AUTHORIZED`, `CANCELLED`,
`DENIED`, or `UNKNOWN`. A cancelled or denied NF-e may be displayed and
persisted but cannot produce a confirmed Goods Receipt or inventory movement.

### PreviewResponse

```json
{
  "preview_id": "uuid",
  "document_id": "uuid",
  "document_number": "15430",
  "series": "0",
  "issuer": { "legal_name": "Fornecedor", "trade_name": "Fornecedor" },
  "retrieval_status": "SUCCESS",
  "fiscal_status": "AUTHORIZED",
  "status": "REVIEW_REQUIRED",
  "version": 0,
  "summary": {
    "total_items": 1,
    "matched_items": 0,
    "new_candidate_items": 1,
    "review_required_items": 0
  },
  "items": [
    {
      "line_number": 1,
      "description": "Produto da NF-e",
      "supplier_product_code": "346",
      "gtin": null,
      "resolution_status": "NEW_CANDIDATE",
      "product_id": null,
      "candidate_name": "Produto da NF-e",
      "purchase_unit": "RS",
      "purchase_quantity": 5.0,
      "purchase_unit_cost": 149.0,
      "product_total": 745.0,
      "base_unit": null,
      "conversion_factor": null,
      "stock_quantity": null,
      "requires_user_action": true
    }
  ]
}
```

`ProductResolutionStatus` is `MATCHED`, `NEW_CANDIDATE`, `NEEDS_REVIEW`, or
`IGNORED`. Resolution remains backend-owned and follows usable GTIN, then
issuer plus supplier product code, then candidate/human review. Missing or
`SEM GTIN` is valid and does not reject a fiscal document. Decimal values are
JSON numbers backed by `BigDecimal`; clients must not coerce quantities or
costs to integers or binary floating-point for business calculations.

### ProductSearchItem

```json
[
  {
    "product_id": "uuid",
    "name": "SULFITE A4 75GR BOREAL (5000FLS)",
    "base_unit": "RS",
    "gtin": "7891234567895"
  }
]
```

Search is active-product, tenant- and membership-scoped, bounded to 1–100
results (default 50), and accepts text (`q`) or an applicable GTIN. It is a
small explicit-selection API, not an ERP catalog API.

### GoodsReceiptResult

```json
{
  "receipt_id": "uuid",
  "status": "CONFIRMED",
  "item_count": 1,
  "items": [
    {
      "line_number": 1,
      "product_id": "uuid",
      "product_name": "SULFITE A4 75GR BOREAL (5000FLS)",
      "base_unit": "RS",
      "quantity_added": 5.0,
      "unit_cost": 149.0
    }
  ]
}
```

`GoodsReceiptStatus` is `CONFIRMED` or `CANCELLED`. The GET by `receiptId` is
the reconciliation path after timeout, process death, or app restart. Android
projects this result into Room and does not execute a second local stock
mutation for the remote NF-e operation.

## Error contract

Errors use this envelope:

```json
{
  "code": "PACKAGING_CONVERSION_REQUIRED",
  "message": "packaging conversion is required",
  "retryable": false,
  "correlation_id": "uuid"
}
```

Stable codes are:

| Code | Meaning | Retryable |
|---|---|---|
| `INVALID_ACCESS_KEY` | access key is malformed | no |
| `NFE_NOT_FOUND` | fiscal document, preview, or receipt is absent in the tenant | no |
| `RETRIEVAL_UNAVAILABLE` | provider/retrieval failed | depends on response |
| `OUTCOME_UNKNOWN` | provider result is ambiguous | no automatic duplicate retry |
| `FISCAL_CANCELLED` | cancelled NF-e cannot enter stock | no |
| `FISCAL_DENIED` | denied NF-e cannot enter stock | no |
| `PRODUCT_REVIEW_REQUIRED` | product decision is incomplete | no |
| `PACKAGING_CONVERSION_REQUIRED` | explicit conversion is missing/invalid | no |
| `STALE_PREVIEW` | preview version/state is no longer confirmable | no |
| `INVALID_PRODUCT_SELECTION` | product decision is invalid | no |
| `BUSINESS_ACCESS_DENIED` | membership/business access failed | no |
| `IDEMPOTENCY_CONFLICT` | key addresses a different logical request | no |

Android branches on `code`, never on `message`.

## Sync and local-first projection

Manual receipts remain an Android/Room-first operation for offline use and can
be synchronized through the existing sync contracts. The NF-e remote path uses
the immediate `GoodsReceiptResult` from confirmation as its authoritative
projection path, followed by `GET /goods-receipts/{receiptId}` when recovery is
needed. The current generic `/v1/sync/changes` feed is not used to create a
second NF-e stock movement.

If a future sync projection is added, it must use the stable `receipt_id` and
deduplicate against the remote receipt/movement identity. It must project the
already-confirmed operation, never call the legacy local receipt mutation as a
second inventory action. A manual receipt linked to an NF-e later must retain
one GoodsReceipt identity and must not create a second movement.

## Scope and safety

There is one operational GoodsReceipt concept; company type, MEI status,
tax regime, or formality never enables or disables manual/NF-e entry modes.
Catalog creation is never silent, fiscal raw data stays server-side, and the
same PostgreSQL transaction coordinates receiving, catalog authorization,
mapping/conversion, receipt persistence, and inventory movement. Outbox is not
used to weaken that atomicity.

SERPRO Trial credentials are runtime secrets only. SERPRO Production/F7,
retention policy for production raw fiscal data, tax/ERP/ SPED, OCR, and sale
price changes are outside this contract.
