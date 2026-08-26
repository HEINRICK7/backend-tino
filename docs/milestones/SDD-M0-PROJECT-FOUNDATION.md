# SDD-M0 — Project Foundation

## Status

**AUTHORIZED**

## Next Milestone

**M1 is NOT authorized.**

## 1. Goal

Criar a fundação técnica executável. M0 prova Gradle, startup Spring Boot, organização modular, PostgreSQL/Keycloak local, integração Flyway/jOOQ, testes PostgreSQL reais, observabilidade e architecture verification. Não implementa domínio.

## 2. In Scope

- Root Gradle Kotlin DSL, wrapper, version catalog e `build-logic`.
- Containers arquiteturais: `app`, `modules:{identity,business,device,sync}`, `shared:{kernel,infrastructure}`; vazios de domínio.
- Spring Boot, configuração/profiles, DI e Spring Modulith.
- Datasource, Compose v2 PostgreSQL com healthcheck, usuário app e migration; Keycloak dev.
- Flyway com migration técnica mínima sem tabela de domínio.
- jOOQ configurado, integração provada, geração reprodutível preparada; sem repository.
- Resource Server/OIDC configurável; sem User/Business/Membership.
- JUnit 5, Testcontainers PostgreSQL, context/migration/jOOQ/Actuator/Modulith tests.
- Actuator, Micrometer, OpenTelemetry, logs estruturáveis/correlation foundation, Resilience4j, OpenAPI.
- Baseline, ADRs, system specs, milestone e evidence.
- Git em `main`, `origin` verificado, commit/push inicial da fundação e index sem secrets/artefatos.

## 3. Out of Scope

Proibidos: `users`, `businesses`, `business_memberships`, `business_profiles`, `devices`, `sync_events`, `sync_change_log`, `sync_event_rejections`, customers, credit, ledger, payments/Pix, reconciliation, messaging/WhatsApp, bootstrap, push/pull, RLS de negócio, membership authorization e event processing funcional. Qualquer início deve ser removido.

Também proibidos: JPA/Hibernate, Redis, Kafka, RabbitMQ, Kubernetes, microsserviços, serviços Compose além de PostgreSQL/Keycloak e alterações Android.

## 4. Contracts

### DOMAIN CONTRACT
Nenhum. Módulos funcionais são containers vazios.

### APPLICATION CONTRACT
O processo inicia e expõe somente superfícies técnicas; não existe use case de negócio.

### DATABASE CONTRACT
PostgreSQL real, Flyway e jOOQ funcionam; nenhuma tabela/policy de domínio existe.

### API CONTRACT
Somente health/OpenAPI foundation. Nenhum endpoint funcional.

### SECURITY INVARIANTS
Resource Server configurável, secrets externos, health público, nenhuma regra de negócio ou permit-all produtivo.

### TENANCY INVARIANTS
Sem dados tenant em M0; autoridade/RLS não podem ser simulados antecipadamente.

## 5. Required Structure and Dependencies

Estrutura conforme baseline, mais `docs`, `docker`, `compose.yaml`. Permitido: `app → modules/shared`, adapters futuros → application → domain. Proibido: domain → frameworks e módulo A → internals B. Modulith deve verificar o possível em M0.

## 6. Compose and Runtime Gates

Usar `docker compose`, serviços exatos `postgres` e `keycloak`. PostgreSQL tem healthcheck; Keycloak inicia para dev sem secret produtivo. Spring Boot executa por `./gradlew :app:bootRun`, não dentro de Compose.

Actuator deve responder `/actuator/health` sem autenticação. Endpoints administrativos não são expostos indiscriminadamente.

## 7. Flyway and jOOQ Gates

Provar automaticamente `empty PostgreSQL → Flyway → migration success`, sem schema de negócio. Provar `DSLContext`/conexão PostgreSQL real e build/config de geração reproduzível, sem query/repository de domínio.

## 8. Mandatory Tests

- `TEST-M0-001`: application context loads.
- `TEST-M0-002`: Flyway migrates empty PostgreSQL.
- `TEST-M0-003`: jOOQ accesses Testcontainers PostgreSQL.
- `TEST-M0-004`: Spring Modulith verification passes.
- `TEST-M0-005`: Actuator health responds successfully.
- `TEST-M0-006`: no forbidden Spring dependency exists in domain skeleton packages, if present.

## 9. Local Environment Validation

Executar `docker compose version`, `docker compose config`, `docker compose up -d`, `docker compose ps`; provar PostgreSQL healthy e Keycloak reachable; então `docker compose down` ou documentar estado intencional.

## 10. Build, Architecture and Dependency Gates

`./gradlew clean build` deve passar. Spring Modulith deve passar; ciclo/violação bloqueia M0. Confirmar ausência de Hibernate/JPA, Redis, Kafka e RabbitMQ; qualquer transitiva inevitável deve ser explicada e não usada/autoconfigurada.

## 11. Acceptance Criteria

- `M0-AC-001`: Java 21 e Gradle Kotlin DSL multi-module funcionais.
- `M0-AC-002`: Spring Boot inicia e Actuator health é `UP`.
- `M0-AC-003`: Spring Modulith configurado/verificação PASS.
- `M0-AC-004`: Compose contém somente PostgreSQL healthy + Keycloak reachable.
- `M0-AC-005`: Flyway em banco vazio PASS.
- `M0-AC-006`: jOOQ + PostgreSQL real PASS.
- `M0-AC-007`: JUnit 5/Testcontainers PASS.
- `M0-AC-008`: Micrometer/OpenTelemetry/Resilience4j/OpenAPI foundations presentes.
- `M0-AC-009`: baseline, ADRs, specs, milestone e evidence presentes.
- `M0-AC-010`: nenhuma implementação M1+.
- `M0-AC-011`: `./gradlew clean build` PASS.
- `M0-AC-012`: Git inicializado em `main`, `origin` verificado, commit da fundação enviado, SHA evidenciado e nenhum secret/artefato versionado.

## 12. Evidence

`docs/evidence/M0-EVIDENCE.md` registra revisão (se Git), versões Java/Gradle/Docker/Compose, módulos/mapa, comandos e resultados de build/tests/Modulith/Flyway/jOOQ/Compose/health, desvios e itens M1+ removidos.

## Version Control Gate

- Repositório Git inicializado; branch local padrão `main`.
- `origin` igual a `git@github.com:HEINRICK7/backend-tino.git`.
- Commit inicial da foundation existe e o push tem sucesso.
- SHA inicial é registrado na evidence.
- Revisão de segurança prova que secrets, IDE, builds, logs e temporários não entraram no index.

## 13. Stop Conditions

Marcar `BLOCKED` se build não reproduz, PostgreSQL real não pode ser testado, Flyway falha vazio, Modulith viola sem correção segura, incompatibilidade impede tecnologia obrigatória ou M0 exigiria violar baseline. Nunca contornar removendo teste ou gate.

Mesmo em PASS, encerrar com `M1 AUTHORIZED: NO`; não criar migration de users, identidade, bootstrap ou sync funcional.

## 14. Final Output Contract

```text
M0 STATUS: PASS | BLOCKED
Architecture: PASS | FAIL
Build: PASS | FAIL
Tests: PASS | FAIL
Flyway: PASS | FAIL
jOOQ: PASS | FAIL
PostgreSQL Compose: PASS | FAIL
Keycloak Compose: PASS | FAIL
Scope Leakage: NONE | FOUND

M1 AUTHORIZED: NO
```
