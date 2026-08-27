# TINO — SECURITY & GIT SAFETY POLICY

## Status

MANDATORY

Este documento define regras obrigatórias para qualquer agent, modelo,
desenvolvedor ou automação que modifique o TINO Backend.

Estas regras se aplicam a TODOS os milestones.

Nenhum SDD precisa repetir estas regras para que elas sejam obrigatórias.

---

# 1. PRINCÍPIO FUNDAMENTAL

Nenhum segredo ou valor que possa ser interpretado como credencial deve
ser persistido no Git.

Isso inclui ambientes:

- production;
- staging;
- development;
- local;
- test;
- CI;
- Testcontainers;
- Docker Compose.

"É apenas uma senha de teste" NÃO é justificativa para hardcode.

---

# 2. NEVER COMMIT SECRETS

É proibido commitar literalmente:

- passwords;
- API keys;
- access tokens;
- refresh tokens;
- JWTs;
- OAuth client secrets;
- private keys;
- database passwords;
- webhook secrets;
- signing secrets;
- cloud credentials;
- SMTP credentials;
- Pix/API credentials;
- certificados privados;
- qualquer credential equivalente.

Aplica-se também a:

application.yml
application.properties
docker-compose.yml
Dockerfile
Java/Kotlin source
tests
fixtures
scripts
documentation
examples
GitHub Actions
Flyway
SQL
JSON/YAML/TOML
evidence files.

---

# 3. CONFIGURATION POLICY

Configuração versionada pode definir nomes e endpoints não sensíveis.

Exemplo permitido:

SPRING_DATASOURCE_USERNAME=tino_app

Senha:

NÃO deve possuir valor secreto literal versionado.

Preferir:

${SPRING_DATASOURCE_PASSWORD}

e mecanismos equivalentes.

Não usar fallback contendo senha:

${SPRING_DATASOURCE_PASSWORD:my-password}

---

# 4. TEST CREDENTIAL POLICY

Testes não devem conter credenciais literais persistentes apenas porque
o ambiente é descartável.

Quando uma credencial for tecnicamente necessária:

- gerar em runtime;
- manter somente em memória;
- limitar sua vida ao teste;
- não imprimir em logs;
- não persistir em evidence;
- não commitar.

Exemplo conceitual:

runtimeGeneratedPassword()

em vez de:

password = "test-password"

---

# 5. SECRET LOGGING

É proibido logar:

- password;
- Authorization header;
- bearer token;
- JWT completo;
- refresh token;
- API secret;
- database credential;
- private key.

Isso vale inclusive para DEBUG/TRACE.

---

# 6. PRE-COMMIT SECURITY GATE

ANTES DE TODO COMMIT, o agent DEVE executar secret scanning.

Sequência obrigatória:

implementation
    ↓
tests
    ↓
git diff review
    ↓
secret scan
    ↓
staged-files review
    ↓
commit

Se secret scan falhar:

COMMIT PROHIBITED.

O agent deve corrigir primeiro.

---

# 7. PRE-PUSH SECURITY GATE

ANTES DE TODO PUSH:

- revisar commits novos;
- revisar arquivos staged/committed;
- executar secret detection;
- verificar arquivos inesperados;
- verificar .env/private keys/certificates;
- verificar build artifacts.

Se houver finding não classificado:

PUSH PROHIBITED.

---

# 8. CI SECURITY GATE

Secret scanning deve fazer parte do CI.

Pull Request não pode ser considerado PASS enquanto:

Secret Scan != PASS

Security gate é tão obrigatório quanto:

Build
Tests
Architecture
Flyway
jOOQ

---

# 9. GITGUARDIAN POLICY

GitGuardian finding nunca deve ser ignorado automaticamente.

Estados possíveis:

REAL_SECRET
TEST_DEV_CREDENTIAL
FALSE_POSITIVE
UNKNOWN

UNKNOWN:

FAIL CLOSED.

O agent deve investigar.

Não selecionar "false positive" apenas para deixar CI verde.

---

# 10. SECRET ROTATION

Se uma credencial versionada tiver qualquer possibilidade de validade externa:

assumir comprometimento.

Obrigatório:

1. identificar escopo;
2. revogar;
3. rotacionar;
4. atualizar configuração segura;
5. verificar uso;
6. documentar sem registrar o valor.

Nunca confiar apenas em apagar o arquivo.

---

# 11. GIT HISTORY SAFETY

Antes de qualquer:

rebase
reset
filter-repo
filter-branch
force push
history rewrite

o agent DEVE determinar:

- branch atual;
- remote;
- se commit foi publicado;
- se commit está em main;
- se PR foi merged;
- branches descendentes;
- impacto sobre outros colaboradores.

Nenhuma reescrita de `main` é permitida sem autorização humana explícita.

---

# 12. MAIN BRANCH PROTECTION

PROIBIDO autonomamente:

git push --force main
git push --force-with-lease main
git reset --hard origin/... seguido de push
git filter-repo sobre main publicada
reescrever commits já integrados em main.

Se parecer necessário:

STOP.

Retornar:

SECURITY/GIT DECISION REQUIRED

Nenhuma exceção implícita.

---

# 13. FEATURE BRANCH HISTORY REWRITE

Mesmo em feature branch, history rewrite exige preflight.

Somente considerar quando:

- branch não foi merged;
- commits ofensivos não estão em main;
- impacto está entendido;
- branch pertence ao trabalho atual.

Usar:

--force-with-lease

Nunca:

--force

