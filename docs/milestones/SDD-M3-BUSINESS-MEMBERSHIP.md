# SDD-M3 — BUSINESS & MEMBERSHIP

## Status

AUTHORIZED

Este milestone somente poderá ser executado após autorização explícita do usuário.

M4 is NOT authorized.

---

# 1. GOAL

Construir e provar o domínio inicial de Business e Membership do TINO Backend.

M3 estabelece:

Authenticated User
        ↓
Business Membership
        ↓
Business

Ao final do M3 o backend deve conseguir responder, de forma segura:

- quais Businesses um User pode acessar;
- qual é o papel desse User em cada Business;
- como criar um Business inicial;
- como vincular o criador como OWNER;
- como impedir acesso cross-business;
- como preparar o tenant autoritativo `business_id`;
- como preservar a separação entre User, Business e Device.

M3 NÃO implementa Device.

M3 NÃO implementa storeId linking.

M3 NÃO implementa Bootstrap completo.

M3 NÃO implementa Customer.

M3 NÃO implementa Credit.

M3 NÃO implementa Sync funcional.

---

# 2. BASELINE REQUIRED

Antes de executar M3:

M0 — PROJECT FOUNDATION
STATUS: PASS

M1 — DATABASE FOUNDATION
STATUS: PASS

M2 — IDENTITY & SECURITY FOUNDATION
STATUS: PASS

M2 deve estar integrado em:

develop

M3 será criado a partir de `develop`.

Não depende de promoção para staging ou main.

---

# 3. BRANCH POLICY

Branch obrigatória:

feature/m3-business-membership

Base:

develop

Fluxo futuro:

feature/m3-business-membership
        ↓
develop
        ↓
staging
        ↓
main

M3 NÃO deve ser desenvolvido diretamente em:

- develop;
- staging;
- main.

---

# 4. MODEL POLICY

Modelo autorizado:

Luna only.

Não usar:

- Terra;
- Sol;
- delegação para outros modelos.

Se Luna encontrar decisão arquitetural não coberta:

STOP.

Retornar:

M3 STATUS: BLOCKED
ARCHITECTURAL DECISION REQUIRED
HUMAN AUTHORIZATION REQUIRED

---

# 5. SOURCE OF TRUTH

Antes de qualquer edição, ler:

- ARCHITECTURE-BASELINE.md
- EXECUTION-PROTOCOL.md
- SECURITY-GIT-SAFETY-POLICY.md
- GIT-BRANCHING-POLICY.md
- ADRs aplicáveis
- SYSTEM-IDENTITY.md
- SYSTEM-TENANCY.md
- SYSTEM-SECURITY.md
- M1-EVIDENCE.md
- M2-EVIDENCE.md
- SDD-M3-BUSINESS-MEMBERSHIP.md

Se houver conflito:

STOP.

Não escolher silenciosamente uma interpretação.

---

# 6. DOMAIN MODEL

Criar:

Business

BusinessMembership

BusinessRole

BusinessStatus

MembershipStatus

Value Objects:

BusinessId
UserId
BusinessName

Reutilizar foundations existentes quando apropriado.

Não criar abstrações genéricas desnecessárias.

---

# 7. BUSINESS

Business representa o tenant autoritativo do TINO.

Campos mínimos:

- id
- tradeName
- vertical
- status
- createdAt
- updatedAt

Não adicionar:

- CPF;
- CNPJ;
- endereço;
- email;
- ownerName;
- logo;
- Pix;
- telefone;
- módulos;
- capabilities;
- device.

Esses dados pertencem a milestones posteriores quando necessário.

---

# 8. BUSINESS ID

Business.id:

Java:
UUID

PostgreSQL:
UUID

Geração:

UUID v7

`business_id` será a chave oficial de tenant do backend.

---

# 9. BUSINESS NAME

Campo:

tradeName

Representa o nome operacional do estabelecimento.

Exemplos:

