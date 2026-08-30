# TINO NF-e — Contract Coverage Hardening

Status: `PASS — TRIAL CONTRACT COVERAGE`

## Fonte e regra de escopo

Fonte de referência: `/home/carlos-henrique/Downloads/NFE_Campos .xlsx`.
SHA-256 observado: `b026019744681e97d6228d00962c99535d3e81ab349b83c7d0a2a5b87fc7d9a1`.

A planilha é usada somente para identificar o layout/campos. Ela não é
convertida em payload e não substitui as fixtures JSON oficiais, que continuam
sendo a fonte dos testes do parser e do Trial.

Na implementação atual não existe um DTO Java externo SERPRO separado: a
fronteira de contrato é o JSON do provider recebido pelo
`SerproNfeParser` (`nfe-parser-v1`). A persistência grava tanto o raw JSONB
quanto a projeção canônica e as colunas operacionais de `nfe_items`.

## Matriz principal

Legenda: `SIM` em persistência significa coluna dedicada e/ou `canonical_payload`
JSONB; todos os campos raw do provider permanecem preservados em `raw_payload`.

| Campo SERPRO | Classe | Parser | Canonical | Persistência | Uso | Classificação |
|---|---|---|---|---|---|---|
| `infProt/chNFe` / chave | `NfeAccessKey` | `accessKey` | `CanonicalNfeDocument.accessKey` | `nfe_documents.access_key` + canonical | identidade/idempotência | `REQUIRED_FOR_GOODS_RECEIPT` |
| `ide/nNF` | documento | `number` | `CanonicalNfeDocument.number` | `nfe_documents.document_number` | evidência | `PRESERVED_FOR_FISCAL_EVIDENCE` |
| `ide/serie` | documento | `series` | `CanonicalNfeDocument.series` | `nfe_documents.series` | evidência | `PRESERVED_FOR_FISCAL_EVIDENCE` |
| `ide/dhEmi` | documento | `issuedAt` | `CanonicalNfeDocument.issuedAt` | canonical JSONB | evidência | `PRESERVED_FOR_FISCAL_EVIDENCE` |
| `ide/natOp` | documento | `natureOperation` | `CanonicalNfeDocument.natureOperation` | canonical JSONB | contexto da entrada | `OPTIONAL_SUPPORTED` |
| `ide/tpNF` | documento | `operationType` | `CanonicalNfeDocument.operationType` | canonical JSONB | contexto da entrada | `OPTIONAL_SUPPORTED` |
| `emit/CNPJ` ou `emit/CPF` | emitente | `issuer.document` | `CanonicalNfeIssuer.document` | `nfe_documents.issuer_document` + canonical | mapping por emitente | `REQUIRED_FOR_GOODS_RECEIPT` |
| `emit/xNome` | emitente | `issuer.legalName` | `CanonicalNfeIssuer.legalName` | canonical JSONB | candidato/evidência | `REQUIRED_FOR_GOODS_RECEIPT` |
| `emit/xFant` | emitente | `issuer.tradeName` | `CanonicalNfeIssuer.tradeName` | canonical JSONB | evidência | `OPTIONAL_SUPPORTED` |
| `emit/IE` | emitente | `issuer.stateRegistration` | `CanonicalNfeIssuer.stateRegistration` | canonical JSONB | evidência | `PRESERVED_FOR_FISCAL_EVIDENCE` |
| `protNFe/infProt/cStat` | protocolo | `fiscalStatus` | `CanonicalNfeDocument.fiscalStatus` | `nfe_documents.fiscal_status` + canonical | bloqueia CANCELLED/DENIED | `REQUIRED_FOR_GOODS_RECEIPT` |
| `det/@nItem` | item | `lineNumber` | `CanonicalNfeItem.lineNumber` | `nfe_items.line_number` | decisão humana | `REQUIRED_FOR_GOODS_RECEIPT` |
| `det/prod/cProd` | item | `supplierProductCode` | `CanonicalNfeItem.supplierProductCode` | `nfe_items.supplier_product_code` | mapping/candidato | `REQUIRED_FOR_GOODS_RECEIPT` |
| `det/prod/cEAN` | item | `gtin` | `CanonicalNfeItem.gtin` | `nfe_items.gtin` | resolução por GTIN quando utilizável | `OPTIONAL_SUPPORTED` |
| `det/prod/xProd` | item | `description` | `CanonicalNfeItem.description` | `nfe_items.description` | candidato/criação autorizada | `REQUIRED_FOR_GOODS_RECEIPT` |
| `det/prod/NCM` | item | `ncm` | `CanonicalNfeItem.ncm` | `nfe_items.ncm` | evidência | `PRESERVED_FOR_FISCAL_EVIDENCE` |
| `det/prod/CEST` | item | `cest` | `CanonicalNfeItem.cest` | `nfe_items.cest` | evidência | `PRESERVED_FOR_FISCAL_EVIDENCE` |
| `det/prod/CFOP` | item | `cfop` | `CanonicalNfeItem.cfop` | `nfe_items.cfop` | evidência | `PRESERVED_FOR_FISCAL_EVIDENCE` |
| `det/prod/uCom` | item | `commercialUnit` | `CanonicalNfeItem.commercialUnit` | `nfe_items.commercial_unit` | unidade de compra | `REQUIRED_FOR_GOODS_RECEIPT` |
| `det/prod/qCom` | item | `commercialQuantity` | `CanonicalNfeItem.commercialQuantity` | `nfe_items.commercial_quantity` | quantidade recebida | `REQUIRED_FOR_GOODS_RECEIPT` |
| `det/prod/vUnCom` | item | `commercialUnitPrice` | `CanonicalNfeItem.commercialUnitPrice` | `nfe_items.commercial_unit_price` | custo operacional MVP | `REQUIRED_FOR_GOODS_RECEIPT` |
| `det/prod/vProd` | item | `productTotal` | `CanonicalNfeItem.productTotal` | `nfe_items.product_total` | conferência/evidência | `REQUIRED_FOR_GOODS_RECEIPT` |
| `det/prod/cEANTrib` | item | `taxGtin` | `CanonicalNfeItem.taxGtin` | `nfe_items.tax_gtin` | evidência tributária | `PRESERVED_FOR_FISCAL_EVIDENCE` |
| `det/prod/uTrib` | item | `taxUnit` | `CanonicalNfeItem.taxUnit` | `nfe_items.tax_unit` | evidência tributária | `PRESERVED_FOR_FISCAL_EVIDENCE` |
| `det/prod/qTrib` | item | `taxQuantity` | `CanonicalNfeItem.taxQuantity` | `nfe_items.tax_quantity` | conferência | `PRESERVED_FOR_FISCAL_EVIDENCE` |
| `det/prod/vUnTrib` | item | `taxUnitPrice` | `CanonicalNfeItem.taxUnitPrice` | `nfe_items.tax_unit_price` | conferência | `PRESERVED_FOR_FISCAL_EVIDENCE` |
| `det/prod/vDesc` | item | `discount` | `CanonicalNfeItem.discount` | `nfe_items.discount` | preservado, não rateado no MVP | `PRESERVED_FOR_FISCAL_EVIDENCE` |
| `det/prod/vFrete` | item | `freight` | `CanonicalNfeItem.freight` | `nfe_items.freight` | preservado, não rateado no MVP | `PRESERVED_FOR_FISCAL_EVIDENCE` |
| `det/prod/vSeg` | item | `insurance` | `CanonicalNfeItem.insurance` | `nfe_items.insurance` | preservado, não rateado no MVP | `PRESERVED_FOR_FISCAL_EVIDENCE` |
| `det/prod/vOutro` | item | `otherValue` | `CanonicalNfeItem.otherValue` | `nfe_items.other_value` | preservado, não rateado no MVP | `PRESERVED_FOR_FISCAL_EVIDENCE` |
| `det/prod/indTot` | item | `includedInTotal` | `CanonicalNfeItem.includedInTotal` | `nfe_items.included_in_total` | conferência | `PRESERVED_FOR_FISCAL_EVIDENCE` |

