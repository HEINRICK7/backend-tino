# TINO — Business Data Source Onboarding — Android Handoff

Status: **HANDOFF READY / IMPLEMENTATION IN ANDROID REPOSITORY**

O backend entregou o contrato autoritativo. O agent Android deve registrar
neste arquivo, no próprio repositório do app, os arquivos e testes da tela de
onboarding, cliente HTTP, persistência local e renovação de token.

Contrato obrigatório:

```http
PUT https://api.tino.otimizanegocio.com/api/v1/businesses/{businessId}/data-source
Authorization: Bearer <access_token>
Content-Type: application/json
```

Payloads:

```json
{"source_type":"TINO_NATIVE","provider":null}
```

```json
{"source_type":"EXTERNAL_API","provider":"DOCES_SONHOS"}
```

O app não chama a API Doces & Sonhos diretamente, não envia credenciais e não
decide a origem por nome comercial, vertical, device ou installation id.
Depois de instalar outro device no mesmo Business, ler o estado do backend e
reutilizar o `business_id`.

Referências:

- `docs/TINO-BUSINESS-DATA-SOURCE-ONBOARDING-CONTRACT.md`
- `docs/TINO-ANDROID-API-INTEGRATION.md`
