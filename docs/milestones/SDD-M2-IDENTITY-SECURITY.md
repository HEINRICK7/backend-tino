# SDD-M2 — IDENTITY & SECURITY FOUNDATION

## Status

AUTHORIZED

Este milestone somente poderá ser executado após autorização explícita do usuário.

M3 is NOT authorized.

---

# 1. GOAL

Construir e provar a fundação de identidade autenticada do TINO Backend.

M2 estabelece a ponte segura entre:

Keycloak / OIDC
        ↓
JWT validado
        ↓
Spring Security
        ↓
AuthenticatedPrincipal
        ↓
Identity Application
        ↓
User interno do TINO
        ↓
PostgreSQL

Ao final do M2, o backend deve conseguir:

- validar autenticação OIDC/JWT;
- extrair uma identidade externa confiável;
- representar essa identidade sem vazar Spring Security para o domínio;
- resolver/criar idempotentemente um User interno;
- persistir User em PostgreSQL via jOOQ;
- impedir duplicação do mesmo usuário externo;
- suportar concorrência no primeiro acesso;
- preservar minimização de dados;
- manter boundaries hexagonais;
- continuar compatível com a arquitetura aprovada.

M2 NÃO implementa Business.

M2 NÃO implementa Membership.

M2 NÃO estabelece tenant.

M2 NÃO implementa Device.

M2 NÃO implementa Bootstrap.

---

# 2. DEPENDENCIES

M2 depende obrigatoriamente de:

M0 — PROJECT FOUNDATION
STATUS: PASS

M1 — DATABASE FOUNDATION
STATUS: PASS

M1 deve estar integrado conforme processo aprovado antes da implementação funcional do M2.

Se o estado real do repositório divergir:

STOP.

Não reconstruir ou contornar M1 dentro do M2.

---

# 3. ARCHITECTURAL REFERENCES

Antes de qualquer edição, ler integralmente:

- Master Execution Directive, se persistido no repositório;
- EXECUTION-PROTOCOL.md;
- ARCHITECTURE-BASELINE.md;
- ADRs relacionados a:
  - modular monolith;
  - hexagonal architecture;
  - PostgreSQL;
  - Flyway;
  - jOOQ;
  - OAuth2/OIDC;
  - Keycloak;
  - Spring Security;
  - UUID v7;
- System Specs relacionados a:
  - identity;
  - authentication;
  - security;
  - tenancy;
- M0-EVIDENCE.md;
- M1-EVIDENCE.md;
- este SDD.

Se algum documento aplicável contradizer este milestone:

STOP.

Retornar:

M2 STATUS: BLOCKED
SPECIFICATION CONFLICT

Não escolher silenciosamente qual documento ignorar.

---

# 4. SOURCE OF TRUTH

Para autenticação:

Keycloak / OIDC identity token
        ↓
JWT `sub`

é a identidade externa autoritativa.

Para identidade interna:

PostgreSQL
        ↓
users

é a fonte de verdade do User TINO.

Não usar como identidade autoritativa:

- email;
- telefone;
- nome;
- username apresentado pelo cliente;
- store_id;
- device_id;
- business_id recebido em payload;
- headers arbitrários;
- identificadores gerados pelo Android.

---

# 5. CORE IDENTITY MODEL

Modelo mínimo:

User

Campos obrigatórios:

- id
- externalSubject
- status
- createdAt
- updatedAt

Conceitualmente:

User
├── UserId
├── ExternalSubject
├── UserStatus
├── createdAt
└── updatedAt

---

# 6. USER ID

User.id:

Java:
UUID

PostgreSQL:
UUID

Geração:

UUID v7

Utilizar a foundation criada/provada no M1.

Não criar segundo mecanismo independente de geração de UUID.

Não usar:

SERIAL
BIGSERIAL
UUID v4

como padrão para User.

---

# 7. EXTERNAL SUBJECT

externalSubject representa:

JWT claim:

sub

Exemplo:

Keycloak:

sub = "0c4d..."

TINO:

users.external_subject = "0c4d..."

Requisitos:

- obrigatório;
- não vazio;
- tratado como opaque identifier;
- único globalmente no identity provider configurado para esta versão;
- nunca derivado de email/nome/telefone.

O código NÃO deve assumir formato UUID para `sub`.

`sub` é String opaca.

---

# 8. USER STATUS

Criar enum de domínio pequeno e explícito.

Inicialmente:

ACTIVE
DISABLED

Persistência:

VARCHAR + CHECK

Não usar PostgreSQL ENUM.

Novo usuário nasce:

ACTIVE

M2 não implementa fluxo administrativo para alterar status.

A existência de DISABLED estabelece o contrato necessário para impedir uso futuro de usuário desabilitado sem alterar o modelo.

---

# 9. PRIVACY / DATA MINIMIZATION

