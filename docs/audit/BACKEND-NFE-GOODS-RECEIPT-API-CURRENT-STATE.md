# TINO Backend — NF-e Goods Receipt API: auditoria do estado atual

**Data da fotografia:** 2026-08-30
**Branch auditada:** `develop`
**Checkpoint de referência:** `b491d54` (`docs: clarify optional nfe gtin coverage`)
**Escopo:** somente auditoria. Nenhum endpoint, domínio, migration ou comportamento foi alterado para produzir este documento.

## 1. Executive Summary

O backend já oferece um fluxo Trial/local funcional por HTTP:

```text
Bearer Keycloak
  -> POST nfe-documents (chave NF-e)
  -> consulta fiscal via adapter Trial/fixture
  -> raw + canonical persistidos e versionados
  -> preview com resolução de produto
  -> confirmação humana
  -> catálogo, Goods Receipt e Inventory Movement na mesma operação
```

O cliente Android consegue iniciar a consulta, acompanhar o resultado, ler o
preview e confirmar uma entrada. O efeito de estoque é produzido, porém não
há endpoint público para consultá-lo. O catálogo e as conversões também não
possuem API de consulta/manutenção independente; as decisões podem ser
enviadas como parte da confirmação quando o cliente já conhece o `product_id`
ou os dados da conversão.

Conclusões principais:

- **Fiscal/Trial:** implementado, com `SerproNfeAdapter`, OAuth em memória,
  retry controlado, parser `nfe-parser-v1`, raw/canonical e reprocessamento local.
- **Goods Receipt:** implementado com preview, decisão humana, bloqueio para
  NF-e `CANCELLED`/`DENIED`, transação síncrona e idempotência operacional.
- **Tenancy:** autenticação OIDC/JWT, membership ativa, contexto transacional
  `app.business_id` e RLS nas tabelas do fluxo.
- **Contrato móvel:** funcional, mas incompleto para uma UX de catálogo/estoque
  rica. O JSON runtime usa `snake_case`, enquanto os schemas públicos gerados
  pelo OpenAPI refletem nomes camelCase.
- **SERPRO Produção/F7:** permanece explicitamente fora do escopo e bloqueado.

## 2. Estado real por módulo

### Fiscal

- **Domain:** `NfeAccessKey`, `CanonicalNfeDocument`,
  `CanonicalNfeIssuer`, `CanonicalNfeItem`, `RawNfePayload`,
  `RetrievalStatus` e `FiscalStatus`.
- **Application:** `RetrieveNfe`, `RetrieveAndPersistNfe`, `GetNfeDocument`,
  `ReprocessNfe`; entrada pública por `NfeReader` e saídas por
  `NfeRetrievalPort`, `NfeParser` e `NfeDocumentRepository`.
- **Adapters:** `TrialFixtureNfeAdapter`, `SerproNfeAdapter`,
  `SerproOAuthClient`, `SerproNfeParser` e `JooqNfeDocumentRepository`.
- **HTTP:** não possui controller próprio; é orquestrado pelo controller de
  receiving.
- **Persistência:** `nfe_documents`, `nfe_document_versions`, `nfe_items` e
  `nfe_retrieval_idempotency_keys` em `V10__fiscal_nfe.sql`; a permissão
  complementar está em `V13__fiscal_permissions.sql`.
- **Boundary SERPRO:** o adapter conhece HTTP/OAuth/SERPRO; não conhece
  catálogo, receiving, inventory nem persistência.
- **Eventos/outbox:** não há evento/outbox para o fluxo auditado.

O parser lê o layout real da fixture sanitizada
`modules/fiscal/src/test/resources/serpro/consulta-nfe-trial-official-sanitized.json`
e produz o canonical sem expor o payload ao Android. A chave `cEAN` é
opcional para resolução: quando ausente ou inválida, o código do fornecedor e
o documento do emitente podem servir de fallback.

### Catalog

- **Domain/application model:** `ProductResolution` com os estados
  `MATCHED`, `NEW_CANDIDATE` e `NEEDS_REVIEW`.
- **Porta:** `modules/catalog/.../application/port/out/ProductCatalog`.
- **Adapter:** `JooqProductCatalog`.
- **HTTP:** não há controller nem endpoint público de produto, busca, candidato,
  mapping ou conversão.