Mercadinho São José
Padaria Central
Doces da Maria

Requisitos:

- obrigatório;
- não vazio;
- trim;
- tamanho limitado conforme migration;
- não precisa ser globalmente único.

Dois estabelecimentos diferentes podem possuir o mesmo nome.

---

# 10. BUSINESS VERTICAL

Inicialmente aceitar SOMENTE:

RETAIL
BAKERY
RESTAURANT
STORE
OTHER

Esses valores preservam compatibilidade com o Android atual.

Não adicionar agora:

- PRODUCER
- FAIR_VENDOR
- BUTCHER
- FISH_SELLER
- CONFECTIONERY
- MEI

mesmo que possam existir futuramente.

Nova vertical exige especificação futura.

Persistência:

VARCHAR + CHECK.

Não usar PostgreSQL ENUM.

---

# 11. BUSINESS STATUS

Inicialmente:

ACTIVE
DISABLED

Novo Business:

ACTIVE

M3 não implementa fluxo administrativo completo de desativação.

DISABLED existe para garantir contrato futuro e testar acesso negado.

---

# 12. USER != BUSINESS

Invariante:

INV-BUSINESS-001

User e Business são conceitos diferentes.

Nunca:

User == Business

Nunca assumir:

User possui somente um Business.

Modelo deve suportar:

User A
 ├── Business X
 └── Business Y

mesmo que o fluxo inicial crie apenas um.

---

# 13. MEMBERSHIP

BusinessMembership representa:

User
   ↓
Role
   ↓
Business

Campos mínimos:

- id
- businessId
- userId
- role
- status
- createdAt
- updatedAt

---

# 14. MEMBERSHIP ID

UUID v7.

Não usar chave composta como primary key.

Ainda assim deve existir constraint:

UNIQUE (business_id, user_id)

para impedir memberships duplicadas.

---

# 15. BUSINESS ROLE

M3 possui apenas:

OWNER
STAFF

Não adicionar:

ADMIN
MANAGER
CASHIER
SELLER
EMPLOYEE
SUPER_ADMIN

sem requisito posterior.

OWNER:

possui vínculo de propriedade operacional inicial.

STAFF:

representa membro não proprietário.

M3 não implementa RBAC completo.

---

# 16. MEMBERSHIP STATUS

Inicialmente:

ACTIVE
DISABLED

Apenas Membership ACTIVE concede acesso ao Business.

Membership DISABLED:

não autoriza tenant.

---

# 17. CORE TENANCY INVARIANT

INV-TENANT-002

business_id autorizado deve ser derivado de:

Authenticated User
        ↓
User interno
        ↓
ACTIVE Membership
        ↓
ACTIVE Business

Nunca derivar tenant diretamente de:

- request.businessId;
- payload;
- store_id;
- device_id;
- header arbitrário;
- query parameter.

O cliente pode SOLICITAR operar um businessId.

O servidor deve AUTORIZAR esse businessId via Membership.

---

# 18. REQUESTED BUSINESS VS AUTHORIZED BUSINESS

Exemplo:

request solicita:

businessId = X

Servidor:

Authenticated User
      ↓
MembershipRepository
      ↓
membership(user, X)?
      ↓
ACTIVE?
      ↓
Business X ACTIVE?
      ↓
AUTHORIZED

Se qualquer etapa falhar:

ACCESS DENIED.

---

# 19. STORE ID

StoreId Android continua fora da autoridade.

Nunca:

storeId → Business autorizado

M3 não cria relacionamento de storeId.

Isso pertence ao milestone de Device/installation linking.

---

# 20. DEVICE

Device permanece OUT OF SCOPE.

M3 não implementa:

- device registration;
- installation;
- device trust;
- localStoreId;
- linking;
- device authorization.

---

# 21. BUSINESS CREATION

Criar use case:

CreateBusiness

Input mínimo:

- authenticated User
- tradeName
- vertical

Comportamento transacional:

1. validar User ACTIVE;
2. criar Business ACTIVE;
3. criar Membership:
   - user = creator;
   - business = new Business;
   - role = OWNER;
   - status = ACTIVE;
4. commit.

Business sem OWNER devido a falha parcial:

PROIBIDO.

---

# 22. BUSINESS CREATION ATOMICITY

INV-BUSINESS-002

Criação:

Business
+
OWNER Membership

deve ser atômica.

Uma única transação PostgreSQL.

Se Membership falhar:

ROLLBACK Business.

Se Business falhar:

Membership não existe.

---

# 23. FIRST OWNER

O User que cria Business recebe automaticamente:

OWNER

Não existe endpoint separado para:

"criar owner".

---

# 24. MULTIPLE BUSINESSES

M3 deve permitir que o mesmo User crie/participe de múltiplos Businesses.

Não adicionar constraint:

UNIQUE(user_id)

em membership.

A constraint correta é:

UNIQUE(business_id, user_id)

---

# 25. LIST USER BUSINESSES

Criar use case:

ListUserBusinesses

Input:

Authenticated/internal User

Output:

somente Businesses acessíveis através de Membership ACTIVE.

Business DISABLED:

não deve aparecer como operacional ativo.

Se necessário, resposta pode representar status explicitamente.

Não expor businesses de outros Users.

---

# 26. RESOLVE BUSINESS ACCESS

Criar use case/port equivalente a:

ResolveBusinessAccess

Input:

UserId
Requested BusinessId

Output:

AuthorizedBusinessContext

mínimo:

- businessId
- userId
- role

Somente se:

Membership ACTIVE
AND
Business ACTIVE

---

# 27. AUTHORIZED BUSINESS CONTEXT

Criar representação framework-independent.

Conceitualmente:

AuthorizedBusinessContext

- userId
- businessId
- role

Não carregar:

JWT
Spring Security
jOOQ
HTTP request
storeId
deviceId

---

# 28. TENANT CONTEXT CONNECTION

M1 já criou TenantContextExecutor.

M3 passa a conectar:

AuthorizedBusinessContext
        ↓
BusinessId
        ↓
TenantContextExecutor
        ↓
SET LOCAL app.business_id

Importante:

Membership authorization acontece ANTES de estabelecer tenant context.

Nunca:

request businessId
→ SET LOCAL
→ depois verificar membership

A ordem correta:

request businessId
→ authorize membership
→ establish tenant
→ execute tenant-owned operation

---

# 29. RLS

Business é a raiz do tenant.

Tabela:

businesses

não precisa ser protegida como uma tabela interna dependente do próprio tenant da mesma maneira que futuros customers/credit.

A estratégia exata deve seguir SYSTEM-TENANCY.md.

Membership necessita acesso controlado por User/Business e não deve depender de uma policy circular impossível de resolver.

Não inventar policy complexa se System Spec definir mecanismo diferente.

Se RLS de businesses/memberships não estiver claramente especificada:

STOP e reporte conflito antes de improvisar.

---

# 30. DATABASE TABLE — BUSINESSES

Migration conceitual:

businesses

- id UUID PRIMARY KEY
- trade_name VARCHAR(...) NOT NULL
- vertical VARCHAR(...) NOT NULL
- status VARCHAR(...) NOT NULL
- created_at TIMESTAMPTZ NOT NULL
- updated_at TIMESTAMPTZ NOT NULL

CHECK vertical:
RETAIL
BAKERY
RESTAURANT
STORE
OTHER

CHECK status:
ACTIVE
DISABLED

---

# 31. DATABASE TABLE — BUSINESS_MEMBERSHIPS

business_memberships

- id UUID PRIMARY KEY
- business_id UUID NOT NULL
- user_id UUID NOT NULL
- role VARCHAR(...) NOT NULL
- status VARCHAR(...) NOT NULL
- created_at TIMESTAMPTZ NOT NULL
- updated_at TIMESTAMPTZ NOT NULL