M2 aplica minimização de dados.

NÃO persistir automaticamente claims como:

- email;
- email_verified;
- given_name;
- family_name;
- name;
- preferred_username;
- picture;
- locale;
- phone_number;
- address.

Persistir somente o necessário para identidade interna:

- id;
- external_subject;
- status;
- timestamps.

Se futuramente algum dado adicional for necessário:

novo requisito
→ nova especificação
→ nova migration.

---

# 10. NO PASSWORD STORAGE

O TINO Backend NÃO armazena:

- senha;
- password hash;
- refresh token;
- access token;
- ID token completo;
- client secret por usuário.

Autenticação pertence ao Identity Provider.

M2 NÃO implementa sistema próprio de senha.

---

# 11. AUTHENTICATION BOUNDARY

Spring Security permanece adapter de entrada.

Fluxo:

HTTP Request
    ↓
Bearer JWT
    ↓
Spring Security Resource Server
    ↓
validated Jwt
    ↓
Security Adapter
    ↓
AuthenticatedPrincipal
    ↓
Application

Domain NÃO conhece:

Jwt
Authentication
SecurityContext
OAuth2AuthenticationToken
JwtAuthenticationToken
Keycloak classes
Spring Security classes.

---

# 12. AUTHENTICATED PRINCIPAL

Criar representação interna pequena e framework-independent.

Conceitualmente:

AuthenticatedPrincipal

campos mínimos:

externalSubject

Não transportar JWT completo.

Não transportar claims desnecessários.

Não transformar AuthenticatedPrincipal em objeto genérico de sessão.

---

# 13. PRINCIPAL BOUNDARY

INV-IDENTITY-001

Nenhum tipo de Spring Security ou Keycloak pode aparecer em:

- domain;
- application domain contracts;
- persistence ports;
- entities;
- value objects.

Adapter Spring:

Jwt
 ↓
AuthenticatedPrincipal

A partir desse ponto:

Spring Security desaparece da aplicação.

---

# 14. EXTERNAL SUBJECT INVARIANT

INV-IDENTITY-002

Para um mesmo externalSubject existe no máximo um User interno.

Formalmente:

externalSubject X
→ exactly one users row

Garantir através de:

1. application semantics;
2. PostgreSQL UNIQUE constraint;
3. tratamento correto de concorrência.

Não depender apenas de:

SELECT before INSERT.

---

# 15. AUTHENTICATED USER RESOLUTION

Criar use case equivalente a:

ResolveAuthenticatedUser

Input:

AuthenticatedPrincipal

Output:

User / AuthenticatedUser adequado ao contrato da aplicação.

Comportamento:

1. validar principal;
2. extrair externalSubject;
3. buscar User;
4. se existir:
   retornar User;
5. se não existir:
   criar User ACTIVE;
6. se ocorrer criação concorrente:
   resolver deterministicamente o User já criado;
7. retornar exatamente um User.

---

# 16. IDEMPOTENCY

INV-IDENTITY-003

Resolver repetidamente:

externalSubject = X

deve produzir:

mesmo User.id

e nunca duplicação.

Exemplo:

request 1 → X → User A
request 2 → X → User A
request 3 → X → User A

Nunca:

X → User A
X → User B

---

# 17. CONCURRENT FIRST ACCESS

Concorrência no primeiro acesso é requisito obrigatório.

Cenário:

20 requests simultâneos
        ↓
mesmo externalSubject
        ↓
User ainda inexistente

Resultado obrigatório:

1 users row
1 User.id
20 resoluções bem-sucedidas para o mesmo User

Não usar lock global JVM.

Não depender de single-instance deployment.

A proteção deve continuar correta com múltiplas instâncias futuras.

PostgreSQL UNIQUE constraint é a autoridade final contra duplicação.

Implementação deve tratar race de maneira explícita.

---

# 18. CONCURRENCY STRATEGY

Estratégia preferencial:

attempt resolve
     ↓
attempt insert
     ↓
UNIQUE conflict if concurrent creator won
     ↓
resolve existing
     ↓
return same User

Pode ser utilizado mecanismo PostgreSQL/jOOQ equivalente se:

- permanecer explícito;
- preservar port boundary;
- não vazar SQL/jOOQ para application/domain;
- tiver teste concorrente real.

Não adicionar distributed lock.

Não adicionar Redis.

---

# 19. DISABLED USER

INV-IDENTITY-004

User com:

status = DISABLED

não pode ser tratado como identidade ativa.

O use case de resolução deve retornar erro de aplicação apropriado ou resultado explicitamente não autorizado.

Não retornar DISABLED como User ativo silenciosamente.

M2 não implementa endpoint para desabilitar User.

Teste pode preparar estado diretamente pela fixture/persistence setup.

---

# 20. DATABASE TABLE

Criar migration real:

users

Schema conceitual:

