# SDD-M5 — BOOTSTRAP & INSTALLATION CONTEXT

## Status

AUTHORIZED

M5 não está autorizado para implementação enquanto este documento
permanecer PROPOSED.

M6 is NOT authorized.

---

# 1. GOAL

Construir e provar o Bootstrap Context do TINO Backend.

M5 consolida as foundations entregues por:

M2 — Identity & Security
M3 — Business & Membership
M4 — Device Registration & Installation Linking

para responder de forma segura:

"Qual é o estado inicial deste usuário/instalação ao abrir o TINO?"

O backend deve conseguir determinar:

- quem é o User autenticado;
- quais Businesses esse User pode acessar;
- qual Business foi solicitado/selecionado;
- se esse Business está autorizado;
- se existe instalação vinculada ao Business;
- se essa instalação está ACTIVE;
- qual estado de bootstrap o Android deve assumir;
- qual contexto mínimo pode ser retornado ao app.

M5 NÃO implementa Sync.

M5 NÃO implementa Customer.

M5 NÃO implementa Credit.

M5 NÃO implementa Payment.

M5 NÃO implementa Pix.

---

# 2. BASELINE REQUIRED

Antes do M5:

M0 = PASS
M1 = PASS
M2 = PASS
M3 = PASS
M4 = PASS

M4 integrado em:

develop

Baseline esperado inicialmente:

develop @ e0bb557c319ca6e06a95b584398709a2adeb94e5

Antes de futura implementação, Luna deve verificar o SHA remoto real.

Se develop tiver avançado legitimamente:

- utilizar o estado remoto atual;
- registrar o SHA real na evidence;
- não assumir baseline antigo silenciosamente.

---

# 3. BRANCH POLICY

Fase documental:

docs/m5-bootstrap-context-spec
→ develop

Futura implementação:

feature/m5-bootstrap-context
→ develop

Não tocar:

staging
main

Develop → staging permanece NOT AUTHORIZED.

---

# 4. MODEL POLICY

Luna only.

Não usar Terra.

Não usar Sol.

Não delegar para outro modelo.

Se surgir decisão arquitetural não coberta:

STOP.

Retornar:

M5 STATUS: BLOCKED
ARCHITECTURAL DECISION REQUIRED
HUMAN AUTHORIZATION REQUIRED

---

# 5. SOURCE OF TRUTH

Antes de editar ou implementar, ler integralmente:

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
- SDD-M4-DEVICE-INSTALLATION.md
- M4-EVIDENCE.md
- código efetivamente integrado em develop

Se existir System Spec específico de:

Bootstrap
Application Context
Installation Context

ele também é obrigatório.

Em conflito:

STOP.

Não resolver silenciosamente.

---

# 6. RESPONSIBILITY

M5 é uma camada de composição.

Ele NÃO deve duplicar regras pertencentes aos módulos anteriores.

Identity continua responsável por:

User.

Business continua responsável por:

Membership
Business authorization
AuthorizedBusinessContext.

Device continua responsável por:

DeviceInstallation
ActiveDeviceInstallationContext.

Bootstrap:

orquestra esses contratos.

---

# 7. BOOTSTRAP DEFINITION

Bootstrap representa o contexto mínimo necessário para o Android
decidir qual experiência inicial apresentar.

Não é:

- dump completo do banco;
- sync inicial;
- catálogo;
- dashboard;
- configuração inteira do Business;
- payload de domínio completo.

Bootstrap é:

STATE + MINIMAL CONTEXT.

---

# 8. BOOTSTRAP STATES

Estados oficiais do M5:

BUSINESS_REQUIRED

LOCAL_BUSINESS_LINK_REQUIRED

READY

Não adicionar estados adicionais sem novo requisito.

---

# 9. BUSINESS_REQUIRED

Retornar:

BUSINESS_REQUIRED

quando o User autenticado não possui nenhum Business operacional
acessível através de Membership ACTIVE + Business ACTIVE.

Exemplo:

JWT válido
 ↓
User ACTIVE
 ↓
0 Businesses acessíveis
 ↓
BUSINESS_REQUIRED

Isso sinaliza ao Android que deve iniciar fluxo de criação/seleção
apropriado.

M5 não inventa Business automaticamente.

---

# 10. LOCAL_BUSINESS_LINK_REQUIRED