FK:

business_id → businesses(id)

user_id → users(id)

UNIQUE:

(business_id, user_id)

CHECK role:

OWNER
STAFF

CHECK status:

ACTIVE
DISABLED

---

# 32. FOREIGN KEYS

Usar FKs reais.

Não reproduzir limitações do Android Room.

Deletion:

não configurar cascades destrutivos que possam apagar histórico automaticamente.

M3 não implementa exclusão física de Business/User.

---

# 33. SOFT DELETE

M3 não adiciona:

deleted_at

Status é suficiente:

ACTIVE
DISABLED

Não implementar generic soft-delete framework.

---

# 34. BUSINESS REPOSITORY PORT

Criar port específico.

Somente métodos necessários ao M3.

Exemplos conceituais:

- create
- findById
- findAccessibleByUser

Não criar CRUD genérico.

---

# 35. MEMBERSHIP REPOSITORY PORT

Criar port específico.

Possíveis operações:

- create
- findByUserAndBusiness
- findActiveByUser
- existsActiveMembership

Somente as necessárias aos casos de uso implementados.

---

# 36. jOOQ ADAPTERS

Criar:

JooqBusinessRepository

JooqBusinessMembershipRepository

jOOQ permanece apenas em infrastructure adapters.

Não expor generated records.

---

# 37. MODULE STRUCTURE

modules/business

conterá domínio Business/Membership.

Não espalhar Business em identity.

Identity continua responsável apenas por User/authentication.

---

# 38. MODULE COMMUNICATION

Business precisa saber qual User está autenticado.

Não acessar repository interno do identity.

Usar API/port público apropriado.

Não:

business → JooqUserRepository

Permitido:

business → Identity public application contract

ou UserId já resolvido pela borda/application composition.

Escolher a solução compatível com boundaries existentes.

---

# 39. SPRING MODULITH

M3 deve manter:

ApplicationModules.verify() = PASS

Sem ciclos:

identity ↔ business

Se aparecer ciclo:

STOP.

Não resolver movendo tudo para shared.

---

# 40. BUSINESS PROFILE

Não criar business_profiles ainda.

M3 Business contém somente identidade estrutural do estabelecimento.

Configurações operacionais futuras pertencem a milestone posterior.

---

# 41. OWNER NAME

Não persistir ownerName no Business.

OWNER é determinado por Membership.

Isso evita duplicação:

Business.ownerName
vs
Membership OWNER

---

# 42. PHONE

M3 não adiciona telefone ao Business.

Telefone operacional será especificado quando necessário.

---

# 43. PIX

Nenhum campo:

pix_key
pix_key_type
pix_recipient

em M3.

Pix pertence a milestone futura.

---

# 44. MODULES / CAPABILITIES

Não persistir modules/capabilities em Business ainda.

Isso entra em BusinessProfile/bootstrap milestone quando necessário.

---

# 45. API SURFACE

M3 pode implementar APIs mínimas necessárias.

Permitidas:

POST /api/v1/businesses

GET /api/v1/businesses

ou contratos equivalentes aprovados.

Não implementar:

PUT genérico
DELETE
admin APIs
role management complexo
staff invitation

---

# 46. POST BUSINESS

Conceitualmente:

POST /api/v1/businesses

Authorization:
Bearer JWT

Payload:

{
  "tradeName": "...",
  "vertical": "RETAIL"
}

Servidor:

JWT
→ User
→ CreateBusiness

Nunca aceitar:

ownerUserId

do client.

OWNER é sempre o User autenticado.

---

# 47. LIST BUSINESSES

GET /api/v1/businesses

Retorna somente Businesses do User autenticado.

Nunca:

"listar todos businesses"

---

# 48. BUSINESS DTO

Response mínima:

- id
- tradeName
- vertical
- status
- role

Não expor:

internal jOOQ
Membership IDs se não necessário
User externalSubject
JWT claims