id UUID PRIMARY KEY

external_subject VARCHAR(...) NOT NULL

status VARCHAR(...) NOT NULL

created_at TIMESTAMPTZ NOT NULL

updated_at TIMESTAMPTZ NOT NULL

Constraints obrigatórias:

PRIMARY KEY (id)

UNIQUE (external_subject)

CHECK status IN (
  'ACTIVE',
  'DISABLED'
)

Não adicionar campos especulativos.

---

# 21. TIMESTAMPS

Usar:

PostgreSQL:
TIMESTAMPTZ

Java:
Instant

UTC.

created_at:

imutável após criação.

updated_at:

representa última alteração persistida do User.

Não usar LocalDateTime para timestamps persistidos desta entidade.

---

# 22. USERS AND RLS

A tabela:

users

NÃO é tenant-owned.

Portanto:

users NÃO recebe business_id.

M2 NÃO aplica Business RLS a users.

Isso é intencional.

User poderá futuramente possuir Membership em múltiplos Businesses.

Não inventar tenant durante M2.

---

# 23. DATABASE ACCESS

Runtime:

tino_app

Migration:

tino_migrator

Preservar separação comprovada no M1.

Flyway:

tino_migrator

Application/jOOQ runtime:

tino_app

Não conceder privilégios extras ao tino_app apenas para facilitar implementação.

---

# 24. FLYWAY

Criar nova migration versionada.

Não editar migrations publicadas de M0/M1.

Migration deve funcionar:

empty database
      ↓
M0
      ↓
M1
      ↓
M2
      ↓
current schema

Flyway validate deve passar.

---

# 25. jOOQ

Após migration:

regenerar jOOQ conforme pipeline oficial.

Generated:

users records/table metadata

Generated types permanecem em infrastructure/persistence boundary.

PROIBIDO retornar generated record para application/domain.

---

# 26. USER REPOSITORY PORT

Criar port de saída mínimo.

Conceitualmente:

UserRepository

operações necessárias apenas ao M2.

Exemplo:

findByExternalSubject(...)

create(...)

findById(...) somente se realmente necessário ao use case/teste.

Não criar CRUD genérico.

Não adicionar métodos futuros especulativos.

---

# 27. PERSISTENCE ADAPTER

Criar adapter jOOQ.

Responsabilidades:

- map domain ↔ persistence;
- executar queries;
- preservar constraints;
- traduzir violações esperadas necessárias ao use case;
- não expor jOOQ.

Não colocar regra de autenticação dentro do repository.

---

# 28. HEXAGONAL BOUNDARY

Fluxo obrigatório:

Security Adapter
      ↓
AuthenticatedPrincipal
      ↓
Application Use Case
      ↓
UserRepository Port
      ↓
jOOQ Adapter
      ↓
PostgreSQL

Dependências apontam para dentro.

---

# 29. MODULE BOUNDARY

Identity deve permanecer módulo funcional isolado.

Nenhum outro módulo funcional deve acessar:

identity internal packages.

Futuras integrações utilizarão API pública do módulo.

Spring Modulith verification deve permanecer PASS.

---

# 30. PUBLIC IDENTITY API

Se for necessário expor uma API interna entre módulos, ela deve ser mínima.

Exemplo conceitual:

IdentityApplicationApi

Não expor:

- repository;
- jOOQ;
- Jwt;
- Spring Security;
- persistence records.

M2 não deve criar API pública especulativa se não houver consumidor real.

---

# 31. HTTP ENDPOINT POLICY

M2 NÃO precisa criar endpoint funcional apenas para provar autenticação.

Não criar automaticamente:

POST /login
POST /logout
POST /register
GET /me

Login/register pertencem ao IdP ou a milestone explicitamente responsável pelo onboarding.

A autenticação pode ser comprovada por testes do Resource Server/security adapter.

Se algum endpoint funcional for considerado indispensável:

STOP.

Justificar antes de ampliar escopo.

---

# 32. KEYCLOAK RESPONSIBILITY

Keycloak é responsável por:

- autenticação;
- emissão de tokens;
- validação de credenciais;
- identidade OIDC externa.

TINO é responsável por:

- User interno;
- status interno;
- relacionamento futuro com Business;
- autorização de domínio futura.

Não duplicar Keycloak dentro do TINO.

---

# 33. JWT VALIDATION

Spring Security Resource Server deve validar JWT conforme configuração OIDC aprovada.

A validação deve verificar, conforme o contrato OIDC configurado para o cliente TINO:

- assinatura;
- expiração;
- issuer;
- audience e/ou authorized party;
- subject obrigatório (`sub`).

Assinatura válida isoladamente NÃO torna o token aceitável.

Não aceitar token apenas por decodificar payload.

Token inválido:

401

Token expirado:

401

Token com assinatura inválida:

401