- **Persistência:** `products`, `product_identifiers`,
  `supplier_product_mappings` e `packaging_conversions` em
  `V11__catalog_products.sql`.
- **Fluxo de resolução:** GTIN utilizável por tenant; depois
  `issuer_document + supplier_product_code`; caso contrário,
  `NEW_CANDIDATE`.
- **Eventos/outbox:** não há evento/outbox nesse fluxo.

Receiving chama a porta pública de catálogo. Não foi encontrado acesso do
receiving a `JooqProductCatalog` nem a tabela diretamente.

### Receiving

- **Application:** `CreateGoodsReceiptPreview`, `GetGoodsReceiptPreview` e
  `ConfirmGoodsReceipt`.
- **HTTP adapter:** `ReceivingController` e
  `ReceivingApiExceptionHandler`.
- **Porta de persistência:** `ReceivingRepository`.
- **DTOs HTTP:** `NfeRequest`, `NfeResponse`, `PreviewResponse`,
  `PreviewItemResponse`, `ConfirmRequest`, `DecisionRequest` e
  `ReceiptResponse`.
- **Persistência:** `goods_receipt_previews`,
  `goods_receipt_preview_items`, `goods_receipts` e
  `goods_receipt_items` em `V12__receiving_inventory.sql`.
- **Transação:** confirmação coordena catálogo, receiving e inventory por
  portas públicas dentro do contexto transacional do PostgreSQL.
- **Eventos/outbox:** não há outbox para substituir a atomicidade da entrada.

### Inventory

- **Porta:** `InventoryPort`.
- **Adapter:** `JooqInventoryRepository`.
- **HTTP:** não há controller nem porta de leitura pública.
- **Persistência:** `inventory_movements` e `inventory_balances` em
  `V12__receiving_inventory.sql`.
- **Idempotência interna:** movimento usa unicidade por
  `(business_id, receipt_id, product_id)` e atualização do saldo.
- **Eventos/outbox:** não há evento/outbox para a confirmação auditada.

### Dependências e boundaries

Os `build.gradle.kts` mostram receiving dependente das APIs de fiscal, catalog
e inventory, enquanto business/identity são usados para autorização. Catalog
expõe a porta necessária e usa o canonical fiscal como dado de entrada.
Inventory só expõe `InventoryPort`. O código observado respeita a regra de
que os casos de uso cruzam módulos por contratos públicos; os repositories e
as tabelas permanecem nos adapters de saída.

## 3. HTTP API existente

Todas as rotas abaixo, exceto quando indicado, exigem `Authorization: Bearer
<JWT>` emitido pelo Keycloak configurado para o realm TINO. Cada `businessId`
é validado contra membership ativa do usuário e Business ativo.

### Endpoints do fluxo NF-e

| Método | Path | Auth/tenant | Headers | Request | Response de sucesso | Status observado | Idempotência | Módulo |
|---|---|---|---|---|---|---|---|---|
| POST | `/api/v1/businesses/{businessId}/nfe-documents` | Bearer + membership | `Idempotency-Key` obrigatório em runtime, máximo 200 caracteres | `{ "access_key": "<44 caracteres>" }` | `NfeResponse`, com `preview` quando `SUCCESS` | `200` | Chave por business + access key | receiving/fiscal |
| GET | `/api/v1/businesses/{businessId}/nfe-documents/{documentId}` | Bearer + membership | nenhum adicional | nenhum | `NfeResponse` | `200` | não se aplica | receiving/fiscal |
| GET | `/api/v1/businesses/{businessId}/nfe-documents/{documentId}/preview` | Bearer + membership | nenhum adicional | nenhum | `PreviewResponse` | `200` | não se aplica | receiving/catalog |
| POST | `/api/v1/businesses/{businessId}/nfe-documents/{documentId}/reprocess` | Bearer + membership | `Idempotency-Key` obrigatório | nenhum body | `NfeResponse` | `200` | Chave por business + document | receiving/fiscal |
| POST | `/api/v1/businesses/{businessId}/goods-receipts/{previewId}/confirm` | Bearer + membership | `Idempotency-Key` obrigatório | `ConfirmRequest` | `{ "receipt_id": "uuid" }` | `200` | receipt único por preview; header é exigido mas não é a chave persistida | receiving/catalog/inventory |

