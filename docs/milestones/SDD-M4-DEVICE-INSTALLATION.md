# SDD-M4 — DEVICE REGISTRATION & INSTALLATION LINKING

## Status

AUTHORIZED

M4 não está autorizado para implementação enquanto este documento
permanecer PROPOSED.

M5 is NOT authorized.

---

# 1. GOAL

Construir a foundation de Device Registration e Installation Linking
do TINO Backend.

M3 estabeleceu:

Authenticated User
        ↓
Membership
        ↓
Business
        ↓
AuthorizedBusinessContext

M4 acrescenta:

Authenticated User
        ↓
Membership
        ↓
Business
        ↓
AuthorizedBusinessContext
        ↓
Device Installation

O backend deve passar a conseguir responder:

- qual instalação do aplicativo está se comunicando;
- a qual Business essa instalação está vinculada;
- se o User autenticado pode registrar essa instalação;
- se a instalação continua ativa;
- como reconhecer novamente uma instalação já registrada;
- como impedir que deviceId/storeId concedam acesso a um Business;
- como impedir cross-business device linking;
- como preservar a autoridade de Membership sobre tenant.

M4 NÃO altera a autoridade de tenant definida no M3.

---

# 2. BASELINE REQUIRED

Antes de M4:

M0 = PASS
M1 = PASS
M2 = PASS
M3 = PASS

M3 integrado em:

develop

Baseline esperado inicialmente:

develop @ 5a5ab696f856d2b1a28066fb72cb871b952b6f88

Antes de executar futuramente, Luna deve verificar o SHA remoto real.

Se develop avançou legitimamente, usar o estado remoto verificado e
registrar o novo SHA na evidence.

Não assumir SHA antigo silenciosamente.

---

# 3. BRANCH POLICY

Fase documental:

docs/m4-device-installation-spec
→ develop

Futura implementação, somente após autorização:

feature/m4-device-installation
→ develop

Não tocar:

staging
main

Develop → staging continua NOT AUTHORIZED.

---

# 4. MODEL POLICY

Luna only.

Não usar Terra.
Não usar Sol.
Não delegar para outro modelo.

Se decisão arquitetural não estiver coberta:

STOP.

Retornar:

M4 STATUS: BLOCKED
ARCHITECTURAL DECISION REQUIRED
HUMAN AUTHORIZATION REQUIRED

---

# 5. SOURCE OF TRUTH

Antes de editar, ler integralmente:

- ARCHITECTURE-BASELINE.md
- EXECUTION-PROTOCOL.md
- SECURITY-AND-GIT-SAFETY.md
- GIT-BRANCHING-POLICY.md
- ADRs aplicáveis
- SYSTEM-IDENTITY.md
- SYSTEM-TENANCY.md
- SYSTEM-SECURITY.md
- SDD-M3-BUSINESS-MEMBERSHIP.md
- M3-EVIDENCE.md
- código efetivamente integrado em develop

Se existir System Spec específico de Device/Installation, ele também
é obrigatório.

Em caso de conflito:

STOP.

---

# 6. TERMINOLOGY

M4 deve distinguir:

Device

de:

Installation

Conceitualmente:

Device
= aparelho físico

Installation
= instalação lógica do TINO naquele aparelho

Entretanto, M4 NÃO deve inventar fingerprinting invasivo para provar
identidade física do hardware.

A autoridade operacional deve estar associada à instalação registrada,
não a técnicas frágeis de hardware fingerprint.

---

# 7. DOMAIN MODEL

Modelo mínimo esperado:

DeviceInstallation

DeviceInstallationId

InstallationExternalId

InstallationStatus

BusinessDeviceLink

ou estrutura equivalente caso os System Specs existentes já definam
outros nomes.

Não criar abstrações adicionais sem necessidade.

---

# 8. INSTALLATION IDENTITY

O Android deve possuir um identificador estável da instalação.

Conceitualmente:

installationId

Características:

- gerado pelo aplicativo;
- opaco;
- não contém PII;
- não contém businessId;
- não contém userId;
- não contém JWT;
- não contém storeId como autoridade;
- suficientemente aleatório;
- persistido localmente pelo aplicativo.