Token ausente em recurso protegido:

401

M2 não deve implementar parser JWT manual.

---

# 34. JWT SUBJECT

Token autenticado sem claim:

sub

não pode produzir AuthenticatedPrincipal válido.

Fail closed.

Não gerar subject substituto.

Não usar email como fallback.

Não usar username como fallback.

---

# 35. ISSUER

Preservar issuer validation configurada pelo Resource Server.

Não desabilitar issuer validation para facilitar testes.

Testes podem utilizar infraestrutura adequada de security testing sem enfraquecer configuração de produção.

---

# 36. AUTHORIZATION

M2 implementa foundation de identidade, NÃO autorização por Business.

Ainda não existem:

Membership
BusinessRole
BusinessPermission

Portanto não inventar:

OWNER
ADMIN
EMPLOYEE

neste milestone.

Autenticação:

M2

Autorização de Business:

milestone posterior.

---

# 37. TENANT CONTEXT

M1 criou foundation técnica de tenant context.

M2 NÃO resolve BusinessId.

Portanto:

User authentication
≠
Tenant resolution

Não executar:

JWT sub
→ business_id

diretamente.

O futuro fluxo será:

JWT
 ↓
User
 ↓
Membership
 ↓
Business
 ↓
TenantContext

M2 termina em:

User.

---

# 38. STORE ID

store_id Android NÃO participa da identidade M2.

Nunca:

store_id → User
store_id → tenant
store_id → authorization

StoreId permanece compatibilidade local a ser tratada em milestone apropriado.

---

# 39. DEVICE

Device não pertence ao M2.

Não implementar:

- device registration;
- device linking;
- device authorization;
- installation identity;
- push token;
- device secret.

---

# 40. ERROR MODEL

Criar erros de aplicação explícitos somente quando necessários.

Exemplos conceituais:

InvalidAuthenticatedPrincipal
DisabledUser

Não vazar:

DataAccessException
jOOQ exception
SQLException
Spring Security internals

para domain/public application API.

Conflito UNIQUE esperado durante concorrência deve ser tratado internamente.

---

# 41. TRANSACTIONS

User resolution/create deve possuir boundary transacional adequado.

Não abrir transação durante validação remota externa.

JWT já chega validado pelo Resource Server antes do use case.

Transações devem ser curtas.

Default:

READ COMMITTED

Preservar baseline.

---

# 42. NO EXTERNAL CALL IN DB TRANSACTION

M2 não deve chamar Keycloak Admin API dentro da transação de User.

Fluxo:

JWT já validado
    ↓
principal
    ↓
database transaction
    ↓
resolve/create User

Nenhuma chamada HTTP externa é necessária.

---

# 43. OBSERVABILITY

Pode registrar eventos técnicos como:

identity.user.resolved
identity.user.created
identity.user.disabled_rejected

Sem logar:

- JWT completo;
- Authorization header;
- access token;
- refresh token;
- client secret.

externalSubject não deve ser usado indiscriminadamente em logs de texto.

Preferir identificador interno UserId quando disponível.

---

# 44. AUDIT

M2 NÃO cria sistema completo de audit trail.

Não antecipar tabela de auditoria sem milestone correspondente.

Logs técnicos não substituem audit trail futuro.

---

# 45. REQUIRED TESTS

## TEST-M2-001 — VALID JWT PRINCIPAL MAPPING

Given:

JWT autenticado e validado com:

sub = X

When:

security adapter cria principal interno

Then:

AuthenticatedPrincipal.externalSubject = X

e nenhum tipo Spring vaza para application/domain.

---

## TEST-M2-002 — MISSING SUBJECT FAIL CLOSED

Given:

JWT autenticado sem `sub`

When:

adapter tenta construir principal

Then:

principal válido NÃO é produzido.

Não usar fallback.

---

## TEST-M2-003 — INVALID TOKEN REJECTED

Given:

token inválido

When:

recurso protegido é acessado no security test

Then:

HTTP 401.

Nenhum use case de identity é executado.

---

## TEST-M2-004 — EXPIRED TOKEN REJECTED

Given:

token expirado

Then:

HTTP 401.

---

## TEST-M2-005 — WRONG ISSUER REJECTED

Given:

JWT criptograficamente válido, porém emitido por issuer diferente do issuer configurado para o TINO.

When:

um recurso protegido é acessado.

Then:

HTTP 401.

Nenhum use case de Identity deve ser executado.

---

## TEST-M2-006 — INVALID AUDIENCE / AUTHORIZED PARTY REJECTED

Given:

JWT criptograficamente válido cujo audience e/ou authorized party não satisfaz o contrato OIDC configurado para o cliente TINO.

When:

um recurso protegido é acessado.

Then:

HTTP 401.

Nenhum use case de Identity deve ser executado.

---

## TEST-M2-007 — SAME SUBJECT SAME USER

