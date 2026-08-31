# TINO — Guia de integração do aplicativo Android

Status: **PRONTO PARA IMPLEMENTAÇÃO NO APP**  
Backend: **TINO hospedado na VPS Hostinger**

Este documento descreve como o aplicativo Android deve consumir o TINO. O
aplicativo conversa somente com o backend TINO; ele não chama a API da Doces &
Sonhos diretamente.

## URLs do ambiente VPS

```text
API base:       https://api.tino.otimizanegocio.com/
Keycloak:       https://auth.tino.otimizanegocio.com/realms/tino
Client ID:      tino-android
Redirect URI:   tino://oauth/callback
```

Não usar `localhost`, `10.0.2.2`, IP da VPS ou token fixo no APK.

## 1. Autenticação

Usar OAuth 2.0 Authorization Code com PKCE, preferencialmente através de uma
biblioteca OIDC/AppAuth compatível com Android.

Fluxo:

```text
Android → Keycloak login
        → authorization code
        → troca com PKCE
        → access token + refresh token
        → chamadas HTTPS ao TINO
```

O app deve:

- manter o access token em memória sempre que possível;
- guardar a sessão usando o armazenamento seguro já adotado pelo app;
- renovar o access token usando o refresh token;
- ao receber `401`, renovar uma única vez e repetir a requisição uma única vez;
- se a renovação falhar, limpar a sessão e voltar ao login;
- nunca registrar token, refresh token, senha ou header `Authorization` nos logs.

O grant `password` pode ser usado apenas para testes manuais com `curl`. Não é
o fluxo que o aplicativo deve implementar.

## 2. Configuração por ambiente

Usar `BuildConfig`, flavor ou configuração equivalente:

```kotlin
const val TINO_API_BASE_URL = "https://api.tino.otimizanegocio.com/"
const val TINO_OIDC_ISSUER =
    "https://auth.tino.otimizanegocio.com/realms/tino"
const val TINO_OIDC_CLIENT_ID = "tino-android"
const val TINO_OIDC_REDIRECT_URI = "tino://oauth/callback"
```

Em produção, os valores devem vir da configuração do build. Nenhuma senha,
Consumer Key, Consumer Secret ou credencial SERPRO deve entrar no Android.

## 3. Onboarding da tela “Vamos preparar seu comércio”

A tela possui comércio, nome do usuário, celular e vertical. O fluxo do botão
`CONTINUAR` deve ser:

1. autenticar o usuário no Keycloak;
2. chamar `POST /api/v1/bootstrap`;
3. se não houver empresa, criar a empresa;
4. definir explicitamente a origem de dados do Business;
5. registrar a instalação do aparelho;
6. chamar o bootstrap novamente;
7. somente com estado `READY`, abrir a tela principal.

### 3.1 Consultar o contexto inicial

```http
POST /api/v1/bootstrap
Authorization: Bearer <access_token>
Content-Type: application/json

{}
```

Estados possíveis:

| Estado | Ação do app |
|---|---|
| `BUSINESS_REQUIRED` | mostrar/criar uma empresa |
| `LOCAL_BUSINESS_LINK_REQUIRED` | selecionar empresa e/ou registrar instalação |
| `READY` | abrir o app com a empresa selecionada |

### 3.2 Criar a empresa

Usar somente os campos atualmente suportados pelo backend:

```http
POST /api/v1/businesses
Authorization: Bearer <access_token>
Content-Type: application/json

{
  "trade_name": "Mercadinho São José",
  "vertical": "RETAIL"
}
```

Verticais atuais: `RETAIL`, `BAKERY`, `RESTAURANT`, `STORE` e `OTHER`.

O backend atualmente não possui endpoint de perfil para `nome` e `celular` do
proprietário. O app não deve enviar esses campos para `POST /businesses`, pois
eles não fazem parte desse contrato. Enquanto o perfil não existir, mantê-los
localmente ou tratá-los como dados pendentes.

Resposta esperada:

```json
{
  "id": "uuid-da-empresa",
  "trade_name": "Mercadinho São José",
  "vertical": "RETAIL",
  "status": "ACTIVE",
  "role": "OWNER",
  "data_source_type": "TINO_NATIVE"
}
```

### 3.3 Definir a origem dos dados

O app deve coletar uma escolha simples, sem inferir pelo nome do comércio:

```text
Você já usa algum sistema no seu comércio?
[ Não, começar no TINO ]
[ Sim, conectar meu sistema ]
```

Depois de criar ou selecionar o Business, enviar uma única vez:

```http
PUT /api/v1/businesses/{businessId}/data-source
Authorization: Bearer <access_token>
Content-Type: application/json

{
  "source_type": "TINO_NATIVE",
  "provider": null
}
```

Para a integração piloto:

```json
{
  "source_type": "EXTERNAL_API",
  "provider": "DOCES_SONHOS"
}
```

O backend é a autoridade. Não usar `trade_name`, vertical, device ou
`installation_id` para decidir a fonte. Em outro aparelho, reutilizar o mesmo
`business_id` e consultar `GET /api/v1/businesses/{businessId}/data-source`;
não selecionar novamente nem chamar a API Doces & Sonhos diretamente.

### 3.4 Registrar a instalação do aparelho

Gerar um identificador estável por instalação do app. Não usar um valor novo a
cada abertura.

```http
POST /api/v1/businesses/{businessId}/installations
Authorization: Bearer <access_token>
Content-Type: application/json

{
  "installation_id": "id-estavel-da-instalacao"
}
```

O identificador não deve conter senha, token ou informação fiscal.

### 3.5 Finalizar o bootstrap

```http
POST /api/v1/bootstrap
Authorization: Bearer <access_token>
Content-Type: application/json

{
  "requested_business_id": "uuid-da-empresa",
  "installation_external_id": "id-estavel-da-instalacao"
}
```

Guardar localmente o `business_id` selecionado e a instalação ativa. Não
selecionar uma empresa apenas por `storeId`, nome ou valor enviado pela UI.

## 4. Catálogo de produtos

Depois que o bootstrap estiver `READY`, consultar o catálogo do TINO:

```http
GET /api/v1/businesses/{businessId}/products?limit=100
Authorization: Bearer <access_token>
Accept: application/json
```

Busca opcional:

```http
GET /api/v1/businesses/{businessId}/products?q=bolo&gtin=7890000000000&limit=50
```

Resposta:

```json
[
  {
    "product_id": "uuid-do-produto-tino",
    "name": "Bolo Crocante",
    "base_unit": "UN",
    "gtin": null,
    "price": 65.000000000
  }
]
```

Regras no app:

- `product_id` é a identidade estável para o Room;
- `gtin: null` é válido;
- converter `price` para centavos com `BigDecimal`, nunca `Double` ou `Float`;
- `65.00` deve virar `6500`;
- não aceitar preço negativo, inválido ou com fração de centavo;
- fazer upsert transacional por `product_id`;
- não apagar o catálogo local quando houver erro de rede;
- não criar produto por nome parecido;
- manter a lista da UI observando o Room, e não diretamente a rede.

O endpoint atual aceita `limit` de 1 a 100 e ainda não retorna cursor de
paginação. O app não deve inventar `page`, `offset` ou `cursor`. Enquanto o
contrato de paginação completa não existir, não declarar que uma consulta de
100 itens representa necessariamente todo o catálogo.

## 5. Recebimento de NF-e

O fluxo fiscal também passa pelo TINO e exige confirmação humana:

```text
consultar NF-e → visualizar preview → revisar produtos
              → confirmar → Goods Receipt → Inventory Movement
```

### Consultar a NF-e

```http
POST /api/v1/businesses/{businessId}/nfe-documents
Authorization: Bearer <access_token>
Idempotency-Key: nfe-<access-key>
Content-Type: application/json

{
  "access_key": "chave-de-44-digitos"
}
```