O endpoint de retrieve cria ou reutiliza o preview automaticamente quando a
consulta retorna `SUCCESS`. O GET do documento não retorna raw nem canonical.

### Endpoints de contexto que o Android usa antes do fluxo

| Método | Path | Auth | Request/headers | Response | Status |
|---|---|---|---|---|---|
| POST | `/api/v1/bootstrap` | Bearer | body opcional `{ "requested_business_id": "uuid", "installation_external_id": "string" }` | `BootstrapContext`: `state`, `user`, `businesses`, `selected_business`, `installation` | `200` |
| GET | `/api/v1/businesses` | Bearer | nenhum | lista de business com `id`, `trade_name`, `vertical`, `status`, `role` | `200` |
| POST | `/api/v1/businesses` | Bearer | `{ "trade_name": "...", "vertical": "RETAIL" }` | business criado com os mesmos campos | `201` no controller |

Esses endpoints não substituem uma API de catálogo ou inventário.

## 4. Fluxo E2E disponível por HTTP

| Passo | Estado | Evidência do comportamento atual | Observação para Android |
|---|---|---|---|
| 1. Informar chave NF-e | AVAILABLE | `NfeRequest.accessKey` validado por `NfeAccessKey` | Enviar `access_key` com 44 caracteres e dígito válido |
| 2. Iniciar consulta/processamento | AVAILABLE | POST `nfe-documents` chama fiscal e persiste resultado | Trial/fixture é selecionado por configuração; Produção não está autorizada |
| 3. Obter status | AVAILABLE | `NfeResponse.retrievalStatus` e `fiscalStatus` | GET retorna o estado persistido |
| 4. Obter preview | AVAILABLE | POST retorna preview após sucesso; GET `/preview` também existe | Preview tem `version` para confirmação |
| 5. Visualizar itens | PARTIAL | `PreviewItemResponse` expõe linha, resolução, produto, unidade, quantidade, fator e custo | Não expõe descrição, GTIN, NCM, emitente ou número da NF-e |
| 6. Resolver ProductResolution | PARTIAL | Preview mostra `MATCHED`, `NEW_CANDIDATE` ou `NEEDS_REVIEW` | Não existe endpoint para procurar/selecionar produto ou atualizar candidato |
| 7. Informar PackagingConversion | PARTIAL | `DecisionRequest.baseUnit` + `conversionFactor` na confirmação | Não há endpoint separado para consultar/corrigir conversão |
| 8. Criar/autorizar produto novo | AVAILABLE no comando de confirmação | `action: CREATE_PRODUCT` cria produto durante confirmação | Não há criação prévia ou endpoint de catálogo |
| 9. Associar produto existente | AVAILABLE condicionado | `action: USE_EXISTING` aceita `product_id` | Android precisa já ter o UUID; não há busca/listagem pública |
| 10. Ignorar item | AVAILABLE | `action: IGNORE` grava item sem produto e sem movimento | A decisão pode ser enviada na confirmação |
| 11. Confirmar Goods Receipt | AVAILABLE | POST `goods-receipts/{previewId}/confirm` | Exige `preview_version`, decisões e Idempotency-Key |
| 12. Obter resultado | AVAILABLE | Retorna `receipt_id` | Não há endpoint de consulta/cancelamento de receipt no código atual |
| 13. Consultar efeito no inventário | INTERNAL_ONLY | `InventoryPort.receive` grava movimento e saldo | Não existe GET de saldo/movimentos no escopo auditado |

O fluxo não cria produto silenciosamente: um candidato só vira produto quando
a confirmação contém `CREATE_PRODUCT`. A confirmação pode ocorrer com itens
ignorados; itens não ignorados exigem produto e conversão quando necessária.

## 5. DTOs públicos

### Receiving

```text
NfeRequest(accessKey)
NfeResponse(id, accessKey, retrievalStatus, fiscalStatus, itemCount, preview)
PreviewResponse(id, documentId, status, version, items)
PreviewItemResponse(lineNumber, resolutionStatus, productId, candidateName,
  purchaseUnit, purchaseQuantity, baseUnit, conversionFactor, unitCost)
ConfirmRequest(previewVersion, items)
DecisionRequest(lineNumber, action, productId, conversionFactor, baseUnit)
ReceiptResponse(receiptId)
```

