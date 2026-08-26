# SDD-M1 — DATABASE FOUNDATION

## Status

**AUTHORIZED**

Execução autorizada explicitamente pelo solicitante em 2026-08-26.
**M2 is NOT authorized.**

---

## 1. GOAL

Construir e provar a fundação de persistência e multi-tenancy do TINO Backend: PostgreSQL autoritativo; identities separadas (`tino_migrator`/`tino_app`); Flyway como autoridade única; jOOQ como adapter; UUID v7; convenções executáveis de tipos; tenant context seguro; RLS; isolamento real; pipeline reprodutível migration → jOOQ → build. M1 não implementa domínio funcional.

## 2. ARCHITECTURAL REFERENCES

Antes da implementação ler obrigatoriamente `ARCHITECTURE-BASELINE.md`, `EXECUTION-PROTOCOL.md`, ADRs de PostgreSQL/jOOQ/Flyway/RLS, `SYSTEM-TENANCY.md`, `SYSTEM-SECURITY.md` e `M0-EVIDENCE.md`. Em conflito: **STOP**; não resolver divergência arquitetural silenciosamente.

## 3. IN SCOPE

### PostgreSQL Foundation

Implementar somente a infraestrutura necessária para migrations, application connections, RLS, tenant context, jOOQ generation e integration testing.

### Database identities

`tino_migrator` é exclusivamente de Flyway/evolução de schema/policies/constraints/grants. `tino_app` é runtime e obrigatoriamente `NOSUPERUSER`/`NOBYPASSRLS`, sem privilégios indevidos de alteração de schema.

## 4. OUT OF SCOPE

Não implementar users, businesses, memberships, profiles, devices funcionais, customers, credit, ledger, payments/Pix, reconciliation, messaging, sync push/pull, bootstrap, controllers/APIs de negócio. Também não introduzir Redis, Kafka, RabbitMQ, Kubernetes ou JPA/Hibernate ORM.

## 5. DATABASE CONVENTIONS

- IDs de domínio: UUID v7 gerado pela aplicação; não usar SERIAL/BIGSERIAL como identidade de domínio. Sequences permanecem permitidas para infraestrutura técnica futura (por exemplo cursor).
- Tempo: PostgreSQL `TIMESTAMPTZ`, Java `Instant`, UTC.
- Dinheiro: PostgreSQL `NUMERIC`, Java `BigDecimal`; nunca FLOAT/REAL/DOUBLE PRECISION.
- Enum-like: `VARCHAR + CHECK`; não PostgreSQL ENUM por padrão.
- PostgreSQL `snake_case`; Java `camelCase`; FKs reais; `business_id NOT NULL` quando tenant-owned.

## 6. UUID V7 FOUNDATION

Criar abstraction pequena no shared kernel (`UuidGenerator → UUID v7`), sem acoplar domínio a biblioteca externa. Se houver biblioteca, ela fica no adapter. Testar validade, `version == 7`, unicidade e ordenação temporal razoável. UUID v7 não é cursor de sync.

## 7. FLYWAY

Flyway é a única autoridade: migration → PostgreSQL → jOOQ generation → compile. Migrations publicadas em `main` são imutáveis; correções usam nova migration. Auto-DDL é proibido.

## 8. MIGRATION STRUCTURE

M1 pode criar somente infraestrutura necessária à foundation: roles/grants, tenant context, RLS, generation e verificação. Não criar tabelas de domínio. Fixture de RLS, se necessária, deve existir exclusivamente no integration test e não virar tabela funcional permanente.

## 9. TENANT CONTEXT

Implementar apenas o mecanismo técnico para um `BusinessId` já resolvido:

```text
resolved BusinessId → transaction → SET LOCAL app.business_id → jOOQ → RLS
```

A resolução JWT → User → Membership → Business pertence a milestone posterior.

## 10. CRITICAL TENANT INVARIANT

`INV-TENANT-001`: nenhum valor enviado pelo cliente estabelece autoridade de tenant. A infraestrutura não pode assumir que `store_id`, header arbitrário ou payload seja `business_id` autorizado.

## 11. CONNECTION POOL SAFETY

Nunca usar `SET app.business_id` persistente. Usar `SET LOCAL` ou mecanismo equivalente comprovado. Após COMMIT/ROLLBACK o contexto tenant deve desaparecer, inclusive ao reutilizar conexão do pool.

## 12. TENANT CONTEXT API

Criar abstraction explícita como `TenantContextExecutor.execute(BusinessId, operation)`. Application não conhece detalhes PostgreSQL; domínio não conhece session variables; implementação fica no infrastructure adapter; execução exige transaction boundary; ausência de tenant falha fechada. Não criar framework interno complexo.

## 13. RLS FOUNDATION

Policies tenant-owned devem usar condição equivalente a `business_id = current_setting('app.business_id', true)::uuid`, com ausência de tenant falhando fechada: zero rows ou operação rejeitada, nunca todos os tenants.

## 14. RLS WRITE PROTECTION

RLS deve proteger SELECT/INSERT/UPDATE/DELETE quando aplicável, combinando `USING` e `WITH CHECK` para impedir que contexto A insira ou mova linha para B.

## 15. EXPLICIT QUERY SCOPING

RLS é defense-in-depth. Adapters tenant-owned futuros continuam usando escopo explícito `WHERE business_id = :businessId` quando aplicável.

## 16. DATABASE PRIVILEGES

`tino_app` recebe somente privilégios de runtime e não é superuser, `BYPASSRLS`, `CREATEDB`, `CREATEROLE` ou schema owner sem necessidade. `tino_migrator` não é usado pela aplicação.