Retornar:

LOCAL_BUSINESS_LINK_REQUIRED

quando:

- User autenticado;
- Business válido/autorizado;
- nenhuma instalação ACTIVE correspondente ao contexto da request
  está vinculada adequadamente.

Isso NÃO autoriza Device automaticamente.

O Android deverá usar o fluxo M4 apropriado para registro/link.

---

# 11. READY

Retornar:

READY

somente quando:

- JWT validado;
- User ACTIVE;
- Membership ACTIVE;
- Business ACTIVE;
- Business autorizado;
- DeviceInstallation ACTIVE;
- instalação vinculada ao mesmo Business.

READY significa:

"o backend consegue estabelecer o contexto operacional inicial."

READY NÃO significa:

- sync concluído;
- dados locais atualizados;
- estoque sincronizado;
- caderneta sincronizada.

---

# 12. AUTHORITY CHAIN

Toda resolução READY deve respeitar:

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
DeviceInstallation
 ↓
BootstrapContext

Nunca inverter essa cadeia.

---

# 13. BOOTSTRAP MUST NOT AUTHORIZE

Bootstrap não cria nova autoridade.

Ele apenas compõe autoridades já comprovadas.

Nunca:

request businessId
→ READY

Nunca:

storeId
→ READY

Nunca:

installationId
→ READY

Nunca:

deviceId
→ READY

sem passar pelos contratos de autorização anteriores.

---

# 14. REQUEST MODEL

Endpoint conceitual:

POST /api/v1/bootstrap

ou:

GET /api/v1/bootstrap

A escolha deve seguir o contrato HTTP existente/System Spec.

Se não houver contrato superior, preferir request semanticamente
idempotente e sem efeitos colaterais.

O bootstrap é leitura/composição.

Não deve criar Business.

Não deve registrar Device.

---

# 15. INPUT

Input mínimo pode conter:

requestedBusinessId
installationExternalId

somente se necessários para resolução.

Esses valores representam:

REQUESTED CONTEXT

não:

AUTHORIZED CONTEXT.

---

# 16. USER IDENTITY

User vem exclusivamente do contexto autenticado.

Nunca aceitar:

userId

em payload como autoridade.

Nunca aceitar:

externalSubject

do cliente para resolver outro User.

---

# 17. BUSINESS SELECTION

Se User possuir exatamente um Business ACTIVE acessível:

o servidor pode resolver esse Business automaticamente
se System Spec permitir.

Se User possuir múltiplos Businesses:

não escolher silenciosamente um deles.

Nesse caso:

requestedBusinessId deve ser validado por Membership

ou retornar contexto que permita seleção.

Se o System Spec atual definir comportamento diferente:

seguir o System Spec.

---

# 18. MULTIPLE BUSINESSES

M5 deve preservar:

User
 ├── Business A
 └── Business B

Não assumir:

primeiro Business = Business atual.

Não usar:

createdAt mais antigo
último ID
primeira Membership

como seleção implícita sem contrato explícito.

---

# 19. BUSINESS SELECTION STATE

O contrato oficial de estados permanece apenas:

BUSINESS_REQUIRED
LOCAL_BUSINESS_LINK_REQUIRED
READY

Se múltiplos Businesses exigirem seleção explícita e o contrato atual
não consegue representar isso sem novo estado:

STOP.

Retornar:

SPECIFICATION GAP

Não inventar:

BUSINESS_SELECTION_REQUIRED

sem autorização.

---

# 20. INSTALLATION RESOLUTION

Installation deve ser resolvida SOMENTE após Business authorization.

Fluxo:

Authenticated User
 ↓
Business authorization
 ↓
AuthorizedBusinessContext
 ↓
installation resolution

Nunca:

installation
→ descobrir tenant
→ depois autorizar.

---

# 21. STORE ID

storeId continua sendo compatibilidade local.

Ele NÃO pode:

- determinar Business;
- determinar User;
- conceder READY;
- conceder tenant access.

Se bootstrap precisar retornar storeId:

somente como metadata de compatibilidade explicitamente aprovada.

Não criar nova autoridade.

---

# 22. ACTIVE INSTALLATION

READY exige:

InstallationStatus.ACTIVE

REVOKED:

não pode produzir READY.

Resultado esperado:

LOCAL_BUSINESS_LINK_REQUIRED