Com a estratégia Jackson global `SNAKE_CASE`, os nomes efetivos no JSON são,
por exemplo, `access_key`, `retrieval_status`, `preview_version`,
`resolution_status` e `receipt_id`.

### Contexto de bootstrap/business

`BootstrapContext` contém `state`, `user`, `businesses`, `selected_business` e
`installation`; `BootstrapBusinessSummary` contém `id`, `trade_name`,
`vertical`, `status` e `role`. Esses DTOs são de composição de identidade,
business e device, não de fiscal.

### Boundary e exposição indevida

Não foram encontrados no DTO HTTP: `raw_payload`, `canonical_payload`,
`parser_version`, `nfeProc`, DTO SERPRO, repository, token/secret ou Consumer
Key/Secret. Também não são expostos os itens fiscais completos; a API retorna
apenas o resumo necessário ao preview atual. Isso preserva o boundary, embora
limite a UX móvel de conferência.

### 5.1 Modelo de preview

O equivalente efetivo de `GoodsReceiptPreview` é `PreviewResponse`; o de
`GoodsReceiptPreviewItem` é `PreviewItemResponse`; `ProductResolution` é
representado por `resolution_status`, `product_id` e `candidate_name`; e
`GoodsReceiptResult` é `ReceiptResponse`.

Não existem DTOs HTTP equivalentes independentes para `IssuerSummary`,
`PackagingConversion`, `GoodsReceiptConfirmation` ou `ReceiptSummary`.
Os dados de confirmação e conversão ficam embutidos em `ConfirmRequest` e
`DecisionRequest`.

O Android consegue saber, por item:

- se está `MATCHED`, `NEW_CANDIDATE` ou `NEEDS_REVIEW`;
- produto associado, quando há `product_id`;
- nome candidato;
- unidade e quantidade de compra;
- unidade base e fator, quando resolvidos;
- custo unitário operacional usado no recebimento.

Não consegue saber pelo preview atual a descrição fiscal completa, o GTIN, a
quantidade final de estoque ou o movimento já criado. A quantidade final é
calculada internamente como `purchaseQuantity * conversionFactor`; o custo de
estoque é `unitCost / conversionFactor`.

## 6. Product Resolution

O código de `JooqProductCatalog.resolve` confirma a ordem:

```text
GTIN utilizável no tenant
  -> issuer_document + supplier_product_code
  -> NEW_CANDIDATE
```

GTIN vazio, `SEM GTIN`, inválido ou sem produto correspondente não invalida o
documento fiscal e não cria produto. A resolução cai para o código do
fornecedor quando o emitente e `cProd` existem; sem match, o preview fica como
`NEW_CANDIDATE`. Um match sem unidade base compatível pode virar
`NEEDS_REVIEW`.

O contrato público para resolver o candidato é apenas a decisão de confirmação:

- `USE_EXISTING` + `product_id` existente;
- `CREATE_PRODUCT` + `base_unit` opcional;
- `IGNORE`.

Não há endpoint HTTP de busca de produtos, criação antecipada, associação
manual ou aprovação isolada de candidato. Portanto, a capacidade de exibir um
candidato existe; a capacidade de administrá-lo de forma independente é
**INTERNAL_ONLY/PARTIAL**.

## 7. Packaging Conversion

Packaging Conversion é persistida em `packaging_conversions` por business,
emitente, código do fornecedor, unidade de compra, unidade base e fator. A
porta está no `ProductCatalog`; o adapter implementa `conversion` e
`confirmConversion`.

No preview:

- mesma unidade de compra/base: fator `1`;
- conversão já confirmada: fator persistido;
- match sem conversão conhecida: `NEEDS_REVIEW`, fator nulo;
- candidato novo: unidade base ainda não resolvida.

Na confirmação, o Android pode fornecer `base_unit` e `conversion_factor`.
O fator deve ser positivo; se as unidades diferirem e o fator não for
informado, a operação falha com `packaging conversion is required`. A
conversão só é persistida quando existem documento do emitente e código do
fornecedor. Não há invenção de conversão.