O backend NÃO deve confiar nele como autorização.

Ele identifica uma instalação.

Ele não concede acesso.

---

# 9. SERVER ID

Cada instalação persistida no backend deve possuir:

id UUID

Geração:

UUID v7

PostgreSQL:

UUID

Este é o identificador interno do registro.

---

# 10. EXTERNAL INSTALLATION ID

O identificador recebido do Android deve ser tratado como:

InstallationExternalId

e armazenado separadamente do UUID interno.

Deve possuir limite de tamanho.

Deve ser validado.

Não permitir payload arbitrariamente grande.

---

# 11. STORE ID

O Android já pode possuir conceito local chamado:

storeId

M4 deve preservar uma regra fundamental:

storeId NÃO é BusinessId.

Nunca:

storeId == business_id

por inferência implícita.

Nunca:

storeId
→ autorização de tenant

Se storeId precisar ser persistido para compatibilidade/mapeamento,
ele é apenas referência externa da instalação.

Sua existência NÃO autoriza Business.

---

# 12. AUTHORITY MODEL

A autoridade continua:

JWT
 ↓
User
 ↓
ACTIVE Membership
 ↓
ACTIVE Business
 ↓
AuthorizedBusinessContext

Somente depois:

AuthorizedBusinessContext
 ↓
register/link installation

Nunca:

installationId
→ Business authorization

Nunca:

storeId
→ Business authorization

Nunca:

device identifier
→ Business authorization

---

# 13. REGISTRATION ORDER

Fluxo obrigatório:

JWT
 ↓
resolve internal User
 ↓
requested BusinessId
 ↓
ResolveBusinessAccess
 ↓
AuthorizedBusinessContext
 ↓
validate installation identity
 ↓
register/link installation

Não:

installationId
 ↓
lookup Business
 ↓
authorize User

---

# 14. BUSINESS LINK

Uma instalação operacional deve possuir vínculo explícito com Business.

Conceitualmente:

Business
  1
  |
  N
DeviceInstallation

ou relacionamento equivalente.

O BusinessId persistido é resultado de uma operação previamente
autorizada por Membership.

---

# 15. MULTIPLE DEVICES

Um Business pode possuir múltiplas instalações.

Exemplo:

Mercadinho
 ├── celular do proprietário
 ├── tablet do caixa
 └── segundo celular

Não criar:

UNIQUE(business_id)

---

# 16. INSTALLATION OWNERSHIP

M4 não deve confundir:

User que registrou a instalação

com:

Business ao qual a instalação pertence operacionalmente.

Se `registeredByUserId` for necessário para provenance, ele não vira
autoridade futura.

Business authorization continua por Membership.

---

# 17. MULTIPLE BUSINESSES PER USER

Como User pode participar de múltiplos Businesses, a existência de uma
instalação não deve transformar:

User → único Business.

Nenhuma constraint deve quebrar o modelo multi-business do M3.

---

# 18. INSTALLATION STATUS

Estados mínimos:

ACTIVE
REVOKED

Novo registro:

ACTIVE

REVOKED:

não pode ser usado como instalação operacional válida.

Não adicionar estados sem necessidade.

---

# 19. REVOKED INSTALLATION

Uma instalação REVOKED:

- permanece persistida;
- preserva histórico;
- não é apagada automaticamente;
- não concede operação;
- não deve ser reativada implicitamente apenas porque o mesmo
  installationId apareceu novamente.

Reativação futura, se necessária, exige contrato explícito.

---

# 20. IDEMPOTENT REGISTRATION

Registrar novamente a mesma instalação para o mesmo Business deve ser
idempotente quando o estado atual permitir.

Exemplo:

Business A
+
installationExternalId X
+
ACTIVE existente

Resultado:

retornar o vínculo existente ou semanticamente equivalente.

Não criar duplicatas.

---

# 21. CROSS-BUSINESS COLLISION

Se a mesma InstallationExternalId já estiver vinculada ao Business A,
uma tentativa de registrá-la silenciosamente no Business B:

PROIBIDA.

Não mover automaticamente.

Não reatribuir silenciosamente.

Retornar conflito/denial apropriado.

