CREATE TABLE public.reconciliation_runs (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    provider VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    state VARCHAR(16) NOT NULL,
    total_count INTEGER NOT NULL,
    matched_count INTEGER NOT NULL DEFAULT 0,
    discrepancy_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT reconciliation_runs_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT reconciliation_runs_pk_business UNIQUE (business_id, id),
    CONSTRAINT reconciliation_runs_idempotency UNIQUE (business_id, idempotency_key),
    CONSTRAINT reconciliation_runs_state_check CHECK (state IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT reconciliation_runs_counts_check CHECK
        (total_count >= 0 AND matched_count >= 0 AND discrepancy_count >= 0
         AND matched_count + discrepancy_count <= total_count)
);

CREATE TABLE public.reconciliation_items (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    run_id UUID NOT NULL,
    provider VARCHAR(64) NOT NULL,
    provider_event_id VARCHAR(200) NOT NULL,
    provider_payment_id VARCHAR(200) NOT NULL,
    payment_id UUID,
    amount NUMERIC(19, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    provider_status VARCHAR(16) NOT NULL,
    classification VARCHAR(32) NOT NULL,
    payload_sha256 VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT reconciliation_items_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT reconciliation_items_run_fk FOREIGN KEY (business_id, run_id)
        REFERENCES public.reconciliation_runs (business_id, id),
    CONSTRAINT reconciliation_items_payment_fk FOREIGN KEY (business_id, payment_id)
        REFERENCES public.payments (business_id, id),
    CONSTRAINT reconciliation_items_amount_check CHECK (amount > 0 AND amount = round(amount, 2)),
    CONSTRAINT reconciliation_items_currency_check CHECK (currency = 'BRL'),
    CONSTRAINT reconciliation_items_status_check CHECK
        (provider_status IN ('AUTHORIZED', 'CAPTURED', 'FAILED', 'CANCELLED', 'REFUNDED')),
    CONSTRAINT reconciliation_items_classification_check CHECK
        (classification IN ('MATCHED', 'MISSING_PAYMENT', 'AMOUNT_MISMATCH', 'STATUS_MISMATCH', 'DUPLICATE_EVENT')),
    CONSTRAINT reconciliation_items_event_unique UNIQUE (business_id, run_id, provider, provider_event_id)
);

CREATE INDEX reconciliation_items_run_idx ON public.reconciliation_items (business_id, run_id, created_at);

CREATE OR REPLACE FUNCTION public.reconciliation_run_transition_guard()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.business_id <> NEW.business_id OR OLD.provider <> NEW.provider
        OR OLD.idempotency_key <> NEW.idempotency_key
        OR OLD.request_fingerprint <> NEW.request_fingerprint
        OR OLD.total_count <> NEW.total_count OR OLD.created_at <> NEW.created_at
        OR OLD.state <> 'PROCESSING' OR NEW.state NOT IN ('COMPLETED', 'FAILED') THEN
        RAISE EXCEPTION 'invalid_reconciliation_run_transition';
    END IF;
    RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION public.reconciliation_append_only()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'reconciliation_evidence_is_append_only';
END $$;

CREATE TRIGGER reconciliation_runs_immutable
BEFORE UPDATE ON public.reconciliation_runs FOR EACH ROW
EXECUTE FUNCTION public.reconciliation_run_transition_guard();
CREATE TRIGGER reconciliation_runs_no_delete
BEFORE DELETE ON public.reconciliation_runs FOR EACH ROW
EXECUTE FUNCTION public.reconciliation_append_only();
CREATE TRIGGER reconciliation_items_immutable
BEFORE UPDATE OR DELETE ON public.reconciliation_items FOR EACH ROW
EXECUTE FUNCTION public.reconciliation_append_only();

ALTER TABLE public.reconciliation_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reconciliation_runs FORCE ROW LEVEL SECURITY;
CREATE POLICY reconciliation_runs_business_isolation ON public.reconciliation_runs
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE public.reconciliation_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reconciliation_items FORCE ROW LEVEL SECURITY;
CREATE POLICY reconciliation_items_business_isolation ON public.reconciliation_items
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE ON TABLE public.reconciliation_runs TO tino_app;
GRANT SELECT, INSERT ON TABLE public.reconciliation_items TO tino_app;
