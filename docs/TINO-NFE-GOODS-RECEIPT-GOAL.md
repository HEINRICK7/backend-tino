# TINO — NF-e como Entrada Inteligente de Mercadoria

**Status:** `APPROVED_FOR_TRIAL_IMPLEMENTATION`
**Tipo:** GOAL/SDD canônico
**Data:** 2026-08-29
**Escopo:** implementação autorizada somente até F6 para o ambiente Trial; F7 Produção permanece bloqueada
**Autoridade:** este documento substitui os drafts anteriores de integração Consulta NF-e SERPRO para este escopo

## 1. Resumo executivo

O TINO deve transformar a chegada de uma mercadoria em uma entrada orientada
por evidência fiscal:

```text
DANFE/chave da NF-e
        ↓
SERPRO (recuperação)
        ↓
NF-e canônica e itens estruturados
        ↓
resolução de produtos e unidades
        ↓
preview operacional
        ↓
revisão humana explícita
        ↓
catálogo + Goods Receipt + estoque
```

A feature não é “consultar uma nota fiscal”. É “recebi mercadoria; o TINO lê
a NF-e, entende o que chegou e prepara a entrada para mim”. O SERPRO é um
provider externo de recuperação de documento. Ele não é o catálogo, o estoque,
o fornecedor de regras de negócio nem a autoridade para mutar dados internos.

## 2. Regra de consolidação

Este é o único GOAL/SDD canônico para a feature. Ele incorpora e corrige o
draft anterior de integração SERPRO:

| Tema | Decisão consolidada |
|---|---|
| Fonte externa | SERPRO isolado atrás de adapter e port próprio do TINO |
| Unidade da feature | Preparar e confirmar entrada de mercadoria, não arquivar NF-e |
| Catálogo | Catálogo mínimo passa a ser parte do escopo operacional |
| Produto | Resolução por GTIN, depois fornecedor + `cProd`, depois revisão |
| Estoque | Somente Goods Receipt confirmado pode atualizar saldo |
| Estados | `RetrievalStatus`, `FiscalStatus` e `GoodsReceiptStatus` separados |
| Billing | Status documentados como não bilhetados orientam retries limitados; não há retry cego |
| Cache | Primeiro tenant-owned; cache global somente após threat model e prova contratual |
| OCR | Fora deste GOAL; somente boundary `DetectedNfeAccessKey` |
| Produção | Endpoint/contrato contratado ainda não é congelado por suposição; F7 deferido |
| Implementação | F1–F6 autorizados para Trial; F7 Produção não autorizado |

Não existe atualmente no repositório um SDD/GOAL canônico para NF-e. O
documento presente passa a ser o artefato de referência quando aprovado.

## 3. Contexto atual do repositório

O baseline existente foi inspecionado em `docs/architecture/` e `docs/specs/`.
As decisões que permanecem obrigatórias são:

- Java 21, Spring Boot, Gradle Kotlin DSL e PostgreSQL;
- modular monolith, Hexagonal Architecture e Spring Modulith;
- jOOQ somente em adapters de persistência; Flyway para schema;
- REST/OpenAPI em `/api/v1`;
- Keycloak/OIDC e Resource Server JWT;
- `business_id` como tenant authority, com RLS real e predicados explícitos;
- IDs UUID v7, `NUMERIC`/`BigDecimal`, `TIMESTAMPTZ`/`Instant` e SQL `snake_case`;
- padrão de transação `READ COMMITTED`, constraints/idempotência e locks apenas
  nos caminhos que exigem invariantes;
- secrets somente em runtime e nenhuma informação sensível em logs;
- Docker Compose apenas para runtime local; não introduzir Redis, broker,
  microsserviços ou outro datastore sem ADR e necessidade mensurável.

O repositório contém os módulos de identidade, business, device, sync,
bootstrap, customer, credit, payment, reconciliation e messaging. Não há
implementação funcional de fiscal/NF-e, produto, catálogo, fornecedor,
conversão de embalagem, estoque ou Goods Receipt. Portanto, o trabalho futuro
deve ser aditivo e começar pelos contratos deste documento.

## 4. Objetivos

### 4.1 Objetivo do comerciante

Reduzir a digitação manual dos seguintes dados quando eles estiverem na NF-e:

- nome e código do produto do fornecedor;
- GTIN/EAN, NCM, CEST e CFOP;
- quantidade, unidade comercial e custo unitário;
- total, desconto, frete e dados fiscais básicos do fornecedor.

O usuário deve revisar exceções, não reescrever a nota inteira.

### 4.2 Resultado mínimo correto

Uma NF-e válida deve poder percorrer:

```text
chave válida → consulta Trial → payload bruto → parser → documento canônico
→ produtos resolvidos/candidatos → conversões revisadas → preview
→ confirmação explícita → entrada exatamente uma vez → estoque correto
```

### 4.3 Não objetivos

Este GOAL não cria:

- ERP fiscal, escrituração, SPED ou cálculo tributário completo;
- emissor NF-e, NFC-e ou NFS-e;
- OCR completo, leitura de imagem ou captura de DANFE;
- cadastro manual complexo de fornecedores;
- alteração automática de preço de venda;
- entrada silenciosa ou atualização de estoque durante consulta;
- arquitetura de catálogo genérica ou ERP de produtos;
- cache cross-tenant sem prova de segurança, privacidade e contrato;
- integração de Produção antes da confirmação do contrato contratado.

## 5. Experiência e fluxo funcional

### 5.1 Fluxo do usuário

1. O comerciante informa, cola ou futuramente escaneia a chave.
2. O TINO normaliza e valida a chave antes de qualquer chamada externa.
3. O TINO recupera a NF-e pelo SERPRO Trial.
4. O TINO persiste a versão bruta e gera o documento canônico.
5. Cada item passa por resolução de produto e de unidade.
6. O TINO apresenta um preview operacional, sem XML/JSON fiscal.
7. O usuário cria, associa ou ignora produtos desconhecidos e revisa conversões.
8. O usuário confirma explicitamente a entrada.
9. Em uma transação interna, o TINO cria/associa catálogo, Goods Receipt e
   movimentos de entrada conforme o contrato do módulo de estoque.
10. Uma confirmação repetida retorna a entrada existente e não soma estoque.

### 5.2 Conteúdo do preview

O contrato de preview deve ser suficiente para a UI mostrar, por exemplo:

```text
18 produtos encontrados
14 reconhecidos · 3 novos · 1 precisa de você

Café Maratá 250g · 12 UN · R$ 6,80/un · reconhecido
Coca-Cola 2L · 10 CX · 1 CX = 6 UN · entrada: 60 UN
Produto XYZ · cProd 8282 · produto novo

[REVISAR] [CONFIRMAR ENTRADA]
```

O payload interno pode manter dados fiscais completos, mas a resposta da UI
deve ser linguagem operacional e minimizar dados pessoais do documento.

## 6. Bounded contexts e responsabilidades

O desenho permanece um modular monolith. Os módulos abaixo são uma proposta
de implementação, não autorização de criação antecipada:

| Contexto/módulo | Responsabilidade | Não pode fazer |
|---|---|---|
| `fiscal` | chave, recuperação SERPRO, payload bruto, parser, NF-e canônica, situação fiscal | criar produto, alterar estoque ou decidir entrada |
| `catalog` | Product, identificadores, mapping fornecedor-produto e conversões aprovadas | conhecer OAuth/HTTP SERPRO ou movimentar saldo |
| `receiving` | resolução, preview, revisão, confirmação e Goods Receipt | acessar tabelas internas de outro contexto |
| `inventory` | movimento e saldo de estoque após comando autorizado | interpretar DTO SERPRO ou criar produto silenciosamente |

Relações:

```text
fiscal → receiving       documento canônico por port/application API
catalog ↔ receiving      resolução e comandos explícitos por ports
receiving → inventory    comando de entrada confirmado
receiving → catalog      criação/associação somente após confirmação
```

O `fiscal` deve ser o único módulo que conhece o provider externo. O
`receiving` não importa DTO SERPRO. O `inventory` não consulta SERPRO. Cada
contexto referencia outro por ID ou port público, nunca por repository, tabela,
adapter ou classe interna.

### 6.1 Anti-corruption layer

```text
SERPRO DTO/OAuth/HTTP
        ↓ adapter + mapper
NfeRetrievalResult / CanonicalNfeDocument
        ↓ application port
receiving
```

Nenhum DTO SERPRO, token, URL, status HTTP ou detalhe Trial/Produção pode
atravessar o boundary do adapter.

## 7. Estrutura de código proposta

### 7.1 Módulo fiscal

```text
modules/fiscal/
  domain/
    model/
    service/
    exception/
  application/
    port/in/
    port/out/
    usecase/
  adapter/in/web/
  adapter/out/persistence/
  adapter/out/serpro/
  api/
```

O mesmo padrão pode ser aplicado a `catalog`, `receiving` e `inventory`,
mantendo apenas classes que escondam complexidade real.

### 7.2 Regra de dependência

```text
HTTP / jOOQ / SERPRO / Spring
              ↓
         application
              ↓
           domain
```

Domain e application não dependem de Spring, PostgreSQL, jOOQ, Keycloak ou
cliente HTTP. DTO HTTP é mapeado para Command/Query; entidade não é resposta
de API.

## 8. Contrato externo SERPRO

