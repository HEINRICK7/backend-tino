# TINO — Identity WhatsApp OTP — Evidence

Status: **PASS_LOCAL_E2E_PENDING_VPS_AND_DEVICE_SMOKE**

## Incidente de origem

1. O usuário preencheu o onboarding nativo do TINO.
2. Selecionou conectar o sistema Doces & Sonhos.
3. Tocou em `CONTINUAR`.
4. O Android iniciou OIDC.
5. O Keycloak exibiu `Username or email / Password / Sign In`.
6. A experiência foi considerada incompatível com o produto.
7. A autenticação foi cancelada e o TINO exibiu a mensagem correspondente.
8. Foi decidida a autenticação por celular + OTP via WhatsApp.

## Gates concluídos localmente

| Gate | Evidência |
|---|---|
| Domínio/aplicação sem provider concreto | `OtpDeliveryPort` e use cases em `modules/identity` |
| OTP não retornado | `OtpUseCaseTest.requestDoesNotReturnCodeAndVerifyCreatesOneTimeProof` |
| Hash e segredo de runtime | `HmacOtpSecretHasher` + `TINO_OTP_HASH_SECRET` |
| Expiração/tentativas/replay | `OtpUseCaseTest` |
| Persistência PostgreSQL/Flyway | `OtpChallengePostgresTest` e migration `V16` |
| Adapter Go isolado | `delivery/main.go`, imagem compilada em container |
| Comportamento do delivery | `delivery/main_test.go`; `go test ./...` passou em container Go 1.22 |
| Keycloak SPI compilável | `keycloak-extension`, imagem Keycloak compilada em container |
| Compose | `docker compose ... config --quiet` passou |
| Gates completos | `./gradlew check architecture migrations --no-daemon` passou; 200 testes da aplicação |
| Integridade do diff e segredos | `git diff --check` e `./scripts/secret-scan.sh` passaram |

## Gates pendentes

- não há sessão/credencial real do wa-evolution disponível neste workspace;
- o smoke Android físico/emulador até `bootstrap → READY` ainda não foi
  executado;
- a VPS pública ainda responde com a versão anterior no endpoint OTP;
- o delivery físico wa-evolution/WhatsApp depende de credenciais de runtime.

O fluxo Keycloak foi validado localmente com a imagem real do servidor e um
delivery fake controlado: ticket válido produziu callback OIDC e code exchange
PKCE; ausência/ticket inválido foram rejeitados sem formulário de senha. A
configuração do Browser Flow está em `docker/keycloak/tino-realm.json` e o
script idempotente para realm já existente está em
`docker/keycloak/configure-tino-otp-flow.sh`.

Esses gates não são simulados como PASS. A infraestrutura retorna erro quando
provider/segredos não estão configurados e o serviço Go retorna `503` quando a
configuração do provider está incompleta.

## Arquivos principais

- `modules/identity/src/main/java/com/tino/backend/identity/application/usecase/RequestOtp.java`
- `modules/identity/src/main/java/com/tino/backend/identity/application/usecase/VerifyOtp.java`
- `modules/identity/src/main/java/com/tino/backend/identity/adapter/out/delivery/WaEvolutionOtpDeliveryAdapter.java`
- `modules/identity/src/main/java/com/tino/backend/identity/adapter/out/persistence/JooqOtpChallengeRepository.java`
- `app/src/main/resources/db/migration/V16__identity_otp_challenges.sql`
- `delivery/main.go`
- `keycloak-extension/src/main/java/com/tino/backend/keycloak/TinoOtpAuthenticator.java`
- `docs/TINO-IDENTITY-WHATSAPP-OTP-CONTRACT.md`

## Próximo smoke autorizado

Configurar somente por secrets/runtime: `TINO_OTP_ENABLED`,
`TINO_OTP_HASH_SECRET`, `TINO_OTP_INTERNAL_TOKEN`,
`TINO_OTP_DELIVERY_INTERNAL_TOKEN`, `WA_EVOLUTION_BASE_URL`,
`WA_EVOLUTION_API_KEY` e `WA_EVOLUTION_INSTANCE`. Executar request, entrega,
verify, Browser Flow OIDC, bootstrap e reexecução; nunca registrar OTP, token,
telefone em claro ou segredo.