O impacto no inventário é síncrono: `stockQuantity = purchaseQuantity * factor`
e o custo unitário base é `unitCost / factor`. Não há endpoint público para
consultar ou corrigir a conversão fora do confirm.

## 8. Idempotência

### A. Retrieval e reprocessamento fiscal

- O header exigido em runtime é `Idempotency-Key`, máximo 200 caracteres.
- A chave é escopada por business na tabela
  `nfe_retrieval_idempotency_keys`; a chave guarda a NF-e/documento associado.
- No replay com a mesma chave e mesma NF-e, o documento existente é reutilizado.
- Com a mesma chave para outra NF-e, retorna erro de conflito lógico tratado
  como `400 INVALID_NFE_REQUEST`.
- Se já existe documento `SUCCESS` para a access key, a consulta é reutilizada.
- A disputa concorrente usa claim/constraint no banco; o vencedor fornece o
  document id persistido ao outro fluxo.
- Reprocessamento usa a mesma tabela de idempotência e reparseia o raw local;
  não chama SERPRO.
- A consulta ao provider ocorre fora da transação curta de persistência; um
  timeout ambíguo pode deixar o resultado fiscal como `OUTCOME_UNKNOWN`.

O Android deve sempre enviar uma chave estável por tentativa lógica e manter a
mesma chave quando repetir a requisição.

### B. Confirmação Goods Receipt

- O header `Idempotency-Key` também é obrigatório e máximo 200 caracteres.
- O header não é persistido como chave de receipt no código atual.
- A proteção durável é `findReceiptByPreview` e a unicidade do receipt por
  `(business_id, preview_id)`.
- Replay do mesmo preview retorna o `receipt_id` já criado, inclusive após
  mudança do header.
- A confirmação bloqueia a linha do preview; a unicidade de receipt/movimento
  protege contra concorrência e duplicação de saldo.
- `preview_version` é verificado antes da criação; preview obsoleto não pode
  ser confirmado.

O Android deve enviar `preview_version` retornado no preview e uma chave
estável para a tentativa de confirmação. A semântica efetiva de replay vem do
preview/receipt, não de uma tabela de idempotência específica do header.

## 9. Status

### RetrievalStatus

Os valores persistidos e retornáveis no `NfeResponse` são:

`PENDING`, `IN_PROGRESS`, `SUCCESS`, `NOT_FOUND`, `FAILED` e
`OUTCOME_UNKNOWN`.

O provider Trial/fixture pode produzir `SUCCESS` ou `NOT_FOUND`; adapter e
persistência também suportam falha de provider, payload inválido e resultado
ambíguo. A resposta HTTP continua sendo o DTO de documento com o status
persistido, salvo erro de validação/autorização.

### FiscalStatus

`AUTHORIZED`, `CANCELLED`, `DENIED` e `UNKNOWN`. O parser reconhece os códigos
de autorização/cancelamento/denegação da resposta fiscal. `CANCELLED` e
`DENIED` podem ser consultadas/persistidas, mas são bloqueadas no preview e na
confirmação de estoque.

### GoodsReceiptStatus

Não existe enum/DTO público `GoodsReceiptStatus`. O status externo do preview
é uma string persistida com valores observados no código/migrations:
`DRAFT`, `REVIEW_REQUIRED`, `READY`, `CONFIRMED` e `CANCELLED`. A resposta de
confirmação contém somente `receipt_id`; o status do receipt não é retornado.

## 10. Erros

No controller de receiving, os erros de negócio são mapeados assim:

| Condição | HTTP | Payload efetivo |
|---|---:|---|
| access key ausente/inválida, header ausente/excedido, authentication requerida | 400 | `{ "code": "INVALID_NFE_REQUEST", "message": "..." }` |
| documento fiscal não encontrado no GET/reprocess ou sem raw | 400 em exceções `IllegalArgumentException` | `INVALID_NFE_REQUEST` |
| chave de idempotência reutilizada para outra NF-e | 400 | `INVALID_NFE_REQUEST` |
| NF-e inexistente no provider | `200` com `retrieval_status: NOT_FOUND` | NfeResponse |
| timeout/crash ambíguo do provider | `200` com `retrieval_status: OUTCOME_UNKNOWN` quando persistido | NfeResponse |
| preview não pronto, stale, não confirmável, cancelado/denegado ou conversão ausente | 409 | `{ "code": "RECEIVING_NOT_READY", "message": "..." }` |
| decisão não ignorada sem produto | 409 | `RECEIVING_NOT_READY` |
| fator não positivo | 409 | `RECEIVING_NOT_READY` |
| business sem membership ativa ou inativo | 403 | `{ "code": "BUSINESS_ACCESS_DENIED", "message": "business access denied" }` |
| ausência/token Bearer inválido | 401 | resposta do resource server, normalmente sem body |