---

# 49. ERROR MODEL

Casos mínimos:

BusinessNotFound
BusinessAccessDenied
BusinessDisabled
MembershipDisabled
InvalidBusinessName
InvalidBusinessVertical

Não vazar SQLException/jOOQ exceptions.

---

# 50. SECURITY

Toda API M3 exige autenticação.

Nenhum Business endpoint anônimo.

JWT validation continua conforme M2:

- assinatura;
- expiração;
- issuer;
- audience / authorized party;
- sub.

Não enfraquecer M2.

---

# 51. AUTHORIZATION

TESTAR especificamente:

User A não acessa Business B do User B.

Mesmo conhecendo UUID do Business B:

403/adequate access denied.

Nunca retornar dados cross-business.

---

# 52. INFORMATION DISCLOSURE

Para operações sensíveis, considerar não revelar excessivamente se:

Business não existe
vs
Business existe mas não pertence ao User.

Contrato HTTP deve seguir error model/security spec.

Não criar enumeração fácil de tenants.

---

# 53. CONCURRENCY — BUSINESS CREATION

Duas criações simultâneas pelo mesmo User são permitidas se representam Businesses diferentes.

TradeName não é unique global.

Não criar locks desnecessários.

---

# 54. CONCURRENCY — MEMBERSHIP

Duas tentativas concorrentes de criar mesma membership:

UNIQUE(business_id, user_id)

garante integridade.

Application deve tratar conflito esperado adequadamente.

---

# 55. TRANSACTIONS

CreateBusiness:

uma transação.

Business insert
+
Membership OWNER insert

READ COMMITTED.

Não chamar serviços externos.

---

# 56. AUDIT

Não criar audit system novo.

Eventos técnicos podem ser emitidos/logados:

business.created
business.access_denied

Sem criar tabela audit prematuramente.

---

# 57. DOMAIN EVENTS

Se eventos internos fizerem sentido:

BusinessCreated

pode ser Domain/Application Event.

Não introduzir broker.

Spring Application Events podem ser usados conforme baseline.

Nenhum efeito externo necessário no M3.

---

# 58. OUTBOX

M3 não precisa criar outbox event se não houver efeito durável externo.

Não usar outbox apenas porque existe foundation futura.

---

# 59. PRIVACY

Business não contém dados pessoais adicionais.

Membership referencia apenas:

UserId

Não copiar:

externalSubject
email
name
phone

para membership.

---

# 60. SECURITY & GIT POLICY

Aplicar integralmente:

SECURITY-GIT-SAFETY-POLICY.md

Antes de TODO commit:

- tests;
- diff review;
- secret scan;
- staged review.

Antes de push:

- secret scan;
- history review.

Nenhuma credencial hardcoded.

---

# 61. REQUIRED TESTS

## TEST-M3-001 — CREATE BUSINESS

User ACTIVE cria Business válido.

Resultado:

Business ACTIVE.

---

## TEST-M3-002 — CREATOR BECOMES OWNER

Após CreateBusiness:

Membership:

role = OWNER
status = ACTIVE.

---

## TEST-M3-003 — ATOMIC BUSINESS + OWNER

Forçar falha na Membership.

Resultado:

Business não permanece salvo.

---

## TEST-M3-004 — LIST OWN BUSINESSES

User A possui A1/A2.

Resultado:

lista A1/A2.

---

## TEST-M3-005 — NO FOREIGN BUSINESS LISTING

User B Business não aparece para User A.

---

## TEST-M3-006 — ACTIVE MEMBERSHIP AUTHORIZES ACCESS

User A + Membership ACTIVE + Business ACTIVE:

AuthorizedBusinessContext produzido.

---

## TEST-M3-007 — MISSING MEMBERSHIP DENIED

User A tenta acessar Business B sem Membership.

Resultado:

DENIED.

---

## TEST-M3-008 — DISABLED MEMBERSHIP DENIED

