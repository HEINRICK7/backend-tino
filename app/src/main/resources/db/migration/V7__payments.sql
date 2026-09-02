CREATE TABLE public.payments (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    method VARCHAR(16) NOT NULL,
    external_reference VARCHAR(200),
    provider VARCHAR(64) NOT NULL,
    provider_payment_id VARCHAR(200),
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT payments_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT payments_customer_fk FOREIGN KEY (business_id, customer_id)
        REFERENCES public.customers (business_id, id),
    CONSTRAINT payments_amount_check CHECK (amount > 0 AND amount = round(amount, 2)),
    CONSTRAINT payments_currency_check CHECK (currency = 'BRL'),
    CONSTRAINT payments_method_check CHECK (method = 'PIX'),
    CONSTRAINT payments_status_check CHECK (status IN
        ('CREATED', 'AUTHORIZED', 'CAPTURED', 'FAILED', 'CANCELLED', 'REFUNDED')),
    CONSTRAINT payments_external_reference_check CHECK
        (external_reference IS NULL OR length(btrim(external_reference)) > 0),
    CONSTRAINT payments_provider_payment_key UNIQUE (provider, provider_payment_id)
);

CREATE INDEX payments_business_created_idx ON public.payments (business_id, created_at, id);
ALTER TABLE public.payments ADD CONSTRAINT payments_business_id_key UNIQUE (business_id, id);

CREATE TABLE public.payment_idempotency_keys (
    business_id UUID NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    payment_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT payment_idempotency_pk PRIMARY KEY (business_id, idempotency_key),
    CONSTRAINT payment_idempotency_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT payment_idempotency_payment_fk FOREIGN KEY (business_id, payment_id)
        REFERENCES public.payments (business_id, id) DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE public.payment_provider_events (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    payment_id UUID NOT NULL,
    provider VARCHAR(64) NOT NULL,
    provider_event_id VARCHAR(200) NOT NULL,
    provider_payment_id VARCHAR(200) NOT NULL,
    status VARCHAR(16) NOT NULL,
    payload_sha256 VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT payment_events_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT payment_events_payment_fk FOREIGN KEY (business_id, payment_id)
        REFERENCES public.payments (business_id, id),
    CONSTRAINT payment_events_status_check CHECK (status IN
        ('AUTHORIZED', 'CAPTURED', 'FAILED', 'CANCELLED', 'REFUNDED')),
    CONSTRAINT payment_events_unique_provider_event UNIQUE (provider, provider_event_id)
);

CREATE TABLE public.payment_outbox (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    payment_id UUID NOT NULL,
    command_type VARCHAR(64) NOT NULL,
    state VARCHAR(16) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    last_error VARCHAR(240),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT payment_outbox_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT payment_outbox_payment_fk FOREIGN KEY (business_id, payment_id)
        REFERENCES public.payments (business_id, id),
    CONSTRAINT payment_outbox_state_check CHECK (state IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT payment_outbox_command_check CHECK (command_type = 'AUTHORIZE_PAYMENT'),
    CONSTRAINT payment_outbox_unique_command UNIQUE (business_id, payment_id, command_type)
);

CREATE INDEX payment_outbox_pending_idx ON public.payment_outbox (business_id, state, available_at);

CREATE OR REPLACE FUNCTION public.payment_status_transition_guard()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.business_id <> NEW.business_id OR OLD.customer_id <> NEW.customer_id
        OR OLD.amount <> NEW.amount OR OLD.currency <> NEW.currency
        OR OLD.method <> NEW.method OR OLD.created_at <> NEW.created_at THEN
        RAISE EXCEPTION 'payment_identity_is_immutable';
    END IF;
    IF OLD.status <> NEW.status AND NOT (
        (OLD.status = 'CREATED' AND NEW.status IN ('AUTHORIZED', 'FAILED', 'CANCELLED'))
        OR (OLD.status = 'AUTHORIZED' AND NEW.status IN ('CAPTURED', 'FAILED'))
        OR (OLD.status = 'CAPTURED' AND NEW.status = 'REFUNDED')
    ) THEN
        RAISE EXCEPTION 'invalid_payment_status_transition';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER payments_transition_guard
BEFORE UPDATE ON public.payments
FOR EACH ROW EXECUTE FUNCTION public.payment_status_transition_guard();

CREATE OR REPLACE FUNCTION public.payment_provider_events_append_only()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'payment_provider_events_are_append_only';
END $$;

CREATE TRIGGER payment_provider_events_immutable_update
BEFORE UPDATE ON public.payment_provider_events FOR EACH ROW
EXECUTE FUNCTION public.payment_provider_events_append_only();
CREATE TRIGGER payment_provider_events_immutable_delete
BEFORE DELETE ON public.payment_provider_events FOR EACH ROW
EXECUTE FUNCTION public.payment_provider_events_append_only();

ALTER TABLE public.payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.payments FORCE ROW LEVEL SECURITY;
CREATE POLICY payments_business_isolation ON public.payments
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE public.payment_idempotency_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.payment_idempotency_keys FORCE ROW LEVEL SECURITY;
CREATE POLICY payment_idempotency_business_isolation ON public.payment_idempotency_keys
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE public.payment_provider_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.payment_provider_events FORCE ROW LEVEL SECURITY;
CREATE POLICY payment_events_business_isolation ON public.payment_provider_events
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE public.payment_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.payment_outbox FORCE ROW LEVEL SECURITY;
CREATE POLICY payment_outbox_business_isolation ON public.payment_outbox
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE ON TABLE public.payments TO tino_app;
GRANT SELECT, INSERT ON TABLE public.payment_idempotency_keys TO tino_app;
GRANT SELECT, INSERT ON TABLE public.payment_provider_events TO tino_app;
GRANT SELECT, INSERT, UPDATE ON TABLE public.payment_outbox TO tino_app;