Given:

externalSubject X

When:

ResolveAuthenticatedUser é executado repetidamente

Then:

mesmo User.id é retornado.

A tabela contém somente uma row para X.

---

## TEST-M2-008 — DIFFERENT SUBJECT DIFFERENT USER

Given:

externalSubject X
externalSubject Y

Then:

User X != User Y

e existem duas rows distintas.

---

## TEST-M2-009 — DATABASE UNIQUE SUBJECT

Attempt:

persistir dois Users com mesmo external_subject

Then:

PostgreSQL UNIQUE impede duplicação.

Constraint deve existir fisicamente no PostgreSQL.

---

## TEST-M2-010 — CONCURRENT FIRST ACCESS

Given:

User X ainda não existe.

When:

múltiplas execuções concorrentes resolvem X.

Preferência:

20 operações concorrentes.

Then:

todas resolvem com sucesso.

Todos recebem:

mesmo User.id.

Database:

COUNT(users WHERE external_subject = X) = 1.

Teste deve usar PostgreSQL real.

---

## TEST-M2-011 — DISABLED USER REJECTED

Given:

User X
status = DISABLED

When:

ResolveAuthenticatedUser(X)

Then:

não retorna User ativo.

Resultado deve representar explicitamente usuário desabilitado.

---

## TEST-M2-012 — USER DEFAULT STATUS

Given:

externalSubject novo

When:

User é criado

Then:

status = ACTIVE.

---

## TEST-M2-013 — UUID V7 USER ID

Novo User:

id.version == 7

e utiliza generator foundation aprovada.

---

## TEST-M2-014 — TIMESTAMP CONTRACT

Novo User:

createdAt != null
updatedAt != null

TIMESTAMPTZ round-trip preserva Instant corretamente.

---

## TEST-M2-015 — MIGRATION FROM ZERO

Given:

PostgreSQL vazio

When:

Flyway executa todas migrations até M2

Then:

PASS.

users existe com schema esperado.

---

## TEST-M2-016 — FLYWAY VALIDATE

Após migrations:

Flyway validate

PASS.

Nenhuma migration anterior alterada.

---

## TEST-M2-017 — JOOQ REAL POSTGRESQL

JooqUserRepository opera contra PostgreSQL Testcontainers real.

PASS.

Não usar H2/mock DB.

---

## TEST-M2-018 — JOOQ BOUNDARY

Nenhum tipo jOOQ em:

identity domain
identity application public contracts

PASS.

---

## TEST-M2-019 — SPRING SECURITY BOUNDARY

Nenhum import Spring Security em:

identity domain

e nenhum framework principal vazando para contratos de domínio.

PASS.

---

## TEST-M2-020 — KEYCLOAK CLASS BOUNDARY

Nenhuma dependência direta de classes Keycloak em domain/application.

PASS.

OIDC deve permanecer através de standards/Spring adapter.

---

## TEST-M2-021 — USER NOT TENANT OWNED

users:

não possui business_id.

Nenhuma RLS Business é aplicada à users.

PASS.

---

## TEST-M2-022 — APP DATABASE ROLE

Operações runtime de User usam:

tino_app

e não:

tino_migrator.

Migration continua usando:

tino_migrator.

PASS.

---

## TEST-M2-023 — PRIVACY MINIMIZATION

Schema users NÃO contém:

email
name
phone
username
password
access_token
refresh_token

PASS.

---

## TEST-M2-024 — NO PASSWORD/TOKEN PERSISTENCE

Source/schema audit confirma ausência de persistência de:

password
password_hash
JWT
access token
refresh token

PASS.

---

## TEST-M2-025 — MODULITH

ApplicationModules.verify()

PASS.

Identity respeita boundaries.

---

## TEST-M2-026 — CLEAN BUILD

./gradlew clean build

PASS.

---

## TEST-M2-027 — NO SCOPE LEAKAGE

Auditoria confirma ausência de implementação funcional de:

Business
Membership
BusinessRole
Device
Bootstrap
Customer
Credit
Payment
Pix
Reconciliation
Sync

PASS.

---

# 46. SECURITY INTEGRATION TESTS

Testes de autenticação devem provar comportamento real do Spring Security boundary.

Não é obrigatório iniciar Keycloak real em todos os testes unitários.

Entretanto:

- Resource Server configuration deve ser testável;
- token inválido, expirado, com issuer incorreto ou com audience/authorized party inválido deve ser rejeitado;
- nenhum teste pode desabilitar security globalmente apenas para passar.

Quando mock JWT for usado em teste de adapter/application:

isso NÃO substitui os testes de security boundary exigidos.

---

# 47. POSTGRESQL TESTING

Persistence/concurrency tests usam:

PostgreSQL Testcontainers.

Não substituir por:

H2
SQLite
mock repository

