ALTER TABLE public.customer_payment_intents
    ADD COLUMN confirmed_credit_entry_id UUID;

CREATE TABLE public.customer_payment_evidence (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    payment_intent_id UUID,
    amount NUMERIC(19, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    pix_txid VARCHAR(25),
    source VARCHAR(40) NOT NULL,
    source_package VARCHAR(200) NOT NULL,
    evidence_hash CHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    match_status VARCHAR(16) NOT NULL,
    CONSTRAINT customer_payment_evidence_business_fk
        FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT customer_payment_evidence_intent_fk
        FOREIGN KEY (business_id, payment_intent_id)
        REFERENCES public.customer_payment_intents (business_id, id),
    CONSTRAINT customer_payment_evidence_business_id_unique UNIQUE (business_id, id),
    CONSTRAINT customer_payment_evidence_amount_check
        CHECK (amount > 0 AND amount = round(amount, 2)),
    CONSTRAINT customer_payment_evidence_currency_check CHECK (currency = 'BRL'),
    CONSTRAINT customer_payment_evidence_txid_check
        CHECK (pix_txid IS NULL OR pix_txid ~ '^TINO[A-Z0-9]{1,21}$'),
    CONSTRAINT customer_payment_evidence_source_check
        CHECK (source ~ '^[A-Z][A-Z0-9_]{2,39}$'),
    CONSTRAINT customer_payment_evidence_hash_check
        CHECK (evidence_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT customer_payment_evidence_match_status_check
        CHECK (match_status IN ('UNMATCHED', 'MATCHED', 'DIVERGENT')),
    CONSTRAINT customer_payment_evidence_unique_hash UNIQUE (business_id, evidence_hash)
);

CREATE INDEX customer_payment_evidence_intent_idx
    ON public.customer_payment_evidence (business_id, payment_intent_id, match_status, occurred_at);

CREATE TABLE public.customer_payment_evidence_idempotency_keys (
    business_id UUID NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    evidence_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT customer_payment_evidence_idempotency_pk
        PRIMARY KEY (business_id, idempotency_key),
    CONSTRAINT customer_payment_evidence_idempotency_business_fk
        FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT customer_payment_evidence_idempotency_evidence_fk
        FOREIGN KEY (business_id, evidence_id)
        REFERENCES public.customer_payment_evidence (business_id, id)
        DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE public.customer_payment_intent_confirmation_idempotency_keys (
    business_id UUID NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    payment_intent_id UUID NOT NULL,
    credit_entry_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT customer_payment_intent_confirmation_idempotency_pk
        PRIMARY KEY (business_id, idempotency_key),
    CONSTRAINT customer_payment_intent_confirmation_idempotency_business_fk
        FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT customer_payment_intent_confirmation_idempotency_intent_fk
        FOREIGN KEY (business_id, payment_intent_id)
        REFERENCES public.customer_payment_intents (business_id, id)
        DEFERRABLE INITIALLY DEFERRED
);

ALTER TABLE public.customer_payment_intents
    ADD CONSTRAINT customer_payment_intents_confirmed_entry_fk
        FOREIGN KEY (confirmed_credit_entry_id)
        REFERENCES public.credit_ledger_entries (id);

ALTER TABLE public.customer_payment_evidence ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.customer_payment_evidence FORCE ROW LEVEL SECURITY;
CREATE POLICY customer_payment_evidence_business_isolation
    ON public.customer_payment_evidence
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE public.customer_payment_evidence_idempotency_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.customer_payment_evidence_idempotency_keys FORCE ROW LEVEL SECURITY;
CREATE POLICY customer_payment_evidence_idempotency_business_isolation
    ON public.customer_payment_evidence_idempotency_keys
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE public.customer_payment_intent_confirmation_idempotency_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.customer_payment_intent_confirmation_idempotency_keys FORCE ROW LEVEL SECURITY;
CREATE POLICY customer_payment_intent_confirmation_idempotency_business_isolation
    ON public.customer_payment_intent_confirmation_idempotency_keys
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE ON TABLE public.customer_payment_intents TO tino_app;
GRANT SELECT, INSERT ON TABLE public.customer_payment_evidence TO tino_app;
GRANT SELECT, INSERT ON TABLE public.customer_payment_evidence_idempotency_keys TO tino_app;
GRANT SELECT, INSERT ON TABLE public.customer_payment_intent_confirmation_idempotency_keys TO tino_app;