Membership DISABLED:

DENIED.

---

## TEST-M3-009 — DISABLED BUSINESS DENIED

Business DISABLED:

DENIED.

---

## TEST-M3-010 — USER CAN HAVE MULTIPLE BUSINESSES

Mesmo User possui múltiplas memberships Businesses diferentes.

PASS.

---

## TEST-M3-011 — MEMBERSHIP UNIQUE

Duplicar:

business_id + user_id

PostgreSQL rejeita.

---

## TEST-M3-012 — BUSINESS UUID V7

Business.id version == 7.

---

## TEST-M3-013 — MEMBERSHIP UUID V7

Membership.id version == 7.

---

## TEST-M3-014 — BUSINESS STATUS CHECK

Valor inválido rejeitado pelo PostgreSQL.

---

## TEST-M3-015 — BUSINESS VERTICAL CHECK

Vertical fora da lista aprovada rejeitada.

---

## TEST-M3-016 — MEMBERSHIP ROLE CHECK

Role inválido rejeitado.

---

## TEST-M3-017 — MEMBERSHIP STATUS CHECK

Status inválido rejeitado.

---

## TEST-M3-018 — BUSINESS FK

Membership com business inexistente rejeitada.

---

## TEST-M3-019 — USER FK

Membership com User inexistente rejeitada.

---

## TEST-M3-020 — MIGRATION FROM ZERO

PostgreSQL vazio:

Flyway M0→M3 PASS.

---

## TEST-M3-021 — FLYWAY VALIDATE

PASS.

---

## TEST-M3-022 — JOOQ BUSINESS POSTGRESQL

Repositories operam contra PostgreSQL real.

---

## TEST-M3-023 — JOOQ BOUNDARY

Nenhum jOOQ em domain/application contracts.

---

## TEST-M3-024 — IDENTITY BOUNDARY

Business não acessa internals do Identity.

---

## TEST-M3-025 — MODULITH

ApplicationModules.verify() PASS.

---

## TEST-M3-026 — SECURITY CROSS BUSINESS

User A não acessa Business de B mesmo conhecendo ID.

---

## TEST-M3-027 — CLIENT BUSINESS ID NOT AUTHORITY

businessId enviado pelo client não autoriza acesso sozinho.

---

## TEST-M3-028 — STORE ID NOT AUTHORITY

StoreId não participa de Business authorization.

---

## TEST-M3-029 — NO DEVICE IMPLEMENTATION

Nenhum Device funcional introduzido.

---

## TEST-M3-030 — PRIVACY

Business/Membership não copiam PII desnecessária do User.

---

## TEST-M3-031 — API CREATE AUTHENTICATED

POST businesses sem autenticação:

401.

---

## TEST-M3-032 — API CREATE OWNER IS AUTH USER

Client não pode escolher outro User como OWNER.

---

## TEST-M3-033 — API LIST SCOPED

GET businesses retorna apenas memberships autorizadas.

---

## TEST-M3-034 — TIMESTAMP ROUNDTRIP

TIMESTAMPTZ ↔ Instant PASS.

---

## TEST-M3-035 — CLEAN BUILD

./gradlew clean build PASS.

---

## TEST-M3-036 — SECRET SCAN

PASS.

---

## TEST-M3-037 — NO SCOPE LEAKAGE

Ausência de:

Device
Bootstrap
Customer
Credit
Payment
Pix
Reconciliation
Sync funcional
WhatsApp

PASS.

---

# 62. POSTGRESQL

Usar PostgreSQL real/Testcontainers para:

- migrations;
- constraints;
- FK;
- unique;
- jOOQ;
- transaction tests.

Não usar H2 para substituir gates obrigatórios.

---

# 63. RLS / TENANCY TEST

M3 deve provar que o AuthorizedBusinessContext fornece BusinessId somente após Membership válida.

TenantContextExecutor poderá ser integrado em teste de aplicação.