Transferência de instalação entre Businesses fica fora do M4, salvo
contrato existente explícito.

---

# 22. UNIQUENESS

A estratégia exata de unique constraint deve ser compatível com a
semântica aprovada.

Preferência:

installation_external_id globalmente único

caso o identificador represente de fato uma instalação globalmente
opaca.

Se os System Specs existentes definirem escopo diferente:

seguir os System Specs.

Não decidir silenciosamente se houver contradição.

---

# 23. DEVICE FINGERPRINT

PROIBIDO introduzir fingerprint baseado em:

- IMEI;
- serial number;
- MAC address;
- advertising ID;
- contatos;
- telefone;
- hardware identifiers restritos.

M4 deve ser privacy-preserving.

---

# 24. ANDROID GENERATED ID

Preferir identificador gerado pela própria aplicação.

Exemplo conceitual:

UUID/random installation identifier

persistido no armazenamento apropriado do aplicativo.

Não usar identificador de publicidade.

---

# 25. REGISTRATION USE CASE

Criar:

RegisterDeviceInstallation

Input conceitual:

- Authenticated/internal User
- requested BusinessId
- installationExternalId
- optional localStoreId, somente se realmente exigido pelo contrato
- minimal non-sensitive metadata aprovada

Fluxo:

1. resolver User;
2. autorizar Business via Membership;
3. validar Business ACTIVE;
4. obter AuthorizedBusinessContext;
5. validar InstallationExternalId;
6. verificar registro existente;
7. impedir cross-business reassignment;
8. persistir instalação ACTIVE;
9. retornar contexto mínimo.

---

# 26. CLIENT CANNOT CHOOSE USER

Payload nunca aceita como autoridade:

userId
registeredByUserId
ownerUserId

O servidor deriva User do contexto autenticado.

---

# 27. CLIENT BUSINESS ID

businessId pode existir na request como Business solicitado.

Mas continua não sendo autoridade.

O servidor deve validar:

User
+
Membership
+
Business

antes do vínculo.

---

# 28. RESOLVE INSTALLATION

Criar use case/port equivalente:

ResolveDeviceInstallation

Input:

- AuthorizedBusinessContext
- InstallationExternalId

Output:

ActiveDeviceInstallationContext

somente quando:

- installation existe;
- installation ACTIVE;
- installation pertence ao mesmo Business autorizado.

---

# 29. ACTIVE DEVICE INSTALLATION CONTEXT

Representação framework-independent:

ActiveDeviceInstallationContext

mínimo:

- installationId
- installationExternalId
- businessId

Se necessário:

- registeredByUserId

mas somente como provenance.

Não incluir:

JWT
Spring Security context
jOOQ record
HTTP request
Keycloak types

---

# 30. BUSINESS AUTHORIZATION PRECEDES DEVICE RESOLUTION

Regra obrigatória:

AuthorizedBusinessContext
        ↓
DeviceInstallation resolution

Nunca:

DeviceInstallation
        ↓
descobrir Business
        ↓
assumir autorização

Isso evita que conhecimento de installationId seja utilizado como
capability de acesso.

---

# 31. DATABASE TABLE

Tabela mínima conceitual:

device_installations

Campos:

- id UUID PRIMARY KEY
- business_id UUID NOT NULL
- installation_external_id VARCHAR(...) NOT NULL
- status VARCHAR(...) NOT NULL
- registered_by_user_id UUID NOT NULL
- created_at TIMESTAMPTZ NOT NULL
- updated_at TIMESTAMPTZ NOT NULL

Adicionar local_store_id somente se o contrato existente exigir.

Não adicionar por conveniência.

---

# 32. FOREIGN KEYS

business_id
→ businesses(id)

registered_by_user_id
→ users(id)

FKs reais.

Sem cascades destrutivos.

---

# 33. STATUS CHECK

CHECK:

status IN (
  'ACTIVE',
  'REVOKED'
)

Não usar PostgreSQL ENUM.

---

# 34. UNIQUE EXTERNAL ID

Criar constraint compatível com a decisão aprovada sobre
InstallationExternalId.

Se global:

UNIQUE(installation_external_id)

