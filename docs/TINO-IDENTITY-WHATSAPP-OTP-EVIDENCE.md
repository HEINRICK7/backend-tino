# TINO — Identity WhatsApp OTP — Evidence

Status: **PASS_VPS_OTP_AND_DEVICE_MATRIX_PENDING_OIDC_CONFIRMATION**

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
| E2E OTP na VPS | request autorizado retornou `201 OTP_SENT`; consulta pública posterior retornou `200 OTP_DELIVERED`; PostgreSQL registrou `AUTH_DELIVERED` sem expor o código |
| Compatibilidade Evolution v2.3.7 | relay normaliza `messages.update` flat e também o formato legado; `PENDING`/`SERVER_ACK` são intermediários, recibos terminais são processados |
| Recibo não relacionado a OTP | evento terminal desconhecido foi aceito sem erro e sem gravação, evitando retry infinito da Evolution |
| Matriz física Android | Samsung SM-A042M/API 34: instalação incremental, processo ativo e zero crash fatal |

## Gates pendentes

- o smoke Android físico/emulador até `bootstrap → READY` ainda não foi
  executado;
- o Browser Flow OIDC completo no dispositivo ainda não foi executado;
- F7/Produção continua explicitamente bloqueado até esses gates externos.

O deploy de `main` no commit `0f908e278760125198528658000f69a741312504` passou pelo
workflow `34173443238` (gates, publish e deploy concluídos).
Na VPS, `tino-app`, `tino-keycloak` e `tino-otp-delivery` estão ativos; o
configurador do Browser Flow terminou com exit `0`. A readiness pública retorna
HTTP 200. O E2E autorizado confirmou o caminho completo até a entrega: o
backend criou o desafio, a Evolution entregou o provider message id, o relay
aceitou os recibos flat da versão 2.3.7 e o backend persistiu `AUTH_DELIVERED`.
O `tino-app`, Keycloak OTP e relay foram atualizados sem recriar Evolution,
alterar banco, apagar volumes ou invalidar sessões; Evolution e os bancos
permaneceram ativos.

Imagens usadas no runtime desta validação, publicadas no GHCR por SHA:
`ghcr.io/heinrick7/backend-tino:0f908e278760125198528658000f69a741312504`,
`ghcr.io/heinrick7/backend-tino-keycloak:0f908e278760125198528658000f69a741312504`
e `ghcr.io/heinrick7/backend-tino-delivery:0f908e278760125198528658000f69a741312504`.

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
`WA_EVOLUTION_API_KEY` e `WA_EVOLUTION_INSTANCE`. A request e a entrega já
estão comprovadas na VPS; falta confirmar o código no telefone autorizado e
executar `verify`, Browser Flow OIDC, bootstrap até `READY` e reexecução. Nunca
registrar OTP, token, telefone em claro ou segredo.
