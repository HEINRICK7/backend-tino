-- Customer-session Pix checkout intents. Creating an intent never changes the ledger.
CREATE TABLE public.customer_payment_intents (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    pix_txid VARCHAR(25) NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT customer_payment_intents_business_fk
        FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT customer_payment_intents_customer_fk
        FOREIGN KEY (business_id, customer_id) REFERENCES public.customers (business_id, id),
    CONSTRAINT customer_payment_intents_business_id_unique UNIQUE (business_id, id),
    CONSTRAINT customer_payment_intents_txid_unique UNIQUE (business_id, pix_txid),
    CONSTRAINT customer_payment_intents_amount_check CHECK (amount > 0 AND amount = round(amount, 2)),
    CONSTRAINT customer_payment_intents_currency_check CHECK (currency = 'BRL'),
    CONSTRAINT customer_payment_intents_txid_check CHECK (pix_txid ~ '^TINO[A-Z0-9]{1,21}$'),
    CONSTRAINT customer_payment_intents_status_check CHECK (status IN
        ('PENDING', 'EVIDENCE_FOUND', 'AWAITING_MERCHANT_CONFIRMATION',
         'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT customer_payment_intents_expiry_check CHECK (expires_at > created_at),
    CONSTRAINT customer_payment_intents_updated_check CHECK (updated_at >= created_at)
);

CREATE INDEX customer_payment_intents_pending_idx
    ON public.customer_payment_intents (business_id, customer_id, status, created_at);

CREATE TABLE public.customer_payment_intent_idempotency_keys (
    business_id UUID NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    payment_intent_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT customer_payment_intent_idempotency_pk
        PRIMARY KEY (business_id, idempotency_key),
    CONSTRAINT customer_payment_intent_idempotency_business_fk
        FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT customer_payment_intent_idempotency_intent_fk
        FOREIGN KEY (business_id, payment_intent_id)
        REFERENCES public.customer_payment_intents (business_id, id)
        DEFERRABLE INITIALLY DEFERRED
);

ALTER TABLE public.customer_payment_intents ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.customer_payment_intents FORCE ROW LEVEL SECURITY;
CREATE POLICY customer_payment_intents_business_isolation
    ON public.customer_payment_intents
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE public.customer_payment_intent_idempotency_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.customer_payment_intent_idempotency_keys FORCE ROW LEVEL SECURITY;
CREATE POLICY customer_payment_intent_idempotency_business_isolation
    ON public.customer_payment_intent_idempotency_keys
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE ON TABLE public.customer_payment_intents TO tino_app;
GRANT SELECT, INSERT ON TABLE public.customer_payment_intent_idempotency_keys TO tino_app;