Não existe código dedicado para `product review required`, `conversion
required`, `receipt already confirmed` ou `idempotency conflict`; esses casos
caem em `RECEIVING_NOT_READY` ou `INVALID_NFE_REQUEST`, conforme o caminho.
Validações Bean Validation também não têm uma resposta NF-e específica
documentada no advice de receiving.

## 11. Auth/Tenancy

O Android deve enviar somente:

```http
Authorization: Bearer <JWT do realm TINO>
```

O JWT precisa ser aceito pelo issuer configurado e destinado ao client
`tino-android` por `aud` ou `azp`. O backend resolve o usuário interno a
partir do principal e valida membership `ACTIVE` e Business `ACTIVE` antes de
executar a operação.

O `businessId` aparece no path, mas não é confiança do cliente: ele é
autorizado pelo par usuário/business. Depois da autorização,
`PostgresTenantContextExecutor` abre transação nova e define
`app.business_id` com `set_config(..., true)`. As tabelas fiscais, de catálogo,
receiving e inventory usam RLS com esse setting e são `FORCE ROW LEVEL
SECURITY` nas migrations correspondentes.

Não há header de tenant adicional. O cliente pode selecionar um business ao
qual pertence, mas não acessar arbitrariamente o business id de outro tenant.

## 12. Swagger/OpenAPI

O contrato público está em:

- OpenAPI JSON: `GET /openapi`;
- Swagger UI: `/swagger-ui.html`;
- ambos são permitidos sem autenticação pela configuração de segurança.

Os cinco endpoints NF-e auditados aparecem no documento gerado, com bearer
global e `Idempotency-Key` nos três POST que o controller declara/usa. Não foi
encontrado endpoint interno de catálogo ou inventory exposto acidentalmente.

Lacunas observadas no contrato gerado:

1. `NfeRequest`, `NfeResponse`, preview e confirmação aparecem com propriedades
   camelCase no schema (`accessKey`, `previewVersion`), embora o runtime global
   use `snake_case` (`access_key`, `preview_version`).
2. O retrieve aparece com `Idempotency-Key` `required: false`, embora o método
   rejeite ausência em runtime.
3. As operações têm somente resposta `200` documentada; 400, 401, 403, 409 e
   seus payloads não estão descritos.
4. Status são strings sem enums no schema; ações da confirmação são o único
   enum claramente gerado.
5. Não há exemplos de request/response para o fluxo Trial, preview, decisões,
   cancelamento ou replay idempotente.
6. A autenticação existe globalmente, mas não está repetida como requirement
   específica nas operações serializadas.

Esses pontos são gaps de contrato/documentação e não foram corrigidos nesta
auditoria.

## 13. Security boundary SERPRO

O Android para no TINO Backend. Ele não precisa conhecer:

- Consumer Key ou Consumer Secret;
- token OAuth SERPRO;
- endpoint ou headers SERPRO;
- DTO externo SERPRO;
- `nfeProc` ou nesting fiscal;
- parser version ou raw payload.

`SerproOAuthClient` monta Basic auth e usa `client_credentials`, mantém o token
somente em memória e não o inclui em DTO/log. `SerproNfeAdapter` faz uma
renovação controlada após 401 e no máximo um retry para 408/500/504 com
backoff/jitter conforme a implementação Trial. O raw recebido é persistido
para evidência/reprocessamento, mas não é devolvido pela API móvel.

SERPRO Production/F7 não foi executado nem alterado. A ausência de credencial
real não bloqueia esta auditoria.

## 14. Capacidades INTERNAL_ONLY

