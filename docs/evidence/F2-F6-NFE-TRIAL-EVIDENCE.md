# TINO NF-e Trial — Evidência consolidada F2–F6

Status: `PASS — TRIAL IMPLEMENTATION F2–F6 COMPLETE`

## Escopo entregue

- F2: persistência fiscal multi-tenant em PostgreSQL com `JSONB`, versões raw/canônicas, hash SHA-256, estados independentes, idempotência por `business_id` + chave, FKs compostas e RLS `FORCE` fail-closed; reprocessamento local do raw sem provider.
- F3: catálogo mínimo com produto, GTIN/EAN tenant-scoped, mapping por emitente + `cProd` e resolução determinística; item desconhecido vira candidato.
- F4: conversão de embalagem revisável (`BigDecimal`) e preview operacional sem raw fiscal; consulta/preview não movimentam estoque.
- F5: confirmação explícita de Goods Receipt; criação autorizada de produto, mapping, conversão, receipt, movimento e saldo na mesma transação PostgreSQL; NF-e cancelada/denegada bloqueada.
- F6: API autenticada, idempotency headers, versão do preview, métricas sanitizadas, timeout, retry limitado, renovação única de token, `OUTCOME_UNKNOWN`, breaker/bulkhead configurados e OpenAPI/Swagger exposto pelo app.

## Arquivos principais

- `app/src/main/resources/db/migration/V10__fiscal_nfe.sql`
- `app/src/main/resources/db/migration/V11__catalog_products.sql`
- `app/src/main/resources/db/migration/V12__receiving_inventory.sql`
- `app/src/main/resources/db/migration/V13__fiscal_permissions.sql`
- `modules/fiscal/`
- `modules/catalog/`
- `modules/receiving/`
- `modules/inventory/`
- `app/src/main/resources/application.yml`
- `docs/TINO-NFE-GOODS-RECEIPT-GOAL.md`

A aplicação de movimento de estoque é idempotente também no saldo: um movimento
já existente não incrementa `inventory_balances` novamente.

## API Trial

```text
POST /api/v1/businesses/{businessId}/nfe-documents
GET  /api/v1/businesses/{businessId}/nfe-documents/{documentId}
POST /api/v1/businesses/{businessId}/nfe-documents/{documentId}/reprocess
GET  /api/v1/businesses/{businessId}/nfe-documents/{documentId}/preview
POST /api/v1/businesses/{businessId}/goods-receipts/{previewId}/confirm
```

Todas as rotas exigem Bearer JWT e membership ativa. A recuperação exige
`Idempotency-Key`; a confirmação exige `Idempotency-Key`, `previewVersion` e
as decisões explícitas por item. Raw/token não são retornados pela API.

## Fixture e prova fiscal

Fixture: `modules/fiscal/src/main/resources/serpro/consulta-nfe-trial-official-sanitized.json`.
The same sanitized fixture is retained in test resources for parser tests.
Chave Trial fictícia: `53160911510448000171550010000106771000187760`.
O adapter foi coberto com sucesso, 401 + renovação única, 408/500/504 com no
máximo um retry, 404, payload inválido e timeout como `OUTCOME_UNKNOWN`.
O reprocessamento foi coberto com teste unitário que verifica parser local,
persistência de nova versão e repetição idempotente sem chamada externa.

## Comandos executados

```text
./gradlew :modules:fiscal:test --rerun-tasks --no-daemon --console=plain
./gradlew :modules:fiscal:test --tests com.tino.backend.fiscal.application.usecase.ReprocessNfeTest --rerun-tasks --no-daemon --console=plain
./gradlew test --rerun-tasks --no-daemon --console=plain
./gradlew architecture migrations --rerun-tasks --no-daemon --console=plain
./scripts/secret-scan.sh
git diff --check
TINO_APP_PORT=58080 TINO_KEYCLOAK_PORT=58082 ./scripts/trial-smoke-test.sh
```

Resultado final: suíte Gradle verde, Modulith/architecture verde, migrations
V0–V13 válidas, secret scan verde, smoke E2E Trial verde e nenhum erro no diff.

Smoke E2E observado no Compose isolado: `201` na criação do negócio, `200` na
recuperação/preview/confirm/reprocessamento, zero movimentos e saldos antes da
confirmação, um receipt e um movimento depois, saldo `5.000000000`, confirmação
repetida retornando o mesmo receipt e duas versões fiscais após reprocessar.

## Limites mantidos

Nenhum endpoint, secret, billing ou contrato SERPRO de Produção foi criado.
F7 permanece bloqueada até contrato/Swagger real, retenção/expurgo e operação
serem aprovados.
