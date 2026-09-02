CREATE TABLE public.message_consents (
    business_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    channel VARCHAR(16) NOT NULL,
    purpose VARCHAR(24) NOT NULL,
    granted BOOLEAN NOT NULL,
    recipient_ref_hash VARCHAR(64) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT message_consents_pk PRIMARY KEY (business_id, customer_id, channel, purpose),
    CONSTRAINT message_consents_customer_fk FOREIGN KEY (business_id, customer_id)
        REFERENCES public.customers (business_id, id),
    CONSTRAINT message_consents_channel_check CHECK (channel = 'WHATSAPP'),
    CONSTRAINT message_consents_purpose_check CHECK (purpose IN ('TRANSACTIONAL', 'OPERATIONAL')),
    CONSTRAINT message_consents_recipient_hash_check CHECK (length(recipient_ref_hash) = 64)
);

CREATE TABLE public.message_consent_audit (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    channel VARCHAR(16) NOT NULL,
    purpose VARCHAR(24) NOT NULL,
    granted BOOLEAN NOT NULL,
    recipient_ref_hash VARCHAR(64) NOT NULL,
    actor_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT message_consent_audit_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT message_consent_audit_customer_fk FOREIGN KEY (business_id, customer_id)
        REFERENCES public.customers (business_id, id)
);

CREATE TABLE public.messages (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    channel VARCHAR(16) NOT NULL,
    purpose VARCHAR(24) NOT NULL,
    template VARCHAR(32) NOT NULL,
    recipient_ref_hash VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    provider_message_id VARCHAR(200),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT messages_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT messages_customer_fk FOREIGN KEY (business_id, customer_id)
        REFERENCES public.customers (business_id, id),
    CONSTRAINT messages_business_id_key UNIQUE (business_id, id),
    CONSTRAINT messages_idempotency UNIQUE (business_id, idempotency_key),
    CONSTRAINT messages_channel_check CHECK (channel = 'WHATSAPP'),
    CONSTRAINT messages_purpose_check CHECK (purpose IN ('TRANSACTIONAL', 'OPERATIONAL')),
    CONSTRAINT messages_template_check CHECK (template IN ('PAYMENT_UPDATE', 'RECONCILIATION_ALERT')),
    CONSTRAINT messages_status_check CHECK (status IN ('QUEUED', 'PROCESSING', 'SENT', 'FAILED', 'DEAD_LETTER'))
);

CREATE TABLE public.message_delivery_evidence (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    message_id UUID NOT NULL,
    provider VARCHAR(64) NOT NULL,
    provider_event_id VARCHAR(200) NOT NULL,
    provider_message_id VARCHAR(200) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT message_evidence_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT message_evidence_message_fk FOREIGN KEY (business_id, message_id)
        REFERENCES public.messages (business_id, id),
    CONSTRAINT message_evidence_status_check CHECK (status IN ('SENT', 'FAILED', 'DEAD_LETTER')),
    CONSTRAINT message_evidence_event_unique UNIQUE (provider, provider_event_id)
);

CREATE TABLE public.message_outbox (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    message_id UUID NOT NULL,
    command_type VARCHAR(32) NOT NULL,
    state VARCHAR(16) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    last_error VARCHAR(240),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT message_outbox_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT message_outbox_message_fk FOREIGN KEY (business_id, message_id)
        REFERENCES public.messages (business_id, id),
    CONSTRAINT message_outbox_command_check CHECK (command_type = 'DELIVER_MESSAGE'),
    CONSTRAINT message_outbox_state_check CHECK (state IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT message_outbox_unique UNIQUE (business_id, message_id, command_type)
);

CREATE OR REPLACE FUNCTION public.message_evidence_append_only()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'message_evidence_is_append_only'; END $$;
CREATE TRIGGER message_evidence_no_update BEFORE UPDATE OR DELETE ON public.message_delivery_evidence
FOR EACH ROW EXECUTE FUNCTION public.message_evidence_append_only();
CREATE TRIGGER message_audit_no_update BEFORE UPDATE OR DELETE ON public.message_consent_audit
FOR EACH ROW EXECUTE FUNCTION public.message_evidence_append_only();

ALTER TABLE public.message_consents ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.message_consents FORCE ROW LEVEL SECURITY;
CREATE POLICY message_consents_business_isolation ON public.message_consents
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);
ALTER TABLE public.message_consent_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.message_consent_audit FORCE ROW LEVEL SECURITY;
CREATE POLICY message_consent_audit_business_isolation ON public.message_consent_audit
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.messages FORCE ROW LEVEL SECURITY;
CREATE POLICY messages_business_isolation ON public.messages
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);
ALTER TABLE public.message_delivery_evidence ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.message_delivery_evidence FORCE ROW LEVEL SECURITY;
CREATE POLICY message_evidence_business_isolation ON public.message_delivery_evidence
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);
ALTER TABLE public.message_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.message_outbox FORCE ROW LEVEL SECURITY;
CREATE POLICY message_outbox_business_isolation ON public.message_outbox
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE ON TABLE public.message_consents TO tino_app;
GRANT SELECT, INSERT ON TABLE public.message_consent_audit TO tino_app;
GRANT SELECT, INSERT, UPDATE ON TABLE public.messages TO tino_app;
GRANT SELECT, INSERT ON TABLE public.message_delivery_evidence TO tino_app;
GRANT SELECT, INSERT, UPDATE ON TABLE public.message_outbox TO tino_app;
