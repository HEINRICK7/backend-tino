# TINO — Business Data Source Onboarding — Backend Evidence

Status: **IMPLEMENTED / READY FOR ANDROID HANDOFF**  
Scope: explicit Business source selection and bootstrap contract

## Implemented

- `businesses.data_source_type` persisted by migration `V15__business_data_source.sql`;
- default seguro `TINO_NATIVE` para novos Businesses;
- migração histórica de Businesses que já possuíam conexão externa;
- grant mínimo de `UPDATE` para `tino_app`;
- `PUT /api/v1/businesses/{businessId}/data-source`;
- `GET /api/v1/businesses/{businessId}/data-source` agora lê a configuração do Business;
- `POST /external-connections` marca a origem como `EXTERNAL_API`;
- `data_source_type` exposto em `GET /businesses` e no resumo do `/bootstrap`;
- provider aceito no piloto: `DOCES_SONHOS`;
- nenhum secret ou credencial entra no contrato Android;
- conexão externa permanece vinculada ao `business_id`, nunca à instalação.

## Arquivos principais

- `modules/business/.../BusinessDataSourceType.java`
- `modules/business/.../Business.java`
- `modules/business/.../JooqBusinessRepository.java`
- `modules/external/.../ManageExternalBusinessDataSource.java`
- `modules/external/.../BusinessDataSourceController.java`
- `modules/bootstrap/.../BootstrapBusinessSummary.java`
- `app/src/main/resources/db/migration/V15__business_data_source.sql`
- `docs/TINO-BUSINESS-DATA-SOURCE-ONBOARDING-CONTRACT.md`
- `docs/TINO-ANDROID-API-INTEGRATION.md`

## Evidência de testes

Comandos executados:

```text
./gradlew compileJava compileTestJava --no-daemon
./gradlew :modules:external:test :modules:business:test :modules:bootstrap:test --no-daemon
./gradlew :app:test --tests com.tino.backend.M13ExternalBusinessDataSourcePostgresTest --tests com.tino.backend.M5BootstrapHttpApiTest --tests com.tino.backend.M3BusinessHttpApiTest --no-daemon
```

Resultado:

- compilação Java/teste: PASS;
- testes unitários Business/Bootstrap/External: PASS;
- testes HTTP Business/Bootstrap e PostgreSQL/RLS/migration: PASS;
- teste de persistência confirma `TINO_NATIVE → EXTERNAL_API` mesmo sem conexão externa;
- testes de use case confirmam seleção externa replay-safe e nativo sem conexão.

## Handoff

O Android deve implementar a UX e chamar `PUT /data-source` depois de criar ou
selecionar o Business. A implementação Android não está neste repositório; a
especificação compartilhada e o guia de consumo foram atualizados para o agent
do app.

F7/SERPRO Produção permanece fora deste trabalho.