como padrão.

Se PR já foi merged:

não assumir que reescrever feature branch limpa main.

---

# 14. REMOTE STATE BEFORE DESTRUCTIVE GIT

Estado local NÃO é suficiente para decisões destrutivas.

Antes de qualquer ação destrutiva:

git fetch

e verificar o estado remoto.

Obrigatório verificar:

- origin/main;
- merge status do PR quando aplicável;
- ancestry do commit;
- branch divergence.

O agent NÃO pode executar ação destrutiva baseado em suposição.

---

# 15. DESTRUCTIVE ACTION POLICY

Antes de qualquer ação potencialmente destrutiva, responder internamente:

WHAT WILL CHANGE?
WHAT CAN BE LOST?
IS IT REVERSIBLE?
IS REMOTE STATE VERIFIED?
IS HUMAN AUTHORIZATION REQUIRED?

Se houver dúvida:

STOP.

Fail closed.

---

# 16. NO SILENT WORKAROUNDS

É proibido "resolver" gate de segurança:

- desabilitando scanner;
- removendo workflow;
- adicionando ignore amplo;
- mascarando detector;
- mudando CI para sempre PASS;
- marcando automaticamente false positive;
- removendo teste de segurança.

Security gate deve ser corrigido, não contornado.

---

# 17. ENV FILES

Arquivos contendo valores reais:

.env
.env.local
.env.production
secrets.*
credentials.*

não devem ser versionados.

`.gitignore` deve protegê-los.

Quando necessário, fornecer apenas template seguro:

.env.example

Template NÃO contém segredo válido.

---

# 18. DOCUMENTATION AND EVIDENCE

Evidence e documentação também passam por secret scan.

Nunca copiar para evidence:

"detected password was ..."

Registrar:

Finding:
Generic Database Credential

Status:
REMOVED / ROTATED / DEV-ONLY

Nunca registrar o valor.

---

# 19. SECURITY CHECKPOINT BEFORE MILESTONE PASS

Nenhum milestone recebe PASS antes de:

[ ] clean build PASS
[ ] tests PASS
[ ] architecture gates PASS
[ ] secret scan PASS
[ ] dependency/security audit PASS
[ ] staged files reviewed
[ ] Git history of new commits reviewed
[ ] no unexpected credentials
[ ] CI security checks PASS

Se GitGuardian ou scanner equivalente estiver RED:

MILESTONE != PASS.

---

# 20. SECURITY CHECKPOINT BEFORE MERGE

Nenhum PR pode ser recomendado para merge enquanto houver:

- unresolved secret finding;
- failed security workflow;
- unexplained credential;
- security scan failure.

Mesmo que:

Build = PASS
Tests = PASS

Security FAIL bloqueia merge.

---

# 21. AGENT RESPONSIBILITY

O agent que implementa é responsável por evitar introduzir secrets.

O agent supervisor é responsável por verificar independentemente.

Fluxo:

Luna
→ implementation
→ local secret scan
→ report

Terra
→ independent diff review
→ independent secret scan
→ CI verification
→ PASS/FAIL

A declaração de Luna não substitui verificação de Terra.

---

# 22. SECURITY FINDING DURING ANOTHER MILESTONE

Se um problema de segurança for encontrado durante M2/M3/etc.:

PAUSE milestone.

Preservar trabalho de forma reversível.

Resolver ou classificar o incidente primeiro.

Depois:

reconstruir estado
→ verificar gates
→ retomar milestone.

Não misturar silenciosamente security remediation com implementação funcional.

---

# 23. PROTECTED FILES

Mudanças em:

.github/workflows/
security configuration
authentication configuration
secret-scanning configuration
branch protection related files

exigem revisão explícita do supervisor.

Agent implementador não deve enfraquecer controles para fazer pipeline passar.

---

# 24. DATABASE DEVELOPMENT CREDENTIALS

Mesmo banco local deve seguir:

username:
pode ser configuração versionada quando não sensível.

password:
runtime/environment generated or supplied.

Não assumir:

"localhost = safe to hardcode."

---

# 25. DEFINITION OF DONE — SECURITY

Todo milestone passa a ter implicitamente:

SECURITY GATE

PASS somente se:

- nenhum secret novo foi versionado;
- secret scanner local PASS;
- CI secret scanner PASS;
- nenhuma credencial real em source;
- nenhuma ação Git destrutiva não autorizada;
- nenhum security control enfraquecido;
- evidence não contém secrets.

---

# 26. STOP CONDITIONS

STOP imediatamente se:

- secret real for encontrado;
- validade externa for desconhecida;
- rotação puder ser necessária;
- main precisar ser reescrita;
- force push em protected branch parecer necessário;
- scanner precisar ser desabilitado;
- histórico remoto estiver diferente da premissa;
- ação puder destruir trabalho;
- impacto não puder ser determinado.

Retornar:

SECURITY STATUS: BLOCKED
HUMAN DECISION REQUIRED

Reason:
...

No destructive action performed.

---

# 27. PERMANENT RULE

Segurança deve ser preventiva.

O fluxo correto é:

CODE
 ↓
TEST
 ↓
SECURITY SCAN
 ↓
DIFF REVIEW
 ↓
COMMIT
 ↓
SECURITY SCAN
 ↓
PUSH
 ↓
CI
 ↓
SECURITY PASS
 ↓
MERGE

Nunca:

CODE
 ↓
COMMIT
 ↓
PUSH
 ↓
MERGE
 ↓
discover secret
