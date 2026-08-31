# TINO — BUSINESS DATA SOURCE ONBOARDING CONTRACT

Status: **AUTHORIZED**  
Scope: **Backend TINO + Android**  
Execution: **continuous, backend first**

## 1. Regra de autoridade

A origem dos dados pertence ao `Business`. Ela nunca pode ser inferida por
`trade_name`, vertical, tipo de negócio, device, `installation_id` ou heurística.
O Android coleta a intenção do usuário; o backend persiste e devolve o estado
autoritativo.

O modelo é:

```text
Installation/device ──opera──> Business ──possui──> data source
                                             ├── TINO_NATIVE
                                             └── EXTERNAL_API / DOCES_SONHOS
```

`Doces & Sonhos` é apenas um nome exibido. O identificador técnico do provider
é `DOCES_SONHOS`.

## 2. Contrato do backend

Todo Business novo começa explicitamente como `TINO_NATIVE` (default seguro).
O backend persiste `businesses.data_source_type` com os valores:

- `TINO_NATIVE`: não cria `ExternalBusinessConnection`;
- `EXTERNAL_API`: exige provider suportado e cria/reutiliza a conexão externa.

Selecionar a mesma origem novamente é replay-safe. A conexão é vinculada ao
`business_id`, não à instalação. Não há credenciais no contrato do Android.

### Selecionar a fonte

```http
PUT /api/v1/businesses/{businessId}/data-source
Authorization: Bearer <access_token>
Content-Type: application/json

{
  "source_type": "EXTERNAL_API",
  "provider": "DOCES_SONHOS"
}
```

Para iniciar no TINO:

```json
{
  "source_type": "TINO_NATIVE",
  "provider": null
}
```

Resposta de uma fonte externa:

```json
{
  "business_id": "uuid",
  "source_type": "EXTERNAL_API",
  "provider": "DOCES_SONHOS",
  "connection_id": "uuid",
  "status": "CONNECTED"
}
```

Consulta autoritativa:

```http
GET /api/v1/businesses/{businessId}/data-source
Authorization: Bearer <access_token>
```

O `POST /external-connections` continua disponível para compatibilidade e
agora também marca o Business como `EXTERNAL_API`. Sincronização continua
exclusivamente backend → TINO:

```http
POST /api/v1/businesses/{businessId}/external-connections/{connectionId}/sync
Authorization: Bearer <access_token>
```

## 3. Bootstrap e onboarding Android

Fluxo recomendado:

```text
OAuth → POST /bootstrap → criar/selecionar Business
      → PUT /data-source → registrar installation
      → POST /bootstrap final → READY → consumir catálogo TINO
```

Na tela, usar linguagem de negócio:

```text
Você já usa algum sistema no seu comércio?

[ Não, começar no TINO ]
[ Sim, conectar meu sistema ]
```

Se escolher conectar, mostrar somente as integrações disponíveis, inicialmente
`Doces & Sonhos`. O app envia `EXTERNAL_API` + `DOCES_SONHOS` uma vez. Em outro
device, ele reutiliza o mesmo `business_id` e consulta a fonte já persistida;
não deve pedir a escolha novamente nem mudar a origem ao trocar instalação.

O bootstrap retorna `data_source_type` dentro de cada Business. O app deve
tratar esse valor como leitura do backend e não como regra local.

Estados canônicos permanecem:

- `BUSINESS_REQUIRED`: criar o estabelecimento;
- `LOCAL_BUSINESS_LINK_REQUIRED`: selecionar Business e/ou registrar instalação;
- `READY`: abrir o app com a fonte já definida.

Não é necessário introduzir um quarto estado enquanto o fluxo puder continuar
com a configuração explícita após a criação. Uma implementação futura só pode
adicionar `DATA_SOURCE_REQUIRED` mediante decisão de contrato compatível.

## 4. Segurança e isolamento

- Nenhum secret de provider, token ou credencial externa é aceito pelo Android.
- Toda operação recebe `business_id` e passa pela autorização do usuário.
- Dados externos são persistidos sob o tenant correto, com RLS nas tabelas externas.
- O backend não loga token, credencial, payload sensível ou dados fiscais.
- Não criar produto silenciosamente; o catálogo externo é projetado pelo backend
  e fica disponível em `/api/v1/businesses/{businessId}/products`.

## 5. Critérios de aceite

Backend:

- Business nativo não possui conexão externa;
- Business externo possui `EXTERNAL_API` + provider válido;
- provider inválido é rejeitado;
- nomes comerciais duplicados não alteram a fonte;
- dois devices do mesmo Business veem a mesma fonte;
- trocar installation não troca fonte;
- repetição não cria conexão duplicada;
- RLS impede leitura/projeção em outro Business;
- bootstrap e `/data-source` devolvem a configuração autoritativa.

Android:

- escolha aparece no onboarding sem termos técnicos desnecessários;
- a escolha é enviada somente após Business existir;
- o app nunca chama a API Doces & Sonhos diretamente;
- `/products` do TINO é a única fonte da tela de produtos;
- tokens são renovados conforme o guia de integração e nunca vão para logs.

## 6. Fora de escopo

Não implementar aqui ERP fiscal, emissão, impostos, SPED, OCR, mudança
automática de preço, credenciais no APK ou inferência pelo nome comercial.

Evidências esperadas:

- `docs/TINO-BUSINESS-DATA-SOURCE-ONBOARDING-BACKEND-EVIDENCE.md`
- `docs/TINO-BUSINESS-DATA-SOURCE-ONBOARDING-ANDROID-EVIDENCE.md`

O segundo documento deve ser preenchido no repositório Android após a
implementação do fluxo de telas e cliente HTTP.