Referência principal: [API Consulta NFe — SERPRO](https://apicenter.estaleiro.serpro.gov.br/documentacao/consulta-nfe/).

### 8.1 O que está confirmado pela documentação oficial

- autenticação OAuth2 com `client_credentials`;
- credenciais `Consumer Key` e `Consumer Secret` obtidas na Área do Cliente;
- token por `POST` em `https://gateway.apiserpro.serpro.gov.br/token`, usando
  `Authorization: Basic base64(consumerKey:consumerSecret)` e
  `grant_type=client_credentials`;
- consulta Trial de demonstração em
  `https://gateway.apiserpro.serpro.gov.br/consulta-nfe-df-trial/api/v1/nfe/{chave}`;
- resposta alinhada aos schemas XML/leiaute da NF-e/ENCAT;
- campos numéricos como JSON `NUMBER`, podendo aparecer em notação científica;
- header opcional `X-Request-Tag`, texto livre de até 32 caracteres;
- HTTP `400`, `401`, `403`, `404`, `406`, `408`, `500` e `504` documentados
  como não bilhetados.

O guia oficial também fornece chaves fictícias de demonstração, inclusive casos
com arrays, cancelamento, denegação e CNPJ alfanumérico. Elas serão usadas
somente como fixtures/validação Trial, sem secrets reais.

### 8.2 O que permanece deliberadamente aberto

O endpoint de Produção, o produto contratado, a variante DF/Escalonado, os
limites, o comportamento de disponibilidade e a semântica contratual de
repetição devem ser confirmados no Swagger/contrato do TINO. Nenhum desses
valores será congelado por inferência do endpoint Trial.

### 8.3 Segredos e token

- Consumer Key, Consumer Secret e Bearer token ficam exclusivamente no backend.
- O token é mantido apenas em memória e renovado conforme `expires_in`.
- Nenhum segredo vai para Android, Git, banco permanente, métrica, log,
  resposta HTTP ou payload interno.
- Configuração deve ser runtime-only, com nomes separados para Trial e Produção.
- O adapter redige Authorization, secrets e respostas sensíveis antes de logs.

### 8.4 `X-Request-Tag`

O uso será avaliado para agrupamento de faturamento. Se adotado, deve usar
uma taxonomia técnica opaca, de baixa cardinalidade e com no máximo 32
caracteres, como `tino-nfe`. Nunca deverá carregar CNPJ, CPF, chave, nome ou
qualquer identificador sensível.

## 9. Modelo canônico fiscal

### 9.1 Tipos de domínio

O módulo fiscal deverá definir tipos próprios, no mínimo:

- `NfeAccessKey`;
- `DetectedNfeAccessKey`;
- `CanonicalNfeDocument`;
- `CanonicalNfeIssuer`;
- `CanonicalNfeItem`;
- `RawNfePayload`;
- `NfeRetrievalResult`;
- `NfeRetrievalFailure`.

### 9.2 Chave NF-e

`NfeAccessKey` é um value object, não uma string validada no controller.
Antes de consulta ele deve:

- normalizar a entrada;
- validar formato e tamanho segundo a especificação vigente;
- validar dígito verificador quando aplicável;
- suportar evolução oficial documentada, sem presumir que CNPJ seja sempre
  numérico;
- rejeitar obviamente inválida sem chamar o provider.

### 9.3 Documento e emitente

`CanonicalNfeDocument` deve preservar, quando disponível:

| Grupo | Campos mínimos |
|---|---|
| Identidade | chave, `nNF`, série, `dhEmi`, `natOp`, `tpNF`, protocolo/eventos |
| Fiscal | `FiscalStatus` derivado de protocolo/eventos |
| Emitente | CNPJ/CPF, `xNome`, `xFant`, IE, endereço quando necessário |
| Proveniência | provider, ambiente, versão do parser, hash/identidade da resposta |
| Completeness | campos opcionais ausentes, warnings de parsing e versão do leiaute |

### 9.4 Item canônico

`CanonicalNfeItem` deve conter:

| Campo | Tipo/regra |
|---|---|
| `lineNumber` | inteiro fiscal |
| `supplierProductCode` | texto preservado; `cProd` não é global |
| `gtin` / `taxGtin` | texto normalizado, origem preservada |
| `description` | texto fiscal |
| `ncm`, `cest`, `cfop` | strings/tipos que preservem zeros e formato |
| `commercialUnit`, `taxUnit` | texto normalizado |
| `commercialQuantity`, `taxQuantity` | `BigDecimal` |
| `commercialUnitPrice`, `taxUnitPrice` | `BigDecimal` |
| `productTotal`, `discount`, `freight`, `insurance`, `otherValue` | `BigDecimal` |
| `indTot` | flag/enum conforme payload |

PostgreSQL usa `NUMERIC`; Java usa `BigDecimal`. É proibido usar
`double`/`float` em quantidade ou dinheiro fiscal.

`cEAN` e `cEANTrib` não são fundidos sem preservar a origem. Valores vazios,
`SEM GTIN` e equivalentes devem ser representados explicitamente como ausência
de GTIN utilizável.

### 9.5 Raw payload e parser

O pipeline é:

```text
resposta SERPRO → raw payload persistido → parser versionado → canônico
```

O raw payload é evidência técnica, não resposta para o comerciante e não deve
ser logado. Um parser futuro poderá reprocessar o raw payload localmente sem
nova chamada paga ao SERPRO.

## 10. Máquinas de estado

As três máquinas são independentes.

### 10.1 RetrievalStatus

`PENDING → IN_PROGRESS → SUCCESS | NOT_FOUND | FAILED | OUTCOME_UNKNOWN`

- `OUTCOME_UNKNOWN` representa timeout, crash ou falha depois de uma tentativa
  cujo resultado/custo não pôde ser determinado.
- `SUCCESS` permite gerar/atualizar a representação canônica e preview.
- `NOT_FOUND` não é erro de parsing nem estado fiscal cancelado.

### 10.2 FiscalStatus

Valores iniciais: `AUTHORIZED`, `CANCELLED`, `DENIED`, `UNKNOWN`.

O mapper deriva esse status dos protocolos e eventos oficiais disponíveis.
Uma NF-e `CANCELLED` continua podendo existir como evidência fiscal, mas não é
um `GoodsReceiptStatus.CANCELLED`.

### 10.3 GoodsReceiptStatus

`DRAFT → REVIEW_REQUIRED → READY → CONFIRMED` e, quando permitido pelo
contrato do recebimento, `DRAFT/REVIEW_REQUIRED/READY → CANCELLED`.

Não se confirma uma NF-e cancelada automaticamente; a regra inicial é bloquear
ou encaminhar para revisão explícita, conforme decisão de produto registrada
antes da implementação.

## 11. Catálogo mínimo

O catálogo é supporting domain do fluxo, não um ERP. Ele deve conter somente o
necessário para identificar itens e reutilizar decisões:

### 11.1 `Product`

Produto pertencente ao `business_id`, com nome operacional, unidade base,
status e campos fiscais úteis ao catálogo. Não recebe alteração automática de
preço de venda a partir da NF-e.

### 11.2 `ProductIdentifier`

Identificadores do produto, incluindo GTIN/EAN quando utilizável, com tipo,
valor normalizado, origem e vigência. Índice/unique sempre tenant-scoped.

### 11.3 `SupplierProductMapping`

Mapping aprendido após decisão humana:

```text
business_id + issuer_document + supplier_product_code → product_id
```

`issuer_document + cProd` não é uma identidade global. O mapping deve manter
proveniência, quem confirmou, quando confirmou e possibilidade de revisão.

### 11.4 `PackagingConversion`

Conversão revisável e tenant-owned:

```text
business_id + supplier + supplier_product_code
purchase_unit + base_unit + conversion_factor
```

Exemplo: `10 CX × 6 UN/CX = 60 UN`. Conversão desconhecida exige revisão;
nunca é inventada pelo sistema. Depois de confirmada, pode ser reutilizada e
alterada explicitamente.

## 12. Product Resolution

Todo item precisa de resultado de resolução antes de compor a entrada.

Ordem preferencial:

1. GTIN/EAN confiável normalizado e validado;
2. `issuer_document + supplierProductCode` no mapping do tenant;
3. regras de matching existentes, se aprovadas e determinísticas;
4. `NewProductCandidate` para revisão.

Resultados conceituais:

- `ExistingProduct`;
- `NewProductCandidate`;
- `NeedsReview`.

Nunca criar produto silenciosamente. Um candidato pré-preenche `xProd`, GTIN,
NCM, CEST, unidade, `cProd` e custo de compra para o usuário escolher:

- `CRIAR PRODUTO`;
- `ASSOCIAR A PRODUTO EXISTENTE`;
- `IGNORAR ITEM`.

Múltiplos candidatos, EAN inválido ou conversão incompleta sempre exigem
revisão explícita.

## 13. Goods Receipt e custo

### 13.1 Modelo

`GoodsReceipt` pertence ao contexto de receiving e referencia:

- `business_id`;
- `source_type = NFE`;
- `source_document_id`;
- emitente/fornecedor fiscal;
- `received_at`;
- status;
- `confirmed_by` e `confirmed_at`;
- itens resolvidos.

`GoodsReceiptItem` preserva, no mínimo:

- linha da NF-e;
- `product_id` ou decisão de ignorar;
- quantidade e unidade de compra;
- fator de conversão;
- quantidade e unidade de estoque;
- custo unitário de entrada;
- vínculo com o item fiscal original.

### 13.2 Unidade de compra e estoque

`qCom` não pode ser somado diretamente ao estoque quando a unidade comercial
for diferente da unidade base. A quantidade de estoque é derivada somente de
uma `PackagingConversion` confirmada:

```text
stock_quantity = purchase_quantity × conversion_factor
```

Sem conversão confirmada, o item fica em `REVIEW_REQUIRED`.

### 13.3 Política de custo

O fiscal preserva `vUnCom`, `vProd`, `vDesc`, `vFrete`, `vSeg` e `vOutro`.
O Goods Receipt transforma esses valores em custo de entrada conforme política
explicitamente definida pelo domínio de estoque. Este GOAL não inventa custo
médio, último custo, rateio contábil, arredondamento ou preço de venda.

## 14. Confirmação e atomicidade

Antes de qualquer mutation de catálogo ou estoque, o usuário precisa confirmar
um preview. A confirmação deve ser explícita, auditável, idempotente e
transacional no domínio interno.

Na confirmação, em uma transação interna adequada ao bounded context:

1. validar novamente tenant, status fiscal, estado do preview e decisões;
2. criar produtos novos autorizados;
3. persistir mappings fornecedor-produto;
4. persistir conversões confirmadas;
5. criar o Goods Receipt;
6. criar movimentos de entrada;
7. atualizar saldo segundo o contrato do inventory;
8. registrar custo segundo a política aprovada;
9. vincular tudo à NF-e de origem e à evidência de confirmação.

Se o contrato de estoque dividir contextos transacionais, o desenho final deve
usar um comando/evento durável e estados explícitos para evitar falsa
confirmação. Não declarar atomicidade distribuída sem uma decisão específica.

## 15. Persistência proposta (não criar ainda)

O modelo mínimo a fechar antes de migration é:

| Tabela conceitual | Contexto | Conteúdo |
|---|---|---|
| `nfe_documents` | fiscal | identidade tenant + chave + retrieval/fiscal status |
| `nfe_document_versions` | fiscal | raw payload, parser/provider version, hash e warnings |
| `nfe_items` | fiscal | itens canônicos e vínculo de linha |
| `products` | catalog | produto operacional tenant-owned |
| `product_identifiers` | catalog | GTIN/EAN e outros identificadores |
| `supplier_product_mappings` | catalog | issuer + `cProd` → product |
| `packaging_conversions` | catalog | unidade de compra → unidade base |
| `goods_receipt_previews` | receiving | snapshot de resolução/revisão |
| `goods_receipts` | receiving | entrada confirmada e estado |
| `goods_receipt_items` | receiving | decisão final por item |
| `inventory_movements`/saldo | inventory | movimentos e saldo conforme SDD do estoque |

Regras obrigatórias:

- toda tabela tenant-owned possui `business_id NOT NULL`, FK e RLS real;
- `UNIQUE(business_id, access_key)` protege retrieval por tenant;
- Goods Receipt possui unique tenant-scoped para impedir confirmação dupla da
  mesma NF-e, sem confundir isso com idempotência da consulta;
- FKs compostas impedem referências cross-tenant;
- queries filtram explicitamente `business_id` além do RLS;
- raw payload não aparece em listagens ou respostas de UI;
- índices começam pelas consultas reais do fluxo e todas as listas são paginadas.

### 15.1 Migrations propostas

Uma primeira migration pode ser `V10__fiscal_nfe.sql`, seguida por migrations
aditivas do catálogo, receiving e inventory quando seus contratos estiverem
aprovados. O número e o conteúdo finais dependem do fechamento do modelo.

Nenhum arquivo de migration foi criado por este GOAL.

## 16. API interna proposta

Todas as rotas são autenticadas, tenant-scoped por membership e usam DTOs
snake_case conforme a convenção HTTP existente.

| Método | Rota | Função |
|---|---|---|
| `POST` | `/api/v1/businesses/{businessId}/nfe-documents` | iniciar recuperação por chave |
| `GET` | `/api/v1/businesses/{businessId}/nfe-documents/{documentId}` | consultar estado/documento operacional |
| `GET` | `/api/v1/businesses/{businessId}/nfe-documents/{documentId}/preview` | obter preview de entrada |
| `POST` | `/api/v1/businesses/{businessId}/nfe-documents/{documentId}/reprocess` | reprocessar raw localmente, sem provider |
| `POST` | `/api/v1/businesses/{businessId}/goods-receipts/{previewId}/confirm` | confirmar entrada explicitamente |
| `POST` | `/api/v1/businesses/{businessId}/goods-receipts/{receiptId}/cancel` | cancelar conforme política aprovada |

### 16.1 Commands mínimos

`StartNfeRetrieval`:

```json
{
  "access_key": "35170608530528000184550000000154301000771561"
}
```

`ConfirmGoodsReceipt` deve conter versão/etag do preview e as decisões por
item: produto existente, produto novo ou ignorado; conversão; quantidade e
unidade resultantes. O cliente nunca envia token SERPRO nem payload bruto para
confirmar.

### 16.2 Idempotência

- consulta: `Idempotency-Key` + `business_id + access_key`;
- entrada: `Idempotency-Key` da confirmação + unique da NF-e/receipt;
- repetição com mesmo comando retorna o mesmo resultado;
- mesma chave com payload conflitante retorna erro de conflito seguro;
- retrieval idempotency nunca substitui goods-receipt idempotency.

## 17. Concorrência e consistência

- manter `READ COMMITTED` como padrão;
- usar constraints para unicidade;
- bloquear a linha do preview/receipt no momento da confirmação quando
  necessário (`SELECT ... FOR UPDATE` no adapter);
- usar version/ETag para impedir confirmação de preview obsoleto;
- atualizar saldo e movimento em ordem definida pelo SDD de inventory;
- não usar mutex em memória, pois o backend deve continuar stateless e
  horizontalmente escalável;
- nunca deixar transação PostgreSQL aberta durante chamada SERPRO.

## 18. Resiliência, billing e recuperação

### 18.1 Política externa

O SERPRO deve ter connect/read timeout, circuit breaker, bulkhead e métricas.
Retries devem ser curtos, limitados, com backoff e jitter e decididos por
semântica:

| Situação | Regra inicial |
|---|---|
| `400` chave inválida | não retry; erro de domínio; não chama novamente |
| `401` | renovar token uma vez e repetir a requisição somente de forma controlada |
| `403` | não retry cego; sinalizar configuração/contrato |
| `404` | não retry automático imediato |
| `406` | não retry; corrigir formato/configuração |
| `408` | retry limitado somente se o contrato permitir; observar risco de tempestade |
| `500` | retry limitado com breaker |
| `504` | retry limitado; a chamada pode não ter chegado ao serviço |
| timeout/crash após envio | marcar `OUTCOME_UNKNOWN`; não assumir sucesso nem repetir indefinidamente |

Embora os códigos listados sejam não bilhetados segundo a documentação, isso
não significa que todo retry seja seguro. O adapter deve registrar tentativa,
resultado, duração e correlação sem guardar chave, token ou payload em log.

### 18.2 Degradação

- provider indisponível: não gerar preview falso; manter estado recuperável;
- token inválido: renovar conforme limite e expor erro operacional sanitizado;
- parser inválido: persistir evidência com falha de parsing e não mutar catálogo;
- falha pós-provider: preservar `OUTCOME_UNKNOWN` para decisão operacional;
- reprocessamento local: usar raw já persistido, sem nova cobrança.

## 19. Cache e ameaça cross-tenant

A garantia inicial obrigatória é tenant-owned:

```text
mesmo business_id + mesma access_key → não consultar SERPRO novamente automaticamente
```

Um cache técnico global opaco só poderá ser considerado depois de um threat
model que prove simultaneamente:

- nenhum vazamento cross-tenant;
- nenhum oracle de existência de NF-e;
- nenhuma leitura de documento de outro tenant;
- autorização de uso do dado compatível com contrato SERPRO;
- privacidade, retenção e apagamento compatíveis.

Até essa prova, não existe cache global. O cache tenant-owned também respeita
retention/legal hold e não expõe raw payload na API comum.

## 20. Segurança, tenancy e privacidade

- fluxo: Android → TINO Backend → adapter SERPRO;
- Android nunca chama SERPRO;
- membership é a autoridade antes de qualquer acesso ao documento;
- RLS `FORCE` em todas as tabelas tenant-owned;
- queries sempre filtram `business_id` explicitamente;
- A não pode descobrir se B consultou uma chave, produto ou mapping;
- erros não diferenciam existência de documento de outro tenant;
- logs, métricas e traces não carregam chave, CNPJ, CPF, token, secret, raw
  payload ou nomes de pessoas;
- dados pessoais do emitente são minimizados; retenção será definida pelo
  contrato de produto/compliance;
- confirmação registra actor, timestamp, correlation ID e decisão sem copiar
  dados fiscais desnecessários para logs.

## 21. Trial first

Ordem autorizável após aprovação deste GOAL:

1. configurar adapter de autenticação Trial sem secret versionado;
2. consultar uma chave fictícia oficial;
3. capturar resposta em fixture sanitizada;
4. construir mapper/parser versionado;
5. validar `CanonicalNfeDocument` e `BigDecimal`;
6. persistir/ler raw e reprocessar localmente;
7. testar resolução de produto;
8. testar packaging/conversões;
9. montar preview;
10. confirmar Goods Receipt em sandbox interno;
11. só então avaliar contrato/endpoint de Produção.

Fixtures devem usar exclusivamente as chaves fictícias do Trial oficial. Nenhum
Consumer Secret ou token real pode entrar no repositório.

## 22. Testes obrigatórios

### 22.1 SERPRO e parser

- autenticação `client_credentials`;
- token expirado e renovação limitada;
- chave inválida não chama provider;
- respostas `200`, `400`, `401`, `403`, `404`, `406`, `408`, `500`, `504`;
- timeout/crash e `OUTCOME_UNKNOWN`;
- payload inválido, campos opcionais, arrays e eventos;
- cancelamento, denegação e protocolos;
- notação científica e conversão para `BigDecimal`;
- CNPJ alfanumérico e preservação de identificadores;
- fixture Trial oficial sanitizada;
- raw reprocessado sem nova chamada externa.

### 22.2 Product Resolution

- match por GTIN válido;
- ausência e GTIN inválido;
- match por issuer + `cProd`;
- `cProd` igual em fornecedores diferentes não conflita;
- produto desconhecido gera candidato;
- múltiplos candidatos exigem revisão;
- associação manual aprende mapping;
- mapping é reutilizado no mesmo tenant;
- tenant A não lê nem usa mapping do tenant B.

### 22.3 Packaging

- `UN → UN`;
- `CX → UN` com conversão confirmada;
- `FD → UN` com conversão confirmada;
- `KG → KG`;
- unidade desconhecida exige revisão;
- fator nunca é inventado;
- conversão confirmada é reutilizada e revisável;
- quantidades e fatores usam `BigDecimal` sem erro de ponto flutuante.

### 22.4 Goods Receipt

- consulta sem confirmação não altera catálogo nem estoque;
- produto novo só é criado com autorização;
- confirmação cria entrada e movimento correto;
- confirmação repetida não duplica estoque;
- commands conflitantes retornam conflito seguro;
- falha transacional não deixa saldo parcial;
- NF-e cancelada bloqueia/encaminha para revisão;
- tenant A não confirma preview/documento de B;
- source document e receipt ficam vinculados;
- preview obsoleto não pode ser confirmado sem nova revisão.

## 23. Observabilidade

Métricas técnicas e de domínio propostas:

- `tino_nfe_serpro_external_calls_total`;
- `tino_nfe_serpro_billable_success_total`;
- `tino_nfe_serpro_non_billable_error_total`;
- `tino_nfe_cache_hits_total`;
- `tino_nfe_duplicate_prevented_total`;
- `tino_nfe_request_duration`;
- `tino_goods_receipt_confirmed_total`;
- `tino_goods_receipt_duplicate_prevented_total`;
- `tino_nfe_product_matched_total`;
- `tino_nfe_product_created_total`;
- `tino_nfe_product_review_required_total`.

Tags não podem conter chave, CNPJ, CPF, nome ou alta cardinalidade sensível.
O correlation ID existente continua atravessando a requisição e a tentativa
externa. Logs são estruturados e resumem somente estado técnico sanitizado.

## 24. Riscos e mitigação

| Risco | Impacto | Mitigação | Prioridade |
|---|---|---|---|
| endpoint Produção presumido errado | chamada inválida/custo | confirmar Swagger/contrato antes de Production | P0 |
| retry após timeout duplicar cobrança | custo e estado ambíguo | limite, breaker, `OUTCOME_UNKNOWN`, telemetria | P0 |
| `cProd` tratado como global | associação errada | mapping por tenant + issuer | P0 |
| `qCom` virar saldo diretamente | estoque incorreto | PackagingConversion obrigatória | P0 |
| criação silenciosa de produto | catálogo poluído | preview + confirmação explícita | P0 |
| raw payload em log | vazamento de dados | redaction e testes de scan | P0 |
| cache global virar oracle | cross-tenant/privacy | tenant-owned até threat model aprovado | P0 |
| confirmação dupla | estoque duplicado | idempotency + unique + lock/version | P0 |
| parser quebrar com novo leiaute | entrada bloqueada | raw + parser versionado + reprocessamento local | P1 |
| atomicidade entre receiving/inventory indefinida | estado parcial | fechar contrato do inventory antes de implementar | P0 |

## 25. Decisões remanescentes e responsáveis

| Decisão | Dono | Prioridade | Status/critério de fechamento |
|---|---|---:|---|
| Produto/endpoint SERPRO de Produção | Owner + SERPRO | P0 | Adiada para F7; contrato e Swagger ainda necessários |
| Retry e cobrança em timeout/408/5xx/504 | Owner + operações | P0 | Fechada para Trial; reconfirmar política antes de F7 |
| regra para NF-e cancelada | Produto/fiscal | P0 | Fechada: bloquear entrada e nunca confirmar |
| política de custo de entrada | Produto/estoque | P0 | Fechada para MVP operacional; sem rateio e sem preço de venda |
| transação receiving → inventory | Arquitetura | P0 | Fechada: síncrona/transacional no mesmo PostgreSQL |
| retenção/expurgo de raw e dados fiscais | Segurança/compliance | P0 | Trial não bloqueado; prazo legal ainda necessário antes de F7 |
| GTIN inválido/“sem GTIN” | Produto | P1 | casos de UI e domínio aprovados |
| campos exatos da planilha oficial | Fiscal | P1 | fixture e mapper cobrem o conjunto necessário |
| uso de `X-Request-Tag` | Operações/financeiro | P1 | taxonomia opaca e objetivo de faturamento definidos |
| cache global | Segurança/privacidade | P2 | threat model e conformidade aprovados |
| regra de matching além de GTIN/cProd | Produto | P2 | algoritmo determinístico e auditável definido |
| UX de OCR/câmera | Produto | P2 | novo SDD de Document Intake |

## 26. Plano de implementação após aprovação

F1–F6 estão autorizadas para Trial. A sequência abaixo organiza a execução e
seus gates; F7 permanece bloqueada:

| Fase | Entrega | Gate |
|---|---|---|
| F0 | fechar decisões P0 e anexar contrato/fixtures Trial | GOAL `APPROVED` |
| F1 | `fiscal`: chave, auth Trial, adapter, raw, parser e canônico | testes SERPRO/parser |
| F2 | persistência fiscal com RLS/idempotência | migration + tenancy gates — implementada |
| F3 | catálogo mínimo e Product Resolution | testes GTIN/cProd/tenant — implementada |
| F4 | PackagingConversion e preview | testes de unidade/conversão — implementada |
| F5 | Goods Receipt e confirmação sandbox | idempotência/atomicidade/estoque — implementada |
| F6 | observabilidade, resiliência e OpenAPI | gates de segurança/RED — implementada |
| F7 | avaliação de Produção | contrato e operação aprovados |

Cada fase precisa de SDD/milestone próprio ou atualização formal deste
documento, evidência de testes e autorização conforme a política de milestones
do repositório. Autorização antiga para outras features não transforma este
GOAL proposto em autorização de implementação.

## 27. Acceptance criteria

O GOAL será considerado implementado somente quando todos os critérios forem
demonstrados por testes e evidência:

- [x] uma chave Trial válida passa por SERPRO, parser e documento canônico;
- [x] chave inválida é rejeitada sem chamada externa;
- [x] payload bruto é persistido e reprocessável sem nova consulta;
- [ ] `RetrievalStatus`, `FiscalStatus` e `GoodsReceiptStatus` permanecem
      semanticamente independentes;
- [ ] itens são resolvidos por GTIN ou issuer + `cProd` quando possível;
- [ ] desconhecidos viram candidatos e nunca produtos silenciosos;
- [ ] conversão de embalagem é confirmada e quantidade de estoque é correta;
- [ ] consulta e preview não alteram estoque;
- [ ] confirmação cria catálogo/entrada/movimento somente após revisão;
- [ ] repetição da confirmação não duplica estoque;
- [ ] falha crítica não deixa estado interno parcial;
- [ ] RLS, membership e predicados impedem qualquer cross-tenant;
- [ ] nenhum segredo, token, chave, CPF/CNPJ, nome ou raw payload aparece em
      logs, métricas, traces ou respostas indevidas;
- [ ] timeout, breaker, bulkhead, retry limitado e `OUTCOME_UNKNOWN` são
      observáveis;
- [x] endpoint de Produção só é habilitado após contrato/Swagger aprovado;
- [x] OpenAPI descreve autenticação Bearer, idempotência, estados e erros;
- [x] gates `test`, `architecture`, `migrations`, secret scan e `git diff --check`
      passam com evidência versionada.

## 28. Gate de autorização

Este documento foi aprovado como `APPROVED_FOR_TRIAL_IMPLEMENTATION`. As
decisões P0 estão fechadas da seguinte forma:

| Decisão P0 | Resolução aprovada |
|---|---|
| SERPRO Production | Deferido para F7; não bloqueia Trial; endpoint de Produção só após contrato/Swagger real |
| Retry/billing Trial | `401` renova token uma vez; `408/500/504` admitem no máximo um retry com backoff/jitter quando tecnicamente seguro; sem retry cego; timeout/crash ambíguo = `OUTCOME_UNKNOWN` |
| NF-e cancelada | Pode ser recuperada, persistida e exibida, mas nunca confirmada como Goods Receipt; nenhuma entrada em estoque |
| Custo MVP | Custo operacional: `vUnCom`; com conversão, `unitCostBase = vUnCom / conversionFactor`; desconto/frete/seguro/outros preservados e não rateados; preço de venda nunca muda automaticamente |
| Receiving → Inventory | Síncrono e transacional no mesmo PostgreSQL; somente ports públicos; confirmação, catálogo autorizado, mappings, conversões, receipt e movement na mesma transação |
| Outbox | Não substitui atomicidade do saldo; reservado para efeitos assíncronos posteriores |
| Raw fiscal | Trial usa dados fictícios/fixtures; retenção/expurgo legal permanece bloqueante antes de F7 |

### 28.1 Autorizado

```text
F1 — Fiscal Trial
F2 — Persistência Fiscal
F3 — Catálogo mínimo / Product Resolution
F4 — Packaging / Preview
F5 — Goods Receipt / Inventory sandbox
F6 — Observabilidade / Resiliência
```

### 28.2 Não autorizado

```text
F7 — SERPRO Production
```

Para F1, a autorização é ainda mais restrita: implementar somente
`NfeAccessKey → OAuth Trial → SerproNfeAdapter → raw response → parser
versionado → CanonicalNfeDocument`. Não criar Product Catalog, Goods Receipt,
Inventory ou mutation de produto/estoque antes de F1 passar integralmente nos
testes. Não criar migration se a fase não exigir persistência.

F2 também não inicia automaticamente: se qualquer gate obrigatório de F1
falhar, o trabalho deve parar para correção e nova verificação.

As seguintes restrições continuam vigentes:

- não criar migration `V10` na F1;
- não criar código fora do slice fiscal Trial autorizado;
- não configurar Consumer Key/Secret real;
- não chamar SERPRO de Produção;
- não implementar F2–F6 antes de os gates de F1 passarem;
- não alterar catálogo ou estoque por causa de consulta.

O GOAL não precisa de novo SDD para F1. A próxima autorização deve ser
registrada como evidência de milestone somente depois dos gates objetivos.

### Fontes externas

- [SERPRO — API Consulta NFe](https://apicenter.estaleiro.serpro.gov.br/documentacao/consulta-nfe/)
- [SERPRO — autenticação e demonstração Trial](https://apicenter.estaleiro.serpro.gov.br/documentacao/consulta-nfe/pt/quick_start/)
- [SERPRO — códigos de retorno e billing](https://apicenter.estaleiro.serpro.gov.br/documentacao/consulta-nfe/pt/codigos_retorno/)
- [SERPRO — leiautes e formatos](https://apicenter.estaleiro.serpro.gov.br/documentacao/consulta-nfe/pt/leiautes_formatos/)
- [SERPRO — `X-Request-Tag`](https://apicenter.estaleiro.serpro.gov.br/documentacao/consulta-nfe/pt/identificador_requisicoes/)
