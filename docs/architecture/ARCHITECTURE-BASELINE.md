# TINO Backend — Architecture Baseline

## Status

**APPROVED**

Este documento define decisões arquiteturais obrigatórias. Qualquer alteração estrutural exige ADR novo ou substituição explícita de decisão existente.

## 1. Objetivo

Construir um backend cloud com segurança multi-tenant, compatibilidade com o Android local-first, sincronização confiável, evolução incremental, forte consistência financeira, infraestrutura substituível por adapters e escala sem reescrita precoce.

## 2. Stack oficial

- Runtime: Java 21 e Spring Boot.
- Build: Gradle com Kotlin DSL.
- Banco: PostgreSQL.
- Persistência: jOOQ, somente em adapters. `DSLContext`, `Record`, `Result`, classes geradas e exceções jOOQ são proibidos em domain/application.
- Migração: Flyway, exclusivamente; auto-DDL é proibido.
- Arquitetura: Modular Monolith, Hexagonal Architecture por módulo, Spring Modulith.
- Segurança: OAuth 2.0, OIDC, Keycloak, Spring Security Resource Server e JWT.
- Testes: JUnit 5, Testcontainers e PostgreSQL real em integração.
- Observabilidade: SLF4J, logs estruturados, Micrometer, OpenTelemetry e Actuator.
- Resiliência: Resilience4j.
- Infra local: Docker e Docker Compose v2.

## 3. Tecnologias fora do baseline

Não introduzir sem ADR e necessidade mensurável: Redis, Kafka, RabbitMQ, Kubernetes, microsserviços, JPA/Hibernate como persistência padrão, MongoDB, banco por tenant ou schema por tenant. Não são proibições eternas; não estão justificadas agora.

## 4. Arquitetura macro

```text
Clients → Inbound Adapters → Application → Domain
                                  ↓          ↑
                             Outbound Ports  │
                                  ↓          │
                             Outbound Adapters
```

Dependências apontam para dentro. São proibidas dependências de domain para Spring, PostgreSQL, jOOQ, Keycloak ou cliente HTTP.

## 5. Estrutura modular

```text
app/
modules/
  identity/
  business/
  device/
  sync/
shared/
  kernel/
  infrastructure/
build-logic/
```

Módulos futuros entram somente por milestone aprovado.

## 6. Hexagonal por módulo

Quando existir implementação funcional, cada módulo segue preferencialmente:

```text
domain/{model,service,exception}
application/{port/{in,out},usecase}
adapter/in/web
adapter/out/{persistence,integration}
api/
```

`api` é a superfície pública. Internals não podem ser acessados diretamente.

## 7. Comunicação entre módulos

Use API/application port quando o chamador precisa de resposta imediata. Use evento quando algo já aconteceu e outros módulos reagem. Consistência imediata implica chamada síncrona; efeito colateral implica evento. Um módulo não acessa repository, tabela ou adapter interno de outro.

## 8. Spring Modulith

Verifica ciclos, dependências permitidas e packages internos; testa módulos e pode documentar a arquitetura. O build falha em violações.

## 9. Multi-tenancy

Shared database, shared schema, tenant key `business_id`. `Business` é o tenant autoritativo; toda informação tenant-owned possui `business_id NOT NULL` quando aplicável.

## 10. Identidade

Separação obrigatória: `User` (pessoa autenticada), `Business` (tenant), `Membership` (autoriza User → Business), `Device` (instalação registrada).

## 11. Android storeId

`storeId != business_id`. É metadado de compatibilidade, pode auxiliar associação local, nunca estabelece autoridade nem autoriza sozinho. Autoridade: `JWT → User → Membership → Business`.

## 12. RLS

RLS é obrigatório desde a primeira tabela tenant-owned e usa conceitualmente `business_id = current_setting('app.business_id')::uuid`. É defesa em profundidade; queries também filtram explicitamente `business_id`. O usuário da aplicação não é superuser nem possui `BYPASSRLS`.

## 13. Convenções PostgreSQL

- IDs UUID v7.
- Dinheiro `NUMERIC`/`BigDecimal`, nunca `float`/`double`.
- Tempo `TIMESTAMPTZ`/`Instant`, UTC.
- Enums `VARCHAR + CHECK`, não PostgreSQL ENUM por padrão.
- PostgreSQL `snake_case`; Java `camelCase`.
- FKs reais; não reproduzir limitações do Room.

## 14. Flyway e jOOQ

Fluxo: migration Flyway → PostgreSQL → geração jOOQ → compilação. Migration quebrada bloqueia o build. Geração deve ser reproduzível quando schemas funcionais existirem.

## 15. Sync

Preservar o Android local-first: `local mutation → Room → DomainEvent → sync`; cloud não é requisito para sucesso local. Sync v1 usa at-least-once, idempotência, cursor sequencial server-side, aplicação transacional e isolamento por tenant. Não implementar distributed exactly-once.

## 16. Idempotência

Eventos usam `UNIQUE(business_id,event_id)`. Mutações HTTP críticas futuras aceitam `Idempotency-Key`. Retries nunca duplicam efeitos.

## 17. Transactional Outbox

Efeitos externos/duráveis usam `@Transactional → state change + outbox insert → COMMIT`; worker realiza o efeito. Nunca chamar API externa com transação PostgreSQL aberta.

## 18. Concorrência

Padrão `READ COMMITTED`; priorizar constraints, idempotência e optimistic version quando apropriado. `SELECT … FOR UPDATE` somente quando necessário. Não aumentar isolamento global sem evidência.

## 19. Ledger financeiro

Quando autorizado: append-only; lançamentos confirmados não são editados/deletados; correção é compensatória. Saldo pode ser materializado, mas o ledger permanece histórico explicável. Auditoria e ledger são distintos.

## 20. API e erros

REST/OpenAPI 3; APIs novas em `/api/v1`. DTO HTTP não é entidade: `DTO → mapper → Command/Query → Use Case → Domain`. Erros têm ao menos `code`, `message`, `correlationId`, sem stack trace ao cliente.

## 21. Auth e secrets

Keycloak é adapter substituível. Application/domain não conhece `Jwt`, `KeycloakPrincipal` ou `OAuth2AuthenticationToken`. O adapter converte identidade externa em principal interno. Secrets nunca são versionados; produção usa runtime secrets, TLS, conexão segura e backups criptografados. `.env` é apenas local e ignorado.

## 22. Dados pessoais

Minimização obrigatória. Customer inicialmente poderá ter nome/apelido e telefone; Business, nome e configurações necessárias. Pix/CPF/endereço/documentos só em milestone com necessidade explícita.

## 23. CI/CD e escalabilidade

Gates: build, tests, architecture e migrations. PR não avança com gate obrigatório falhando. O backend deve ser stateless e horizontalmente escalável, sem infraestrutura especulativa.

## 24. Regra de milestone e escopo

`M0 PASS != M1 AUTHORIZED`. Cada milestone exige autorização explícita. Código futuro não pode ser antecipado por conveniência; preparação arquitetural é permitida, implementação funcional não.

## 25. Autoridade

Ordem: milestone autorizado → system specs → architecture baseline → ADRs → discovery/reference. Conflito específico/recente deve ser reportado antes da implementação.

## 26. Definition of Done arquitetural

Uma entrega só termina quando compila, testes/boundaries/migrations passam, desvios são documentados, evidência é produzida e nenhum requisito futuro vazou para o escopo.
