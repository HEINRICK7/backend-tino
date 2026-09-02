# TINO — Item Purpose Resolution

## Endpoint

`POST /api/v1/businesses/{businessId}/business-understanding/item-purpose/resolve`

The request accepts `product_id` or `description`, an optional `usage_context`,
an optional `semantic_hints` list, and `source`. The `businessId` is only a
requested target: membership authorization and PostgreSQL RLS establish the
tenant.

Example request:

```json
{
  "description": "Farinha de trigo",
  "usage_context": "PURCHASE",
  "semantic_hints": [
    {
      "purpose": "PRODUCTION",
      "source": "CATALOG",
      "reason": "catalog category indicates a production input"
    }
  ]
}
```

The response exposes `purpose`, `confidence`, `resolution`, `authority`,
`needsConfirmation`, `suggestions`, and explainable `evidence`. Automatic
decisions use `SYSTEM_SUGGESTED`; unresolved or conflicting decisions use
`UNKNOWN` and require confirmation.

## Deterministic resolution order

1. Exact contextual history, choosing the highest authority between the
   product identity and canonical item key:
   `USER_CONFIRMED > LEARNED > SYSTEM_SUGGESTED > UNKNOWN`.
2. Explicit semantic usage context such as `DIRECT_SALE`,
   `SERVICE_CONSUMPTION`, or `PRODUCTION_INPUT`.
3. Business activities and operating modes as contextual evidence.
4. Explicit catalog/classifier hints supplied in `semantic_hints`, used only
   to break an ambiguity that remains after business context is considered.
5. `UNKNOWN` when the available signals do not safely distinguish purposes.

The resolver never interprets product names as universal rules. For example,
`SHAMPOO` remains ambiguous for a salon that both provides services and
resells goods when its usage context is only `PURCHASE`. The current catalog
contract does not generate semantic hints automatically; this optional input
is ready for a future catalog adapter without coupling this module to it.

Suggestions are read-only. They are not persisted automatically and cannot
overwrite `USER_CONFIRMED`. Only the explicit confirmation endpoint can
record or correct a user-confirmed contextual purpose.