`cEAN` não é obrigatório para aceitar uma entrada. Quando ausente, vazio ou
não utilizável, o fluxo segue o fallback determinístico `GTIN → issuer + cProd
→ candidato para revisão humana`; a ausência de GTIN nunca deve rejeitar por
si só uma NF-e válida nem criar produto silenciosamente.

## Campos fora do recorte operacional

Os grupos de impostos detalhados, totais fiscais, transporte, cobrança,
pagamento, importação, exportação, combustível, veículo, medicamento, arma,
eventos e informações adicionais ficam preservados no raw fiscal quando vierem
do provider, mas não têm contrato canônico nem uso no Goods Receipt atual.
Classificação: `OUT_OF_SCOPE`. Isso não autoriza cálculo tributário, SPED,
emissão, ERP fiscal, OCR ou alteração de preço de venda.

## Evidência de testes

- `SerproNfeParserTest.mapsOfficialTrialFixtureToCanonicalDocumentWithExactDecimals`
  verifica documento, emitente e todos os campos do item usados/preservados.
- `NfeContractCoveragePostgresTest.persistsEveryGoodsReceiptFieldAndCanonicalEvidence`
  verifica a projeção `nfe_items`, `raw_payload`, `canonical_payload` e
  `parser_version` em PostgreSQL real com RLS/tenant context.
- A fixture usada continua sendo
  `modules/fiscal/src/test/resources/serpro/consulta-nfe-trial-official-sanitized.json`.

F7/SERPRO Produção permanece bloqueado. Este gate não cria Consumer Key/Secret
e não substitui o futuro Trial Real Smoke com credenciais oficiais.