ou denial apropriado conforme contrato superior.

Não reativar automaticamente.

---

# 23. DISABLED USER

User DISABLED:

bootstrap não pode produzir:

BUSINESS_REQUIRED
LOCAL_BUSINESS_LINK_REQUIRED
READY

como se fosse um User operacional.

Deve retornar denial/unauthorized apropriado.

Preservar contrato M2.

---

# 24. DISABLED MEMBERSHIP

Membership DISABLED:

não conta como Business acessível.

Não pode produzir READY.

---

# 25. DISABLED BUSINESS

Business DISABLED:

não conta como Business operacional.

Não pode produzir READY.

---

# 26. CROSS-BUSINESS DEVICE

User autorizado em:

Business A

mas installation pertence a:

Business B

não pode produzir READY para A ou B indevidamente.

Cross-business installation mismatch:

DENIED
ou LOCAL_BUSINESS_LINK_REQUIRED conforme contrato seguro definido.

Nunca remapear automaticamente.

---

# 27. BOOTSTRAP CONTEXT

Criar representação framework-independent:

BootstrapContext

ou equivalente.

Campos mínimos conceituais:

- state
- user
- businesses
- selectedBusiness
- installation

Somente incluir campos necessários para cada estado.

Não retornar entidades completas.

---

# 28. BOOTSTRAP USER SUMMARY

User summary mínima:

- id
- status

Não retornar:

externalSubject

se o Android não precisa.

Não retornar:

JWT claims.

---

# 29. BUSINESS SUMMARY

Resumo mínimo:

- id
- tradeName
- vertical
- status
- role

Somente Businesses acessíveis pelo User.

Nunca retornar Businesses estrangeiros.

---

# 30. SELECTED BUSINESS

Quando houver Business selecionado/autorizado:

retornar summary mínima.

Não retornar Membership interna inteira.

---

# 31. INSTALLATION SUMMARY

Quando existir:

- internal installation id, se necessário;
- external installation identifier, se necessário;
- status.

Não retornar:

registeredByUserId

sem necessidade.

Não retornar tenant internals.

---

# 32. RESPONSE BY STATE

## BUSINESS_REQUIRED

Exemplo conceitual:

{
  "state": "BUSINESS_REQUIRED",
  "user": {...},
  "businesses": []
}

Sem installation.

---

## LOCAL_BUSINESS_LINK_REQUIRED

Exemplo conceitual:

{
  "state": "LOCAL_BUSINESS_LINK_REQUIRED",
  "user": {...},
  "businesses": [...],
  "selectedBusiness": {...}
}

Sem installation ACTIVE.

---

## READY

Exemplo conceitual:

{
  "state": "READY",
  "user": {...},
  "businesses": [...],
  "selectedBusiness": {...},
  "installation": {...}
}

Não incluir dados de Sync.

---

# 33. API VERSION

Usar:

/api/v1

Não criar:

/v2
/graphql

sem necessidade.

---

# 34. BOOTSTRAP ENDPOINT

Contrato preferencial:

POST /api/v1/bootstrap

se parâmetros contextuais forem enviados em body.

Ou:

GET /api/v1/bootstrap

se completamente derivável de auth/query segura.

Escolher somente após revisar System Specs existentes.

Não implementar os dois sem necessidade.

---

# 35. NO SIDE EFFECTS

Bootstrap deve ser semanticamente read-only.

Não:

- criar User;
- criar Business;
- criar Membership;
- registrar Device;
- atualizar status;
- criar Sync cursor.

OBSERVAÇÃO:

JIT User provisioning já pertence ao M2 e pode ocorrer no boundary de
identidade conforme contrato existente.

Bootstrap não deve adicionar novo side effect próprio.

---

# 36. APPLICATION USE CASE

Criar use case:

ResolveBootstrapContext

Input conceitual:

- AuthenticatedPrincipal/User
- requestedBusinessId optional
- installationExternalId optional

Output:

BootstrapContext

---

# 37. ORCHESTRATION

ResolveBootstrapContext deve compor APIs/ports públicos.

Não acessar diretamente:

JooqUserRepository
JooqBusinessRepository
JooqBusinessMembershipRepository
JooqDeviceInstallationRepository

Bootstrap não conhece persistence internals.

---

# 38. MODULE LOCATION

Preferência:

app/application composition

