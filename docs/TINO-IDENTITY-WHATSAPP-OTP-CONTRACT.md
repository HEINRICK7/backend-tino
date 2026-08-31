# TINO — Identity por celular + OTP

Status: **IMPLEMENTED LOCALLY; OIDC/WhatsApp REAL PENDING EXTERNAL SETUP**

## Decisão

O onboarding do TINO usa celular + código de uso único enviado pelo WhatsApp.
O Keycloak continua sendo a autoridade de identidade e o único emissor dos
tokens OIDC. O backend TINO nunca emite JWT próprio, não usa password grant e
não confia em telefone, instalação ou Business como prova de identidade.

## Contrato HTTP

### Solicitar código

```http
POST /api/v1/auth/otp/challenges
Content-Type: application/json

{"phone":"+5586995922924"}
```

Resposta somente após o provider aceitar a entrega:

```json
{
  "challenge_id": "uuid",
  "expires_in_seconds": 300,
  "resend_available_in_seconds": 30,
  "delivery_channel": "WHATSAPP"
}
```

O código nunca aparece na resposta, no log ou no banco. A entrada aceita
formatos brasileiros comuns e normaliza para E.164.

### Verificar código

```http
POST /api/v1/auth/otp/challenges/{challengeId}/verify
Content-Type: application/json

{"code":"482731"}
```

Resposta:

```json
{
  "challenge_id": "uuid",
  "verification_status": "VERIFIED",
  "verification_ticket": "one-time-opaque-value",
  "ticket_expires_in_seconds": 60
}
```

`verification_ticket` não é access token. Ele é uma prova opaca, curta e de
uso único para a extensão de autenticação do Keycloak. O Android inicia o
Authorization Code + PKCE com esse valor como parâmetro temporário da
autorização; depois o fluxo normal do Keycloak emite o code, access token e
refresh token.

### Ponte interna Keycloak

```http
POST /internal/v1/identity/otp/tickets/consume
X-Tino-Internal-Token: <segredo de runtime>
Content-Type: application/json

{"ticket":"one-time-opaque-value","client_id":"tino-android"}
```

Essa rota exige o segredo interno em runtime, valida o `client_id` autorizado e
consome o ticket atomicamente.
Ela retorna apenas o telefone normalizado e os metadados mínimos para o
authenticator Keycloak. Ela não autoriza Business, não registra instalação e
não emite token.

## Arquitetura

```text
Android → TINO RequestOtp → OtpDeliveryPort
                           → WaEvolutionOtpDeliveryAdapter
                           → serviço Go privado
                           → wa-evolution → WhatsApp

Android → TINO VerifyOtp → verification ticket
        → Keycloak Browser Flow / TINO OTP Authenticator
        → OIDC Authorization Code + PKCE
        → bearer token TINO
        → bootstrap / Business / Data Source / instalação
```

O domínio e a aplicação conhecem somente `OtpDeliveryPort`. O provider atual é
selecionado na composição por `WA_EVOLUTION`; Meta e RCS ficam como adapters
futuros. O serviço Go é carteiro: não gera/valida OTP, não conhece Keycloak,
Business ou tenant e não persiste sessão de autenticação.

## Persistência e proteção

`public.otp_challenges` guarda somente o telefone normalizado necessário para a
entrega, hashes HMAC com segredo de runtime, estado, expiração, contadores e
ticket hash. Estados: `PENDING`, `VERIFIED`, `EXPIRED`, `LOCKED`, `CONSUMED` e
`DELIVERY_FAILED`.

Há lock transacional por telefone, cooldown de 30 segundos, limite de cinco
tentativas, três reenvios, dez solicitações por telefone/hora e limite por
origem. O cleanup remove estados terminais antigos. Repetição de verify e
concorrência não podem reutilizar o mesmo desafio.

OTP não é autorização de Business: após OIDC, continuam valendo
`JWT sub → User → Membership → Business → RLS`.

## Keycloak

O plugin `keycloak-extension` implementa o Authentication SPI para a versão
pinada 26.3.5. O realm versionado declara um Browser Flow dedicado contendo
`TINO OTP ticket` como execução `REQUIRED`, vincula-o ao realm e mantém
`standardFlowEnabled=true` e `directAccessGrantsEnabled=false` no client
`tino-android`. O script `docker/keycloak/configure-tino-otp-flow.sh` repete a
mesma configuração de forma idempotente e recusa fallback para usuário/senha.

Essa composição foi validada em um realm local antes de qualquer habilitação no
VPS, incluindo emissão de authorization code, troca com PKCE, rejeição de
ticket inválido/repetido e rejeição de verifier incorreto. O Keycloak permanece
o emissor final dos tokens; o plugin apenas resolve a prova TINO dentro do
Browser Flow.

## Não escopo

Não implementar Meta/WABA agora, RCS, SMS como requisito único, chatbot,
marketing, emissão fiscal, autorização de tenant pelo telefone ou modo offline
baseado em `installation_id` como credencial remota. O Android deve manter a
sessão local/Room e usar refresh/sync somente quando houver conectividade.
