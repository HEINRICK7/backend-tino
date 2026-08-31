# TINO — Identity OTP: evidência ponta a ponta

Data: 2026-08-31
Status: **PASS_LOCAL_E2E_PENDING_VPS_AND_DEVICE_SMOKE**

## Escopo comprovado

O fluxo local controlado foi executado com Keycloak 26.3.5, a imagem do
provider TINO e um delivery HTTP fake. O fake só representa a ponte interna de
consumo; ele não autentica, não cria sessão e não emite tokens.

```text
Android contract
  → request OTP
  → verify OTP
  → one-time ticket
  → Keycloak Browser Flow / TINO OTP Authenticator
  → tino://oauth/callback
  → authorization-code + PKCE exchange
  → access_token + refresh_token
```

## Implementação

- Backend: `OtpDeliveryPort`, desafio persistido com HMAC, expiração, cooldown,
  tentativas, reenvios, rate limit e ticket de uso único.
- Ponte interna: o ticket só é consumido com segredo de runtime e com
  `client_id=tino-android` validado; o banco guarda somente o hash do ticket.
- Keycloak: `TinoOtpAuthenticator` lê o parâmetro transitório, consulta o
  backend, resolve/cria a identidade por `phone_e164` e completa o fluxo OIDC.
- Realm: `tino-otp-browser` é um fluxo explícito com o authenticator REQUIRED;
  `VERIFY_PROFILE` fica desabilitado para não introduzir uma tela após o OTP;
  `directAccessGrantsEnabled=false` permanece obrigatório.
- Android: request/verify nativos, código de seis dígitos, loading/erro,
  expiração, reenvio após cooldown, cancelamento sem persistência, e
  Authorization Code + PKCE somente depois de verificar o OTP.
- Tokens continuam exclusivamente no `SecureTokenStore`; código OTP e ticket
  não são registrados em log nem persistidos pelo Android.
- `wa-evolution`/Go permanece somente adapter de entrega.

## Evidências executadas

| Verificação | Resultado |
|---|---|
| `./gradlew check architecture migrations --no-daemon` | PASS |
| Backend identity tests, incluindo replay e client binding | PASS |
| `docker run ... golang:1.22-alpine go test ./...` | PASS |
| `gradle :app:compileDebugKotlin` | PASS |
| `gradle :app:testDebugUnitTest` | PASS |
| `gradle :app:lintDebug` | PASS |
| `./scripts/secret-scan.sh` | PASS |
| `git diff --check` | PASS |
| `python3 -m json.tool docker/keycloak/tino-realm.json` | PASS |
| import do realm em Keycloak real local | PASS |
| configuração idempotente do flow em realm existente | PASS |
| authorization sem ticket | REJEITADO, sem formulário username/password |
| ticket inválido/curto | REJEITADO, sem formulário username/password |
| ticket válido → callback `tino://oauth/callback` | PASS |
| code verifier PKCE inválido | REJEITADO com `invalid_grant` |
| code exchange válido | PASS, access/refresh token emitidos |

## Arquivos principais

- `modules/identity/src/main/java/com/tino/backend/identity/application/usecase/ConsumeOtpVerificationTicket.java`
- `modules/identity/src/main/java/com/tino/backend/identity/adapter/in/otp/OtpInternalTicketController.java`
- `keycloak-extension/src/main/java/com/tino/backend/keycloak/TinoOtpAuthenticator.java`
- `docker/keycloak/tino-realm.json`
- `docker/keycloak/configure-tino-otp-flow.sh`
- `deploy/compose.vps.yaml`
- `app/src/main/java/com/tino/app/core/auth/OidcAuthCoordinator.kt` no projeto Android
- `app/src/main/java/com/tino/app/core/network/OtpAuthApi.kt` no projeto Android
- `app/src/main/java/com/tino/app/TinoApp.kt` no projeto Android

## Gates ainda abertos

1. O domínio público `api.tino.otimizanegocio.com` ainda está rodando uma
   versão anterior: uma chamada sem bearer ao endpoint OTP respondeu `401`,
   portanto o novo endpoint não foi publicado na VPS nesta execução.
2. O delivery real wa-evolution/WhatsApp ainda depende das credenciais de
   runtime e não foi usado como prova de entrega.
3. Falta o smoke em Android físico/emulador até `bootstrap → READY`.
4. A operação de Produção/F7 continua bloqueada.

Nenhum desses gates foi marcado como PASS por inferência. O próximo passo
seguro é publicar os commits no pipeline autorizado, conferir o realm existente
com o job `keycloak-otp-config`, configurar os secrets de runtime e executar o
smoke real no dispositivo.