ou módulo:

bootstrap

se a arquitetura atual justificar.

Não criar módulo apenas por estética.

Se Spring Modulith exigir package/module específico para composição:

seguir arquitetura existente.

Não mover lógica de Identity/Business/Device para bootstrap.

---

# 39. MODULE DEPENDENCY

Dependências permitidas conceitualmente:

bootstrap/application composition
→ identity public API
→ business public API
→ device public API

Evitar ciclos.

Nenhum:

identity → bootstrap
business → bootstrap
device → bootstrap

---

# 40. MODULITH

ApplicationModules.verify():

PASS.

Se composição gerar ciclo:

STOP.

Não mover contratos para shared indiscriminadamente.

---

# 41. TENANT CONTEXT

Bootstrap pode precisar estabelecer TenantContext apenas para resolver
dados tenant-owned da instalação.

A ordem obrigatória:

authorize Business
 ↓
AuthorizedBusinessContext
 ↓
TenantContext
 ↓
resolve installation

Nunca estabelecer TenantContext usando BusinessId não autorizado.

---

# 42. BUSINESS LISTING BEFORE TENANT

Listar Businesses acessíveis ao User é operação de autorização/global
e não deve depender de tenant context já estabelecido.

Isso preserva a possibilidade:

User
→ multiple Businesses

---

# 43. DEVICE QUERY

Consultar device_installations é tenant-owned.

Portanto:

Business autorizado
→ TenantContext
→ Device query

Preservar RLS M4.

---

# 44. RLS

M5 não cria nova política RLS.

Reutilizar integralmente mecanismo M1/M4.

M5 não modifica:

app.business_id
SET LOCAL
RLS policies

sem necessidade explícita.

---

# 45. DATABASE

M5 preferencialmente NÃO cria novas tabelas.

Bootstrap é composição.

Se parecer necessária tabela:

bootstrap_state
current_business
session
selected_business

STOP.

Isso representa mudança arquitetural.

Não persistir estado de navegação do Android no backend sem requisito.

---

# 46. SELECTED BUSINESS PERSISTENCE

M5 NÃO persiste:

current_business_id

no User.

Não adicionar:

users.selected_business

Business selecionado é contexto da sessão/request, não identidade
permanente do User.

---

# 47. SESSION

Não criar server-side session store.

Backend permanece stateless.

Não adicionar Redis.

---

# 48. CACHE

Não adicionar cache para bootstrap.

Medição deve preceder otimização.

---

# 49. ERROR MODEL

Possíveis erros:

AuthenticatedUserDisabled
BusinessAccessDenied
BusinessDisabled
InstallationRevoked
InvalidBootstrapRequest

Não vazar:

SQL
jOOQ
Spring Security internals
Keycloak internals
tenant GUC

---

# 50. HTTP STATUS

Sem JWT:

401

Business solicitado sem Membership:

403 ou resposta equivalente definida no security contract.

Business inexistente/não acessível:

evitar information disclosure.

Payload inválido:

400.

READY:

200.

BUSINESS_REQUIRED:

200.

LOCAL_BUSINESS_LINK_REQUIRED:

200.

Esses estados são estados de produto válidos, não erros HTTP.

---

# 51. SECURITY

Bootstrap é endpoint autenticado.

Preservar integralmente M2 JWT validation:

- signature
- expiration
- issuer
- audience/authorized party
- sub

Nenhuma redução de segurança.

---

# 52. INFORMATION DISCLOSURE

Bootstrap nunca deve revelar:

- Businesses de outros Users;
- installations de outros Businesses;
- User externalSubject;
- internal security configuration;
- tokens;
- secrets.

---

# 53. IDOR PROTECTION

M5 deve testar IDOR explicitamente.

Conhecer:

BusinessId

ou:

InstallationExternalId

não concede acesso.

---

# 54. PRIVACY

Bootstrap retorna apenas contexto necessário para UI inicial.

Não retornar:

email
telefone
nome pessoal
JWT claims
device hardware identifiers
IP
PII adicional

---

# 55. ANDROID COMPATIBILITY

O endpoint deve preparar integração futura com o Android existente.

Não exigir que o Android abandone:

local-first
Room
local installation identity

M5 não transforma backend em requisito para registrar operação local
de negócio já suportada offline.

---

# 56. LOCAL-FIRST

