# Contrato — PaymentIntent Pix do cliente

## Objetivo

Permitir que o cliente do Meu TINO inicie um pagamento da própria dívida sem o PWA calcular ou baixar saldo. O backend continua sendo a fonte autoritativa.

## Endpoint

`POST /api/v1/me/payment-intents`

Requer sessão de cliente ativa (`tino_customer_session`) e o header `Idempotency-Key`. O corpo é:

```json
{"amount_minor": 49750}
```

O valor deve ser inteiro positivo em centavos. O backend executa, dentro do tenant da sessão:

1. valida a idempotência e o fingerprint do corpo;
2. lê o saldo autoritativo da caderneta;
3. rejeita valor acima do saldo;
4. gera `txid` opaco com prefixo `TINO`;
5. gera QR/copia-e-cola dinâmicos com valor e `txid`;
6. persiste o intento como `PENDING` sem alterar o ledger.

Resposta nova: `201`. Repetição idêntica: `200` com `replayed: true`. Reutilização da chave com outro corpo: `409`.

```json
{
  "paymentIntent": {
    "id": "uuid",
    "customerId": "uuid",
    "amountMinor": 49750,
    "currency": "BRL",
    "pixTxid": "TINO...",
    "pixKey": "chave-pix",
    "copyPaste": "000201...",
    "status": "PENDING",
    "createdAt": "2026-09-10T10:30:00Z",
    "expiresAt": "2026-09-10T11:00:00Z",
    "updatedAt": "2026-09-10T10:30:00Z"
  },
  "replayed": false
}
```

Erros públicos:

- `401 CUSTOMER_SESSION_REQUIRED`: sessão ausente ou inválida;
- `409 PAYMENT_AMOUNT_EXCEEDS_BALANCE`: valor maior que o saldo atual;
- `409 PAYMENT_INTENT_CONFLICT`: mesma idempotência com payload diferente;
- `503 PIX_UNAVAILABLE`: chave Pix inexistente, desabilitada ou indisponível.

## Regras de segurança

- `customerId` e `businessId` vêm exclusivamente da sessão; não são aceitos no corpo.
- O payload Pix é somente uma instrução de pagamento. Criar o intento não confirma nem baixa a dívida.
- O QR contém valor fixo e `txid` vinculado ao intento; o matching futuro deverá validar ambos.
- O PWA não deve persistir saldo financeiro nem confiar em valores enviados pelo cliente para fechar o ledger.
- A tabela usa RLS e chaves únicas por tenant para impedir colisões de intento/txid.

## Evidência e confirmação do comerciante

O Android envia uma `PaymentEvidence` normalizada em:

`POST /api/v1/businesses/{businessId}/payment-evidence`

com `Idempotency-Key` e os campos `amount_minor`, `currency`, `pix_txid` opcional,
`source`, `source_package`, `evidence_hash` e `occurred_at`. O backend nunca
persiste o texto bruto da notificação.

Quando não há TXID, o backend só associa a evidência se existir exatamente um
intento `PENDING` do mesmo valor cujo período cubra `occurred_at`. Se houver
ambiguidade, a evidência fica `UNMATCHED`; divergência de valor, TXID ou prazo
fica `DIVERGENT`. Uma evidência `MATCHED` move o intento para
`EVIDENCE_FOUND`, mas não baixa a dívida.

O comerciante consulta a fila em:

`GET /api/v1/businesses/{businessId}/payment-evidence?limit=20`

e confirma explicitamente em:

`POST /api/v1/businesses/{businessId}/payment-intents/{paymentIntentId}/confirm`

Essa confirmação exige evidência compatível, usa uma chave de operação estável
(`PIX_PAYMENT_INTENT:{paymentIntentId}`) no ledger e registra o
`credit_entry_id` no intento. Repetições retornam o mesmo lançamento sem criar
um segundo débito. Só depois da confirmação o fluxo existente de eventos/outbox
do crédito pode atualizar o saldo, extrato e notificações do Meu TINO.