para testes obrigatórios de:

- UNIQUE;
- concorrência;
- Flyway;
- jOOQ;
- timestamps.

---

# 48. CONCURRENCY TEST VALIDITY

TEST-M2-010 somente é válido se realmente houver concorrência.

Não fazer:

for loop sequencial
→ chamar 20 vezes

e chamar isso de concorrência.

Usar:

threads
executor
futures
ou mecanismo equivalente.

O teste deve começar operações suficientemente próximas para exercitar a race.

---

# 49. DATABASE CONSTRAINTS

users deve possuir constraints reais.

Não depender somente de Bean Validation.

Database permanece responsável por integridade estrutural.

---

# 50. APPLICATION VALIDATION

Application também deve rejeitar:

null externalSubject
blank externalSubject

antes de persistência quando aplicável.

Database constraint não substitui validação de boundary.

---

# 51. DEPENDENCY POLICY

Não introduzir:

- JPA;
- Hibernate ORM;
- Spring Data JPA;
- Redis;
- Kafka;
- RabbitMQ;
- MongoDB;
- novo IdP;
- Keycloak Admin Client sem necessidade aprovada;
- distributed lock library.

Dependência nova somente se estritamente necessária ao M2 e compatível com Architecture Baseline.

Se uma nova dependência estrutural parecer necessária:

STOP.

ARCHITECTURAL DECISION REQUIRED.

---

# 52. NO GENERIC REPOSITORY

Não criar:

Repository<T, ID>

ou abstração CRUD global.

Criar port específico para necessidade real do Identity.

---

# 53. NO BASE ENTITY

Não criar:

BaseEntity
AbstractEntity
AuditableEntity

apenas por antecipação.

User deve permanecer explícito.

---

# 54. NO PREMATURE SHARED ABSTRACTIONS

Não mover código para shared apenas porque:

"outros módulos podem precisar depois."

Shared permanece pequeno.

Só reutilizar foundation comprovadamente existente como UUID.

---

# 55. MIGRATION IMMUTABILITY

Não modificar migration M1 publicada.

M2 adiciona migration nova.

Se migration anterior estiver incorreta:

STOP.

Não reescrever histórico silenciosamente.

---

# 56. SCHEMA EXPECTATION

Ao final do M2, domínio funcional persistido novo deve ser somente:

users

Não devem aparecer tabelas funcionais de:

business
membership
device
customer
credit
payment
sync

---

# 57. PERFORMANCE

M2 não exige cache.

User lookup por external_subject deve possuir suporte eficiente através da UNIQUE constraint/index correspondente.

Não adicionar Redis.

Não adicionar cache in-memory global.

---

# 58. SCALABILITY

A implementação deve funcionar corretamente com múltiplas instâncias do backend.

Por isso:

- não usar JVM global lock para identidade;
- não depender de singleton process-local para idempotência;
- usar PostgreSQL constraint para integridade concorrente.

---

# 59. FAILURE BEHAVIOR

Database indisponível durante User resolution:

não inventar User local.

não retornar sucesso falso.

Falha deve propagar como erro técnico adequado conforme error handling do backend.

Não implementar fallback em memória.

---

# 60. IDEMPOTENCY VS AUTHENTICATION

JWT válido não significa necessariamente novo User.

ResolveAuthenticatedUser deve ser idempotente.

Não criar User a cada login/request.

---

# 61. USER CREATION SEMANTICS

M2 adota:

Just-In-Time internal user provisioning.

Primeiro acesso autenticado válido:

JWT sub X
    ↓
User X inexistente
    ↓
criar User X ACTIVE
    ↓
retornar User X

A criação é interna ao TINO.

Isso NÃO significa criar conta/senha no Keycloak.

---

# 62. JIT PROVISIONING BOUNDARY

Somente principal já autenticado pode disparar provisioning.

Não aceitar endpoint público como:

POST /users
{
  "externalSubject": "..."
}

para criar identidade arbitrária.

---

# 63. API ATTACK SURFACE

M2 deve minimizar superfície HTTP.

Nenhum CRUD público de User.

Nenhuma listagem de Users.

Nenhuma busca de User por externalSubject via HTTP.

Nenhuma alteração de status via endpoint.

---

# 64. FUTURE BUSINESS RELATIONSHIP

Não implementar agora, mas preservar o modelo:

User
  ↓
Membership
  ↓
Business

Nunca assumir:

User == Business

ou:

User possui exatamente um Business.

Um User poderá participar de múltiplos Businesses futuramente.

---

# 65. FUTURE AUTHORIZATION

M2 não decide autorização.

Futuro:

Authenticated User
        ↓
Membership
        ↓
Business
        ↓
Role/Permissions
        ↓
Tenant Context

Não antecipar essa implementação.

---

# 66. REQUIRED PROJECT STRUCTURE