Bootstrap acontece quando conectividade estiver disponível.

Falha de bootstrap não deve redefinir a arquitetura local-first.

O comportamento Android offline pertence ao cliente.

M5 não implementa fallback cloud.

---

# 57. SYNC BOUNDARY

Bootstrap NÃO retorna:

- sync cursor;
- pending events;
- changes;
- server mutations;
- conflict data;
- sync status detalhado.

Esses conceitos entram em M6/M7.

---

# 58. NO DOMAIN DATA

Bootstrap NÃO retorna:

- produtos;
- clientes;
- estoque;
- fiado;
- transações;
- fornecedores;
- dashboard.

Somente contexto estrutural.

---

# 59. IDEMPOTENCY

Chamadas repetidas com o mesmo contexto e estado do banco devem produzir
resultado semanticamente equivalente.

Bootstrap não altera estado.

---

# 60. CONCURRENCY

Bootstrap deve suportar requests concorrentes.

Não criar lock.

Se Business/Device mudar entre queries concorrentes, resultado deve
seguir consistência transacional adequada.

Não elevar isolamento global.

---

# 61. TRANSACTION

Preferir transação read-only curta quando necessário.

READ COMMITTED.

Não manter transação durante chamada externa.

Nenhuma chamada externa é necessária.

---

# 62. PERFORMANCE

Evitar N+1 para listar Businesses.

Bootstrap deve executar quantidade previsível de queries.

Não criar micro-otimização prematura.

Evidence pode registrar query count apenas se ferramenta existente
permitir facilmente.

---

# 63. OBSERVABILITY

Eventos técnicos possíveis:

bootstrap.business_required
bootstrap.local_business_link_required
bootstrap.ready
bootstrap.access_denied

Não logar:

JWT
Authorization header
external secrets.

BusinessId/UserId podem seguir política estruturada existente.

---

# 64. DOMAIN EVENTS

Bootstrap não gera domain event por ser read-only.

---

# 65. OUTBOX

Não usar outbox.

---

# 66. MIGRATION

Expectativa M5:

NO NEW FUNCTIONAL MIGRATION.

Se implementação exigir migration:

STOP.

Justificar necessidade.

---

# 67. jOOQ

Bootstrap não acessa jOOQ diretamente.

Somente módulos responsáveis acessam persistence adapters.

---

# 68. TEST MATRIX

## TEST-M5-001 — BUSINESS REQUIRED

User ACTIVE sem Business acessível:

state = BUSINESS_REQUIRED.

## TEST-M5-002 — BUSINESS REQUIRED NO INSTALLATION DATA

BUSINESS_REQUIRED:

não retorna installation.

## TEST-M5-003 — SINGLE BUSINESS RESOLUTION

User com exatamente um Business ACTIVE:

Business pode ser resolvido conforme contrato aprovado.

## TEST-M5-004 — MULTIPLE BUSINESSES PRESERVED

User com múltiplos Businesses:

nenhum Business estrangeiro ou escolha silenciosa indevida.

## TEST-M5-005 — REQUESTED BUSINESS AUTHORIZED

requestedBusinessId pertencente ao User:

seleção autorizada.

## TEST-M5-006 — REQUESTED BUSINESS FOREIGN DENIED

User A solicita Business B de outro User:

DENIED.

## TEST-M5-007 — CLIENT BUSINESS ID NOT AUTHORITY

businessId no payload/request sozinho:

não concede acesso.

## TEST-M5-008 — DISABLED MEMBERSHIP NOT ACCESSIBLE

Membership DISABLED:

Business não é operacionalmente acessível.

## TEST-M5-009 — DISABLED BUSINESS NOT ACCESSIBLE

Business DISABLED:

não gera READY.

## TEST-M5-010 — LOCAL BUSINESS LINK REQUIRED

Business autorizado sem installation ACTIVE:

LOCAL_BUSINESS_LINK_REQUIRED.

## TEST-M5-011 — READY

User + Membership + Business + ACTIVE installation:

READY.

## TEST-M5-012 — READY SAME BUSINESS INSTALLATION

Installation deve pertencer ao Business autorizado.

## TEST-M5-013 — CROSS BUSINESS INSTALLATION DENIED

Installation do Business B não produz READY para Business A.

## TEST-M5-014 — REVOKED INSTALLATION NOT READY