Isso garante que uma instalação não seja vinculada silenciosamente a
dois Businesses.

---

# 35. TIMESTAMPS

TIMESTAMPTZ.

Java:

Instant

Roundtrip deve ser testado.

---

# 36. RLS

device_installations é tenant-owned.

Portanto deve seguir SYSTEM-TENANCY.md.

business_id é a coluna de tenant.

RLS deve utilizar o mecanismo estabelecido no M1:

SET LOCAL app.business_id

e role:

tino_app

Não criar mecanismo paralelo.

---

# 37. RLS FAIL-CLOSED

Sem tenant context válido:

acesso tenant-owned deve falhar/retornar vazio conforme contrato
estabelecido.

Nunca abrir RLS para facilitar registration.

Se registration exigir operação privilegiada incompatível com o
modelo atual:

STOP.

Não usar BYPASSRLS.

---

# 38. REGISTRATION AND RLS

Authorization acontece primeiro no nível application.

Depois:

AuthorizedBusinessContext
 ↓
TenantContextExecutor
 ↓
SET LOCAL app.business_id
 ↓
device installation persistence

Nunca confiar em RLS como substituto da autorização de Membership.

São camadas complementares.

---

# 39. DATABASE ROLE

Runtime:

tino_app

Migrations:

tino_migrator

Preservar:

NOSUPERUSER
NOBYPASSRLS

Não elevar privilégios.

---

# 40. REPOSITORY PORT

Criar port específico:

DeviceInstallationRepository

Somente operações necessárias.

Exemplos conceituais:

- create
- findByExternalId
- findActiveByExternalIdAndBusiness
- revoke

Não criar GenericRepository.

---

# 41. jOOQ ADAPTER

Criar adapter específico:

JooqDeviceInstallationRepository

jOOQ permanece em infrastructure.

Nenhum:

DSLContext
Record
generated table
generated POJO

vaza para domain/application.

---

# 42. MODULE

Preferência:

modules/device

ou nome equivalente já definido pela arquitetura.

Não colocar Device dentro de:

identity
business
shared

apenas para evitar criar módulo.

---

# 43. MODULE BOUNDARY

Device depende de contrato público para:

AuthorizedBusinessContext

ou composição application equivalente.

Não acessar internals de:

BusinessRepository
JooqBusinessRepository
Identity repository

diretamente.

---

# 44. MODULITH

ApplicationModules.verify():

PASS

Não criar ciclo:

business ↔ device

Business não deve precisar conhecer Device para continuar existindo.

Dependência esperada:

device
→ public business contract

não:

business
→ device

---

# 45. API — REGISTER

Endpoint mínimo permitido:

POST /api/v1/businesses/{businessId}/installations

ou contrato equivalente aprovado.

Authorization:

Bearer JWT

Payload mínimo:

{
  "installationId": "..."
}

Nome exato do campo deve seguir contrato Android/System Spec se já
existir.

---

# 46. API AUTHORIZATION

POST installation:

sem JWT:
401

JWT válido sem Membership no Business:
403/denial apropriado

Business DISABLED:
denied

Membership DISABLED:
denied

Business autorizado:
registration permitida.

---

# 47. RESPONSE

Resposta mínima:

- installationId interno, se necessário;
- external installation identifier, se necessário;
- businessId;
- status.

Não retornar:

User externalSubject
JWT claims
database internals
credentials
tenant session state

---

# 48. REVOKE

M4 pode incluir revogação mínima caso necessária para provar lifecycle:

POST/DELETE semanticamente apropriado.

Mas não criar CRUD administrativo completo.

Se não for necessário para o contrato M4, persistir REVOKED como
foundation/test support sem ampliar API.

---

# 49. DELETE

Não implementar hard delete.

Device installation histórica deve permanecer disponível para
integridade/auditoria futura.

---

# 50. METADATA

Não adicionar coleta de:

- modelo do aparelho;
- fabricante;
- IP histórico;
- localização;
- telefone;
- advertising ID;

sem requisito explícito.

M4 é foundation de identidade da instalação, não telemetria.

---

# 51. DEVICE NAME

Não exigir nome amigável do aparelho no M4.

