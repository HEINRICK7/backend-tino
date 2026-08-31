# TINO External Business Data Source — Evidence

Status: **IMPLEMENTED FOR LOCAL/TRIAL CONTRACT; REAL PROVIDER CONTRACT AND CREDENTIALS PENDING**

## Scope and architecture

The capability is generic and lives in the `external` module. `DOCES_SONHOS`
is only the first outer adapter; there is no provider-specific domain module.
The application boundary is `ExternalCatalogProvider`, which returns the
provider-neutral `ExternalCatalogPage` and `ExternalProduct`. HTTP, JSON,
runtime credentials and provider field aliases stop in
`DocesSonhosCatalogAdapter`.

`catalog` remains the owner of TINO `Product`. External data is projected into
that existing product through its public `ProductCatalog` port. Mapping and
price-option tables are written by the catalog persistence adapter in the same
tenant transaction as the product projection.

Businesses without a connection are represented as `TINO_NATIVE`; registered
connections use `EXTERNAL_API`. The source view is exposed by
`GET /api/v1/businesses/{businessId}/data-source`.

## Database and tenancy

Migration `V14__external_business_data_source.sql` adds:

- `products.sale_price` as nullable `NUMERIC(24,9)`;
- `external_business_connections` with provider, source type, lifecycle,
  cursor/watermark, last successful sync and safe counters;
- `external_product_mappings` with the unique identity
  `(business_id, provider_connection_id, external_product_id)`, TINO product,
  external update time and last synced time;
- `external_product_price_options` with exact decimal prices, quantity, unit,
  `unit_raw`, default marker and category context.

All new tables have forced PostgreSQL RLS keyed by the existing transaction-local
`app.business_id`. No client tenant header is used. Persistence is jOOQ-only.

External product IDs generate a stable projection identity from business,
connection and external ID. Re-delivery therefore updates the same TINO
product/mapping and does not create a duplicate product. Names are not identity
and are never used to silently resolve a product.

## Sync and failure semantics

The sync lifecycle is `CONNECTED → SYNCING → READY`, with `DEGRADED`,
`AUTH_ERROR` and `FAILED` outcomes. A page is parsed and projected inside a
tenant transaction before its cursor is persisted. The completed watermark is
written only after the final page succeeds. A failed run keeps the last good
projection and does not report a new successful synchronization.

Only an explicit `active=false`/`isActive=false` deactivates a product. Missing
products are not deactivated and nothing is deleted. Multiple price options are
preserved; only the explicit default option is used for the operational default
price. Monetary values use `BigDecimal` and invalid values such as `NaN` are
rejected. Units retain `unitRaw`; no P/G/Cento/size conversion is invented.

The provider adapter uses HTTPS-capable configured URLs, bearer credentials only
in runtime memory, no credential request field, no raw response logging, and a
single controlled retry for timeout/network/408/429/5xx responses. 401/403 are
classified as authentication errors. No Android call to the external provider
exists.

## Consumed provider contract

The preferred versioned route is `GET /integrations/tino/v1/products` with an
envelope containing `products`, `next_cursor`, `watermark`, and product fields
`id`, `name`, `active`, `updated_at`, `price_options`, `category` and
`subcategory`. Price options contain `id`, `label`, `quantity`, `unit`, `price`
and `is_default`. The adapter also accepts the locally documented legacy direct
array (`GET /products`) for controlled compatibility; it does not treat that
legacy shape as the final contract because it has no cursor or update watermark.
The external contract must be frozen jointly before a real provider smoke.

## APIs

- `POST /api/v1/businesses/{businessId}/external-connections` registers the
  provider; repeated registration for the same business/provider is replay-safe.
- `GET /api/v1/businesses/{businessId}/external-connections` lists status and
  last sync result without secrets.
- `GET /api/v1/businesses/{businessId}/external-connections/{connectionId}`
  returns one status.
- `POST /api/v1/businesses/{businessId}/external-connections/{connectionId}/sync`
  starts the synchronous initial/incremental sync.
- `GET /api/v1/businesses/{businessId}/data-source` returns the business source
  view and defaults to `TINO_NATIVE` when no external connection exists.

## Tests and gates

Implemented coverage includes:

- canonical provider page parsing, exact decimal handling, options and
  malformed decimal rejection;
- initial two-page sync, cursor advancement, explicit deactivation and replay;
- Spring context compatibility with the existing SERPRO client;
- PostgreSQL V14 migration, connection/product/options mapping, RLS isolation,
  and replay with one product;
- all existing module tests, architecture tests, migration tests, secret scan
  and `git diff --check` are required before checkpoint.

The real external HTTP smoke is intentionally not claimed here: no real
provider credentials or jointly frozen versioned contract are present. F7,
production endpoint/credentials and bidirectional write-back remain out of
scope.