REVOKED:

READY proibido.

## TEST-M5-015 — INSTALLATION ID NOT AUTHORITY

Conhecer installationExternalId:

não concede contexto.

## TEST-M5-016 — STORE ID NOT AUTHORITY

storeId não concede Business/READY.

## TEST-M5-017 — AUTHORIZATION BEFORE TENANT CONTEXT

Provar:

Membership/Business authorization
→ TenantContext
→ installation lookup.

## TEST-M5-018 — RLS SAME TENANT

Installation lookup do Business autorizado funciona.

## TEST-M5-019 — RLS CROSS TENANT

Cross-tenant installation invisível/denied.

## TEST-M5-020 — RLS FAIL CLOSED

Sem tenant:

nenhuma installation tenant-owned acessível.

## TEST-M5-021 — USER DISABLED DENIED

User DISABLED não recebe BootstrapState operacional.

## TEST-M5-022 — API REQUIRES AUTH

Sem JWT:

401.

## TEST-M5-023 — INVALID JWT

401.

## TEST-M5-024 — EXPIRED JWT

401.

## TEST-M5-025 — WRONG ISSUER

401.

## TEST-M5-026 — INVALID AUDIENCE/AZP

401.

## TEST-M5-027 — MISSING SUB

fail closed.

## TEST-M5-028 — NO FOREIGN BUSINESS DISCLOSURE

Response nunca inclui Business estrangeiro.

## TEST-M5-029 — NO FOREIGN INSTALLATION DISCLOSURE

Response nunca inclui installation estrangeira.

## TEST-M5-030 — PRIVACY

Response não contém PII desnecessário.

## TEST-M5-031 — NO JWT CLAIM LEAK

Response não contém JWT claims/tokens.

## TEST-M5-032 — BUSINESS SUMMARY CONTRACT

Resumo contém somente campos aprovados.

## TEST-M5-033 — INSTALLATION SUMMARY CONTRACT

Resumo contém somente campos aprovados.

## TEST-M5-034 — READ ONLY

Bootstrap não cria/atualiza/deleta Business, Membership ou Installation.

## TEST-M5-035 — REPEATED REQUEST STABLE

Requests repetidas com mesmo estado:

resultado semanticamente equivalente.

## TEST-M5-036 — NO BOOTSTRAP TABLE

Schema não cria tabela bootstrap/session/current_business.

## TEST-M5-037 — NO MIGRATION

M5 não adiciona migration funcional sem aprovação.

## TEST-M5-038 — MODULE BOUNDARY IDENTITY

Bootstrap não acessa persistence internals de Identity.

## TEST-M5-039 — MODULE BOUNDARY BUSINESS

Bootstrap não acessa persistence internals de Business.

## TEST-M5-040 — MODULE BOUNDARY DEVICE

Bootstrap não acessa persistence internals de Device.

## TEST-M5-041 — JOOQ BOUNDARY

Bootstrap não importa jOOQ.

## TEST-M5-042 — MODULITH

ApplicationModules.verify() PASS.

## TEST-M5-043 — POSTGRESQL INTEGRATION

Fluxo READY real usa PostgreSQL/Testcontainers.

## TEST-M5-044 — POOL TENANT RESET

Bootstrap não causa tenant leakage entre requests.

## TEST-M5-045 — HTTP BUSINESS REQUIRED

Endpoint retorna estado correto e HTTP 200.

## TEST-M5-046 — HTTP LOCAL BUSINESS LINK REQUIRED

HTTP 200 + estado correto.

## TEST-M5-047 — HTTP READY

HTTP 200 + READY.

## TEST-M5-048 — IDOR BUSINESS

BusinessId conhecido não permite acesso indevido.

## TEST-M5-049 — IDOR INSTALLATION

InstallationExternalId conhecido não permite acesso indevido.

## TEST-M5-050 — NO SYNC DATA

Response não contém cursor/events/changes.

## TEST-M5-051 — NO BUSINESS DATA PAYLOAD

Response não contém produtos/clientes/estoque/crédito.

## TEST-M5-052 — NO SIDE EFFECT EVENT

Bootstrap não cria domain event/outbox por simples leitura.

## TEST-M5-053 — SECRET SCAN

PASS.

## TEST-M5-054 — CLEAN BUILD

./gradlew clean build PASS.