O `Idempotency-Key` é obrigatório e deve ser estável para a mesma operação.
Não repetir cegamente em caso de timeout; o resultado pode ser
`OUTCOME_UNKNOWN`.

Para obter o preview novamente:

```http
GET /api/v1/businesses/{businessId}/nfe-documents/{documentId}/preview
Authorization: Bearer <access_token>
```

Após a revisão humana, confirmar:

```http
POST /api/v1/businesses/{businessId}/goods-receipts/{previewId}/confirm
Authorization: Bearer <access_token>
Idempotency-Key: receipt-<previewId>-<previewVersion>
Content-Type: application/json

{
  "preview_version": 0,
  "items": [
    {
      "line_number": 1,
      "action": "USE_EXISTING",
      "product_id": "uuid-do-produto",
      "conversion_factor": 1,
      "base_unit": "UN"
    }
  ]
}
```

As ações permitidas são `USE_EXISTING`, `CREATE_PRODUCT` e `IGNORE`. O app não
deve confirmar automaticamente uma NF-e cancelada nem criar produtos sem
decisão explícita do usuário.

## 6. Camadas recomendadas no Android

```text
Compose/UI
  ↓ ViewModel
  ↓ Use case (Bootstrap, SyncCatalog, ReceiveNfe)
  ↓ Repository
  ├─ Remote API + OAuth interceptor
  └─ Room DAO
```

O Composable não deve fazer chamadas HTTP. Criar DTOs remotos separados das
entidades Room e dos modelos usados pela UI.

Para o catálogo, a operação deve ser equivalente a:

```text
SyncCatalog(businessId)
  → chama CatalogApi
  → valida DTOs e preço
  → faz upsert transacional no Room
  → retorna criados/atualizados/rejeitados
```

Impedir duas sincronizações simultâneas para a mesma empresa. Durante uma
falha, conservar os produtos já exibidos e informar uma mensagem operacional
sanitizada.

## 7. Tratamento de respostas

| HTTP | Comportamento |
|---|---|
| `200`/`201` | processar resposta |
| `400` | mostrar erro de validação; não repetir automaticamente |
| `401` | renovar token uma vez e repetir uma vez |
| `403` | informar que o usuário não possui acesso à empresa |
| `404` | informar recurso não encontrado |
| `408`, `429`, `5xx` | erro temporário; retry limitado conforme cliente |
| timeout | manter Room e informar indisponibilidade |
| JSON inválido | não alterar dados locais |

Não exibir ao usuário token, stack trace, segredo, raw fiscal ou credencial.

## 8. Checklist do primeiro teste no Android

- [ ] API configurada com `https://api.tino.otimizanegocio.com/`;
- [ ] issuer configurado com o realm `tino` da VPS;
- [ ] login OIDC com PKCE funcionando;
- [ ] refresh token funcionando;
- [ ] `POST /bootstrap` retorna `BUSINESS_REQUIRED` ou `READY`;
- [ ] criação da empresa retorna `201`;
- [ ] instalação é registrada uma única vez;
- [ ] bootstrap final retorna `READY`;
- [ ] `businessId` é armazenado localmente;
- [ ] catálogo é consultado com Bearer;
- [ ] preço é exibido corretamente, por exemplo `R$ 65,00`;
- [ ] produto sem GTIN não é descartado;
- [ ] erro `401` não cria loop de login;
- [ ] reconexão não apaga o Room;
- [ ] nenhum segredo aparece nos logs.

## 9. Endereços úteis para teste manual

- Swagger: `https://api.tino.otimizanegocio.com/swagger-ui.html`;
- OpenAPI: `https://api.tino.otimizanegocio.com/openapi`;
- Health: `https://api.tino.otimizanegocio.com/actuator/health/readiness`;
- OIDC discovery: `https://auth.tino.otimizanegocio.com/realms/tino/.well-known/openid-configuration`.

O Swagger e o OpenAPI são superfícies públicas de documentação. As rotas de
negócio exigem `Authorization: Bearer <access_token>`.