## 17. jOOQ FOUNDATION

jOOQ code generation deve ser reproduzível, integrado ao Gradle, derivado do schema Flyway e isolado em infrastructure. Generated sources/Records não são domain model e não saem de adapters. Não criar repositories funcionais.

## 18. jOOQ BUILD REPRODUCIBILITY

Um checkout limpo deve executar PostgreSQL/Testcontainer → Flyway → jOOQ generation → compile → tests, sem banco pessoal pré-existente.

## 19. TRANSACTION FOUNDATION

Padrão `READ COMMITTED`; constraints, idempotência e optimistic concurrency antes de locks; `SELECT ... FOR UPDATE` somente quando necessário. Tenant context, locks e operação terminam em COMMIT/ROLLBACK.

## 20. RLS INTEGRATION TEST FIXTURE

Usar fixture exclusivamente de teste, por exemplo `tenant_probe(id, business_id, value)`, com uma linha A e uma B. A fixture não representa domínio TINO e não pode ser migration permanente.

## 21. MANDATORY TESTS

- `TEST-M1-001` migration from zero: todas as migrations M0/M1 passam em PostgreSQL vazio.
- `TEST-M1-002` Flyway validate passa sem checksum divergente.
- `TEST-M1-003` `tino_app` é NOSUPERUSER/NOBYPASSRLS e sem schema privileges indevidos.
- `TEST-M1-004` RLS read isolation A/B.
- `TEST-M1-005` RLS insert A→B rejeitado.
- `TEST-M1-006` RLS update A→B rejeitado.
- `TEST-M1-007` RLS delete de B sob A não remove B.
- `TEST-M1-008` sem tenant não acessa dados.
- `TEST-M1-009` tenant reset após COMMIT.
- `TEST-M1-010` tenant reset após ROLLBACK.
- `TEST-M1-011` ausência de leakage em pool repetindo A/B/sem contexto.
- `TEST-M1-012` DSLContext executa PostgreSQL real.
- `TEST-M1-013` nenhum tipo jOOQ em domain/application contracts.
- `TEST-M1-014` UUID v7 válido, único e temporalmente ordenado.
- `TEST-M1-015` audit de ORM proibido: sem `hibernate-core`, Spring Data JPA ou `jakarta.persistence`; `hibernate-validator` isolado não conta.

## 22. SECURITY TEST USER

Testes RLS são inválidos se executarem como superuser ou `BYPASSRLS`; devem reproduzir `tino_app`.

## 23. TESTCONTAINERS

Todos os testes PostgreSQL obrigatórios usam PostgreSQL real via Testcontainers. H2, SQLite e mock database não substituem RLS/Flyway/jOOQ.

## 24. PERFORMANCE

Não otimizar prematuramente. Tenant mechanism não pode exigir conexão, schema ou database por Business; pool compartilhado permanece possível.

## 25. OBSERVABILITY

Pode registrar erros técnicos de tenant context, mas nunca JWT, secrets ou database passwords. `BusinessId` em logs estruturados depende da política aprovada.

## 26. GIT WORKFLOW

Criar branch `sdd/m1-database-foundation`; não trabalhar diretamente em `main`. Antes: `git status`, branch e log. Depois: diff/status, security scan, gates, evidence, commit e push da branch. Não mergear automaticamente.

## 27. REQUIRED GATES

Executar `./gradlew clean build`, Spring Modulith verification, migration from empty PostgreSQL, Flyway validate, jOOQ generation, JUnit, Testcontainers, RLS suite, dependency audit e scope leakage audit.

## 28. SCOPE LEAKAGE AUDIT

Procurar explicitamente users/businesses/membership tables, device funcional, customer, credit, payment/Pix, reconciliation, sync endpoint e bootstrap. Nenhum pertence a M1.

## 29. EVIDENCE

Criar `docs/evidence/M1-EVIDENCE.md` com milestone, branch, base/final commit, files changed, migrations, versões PostgreSQL/Flyway/jOOQ/Testcontainers, roles/grants, tenant mechanism, RLS, comandos, resultados `TEST-M1-001..015`, gates, auditorias, desvios, blockers e status.

## 30. ACCEPTANCE CRITERIA

M1 PASS somente se migration zero, validate, privilégios, isolamento read/write/delete, fail-closed, reset COMMIT/ROLLBACK, pool leakage, jOOQ real/boundary, UUID v7, ORM audit, clean build, Modulith, Testcontainers, scope leakage NONE e M1 evidence passarem.

## 31. STOP CONDITIONS

M1 = BLOCKED se for necessário mudar arquitetura multi-tenant, abandonar RLS, usar superuser runtime/testes, introduzir JPA, quebrar boundary jOOQ, mudar PostgreSQL/IDs, antecipar Business/Identity ou introduzir infraestrutura não autorizada. Reportar `ARCHITECTURAL DECISION REQUIRED` e parar.

## 32. FINAL OUTPUT

```text
MILESTONE: M1 — DATABASE FOUNDATION
STATUS: PASS | BLOCKED | FAIL

Architecture: PASS | FAIL
Build: PASS | FAIL
Tests: PASS | FAIL
Flyway: PASS | FAIL
jOOQ: PASS | FAIL
RLS: PASS | FAIL
Tenant Leakage: PASS | FAIL
Database Privileges: PASS | FAIL
Git: PASS | FAIL
Scope Leakage: NONE | FOUND

Evidence:
docs/evidence/M1-EVIDENCE.md

Branch:
sdd/m1-database-foundation

Commit:
<sha>

NEXT MILESTONE AUTHORIZED: NO

STOP.
```
