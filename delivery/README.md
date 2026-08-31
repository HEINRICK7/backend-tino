# TINO OTP delivery pilot

Este serviço é somente infraestrutura de entrega. Ele não gera nem valida OTP,
não cria usuários, não conhece Business/tenant/Keycloak e não emite tokens.

O backend Java é a autoridade. O serviço recebe a mensagem já preparada pelo
backend através de `POST /internal/v1/messages/otp`, valida o token interno e a
encaminha para uma instância wa-evolution configurada em runtime.

Variáveis obrigatórias:

```dotenv
TINO_INTERNAL_TOKEN=segredo-de-runtime
WA_EVOLUTION_BASE_URL=http://evolution-api:8080
WA_EVOLUTION_API_KEY=segredo-de-runtime
WA_EVOLUTION_INSTANCE=tino
```

`WA_EVOLUTION_SEND_PATH` é opcional e usa `/message/sendText/{instance}`. O
serviço não registra telefone, mensagem, OTP, tokens ou respostas do provider.
Sem a configuração completa, health/readiness retorna `503`; não há falso
sucesso.