As seguintes capacidades existem no backend, mas não como contrato HTTP
independente:

- leitura detalhada do canonical/raw fiscal;
- busca de produto por GTIN ou mapping;
- listagem, edição ou aprovação de Product Candidate;
- criação de produto fora de uma confirmação;
- consulta/edição de Supplier Product Mapping;
- consulta/edição de Packaging Conversion;
- leitura de Goods Receipt por `receipt_id`;
- consulta de Inventory Balance e Inventory Movement;
- inspeção de versão/parser/raw para o Android.

O efeito interno de confirmação é comprovado pelo adapter de inventory e pelas
migrations/testes do Trial; a ausência de GET não é uma falha de persistência,
mas uma ausência de contrato móvel.

## 15. Gaps para Android

| Gap | Módulo responsável | Mudança provável | Risco | Migration? | API/domínio? | Prioridade |
|---|---|---|---|---|---|---|
| Schema OpenAPI divergente do JSON runtime e retrieve marcado como idempotência opcional | foundation/receiving | alinhar anotações/configuração e gerar contrato correto | integração Android usar nomes errados | não | API/documentação | P0 |
| Falta de catálogo/search para selecionar produto existente | catalog | endpoint de consulta/listagem com DTO móvel e autorização | Android não consegue obter `product_id` para `USE_EXISTING` | possivelmente não | API; domínio atual pode permanecer | P0 |
| Falta de API para resolver/aprovar candidato fora do confirm | catalog/receiving | comando/DTO de resolução ou UX baseada no confirm | revisão humana limitada e difícil de retomar | possivelmente não | API; pode exigir caso de uso | P1 |
| Preview não expõe descrição/GTIN/issuer e demais evidências necessárias à conferência | receiving | ampliar DTO de preview, se aprovado | UX fiscal insuficiente sem vazar raw | não | API | P1 |
| Não há leitura de saldo/movimento/receipt | inventory/receiving | endpoints de consulta e DTOs móveis | Android não consegue verificar estoque/resultados após confirm | possivelmente não | API | P0 |
| Conversão só pode ser informada dentro da confirmação | catalog/receiving | endpoint/fluxo separado de consulta e confirmação de conversão | revisão operacional não reutilizável | possivelmente não | API; domínio já persiste | P1 |
| Erros NF-e não têm códigos específicos nem schemas no OpenAPI | receiving/foundation | padronizar error contract e documentação | tratamento móvel frágil | não | API/documentação | P1 |
| Sem endpoint de consulta/cancelamento de Goods Receipt | receiving | definir contrato e política antes de implementar | correção operacional pós-confirm não disponível | provavelmente sim para cancelamento | API/domínio | P1 |
| `GoodsReceiptStatus` não é DTO/enum público | receiving | expor status em resposta de receipt/consulta | cliente precisa inferir status pelo preview | não | API | P1 |

Nenhum desses gaps foi implementado nesta rodada por determinação explícita do
GOAL de auditoria. A tabela indica trabalho futuro, não uma autorização de
alteração imediata.

## 16. Riscos

1. **Contrato gerado vs runtime:** é o risco mais imediato para o Android,
   porque uma geração de cliente pode criar `accessKey` enquanto o servidor
   espera `access_key`.
2. **Idempotência de confirmação:** o header é obrigatório, mas a garantia
   durável está no preview, não no valor do header. Isso funciona para o fluxo
   atual, porém deve ser documentado antes de clientes distribuídos em escala.
3. **Visibilidade de estoque:** a entrada é escrita atomicamente, mas não há
   leitura pública para fechar o ciclo de UX.
4. **Resolução de catálogo:** sem busca pública, `USE_EXISTING` depende de um
   UUID que o Android não consegue descobrir pelo contrato auditado.
5. **Status/error contract:** strings sem enum e erros genéricos aumentam o
   acoplamento implícito do cliente.
6. **Drift de documentação:** `docs/TINO-NFE-GOODS-RECEIPT-GOAL.md` lista uma
   rota de cancelamento de receipt, mas nenhum controller atual a expõe.
7. **Raw fiscal:** a retenção/expurgo legal continua pendente para produção;
   isso não bloqueia Trial, mas permanece gate de F7.