Adaptar à estrutura real existente, preservando o princípio:

modules/identity

domain/
  User
  UserId
  ExternalSubject
  UserStatus

application/
  use case
  ports

adapter/in/security/
  Spring Security mapping

adapter/out/persistence/
  jOOQ implementation

Nomes exatos podem seguir convenções existentes.

Não reorganizar o projeto inteiro durante M2.

---

# 67. GIT WORKFLOW

Antes de qualquer implementação:

git status
git branch --show-current
git log --oneline --decorate

M1 deve estar integrado conforme estado aprovado.

Criar branch:

sdd/m2-identity-security

Não implementar M2 diretamente em main.

Não reutilizar branch M1 como branch de desenvolvimento M2.

---

# 68. MODEL EXECUTION POLICY

Política do projeto:

Primary supervision:
Terra

Delegated implementation:
Luna

Sol:
somente escalonamento arquitetural explicitamente autorizado.

SELECTIVE ROUTE esperada:

delegate

Terra:

- reconstrói estado;
- supervisiona;
- delega;
- revisa diff;
- reroda gates críticos;
- decide PASS/BLOCKED/FAIL.

Luna:

- implementa;
- testa;
- corrige dentro do SDD;
- produz evidence.

Terra não deve duplicar implementação enquanto Luna trabalha.

Sol não deve ser invocado automaticamente.

---

# 69. ARCHITECTURAL ESCALATION

Se durante M2 surgir necessidade de:

- mudar IdP;
- alterar Keycloak/OIDC strategy;
- persistir tokens;
- alterar tenancy;
- introduzir Business;
- introduzir Membership;
- usar JPA;
- mudar jOOQ boundary;
- alterar UUID strategy;
- adicionar Redis;
- adicionar distributed lock;
- mudar modelo User;
- adicionar claims pessoais ao banco;
- criar API de login própria;

STOP.

Retornar:

M2 STATUS: BLOCKED

ARCHITECTURAL DECISION REQUIRED

Context:
...

Requirement:
...

Problem:
...

Options:
...

Trade-offs:
...

Não implementar solução antes da decisão humana.

---

# 70. REQUIRED GATES

Executar obrigatoriamente:

./gradlew clean build

Spring Modulith verification

Flyway migration from empty PostgreSQL

Flyway validate

jOOQ generation

JUnit 5

PostgreSQL Testcontainers integration tests

identity persistence tests

concurrent first-access test

Spring Security boundary tests

dependency audit

source boundary audit

schema audit

privacy audit

scope leakage audit

---

# 71. DEPENDENCY AUDIT

Confirmar ausência funcional de:

hibernate-core
jakarta.persistence
spring-data-jpa
spring-data-redis
lettuce
jedis
kafka-clients
spring-kafka
rabbitmq
spring-amqp

Hibernate Validator isoladamente:

permitido como Bean Validation provider.

Não confundir Hibernate Validator com Hibernate ORM.

---

# 72. SOURCE BOUNDARY AUDIT

Confirmar que:

domain não importa jOOQ.

domain não importa Spring Security.

domain não importa Keycloak.

application domain contracts não expõem jOOQ records.

persistence adapter não vaza para API pública.

---

# 73. SCHEMA AUDIT

Confirmar:

users existe.

users não possui business_id.

users possui UNIQUE external_subject.

users possui CHECK status.

users usa TIMESTAMPTZ.

users não contém dados pessoais desnecessários.

Nenhuma tabela M3+ existe.

---

# 74. SECRET AUDIT

Antes do commit procurar:

- .env;
- passwords reais;
- tokens;
- JWTs reais;
- client secrets;
- private keys;
- certificates privados;
- production credentials.

Não commitar secrets.

Fixtures de teste devem usar valores claramente descartáveis.

---

# 75. SCOPE LEAKAGE AUDIT

Antes de PASS procurar explicitamente por implementação de:

businesses
business_memberships
business_profiles
business roles
devices
device linking
bootstrap
customers
credit
ledger
payments
pix
reconciliation
sync events
sync changes
WhatsApp

Nenhum desses pertence ao M2.

---

# 76. EVIDENCE

Criar:

docs/evidence/M2-EVIDENCE.md

Evidence deve registrar no mínimo:

- milestone;
- status;
- branch;
- base commit;
- implementation commit;
- final evidence commit;
- files changed;
- migration criada;
- schema users;
- constraints;
- Identity domain model;
- AuthenticatedPrincipal boundary;
- security adapter;
- UserRepository port;
- jOOQ adapter;
- JIT provisioning strategy;
- concurrency strategy;
- PostgreSQL version;
- Flyway version;
- jOOQ version;
- Testcontainers version;
- Keycloak/OIDC configuration relevante;
- todos TEST-M2-001..027;
- issuer validation proof;
- audience / authorized party validation proof;
- comandos executados;
- resultados;
- clean build;
- Modulith;
- Flyway;
- jOOQ;
- security tests;
- concurrency test;
- dependency audit;
- privacy audit;
- schema audit;
- scope leakage audit;
- deviations;
- blockers;
- supervisão independente Terra;
- final status.