Pode ser milestone futuro.

---

# 52. PUSH TOKEN

FCM token / push token:

OUT OF SCOPE.

Não adicionar.

---

# 53. NOTIFICATIONS

OUT OF SCOPE.

---

# 54. APP VERSION

Não persistir appVersion no domínio M4 por conveniência.

Observabilidade técnica pode capturar versão em headers/logs se já
existir baseline apropriado, mas isso não entra no modelo de domínio
sem requisito.

---

# 55. BOOTSTRAP

M4 NÃO implementa bootstrap completo.

Pode produzir foundation necessária para bootstrap futuro.

Não criar endpoint que agregue:

BusinessProfile
Capabilities
Customers
Credit
Sync state

---

# 56. SYNC

M4 NÃO implementa:

push
pull
cursor
conflict resolution
offline mutations
change feed

Installation será utilizada futuramente pelo Sync, mas Sync está fora.

---

# 57. SECURITY

Nenhum InstallationExternalId deve ser tratado como secret.

Mesmo assim, não expor IDs desnecessariamente em logs.

Nunca logar:

JWT
Authorization header
credentials

Preservar M2 security.

---

# 58. ENUMERATION

Conhecer InstallationExternalId não deve permitir descobrir:

- Business;
- User;
- Membership;
- tenant data.

Endpoints devem exigir contexto autenticado/autorizado.

---

# 59. CROSS-TENANT SECURITY

User A autorizado no Business A não pode:

- consultar;
- registrar novamente;
- mover;
- revogar;

installation do Business B.

Mesmo conhecendo InstallationExternalId.

---

# 60. IDEMPOTENCY

Registro repetido:

same Business
+
same installationExternalId

deve produzir resultado estável.

Não criar múltiplas rows.

---

# 61. CONCURRENCY

Duas requests simultâneas para registrar a mesma instalação:

PostgreSQL unique constraint deve garantir integridade.

Application deve tratar race adequadamente.

Não usar distributed lock.

---

# 62. TRANSACTION

Registration deve ser transacional.

Authorization ocorre antes.

Persistência tenant-owned ocorre dentro de TenantContext.

Não manter transação aberta durante chamada externa.

M4 não precisa de chamada externa.

---

# 63. DOMAIN EVENTS

Se necessário:

DeviceInstallationRegistered
DeviceInstallationRevoked

podem ser eventos internos.

Não introduzir broker.

Não introduzir Kafka/RabbitMQ.

---

# 64. OUTBOX

Não criar outbox apenas para M4 se não existir efeito externo durável.

---

# 65. PRIVACY

InstallationExternalId deve ser identificador técnico aleatório.

Não derivar de:

email
telefone
CPF
nome
hardware ID sensível

---

# 66. MIGRATION

Adicionar somente schema M4 necessário.

Não alterar migrations publicadas.

Nova tabela permitida:

device_installations

Nenhuma tabela M5+.

---

# 67. jOOQ GENERATION

Regenerar após migration.

Generated code permanece infrastructure-only.

---

# 68. TEST MATRIX

## TEST-M4-001 — REGISTER INSTALLATION

User autorizado registra instalação no próprio Business.

PASS.

## TEST-M4-002 — INSTALLATION UUID V7

Internal DeviceInstallationId:

UUID version 7.

## TEST-M4-003 — ACTIVE BY DEFAULT

Nova instalação:

ACTIVE.

## TEST-M4-004 — BUSINESS FK

Business inexistente:

PostgreSQL rejeita.

## TEST-M4-005 — REGISTERED USER FK

User inexistente:

PostgreSQL rejeita.

## TEST-M4-006 — STATUS CHECK

Status inválido:

PostgreSQL rejeita.

## TEST-M4-007 — EXTERNAL ID UNIQUE

Duplicate incompatível:

PostgreSQL rejeita.

## TEST-M4-008 — IDEMPOTENT SAME BUSINESS

Mesmo Business + mesma instalação:

sem duplicação.

## TEST-M4-009 — CROSS BUSINESS REASSIGNMENT DENIED

Instalação A vinculada ao Business A.

Tentativa Business B:

DENIED.