## TEST-M5-055 — NO SCOPE LEAKAGE

Nenhuma implementação funcional:

Sync
Customer
Credit
Ledger
Payment
Pix
Reconciliation
WhatsApp
Notification

PASS.

---

# 69. POSTGRESQL TESTING

Usar PostgreSQL real/Testcontainers para fluxos que dependem de:

- User
- Membership
- Business
- DeviceInstallation
- RLS
- TenantContext

Não substituir gates obrigatórios por H2.

---

# 70. HTTP TESTING

Provar:

BUSINESS_REQUIRED = 200

LOCAL_BUSINESS_LINK_REQUIRED = 200

READY = 200

auth failure = 401

business denial = security contract

IDOR = denied

---

# 71. SECURITY TESTING

Preservar integralmente security chain M2.

Não mockar toda security stack em todos os testes e declarar segurança
provada.

Testes específicos de boundary continuam obrigatórios.

---

# 72. TENANCY TESTING

Provar:

User A
→ Business A
→ installation A
→ READY

User A
→ Business B
→ DENIED

User A
→ Business A
→ installation B
→ DENIED/not READY

---

# 73. NO NEW RLS

M5 não cria policies novas.

Apenas prova reutilização das existentes.

---

# 74. DEPENDENCY AUDIT

Nenhum:

JPA
Hibernate ORM
Redis
Kafka
RabbitMQ
MongoDB

---

# 75. SOURCE AUDIT

Nenhum acesso indevido a:

JooqUserRepository
JooqBusinessRepository
JooqBusinessMembershipRepository
JooqDeviceInstallationRepository

Bootstrap utiliza contratos públicos.

---

# 76. SCHEMA AUDIT

Schema funcional esperado continua:

users
businesses
business_memberships
device_installations

Nenhuma nova tabela funcional M5.

---

# 77. SECRET SAFETY

Aplicar SECURITY-AND-GIT-SAFETY.md.

Secret scan:

- durante implementação;
- antes de commit;
- antes de evidence commit;
- antes de push.

Nenhuma credencial hardcoded.

---

# 78. EVIDENCE

Criar futuramente, somente após AUTHORIZED:

docs/evidence/M5-EVIDENCE.md

Registrar:

- develop base SHA;
- branch;
- BootstrapContext;
- states;
- API;
- Business resolution;
- Device resolution;
- authorization order;
- TenantContext usage;
- read-only proof;
- no-migration proof;
- privacy;
- IDOR;
- TEST-M5-001..055;
- PostgreSQL;
- Testcontainers;
- HTTP security;
- Modulith;
- secret scan;
- dependency audit;
- schema audit;
- scope leakage;
- deviations/blockers.

Nunca registrar secrets.

---

# 79. REQUIRED GATES

Quando autorizado:

./gradlew clean build

architecture

Spring Modulith

JUnit

PostgreSQL Testcontainers

Bootstrap integration

HTTP security

IDOR

cross-business authorization

RLS/TenantContext

pool isolation

secret scan

dependency audit

source boundary audit

schema audit

scope leakage audit

Flyway validate

jOOQ existing pipeline

---

# 80. INDEPENDENT LUNA REVIEW

Luna only.

Separar:

PHASE A — IMPLEMENTATION

STOP EDITING

PHASE B — INDEPENDENT SELF-REVIEW

Na segunda fase:

- revisar diff integral;
- confirmar zero migration funcional;
- revisar API;
- revisar estados;
- revisar authorization ordering;
- revisar tenant boundaries;
- revisar privacy;
- rerodar gates;
- rerodar secret scan;
- revisar evidence.

PASS somente após segunda verificação.

---

# 81. ACCEPTANCE CRITERIA

M5 PASS somente se:

[ ] BUSINESS_REQUIRED correto

[ ] LOCAL_BUSINESS_LINK_REQUIRED correto

[ ] READY correto

[ ] User ACTIVE obrigatório

[ ] Membership ACTIVE obrigatório

[ ] Business ACTIVE obrigatório

[ ] Installation ACTIVE obrigatória para READY

[ ] múltiplos Businesses preservados

[ ] nenhuma escolha silenciosa indevida

[ ] requestedBusinessId validado

[ ] businessId não é autoridade

[ ] installationId não é autoridade

[ ] storeId não é autoridade

[ ] cross-business denied

