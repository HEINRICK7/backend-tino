# TINO — Android Catalog Sync Handoff

Status: **READY FOR ANDROID IMPLEMENTATION / VPS INTEGRATION**  
Scope: **Android client consuming the TINO backend catalog**  
Provider boundary: **Android must not call Doces & Sonhos directly**

## Objetivo

Preparar o aplicativo Android para consultar e armazenar localmente os produtos
do catálogo do TINO, permitindo que a tela de Produtos mostre os itens
sincronizados do backend hospedado na VPS.

O fluxo correto é:

```text
Android
  -> OAuth/Keycloak
  -> TINO Backend HTTPS
  -> catálogo TINO projetado no PostgreSQL
  -> resposta de produtos
  -> Room local
  -> ProductsScreen
```

O Android não conhece o contrato da Doces & Sonhos, não possui Consumer Key ou
Consumer Secret e não deve executar a sincronização externa por conta própria.
Essa responsabilidade permanece no backend TINO.

## Estado atual conhecido

- A tela Android `ProductsScreen` observa `ProductDao.observeAll()`.
- O modelo local usa `priceCents: Long`.
- O backend TINO já expõe o preço no item de catálogo.
- O Android já possui `RestGoodsReceiptApi.searchProducts()`, mas esse método
  foi criado para seleção explícita durante o recebimento de NF-e; ele ainda
  não alimenta a tela principal de Produtos.
- O backend faz a sincronização Doces & Sonhos → catálogo TINO através das
  conexões externas e persiste o preço em decimal exato.

## Contrato atual do backend

Base URL deve ser configurada em runtime. Não hardcode domínio, IP, token ou
credencial no aplicativo.

```http
GET /api/v1/businesses/{businessId}/products?q={text}&gtin={gtin}&limit={limit}
Authorization: Bearer <access_token>
Accept: application/json
```

Resposta atual:

```json
[
  {
    "product_id": "e733e40a-8797-378b-b1f6-b32e608f0cbc",
    "name": "Bolo Crocante",
    "base_unit": "UN",
    "gtin": null,
    "price": 65.000000000
  }
]
```

Campos:

| Backend | Android | Regra |
|---|---|---|
| `product_id` | `id` | identidade estável do produto TINO |
| `name` | `name` | obrigatório para exibição |
| `base_unit` | `unit` | preservar a unidade retornada |
| `gtin` | campo opcional | `null` é válido; não rejeitar o produto |
| `price` | `priceCents` | Reais decimais → centavos inteiros |

### Regra de preço

Converter usando `BigDecimal`, nunca `Double` ou `Float`:

```text
priceCents = price × 100
65.00      = 6500
69.90      = 6990
```

O Android deve rejeitar ou marcar como indisponível um valor negativo,
malformado ou com fração de centavo. Não arredondar silenciosamente. O preço
de venda não deve ser recalculado a partir de estoque ou NF-e.

## Implementação solicitada ao agent Android

### 1. Camada de rede

Criar um contrato específico, sem acoplar a UI ao transporte:

```kotlin
interface CatalogApi {
    suspend fun listProducts(
        businessId: String,
        query: String? = null,
        gtin: String? = null,
        limit: Int = 100,
    ): List<RemoteCatalogProduct>
}
```

Reutilizar o cliente HTTP/autenticação existente quando compatível. O DTO
remoto deve refletir o JSON (`product_id`, `base_unit`, `price`) e não deve ser
usado diretamente pela Compose UI ou pelo Room.

### 2. Caso de uso

Adicionar um caso de uso semelhante a:

```text
SyncCatalog
  -> recebe businessId
  -> consulta CatalogApi
  -> valida e converte cada produto
  -> faz upsert transacional no Room
  -> retorna contadores e erro operacional sanitizado
```

O caso de uso deve ficar independente de Compose, Retrofit/URLConnection e
Room. A implementação concreta de rede e persistência fica nas bordas.

### 3. Persistência local

Reutilizar a entidade e o DAO de produto existentes sempre que possível.

Requisitos:

- upsert por `product_id`;
- reprocessar a mesma resposta não pode duplicar produtos;
- atualização de preço deve atualizar `priceCents`;
- produto sem `gtin` continua válido;
- não apagar produto local se a resposta estiver vazia por erro de rede;
- não apagar produtos ausentes até existir uma política explícita de
  desativação no contrato do backend;
- manter a operação transacional;
- registrar `lastCatalogSyncAt`, estado e erro sanitizado se já houver padrão
  de sincronização local no app.

Não criar produto silenciosamente a partir de nome parecido. A identidade é o
`product_id` do TINO.

### 4. Integração com a tela

A tela `ProductsScreen` deve continuar observando Room. Não colocar chamada de
rede dentro do Composable.

Adicionar uma ação de sincronização no fluxo apropriado do app, por exemplo:

```text
Produtos → Atualizar catálogo
```

Comportamento mínimo:

- mostrar carregamento;
- impedir duas sincronizações concorrentes;
- mostrar data da última sincronização;
- informar sucesso com quantidade criada/atualizada;
- manter os dados locais visíveis durante erro;
- informar sessão expirada quando a renovação falhar;
- permitir tentar novamente sem apagar o catálogo local.

Ao concluir o upsert, a observação do Room deve atualizar a lista e o detalhe
do produto deve mostrar o preço formatado a partir de `priceCents`.

## VPS, HTTPS e autenticação

O Android deve receber a URL da VPS por configuração de ambiente/build variant,
por exemplo `BuildConfig.TINO_API_BASE_URL`, sem segredo no APK.

Requisitos para o primeiro teste:

- domínio HTTPS válido apontando para a VPS;
- certificado confiável pelo Android;
- backend TINO acessível externamente;
- Keycloak acessível pelo Android para emitir/renovar o token;
- `iss` do JWT exatamente igual ao issuer configurado no backend;
- CORS não é requisito para o aplicativo nativo, mas firewall, DNS e TLS são;
- não usar `localhost` no APK instalado;
- não usar `10.0.2.2` quando o backend estiver na VPS;
- não registrar token, refresh token, senha ou `Authorization` nos logs.

O access token expira rapidamente por segurança. O cliente deve:

1. armazenar credenciais de sessão somente no mecanismo seguro já adotado pelo
   app;
2. manter o access token em memória quando possível;
3. usar refresh token para renovar a sessão;
4. em um único `401`, renovar uma vez e repetir a requisição uma vez;
5. se a renovação falhar, limpar a sessão e solicitar novo login;
6. evitar loops de retry.

Não implementar novo fluxo de senha ou duplicar Keycloak dentro do módulo de
catálogo. Usar a abstração de autenticação do aplicativo.

## Paginação e limite atual

O endpoint atual de produtos aceita `limit` de 1 a 100, mas não retorna
`next_cursor`, `offset` ou `next_page`. Ele foi desenhado inicialmente para
busca/seleção de produtos, não como contrato definitivo de snapshot completo.

Portanto:

- para o primeiro teste na VPS, consultar até `limit=100` é aceitável se o
  catálogo de teste tiver menos de 100 produtos;
- o app não pode tratar exatamente 100 itens como “sincronização completa”;
- não inventar `page`, `offset` ou `cursor` no cliente;
- se o catálogo puder ultrapassar 100 itens, registrar o estado como parcial e
  não declarar sincronização completa;
- antes de produção, o backend deverá publicar um endpoint de catálogo completo
  com paginação estável, preferencialmente cursor-based, ou integrar o catálogo
  ao contrato de sync incremental já existente.

A abstração `CatalogApi` deve permitir paginação futura sem contaminar a UI,
por exemplo retornando internamente uma página com `items` e `nextCursor`.

## Tratamento de falhas

| Situação | Comportamento Android |
|---|---|
| `200` | validar, converter e fazer upsert |
| `401` | renovar token uma vez e repetir uma vez |
| `403` | informar falta de acesso à empresa; não repetir |
| `408`, `429`, `5xx` | erro temporário; retry limitado conforme cliente existente |
| timeout | manter Room local e informar indisponibilidade |
| JSON inválido | não alterar catálogo local; registrar erro sanitizado |
| preço inválido | rejeitar somente o item inválido e reportar contagem |
| resposta vazia | tratar como resposta válida somente se o contrato confirmar; não apagar dados por acidente |
| sincronização concorrente | uma execução por empresa por vez |

## Testes obrigatórios

### Unitários

- mapeamento de todos os campos do DTO;
- `65.00 → 6500`;
- `69.90 → 6990`;
- preço nulo, negativo, `NaN`, string inválida e fração de centavo;
- `gtin = null` sem falha;
- unidade `UN` preservada;
- erro HTTP convertido em erro de domínio sanitizado.

### Persistência

- primeiro sync cria produtos;
- segundo sync da mesma resposta não duplica;
- alteração de preço atualiza o mesmo produto;
- produto local permanece após falha de rede;
- upsert ocorre em transação;
- Room emite a lista atualizada para `ProductsScreen`.

### Instrumentação/E2E na VPS

Com um usuário real de teste e uma empresa autorizada:

1. fazer login pelo fluxo normal do app;
2. abrir Produtos;
3. executar Atualizar catálogo;
4. confirmar que os produtos retornados pelo backend aparecem;
5. confirmar preço, unidade e estoque local;
6. fechar e reabrir o app sem rede e confirmar os dados locais;
7. alterar/recarregar o catálogo no backend e sincronizar novamente;
8. confirmar que o preço é atualizado sem produto duplicado;
9. expirar o access token e confirmar renovação única;
10. confirmar que nenhum segredo aparece nos logs.

## Fora de escopo

- chamada direta à API Doces & Sonhos pelo Android;
- Consumer Key/Secret no Android;
- sincronização bidirecional;
- alteração de preço no provedor externo;
- alteração automática de preço de venda por NF-e;
- emissão fiscal, impostos, SPED ou OCR;
- apagar produtos por ausência sem contrato de desativação;
- inventar paginação incompatível com o backend;
- autorização ou implementação de SERPRO Produção/F7.

## Critério de aceite

O trabalho Android estará pronto para o primeiro teste quando:

- a URL da VPS puder ser trocada sem recompilar regra de negócio;
- o app autenticar e chamar o backend TINO por HTTPS;
- `product_id`, nome, unidade e preço forem mapeados corretamente;
- o preço for exibido em reais a partir de centavos inteiros;
- os produtos forem persistidos no Room de forma idempotente;
- a tela Produtos atualizar sem chamada de rede dentro do Composable;
- falhas preservarem o último catálogo válido;
- os testes unitários, de persistência e instrumentados passarem;
- não houver token, senha ou segredo no Git, APK, fixture ou log;
- o relatório de evidência registrar URL/configuração usada, testes e limitações
  de paginação.

## Primeiro teste recomendado

Não começar pela API Doces & Sonhos no aparelho. Primeiro:

1. publicar o backend TINO na VPS com HTTPS;
2. configurar Keycloak e o realm usado pelo ambiente de teste;
3. confirmar pelo Swagger que o endpoint autenticado retorna produtos com
   `price`;
4. configurar a URL HTTPS da VPS no Android;
5. executar o sync pelo app;
6. validar os mesmos produtos e preços na tela Produtos;
7. só depois testar reautenticação, rede instável e catálogo maior.