8. **SERPRO real:** o adapter real ainda depende da credencial e do payload
   Trial efetivamente recebido; o parser validado localmente não substitui o
   smoke real.

## 17. File map

### Código

- `modules/fiscal/src/main/java/com/tino/backend/fiscal/`
- `modules/catalog/src/main/java/com/tino/backend/catalog/`
- `modules/receiving/src/main/java/com/tino/backend/receiving/`
- `modules/inventory/src/main/java/com/tino/backend/inventory/`
- `modules/business/src/main/java/com/tino/backend/business/`
- `modules/identity/src/main/java/com/tino/backend/identity/`
- `shared/infrastructure/src/main/java/com/tino/backend/shared/infrastructure/tenant/`
- `app/src/main/java/com/tino/backend/foundation/`

### Migrations

- `app/src/main/resources/db/migration/V10__fiscal_nfe.sql`
- `app/src/main/resources/db/migration/V11__catalog_products.sql`
- `app/src/main/resources/db/migration/V12__receiving_inventory.sql`
- `app/src/main/resources/db/migration/V13__fiscal_permissions.sql`

### Evidências e contrato

- `modules/fiscal/src/test/resources/serpro/consulta-nfe-trial-official-sanitized.json`
- `docs/evidence/F1-NFE-TRIAL-EVIDENCE.md`
- `docs/evidence/F2-F6-NFE-TRIAL-EVIDENCE.md`
- `docs/evidence/NFE-CONTRACT-COVERAGE-MATRIX.md`
- `docs/TINO-NFE-GOODS-RECEIPT-GOAL.md`
- Fonte externa de layout consultada sem ser copiada para o repositório:
  `/home/carlos-henrique/Downloads/NFE_Campos .xlsx`
  (SHA-256 `b026019744681e97d6228d00962c99535d3e81ab349b83c7d0a2a5b87fc7d9a1`).

## Matriz final de prontidão

| CAPABILITY | HTTP AVAILABLE | ANDROID READY | GAP | PRIORITY |
|---|---|---|---|---|
| Informar access key e iniciar retrieval | SIM | SIM | alinhar schema OpenAPI com `snake_case` | P0 |
| Consultar retrieval/fiscal status | SIM | SIM | enums e erros não estão no OpenAPI | P1 |
| Persistir e reprocessar raw/canonical | SIM, sem exposição raw | SIM para operação | nenhum funcional no contrato atual | P2 |
| Obter preview | SIM | SIM | ampliar evidência fiscal para UX, se necessário | P1 |
| Ver ProductResolution | SIM no preview | PARCIAL | sem search/approve público | P0/P1 |
| Selecionar produto existente | somente `USE_EXISTING` no confirm | NÃO completo | falta catálogo/search para obter UUID | P0 |
| Criar produto conscientemente | SIM, durante confirm | SIM condicionado | falta fluxo independente de catálogo | P1 |
| Informar PackagingConversion | SIM, durante confirm | SIM condicionado | falta endpoint separado e contrato explícito | P1 |
| Ignorar item | SIM | SIM | sem lacuna funcional observada | P2 |
| Confirmar Goods Receipt | SIM | SIM | response não devolve status/summary | P1 |
| Evitar duplicate receipt/stock | SIM | SIM | documentar que header não é chave persistida | P1 |
| Consultar receipt após confirmação | NÃO | NÃO | falta endpoint de leitura | P1 |
| Consultar saldo/movimento de inventário | NÃO | NÃO | capacidade atualmente INTERNAL_ONLY | P0 |
| Auth Keycloak/OIDC | SIM | SIM | manter token destinado a `tino-android` | P2 |
| Isolamento por business/RLS | SIM | SIM | nenhum gap observado no escopo | P2 |
| Usar SERPRO sem credencial no app | SIM por boundary | SIM | smoke real Trial ainda aguardando credenciais | P1 |
| SERPRO Production/F7 | NÃO autorizado | NÃO | contrato/credenciais/política ainda pendentes | BLOCKED |

**Decisão desta auditoria:** não corrigir os gaps acima neste GOAL. O próximo
trabalho autorizado, após alinhamento com o Android, deve priorizar o contrato
OpenAPI e as leituras públicas que fecham o ciclo de catálogo/estoque. F7
continua bloqueado.