Evidence deve ser factual.

Não declarar PASS apenas porque Luna declarou PASS.

Terra deve verificar independentemente gates críticos.

---

# 77. INDEPENDENT SUPERVISION

Após Luna entregar:

Terra deve:

1. inspecionar todo diff;
2. verificar migration;
3. verificar boundaries;
4. verificar concorrência;
5. verificar que token não é persistido;
6. verificar ausência de Business/Membership;
7. rerodar clean build;
8. rerodar testes críticos;
9. rerodar Flyway;
10. rerodar jOOQ;
11. executar dependency audit;
12. executar scope audit;
13. revisar Evidence.

Somente então:

PASS.

---

# 78. ACCEPTANCE CRITERIA

M2 somente recebe PASS se TODOS forem verdadeiros:

[ ] M1 está corretamente integrado/baseado

[ ] users migration criada

[ ] User usa UUID v7

[ ] external_subject obrigatório

[ ] external_subject UNIQUE

[ ] status CHECK válido

[ ] timestamps TIMESTAMPTZ

[ ] User não possui business_id

[ ] nenhuma Business RLS aplicada a User

[ ] AuthenticatedPrincipal framework-independent

[ ] JWT sub é fonte externa autoritativa

[ ] missing sub fail closed

[ ] invalid JWT rejected

[ ] expired JWT rejected

[ ] wrong issuer rejected

[ ] invalid audience / authorized party rejected

[ ] JIT User provisioning funciona

[ ] repeated subject retorna mesmo User

[ ] subjects diferentes retornam Users diferentes

[ ] concurrent first access cria exatamente um User

[ ] DISABLED User não é tratado como ativo

[ ] jOOQ real PostgreSQL PASS

[ ] jOOQ não vaza boundaries

[ ] Spring Security não vaza domain

[ ] Keycloak classes não vazam domain/application

[ ] Flyway from zero PASS

[ ] Flyway validate PASS

[ ] jOOQ generation PASS

[ ] PostgreSQL Testcontainers PASS

[ ] privacy minimization PASS

[ ] no password/token persistence PASS

[ ] dependency audit PASS

[ ] Modulith PASS

[ ] clean build PASS

[ ] Scope Leakage NONE

[ ] M2-EVIDENCE.md presente

[ ] Terra independent verification PASS

---

# 79. STOP CONDITIONS

M2 deve parar como BLOCKED se:

- M1 não estiver em estado utilizável;
- documentos normativos conflitarem;
- Keycloak/OIDC strategy precisar mudar;
- User precisar ser tenant-owned;
- Business for necessário para concluir identidade;
- Membership for necessário para concluir identidade;
- distributed lock parecer necessário;
- nova infraestrutura estrutural parecer necessária;
- JPA parecer necessário;
- migration M1 precisar ser reescrita;
- dados pessoais adicionais parecerem obrigatórios;
- endpoint de login próprio parecer necessário;
- decisão arquitetural não coberta pelo baseline surgir.

---

# 80. EXPECTED FINAL STATE

Ao final de M2:

Keycloak
   ↓
OIDC/JWT
   ↓
Spring Security
   ↓
AuthenticatedPrincipal
   ↓
ResolveAuthenticatedUser
   ↓
UserRepository
   ↓
jOOQ
   ↓
PostgreSQL
   ↓
users

E SOMENTE isso no escopo funcional novo.

O backend passa a saber:

"quem é este usuário autenticado dentro do TINO?"

Ainda NÃO sabe:

"qual negócio ele está operando?"

Isso começa em M3.

---

# 81. FINAL OUTPUT

Finalizar obrigatoriamente:

MILESTONE: M2 — IDENTITY & SECURITY FOUNDATION
STATUS: PASS | BLOCKED | FAIL

Architecture: PASS | FAIL
Build: PASS | FAIL
Tests: PASS | FAIL
Identity: PASS | FAIL
Authentication: PASS | FAIL
Concurrency: PASS | FAIL
Flyway: PASS | FAIL
jOOQ: PASS | FAIL
Database: PASS | FAIL
Privacy: PASS | FAIL
Security Boundaries: PASS | FAIL
Modulith: PASS | FAIL
Git: PASS | FAIL
Scope Leakage: NONE | FOUND

Evidence:
docs/evidence/M2-EVIDENCE.md

Branch:
sdd/m2-identity-security

Implementation Commit:
<sha>

Final Evidence Commit:
<sha>

Merge to main:
NO

NEXT MILESTONE AUTHORIZED: NO

STOP.