Não permitir SET LOCAL antes da autorização.

---

# 64. BUSINESS ACCESS ORDER TEST

Obrigatório provar:

requested BusinessId
        ↓
membership check
        ↓
business status check
        ↓
AuthorizedBusinessContext
        ↓
TenantContext

Não:

requested BusinessId
→ TenantContext primeiro.

---

# 65. DATABASE ROLE

Runtime:

tino_app

Migration:

tino_migrator

Preservar M1.

Não elevar privilégios.

---

# 66. MIGRATIONS

Adicionar nova(s) migration(s).

Não alterar migrations já publicadas.

Schema novo permitido somente:

businesses
business_memberships

Nenhuma tabela M4+.

---

# 67. jOOQ GENERATION

Regenerar após migration.

Generated records ficam em infrastructure.

---

# 68. API TESTING

Testes HTTP devem provar:

401 sem auth.

201/adequate success para create.

200 para list.

403/appropriate denial cross-business.

Não desabilitar security globalmente.

---

# 69. SECRET SCAN

Executar:

- após migrations/config;
- antes de commit;
- antes de push.

GitGuardian/pre-commit deve permanecer verde.

---

# 70. DEPENDENCY AUDIT

Nenhum:

JPA
Hibernate ORM
Redis
Kafka
RabbitMQ
MongoDB

---

# 71. SOURCE AUDIT

Nenhum:

jOOQ em domain
Spring Security em domain
Keycloak em domain
Identity repository internals em business

---

# 72. SCHEMA AUDIT

Ao final:

users
businesses
business_memberships

como domínio funcional cloud conhecido.

Não:

devices
customers
credit
payments
sync functional tables adicionais

---

# 73. SCOPE LEAKAGE

Buscar explicitamente:

Device
storeId mapping
Bootstrap
BusinessProfile
Capabilities
Customer
Credit
Ledger
Payment
Pix
Reconciliation
Notification
WhatsApp
Sync push/pull

Nenhuma implementação funcional desses conceitos.

---

# 74. EVIDENCE

Criar:

docs/evidence/M3-EVIDENCE.md

Registrar:

- base develop SHA;
- branch;
- migration;
- schema;
- User/Business boundary;
- Membership model;
- roles/status;
- CreateBusiness transaction;
- authorization strategy;
- tenant resolution ordering;
- TEST-M3-001..037;
- PostgreSQL version;
- Flyway;
- jOOQ;
- Testcontainers;
- API tests;
- security;
- secret scan;
- boundaries;
- Modulith;
- clean build;
- scope audit;
- deviations;
- blockers.

Não registrar secrets.

---

# 75. REQUIRED GATES

Obrigatórios:

./gradlew clean build

architecture

Spring Modulith

Flyway from zero

Flyway validate

jOOQ generation

JUnit

PostgreSQL Testcontainers

Business/Membership integration

HTTP security

cross-business authorization

secret scan

dependency audit

source boundary audit

schema audit

scope leakage audit

---

# 76. INDEPENDENT LUNA REVIEW

Como Luna é o único modelo autorizado, a mesma sessão deve separar logicamente:

IMPLEMENTATION PHASE
        ↓
STOP EDITING
        ↓
REVIEW PHASE

Na review phase:

- revisar diff completo;
- rerodar gates;
- não confiar somente nos testes executados durante implementação;
- revisar security;
- revisar schema;
- revisar evidence.

O PASS final exige essa segunda verificação.

---

# 77. ACCEPTANCE CRITERIA

M3 PASS somente se:

[ ] Business criado

[ ] creator OWNER

[ ] criação atômica

[ ] multiple Businesses/User suportado

[ ] Membership UNIQUE

[ ] Business ACTIVE/DISABLED

[ ] Membership ACTIVE/DISABLED

[ ] OWNER/STAFF apenas

[ ] verticals atuais preservadas

[ ] UUID v7

[ ] FKs reais