## TEST-M4-010 — MISSING MEMBERSHIP DENIED

User sem Membership:

DENIED.

## TEST-M4-011 — DISABLED MEMBERSHIP DENIED

DENIED.

## TEST-M4-012 — DISABLED BUSINESS DENIED

DENIED.

## TEST-M4-013 — CLIENT BUSINESS ID NOT AUTHORITY

businessId da request sozinho:

não autoriza.

## TEST-M4-014 — INSTALLATION ID NOT AUTHORITY

Conhecer installationId:

não autoriza Business.

## TEST-M4-015 — STORE ID NOT AUTHORITY

storeId, caso presente:

não autoriza Business.

## TEST-M4-016 — AUTHORIZATION BEFORE TENANT CONTEXT

Provar ordem:

Membership authorization
→ AuthorizedBusinessContext
→ TenantContext

PASS.

## TEST-M4-017 — TENANT RLS OWN BUSINESS

Tenant A acessa installation A.

PASS.

## TEST-M4-018 — TENANT RLS CROSS BUSINESS

Tenant A não acessa installation B.

PASS.

## TEST-M4-019 — RLS FAIL CLOSED

Sem business tenant context:

não acessar installation tenant-owned.

PASS.

## TEST-M4-020 — POOL COMMIT RESET

Tenant context não vaza após COMMIT.

PASS.

## TEST-M4-021 — POOL ROLLBACK RESET

Tenant context não vaza após ROLLBACK.

PASS.

## TEST-M4-022 — CONCURRENT REGISTRATION

Requests concorrentes para mesma installation:

uma identidade persistente.

Sem duplicação.

## TEST-M4-023 — REVOKED INSTALLATION DENIED

REVOKED não resolve como instalação ativa.

## TEST-M4-024 — REVOKED NOT AUTO REACTIVATED

Nova registration não reativa silenciosamente instalação revogada.

## TEST-M4-025 — MULTIPLE INSTALLATIONS PER BUSINESS

Business pode possuir N instalações.

PASS.

## TEST-M4-026 — USER MULTI BUSINESS PRESERVED

M4 não quebra múltiplos Businesses por User.

PASS.

## TEST-M4-027 — NO PII IN INSTALLATION

Nenhum PII desnecessário persistido.

PASS.

## TEST-M4-028 — NO HARDWARE FINGERPRINT

Nenhum IMEI/MAC/advertising ID/serial.

PASS.

## TEST-M4-029 — API REGISTER REQUIRES AUTH

Sem JWT:

401.

## TEST-M4-030 — API CROSS BUSINESS DENIED

JWT User A tentando Business B:

denied.

## TEST-M4-031 — API IDEMPOTENT REGISTRATION

Registro repetido:

resultado semanticamente idempotente.

## TEST-M4-032 — TIMESTAMP ROUNDTRIP

TIMESTAMPTZ ↔ Instant.

PASS.

## TEST-M4-033 — MIGRATION FROM ZERO

Banco vazio:

M0 → M4

PASS.

## TEST-M4-034 — FLYWAY VALIDATE

PASS.

## TEST-M4-035 — JOOQ POSTGRESQL

Repository real:

PASS.

## TEST-M4-036 — JOOQ BOUNDARY

Nenhum jOOQ em domain/application.

PASS.

## TEST-M4-037 — BUSINESS MODULE BOUNDARY

Device não acessa internals de Business.

PASS.

## TEST-M4-038 — IDENTITY MODULE BOUNDARY

Device não acessa internals de Identity.

PASS.

## TEST-M4-039 — MODULITH

ApplicationModules.verify():

PASS.

## TEST-M4-040 — DATABASE PRIVILEGES

tino_app:

NOSUPERUSER
NOBYPASSRLS

PASS.

## TEST-M4-041 — SECRET SCAN

PASS.

## TEST-M4-042 — CLEAN BUILD

./gradlew clean build

PASS.

## TEST-M4-043 — NO SCOPE LEAKAGE

Ausência de implementação funcional:

Bootstrap
Sync
Customer
Credit
Ledger
Payment
Pix
Reconciliation
Notification
WhatsApp
Push token

PASS.

---