[ ] cross-installation denied

[ ] authorization antes de TenantContext

[ ] RLS reutilizada corretamente

[ ] tenant fail-closed

[ ] pool sem leakage

[ ] endpoint autenticado

[ ] JWT security M2 preservada

[ ] IDOR Business PASS

[ ] IDOR Installation PASS

[ ] privacy PASS

[ ] sem token leakage

[ ] read-only PASS

[ ] nenhuma nova tabela

[ ] nenhuma migration funcional

[ ] nenhum Sync data

[ ] nenhum domain data indevido

[ ] contracts públicos respeitados

[ ] Modulith PASS

[ ] PostgreSQL PASS

[ ] secret scan PASS

[ ] clean build PASS

[ ] TEST-M5-001..055 PASS

[ ] Scope Leakage NONE

[ ] M5-EVIDENCE presente

---

# 82. STOP CONDITIONS

M5 = BLOCKED se parecer necessário:

- adicionar novo BootstrapState;
- persistir current Business;
- criar server-side session;
- adicionar Redis;
- criar tabela bootstrap;
- criar migration funcional;
- implementar Sync;
- retornar cursor;
- implementar Customer;
- implementar Credit;
- implementar Payment/Pix;
- transformar Device em autoridade;
- transformar storeId em autoridade;
- alterar Membership model;
- alterar tenancy architecture.

Retornar:

M5 STATUS: BLOCKED
ARCHITECTURAL DECISION REQUIRED
HUMAN AUTHORIZATION REQUIRED

---

# 83. DOCUMENTATION WORKFLOW

Nesta etapa:

M5 permanece PROPOSED.

Luna deve:

1. persistir:

docs/milestones/SDD-M5-BOOTSTRAP-CONTEXT.md

2. revisar integralmente contra source of truth;

3. verificar especialmente possível gap de múltiplos Businesses;

4. revisar TEST-M5-001..055;

5. criar:

docs/m5-bootstrap-context-spec

6. secret scan;

7. commit/push documental;

8. criar PR:

docs/m5-bootstrap-context-spec
→ develop

9. aguardar checks;

10. não mergear sem autorização humana.

---

# 84. DOCUMENTATION CHECKPOINT

Quando PR documental estiver verde:

retornar:

DOCUMENTATION READY FOR DEVELOP

e STOP.

Merge documental NÃO autoriza implementação.

---

# 85. IMPLEMENTATION WORKFLOW

Somente após:

- spec integrada em develop;
- autorização explícita M5.

Criar:

feature/m5-bootstrap-context

a partir de develop.

---

# 86. FEATURE PROMOTION

Após M5 PASS:

feature/m5-bootstrap-context
→ develop

Luna cria PR via gh.

Quando verde:

FEATURE READY FOR DEVELOP

STOP.

Merge exige autorização humana.

---

# 87. STAGING

Develop → staging:

NOT AUTHORIZED.

---

# 88. MAIN

Não tocar main.

---

# 89. M6

M6:

NOT AUTHORIZED.

M6 previsto:

SYNC PUSH / EVENT INGESTION

Nenhuma implementação M6.

---

# 90. EXPECTED FINAL ARCHITECTURE

Após M5:

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
DeviceInstallation
 ↓
BootstrapContext
 ↓
BUSINESS_REQUIRED
or
LOCAL_BUSINESS_LINK_REQUIRED
or
READY

O backend passa a saber:

"qual é o estado inicial operacional deste usuário/instalação?"

Ainda não faz:

"sincronize meus eventos locais."

Isso começa no M6.

---

# 91. FINAL DOCUMENTATION REPORT

MILESTONE:
M5 — BOOTSTRAP & INSTALLATION CONTEXT

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

Device Alignment:
PASS | CONFLICT

Tenancy Alignment:
PASS | CONFLICT

Security Alignment:
PASS | CONFLICT

Privacy Alignment:
PASS | CONFLICT

Bootstrap State Model:
PASS | CONFLICT

Test Matrix:
PASS | FAIL

Implementation:
NOT STARTED

Scope Leakage:
NONE

Changed Documents:
docs/milestones/SDD-M5-BOOTSTRAP-CONTEXT.md

Conflicts:
NONE | <description>

Develop -> staging:
NOT AUTHORIZED

M6 AUTHORIZED:
NO

STOP.