[ ] User não duplicado

[ ] no PII duplication

[ ] Membership autoriza Business

[ ] missing membership denied

[ ] disabled membership denied

[ ] disabled Business denied

[ ] cross-business denied

[ ] client businessId not authority

[ ] storeId not authority

[ ] authorization before TenantContext

[ ] Flyway PASS

[ ] jOOQ PASS

[ ] PostgreSQL PASS

[ ] HTTP security PASS

[ ] Modulith PASS

[ ] secret scan PASS

[ ] clean build PASS

[ ] TEST-M3-001..037 PASS

[ ] Scope Leakage NONE

[ ] M3-EVIDENCE present

---

# 78. STOP CONDITIONS

M3 = BLOCKED se:

- Business precisar armazenar Pix;
- BusinessProfile for necessário;
- Device parecer necessário;
- storeId linking parecer necessário;
- autorização exigir novo modelo arquitetural;
- RLS precisar contrariar System Tenancy;
- Identity precisar ser reescrito;
- JPA parecer necessário;
- Redis/distributed lock parecer necessário;
- nova role além de OWNER/STAFF for necessária;
- nova vertical for necessária;
- migration M2 precisar ser reescrita.

Retornar:

M3 STATUS: BLOCKED
ARCHITECTURAL DECISION REQUIRED
HUMAN AUTHORIZATION REQUIRED

---

# 79. GIT WORKFLOW

Base:

develop

Criar:

feature/m3-business-membership

Após PASS:

push branch.

Criar PR:

feature/m3-business-membership
→ develop

Luna pode criar PR via gh.

Luna NÃO pode mergear sem autorização humana.

Após checks verdes:

reportar:

FEATURE READY FOR DEVELOP

e STOP.

Não promover develop → staging automaticamente.

---

# 80. STAGING

M3 não depende da promoção atual do M2 para staging.

Develop continua sendo integração de próxima feature.

Staging permanece congelada até decisão humana de homologação.

---

# 81. MAIN

M3 não toca main.

---

# 82. M4

M4 permanece:

NOT AUTHORIZED

M4 previsto:

Device Registration / Installation Linking

mas nenhuma implementação deve começar.

---

# 83. EXPECTED FINAL STATE

Ao concluir M3:

JWT
 ↓
User
 ↓
Membership
 ↓
Business
 ↓
AuthorizedBusinessContext
 ↓
TenantContext

O backend passa a saber:

"quem é o usuário?"

e:

"qual Business ele está autorizado a operar?"

Ainda NÃO sabe:

"qual instalação Android está vinculada?"

Isso começa no M4.

---

# 84. FINAL OUTPUT

MILESTONE:
M3 — BUSINESS & MEMBERSHIP

STATUS:
PASS | BLOCKED | FAIL

Architecture:
PASS | FAIL

Build:
PASS | FAIL

Tests:
PASS | FAIL

Business:
PASS | FAIL

Membership:
PASS | FAIL

Authorization:
PASS | FAIL

Cross-Tenant Security:
PASS | FAIL

Flyway:
PASS | FAIL

jOOQ:
PASS | FAIL

PostgreSQL:
PASS | FAIL

HTTP Security:
PASS | FAIL

Privacy:
PASS | FAIL

Secret Scan:
PASS | FAIL

Modulith:
PASS | FAIL

Git:
PASS | FAIL

Scope Leakage:
NONE | FOUND

Tests:
TEST-M3-001..037
PASS | FAIL

Evidence:
docs/evidence/M3-EVIDENCE.md

Base:
develop

Branch:
feature/m3-business-membership

Implementation Commit:
<sha>

Final Evidence Commit:
<sha>

PR:
feature/m3-business-membership -> develop

PR Status:
READY | BLOCKED | NOT CREATED

Merged to develop:
NO

Promoted to staging:
NO

Promoted to main:
NO

M4 AUTHORIZED:
NO

STOP.