# 69. REQUIRED POSTGRESQL TESTING

Usar PostgreSQL real/Testcontainers para:

- migration;
- FKs;
- checks;
- unique;
- RLS;
- concurrency;
- jOOQ;
- timestamps.

Não substituir por H2.

---

# 70. REQUIRED SECURITY TESTING

Provar:

JWT
→ User
→ Membership
→ Business
→ AuthorizedBusinessContext
→ DeviceInstallation

Nunca:

installation
→ tenant authority

---

# 71. REQUIRED RLS TESTING

Provar:

Business A
→ installation A visible

Business A
→ installation B invisible/denied

sem tenant:
fail closed

pool reuse:
sem leakage

---

# 72. DEPENDENCY AUDIT

Não introduzir:

JPA
Hibernate ORM
Redis
Kafka
RabbitMQ
MongoDB

---

# 73. SOURCE AUDIT

Não permitir:

jOOQ em domain/application
Spring Security em domain
Keycloak em domain
Business repository internals em device
Identity repository internals em device

---

# 74. SCHEMA AUDIT

Ao final do M4, schema funcional permitido:

users
businesses
business_memberships
device_installations

além das estruturas técnicas já aprovadas.

Não criar schema funcional M5+.

---

# 75. SECRET SAFETY

Aplicar integralmente SECURITY-AND-GIT-SAFETY.md.

Nenhuma password literal de teste.

Nenhuma credential fallback.

Nenhum token.

Nenhum secret em evidence.

Secret scan obrigatório:

- durante implementação;
- antes de commit;
- antes de push.

---

# 76. EVIDENCE

Criar futuramente, somente quando M4 estiver AUTHORIZED:

docs/evidence/M4-EVIDENCE.md

Registrar:

- develop base SHA;
- feature branch;
- migration;
- schema;
- DeviceInstallation model;
- installation identity strategy;
- Business link;
- authorization ordering;
- RLS;
- concurrency;
- idempotency;
- revoked behavior;
- privacy;
- TEST-M4-001..043;
- PostgreSQL;
- Testcontainers;
- Flyway;
- jOOQ;
- HTTP security;
- secret scan;
- dependency audit;
- Modulith;
- clean build;
- scope leakage;
- deviations/blockers.

Nunca registrar credentials.

---

# 77. REQUIRED GATES

Quando autorizado:

./gradlew clean build

architecture

Spring Modulith

Flyway from zero

Flyway validate

jOOQ generation

JUnit

PostgreSQL Testcontainers

RLS

pool isolation

device integration

HTTP security

cross-business authorization

concurrency

secret scan

dependency audit

source boundary audit

schema audit

scope leakage audit

---

# 78. INDEPENDENT LUNA REVIEW

Luna é o único modelo autorizado.

Separar:

PHASE A — IMPLEMENTATION

STOP EDITING

PHASE B — INDEPENDENT SELF-REVIEW

Na segunda fase:

- revisar diff integral;
- revisar migration;
- revisar RLS;
- revisar constraints;
- revisar autorização;
- revisar privacy;
- rerodar gates;
- rerodar secret scan;
- revisar evidence.

PASS somente após essa segunda verificação.

---

# 79. ACCEPTANCE CRITERIA

M4 PASS somente se:

[ ] instalação registrada

[ ] UUID v7 interno

[ ] ACTIVE default

[ ] Business FK real

[ ] User FK real

[ ] status CHECK

[ ] external ID uniqueness definida

[ ] registration idempotente

[ ] cross-business reassignment denied

[ ] multiple installations/Business

[ ] multi-business User preservado

[ ] Membership obrigatória

[ ] disabled Membership denied

[ ] disabled Business denied

[ ] businessId não é autoridade sozinho

[ ] installationId não é autoridade

[ ] storeId não é autoridade

[ ] authorization antes de TenantContext

[ ] device_installations tenant-owned

[ ] RLS real

[ ] RLS cross-tenant PASS

[ ] fail-closed PASS

[ ] pool COMMIT reset PASS

[ ] pool ROLLBACK reset PASS

[ ] concurrency PASS

[ ] REVOKED denied

[ ] sem auto-reactivation

[ ] sem hardware fingerprint

[ ] sem PII desnecessário

[ ] HTTP auth PASS

[ ] Flyway PASS

[ ] jOOQ PASS

[ ] PostgreSQL PASS

[ ] tino_app NOBYPASSRLS

[ ] Modulith PASS

[ ] secret scan PASS

[ ] clean build PASS

[ ] TEST-M4-001..043 PASS

[ ] Scope Leakage NONE

[ ] M4-EVIDENCE presente

---

# 80. STOP CONDITIONS

M4 = BLOCKED se for necessário:

- transformar storeId em autoridade;
- transformar installationId em autoridade;
- usar hardware fingerprint;
- usar IMEI/MAC/advertising ID;
- elevar tino_app;
- usar BYPASSRLS;
- alterar arquitetura de tenancy;
- alterar M3 Membership authorization;
- reescrever migration publicada;
- introduzir Redis;
- introduzir distributed lock;
- introduzir JPA;
- implementar Bootstrap;
- implementar Sync;
- implementar Customer/Credit/Payment/Pix.

Retornar:

M4 STATUS: BLOCKED
ARCHITECTURAL DECISION REQUIRED
HUMAN AUTHORIZATION REQUIRED

---

# 81. DOCUMENTATION WORKFLOW

Nesta etapa:

M4 permanece PROPOSED.

Luna deve:

1. persistir este documento em:

docs/milestones/SDD-M4-DEVICE-INSTALLATION.md

2. revisar contra todas as fontes de verdade;

3. corrigir apenas inconsistências documentais que não mudem arquitetura;

4. se houver conflito arquitetural:
   STOP;

5. revisar TEST-M4-001..043;

6. criar:

docs/m4-device-installation-spec

7. secret scan;

8. commit/push documental;

9. criar PR:

docs/m4-device-installation-spec
→ develop

10. aguardar checks;

11. NÃO mergear sem autorização humana.

---

# 82. DOCUMENTATION CHECKPOINT

Quando o PR documental estiver verde:

retornar:

DOCUMENTATION READY FOR DEVELOP

e STOP.

Merge documental não autoriza implementação.

---

# 83. IMPLEMENTATION WORKFLOW

Somente após:

- documento integrado em develop;
- M4 explicitamente AUTHORIZED pelo usuário.

Criar:

feature/m4-device-installation

a partir do develop atualizado.

Implementar somente M4.

---

# 84. FEATURE PROMOTION

Após implementação PASS:

feature/m4-device-installation
→ develop

Luna cria PR via gh.

Quando checks estiverem verdes:

FEATURE READY FOR DEVELOP

STOP.

Merge exige autorização humana.

---

# 85. STAGING

Develop → staging:

NOT AUTHORIZED.

M4 não deve promover nada para staging.

---

# 86. MAIN

Não tocar main.

---

# 87. M5

M5:

NOT AUTHORIZED.

Nenhuma implementação M5 pode ser criada.

---

# 88. EXPECTED FINAL ARCHITECTURE

Após M4:

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
 ↓
DeviceInstallation

A instalação identifica:

"qual instalação do TINO está operando?"

Mas não responde sozinha:

"qual tenant posso acessar?"

Essa autoridade continua pertencendo a:

User + Membership + Business.

---

# 89. FINAL DOCUMENTATION REPORT

MILESTONE:
M4 — DEVICE REGISTRATION & INSTALLATION LINKING

STATUS:
PROPOSED

Documentation:
PASS | BLOCKED

Specification Alignment:
PASS | CONFLICT

Identity Alignment:
PASS | CONFLICT

Business Alignment:
PASS | CONFLICT

Tenancy Alignment:
PASS | CONFLICT

Security Alignment:
PASS | CONFLICT

Privacy Alignment:
PASS | CONFLICT

Test Matrix:
PASS | FAIL

Implementation:
NOT STARTED

Scope Leakage:
NONE

Changed Documents:
docs/milestones/SDD-M4-DEVICE-INSTALLATION.md

Conflicts:
NONE | <description>

Develop -> staging:
NOT AUTHORIZED

M5 AUTHORIZED:
NO

STOP.
