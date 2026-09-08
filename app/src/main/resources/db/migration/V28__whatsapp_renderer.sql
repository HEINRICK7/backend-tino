-- M28: versioned WhatsApp previews and auditable, idempotent deliveries.
CREATE TABLE public.whatsapp_previews (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    account_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    message_type VARCHAR(32) NOT NULL,
    template_id VARCHAR(64) NOT NULL,
    template_version VARCHAR(16) NOT NULL,
    rendered_hash CHAR(64) NOT NULL,
    snapshot_version BIGINT NOT NULL,
    text_content TEXT NOT NULL,
    media_mime_type VARCHAR(64) NOT NULL,
    media_content BYTEA NOT NULL,
    media_filename VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT whatsapp_previews_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT whatsapp_previews_account_fk FOREIGN KEY (business_id, account_id)
        REFERENCES public.credit_accounts (business_id, id),
    CONSTRAINT whatsapp_previews_customer_fk FOREIGN KEY (business_id, customer_id)
        REFERENCES public.customers (business_id, id),
    CONSTRAINT whatsapp_previews_message_type_check CHECK (message_type IN
        ('DEBT_STATEMENT', 'DEBT_CREATED', 'PAYMENT_CONFIRMED',
         'PARTIAL_PAYMENT_CONFIRMED', 'PAYMENT_REMINDER', 'PROMISE_TO_PAY',
         'PAYMENT_AGREEMENT')),
    CONSTRAINT whatsapp_previews_template_version_check CHECK (template_version <> ''),
    CONSTRAINT whatsapp_previews_hash_check CHECK (length(rendered_hash) = 64),
    CONSTRAINT whatsapp_previews_snapshot_version_check CHECK (snapshot_version >= 0),
    CONSTRAINT whatsapp_previews_media_check CHECK
        (media_mime_type = 'image/png' AND length(media_content) > 0 AND length(media_content) <= 5242880),
    CONSTRAINT whatsapp_previews_expiry_check CHECK (expires_at > created_at)
);
ALTER TABLE public.whatsapp_previews ADD CONSTRAINT whatsapp_previews_business_id_key UNIQUE (business_id, id);

CREATE TABLE public.whatsapp_message_deliveries (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    preview_id UUID NOT NULL,
    account_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    message_type VARCHAR(32) NOT NULL,
    template_id VARCHAR(64) NOT NULL,
    template_version VARCHAR(16) NOT NULL,
    rendered_hash CHAR(64) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    provider_message_id VARCHAR(200),
    status VARCHAR(16) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL,
    sending_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    last_error VARCHAR(240),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT whatsapp_deliveries_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT whatsapp_deliveries_preview_fk FOREIGN KEY (business_id, preview_id)
        REFERENCES public.whatsapp_previews (business_id, id),
    CONSTRAINT whatsapp_deliveries_account_fk FOREIGN KEY (business_id, account_id)
        REFERENCES public.credit_accounts (business_id, id),
    CONSTRAINT whatsapp_deliveries_customer_fk FOREIGN KEY (business_id, customer_id)
        REFERENCES public.customers (business_id, id),
    CONSTRAINT whatsapp_deliveries_message_type_check CHECK (message_type IN
        ('DEBT_STATEMENT', 'DEBT_CREATED', 'PAYMENT_CONFIRMED',
         'PARTIAL_PAYMENT_CONFIRMED', 'PAYMENT_REMINDER', 'PROMISE_TO_PAY',
         'PAYMENT_AGREEMENT')),
    CONSTRAINT whatsapp_deliveries_hash_check CHECK (length(rendered_hash) = 64),
    CONSTRAINT whatsapp_deliveries_status_check CHECK (status IN ('QUEUED', 'SENDING', 'SENT', 'FAILED')),
    CONSTRAINT whatsapp_deliveries_attempts_check CHECK (attempt_count >= 0),
    CONSTRAINT whatsapp_deliveries_idempotency UNIQUE (business_id, idempotency_key)
);
ALTER TABLE public.whatsapp_message_deliveries ADD CONSTRAINT whatsapp_deliveries_business_id_key UNIQUE (business_id, id);

CREATE TABLE public.whatsapp_delivery_audit (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    delivery_id UUID,
    event_type VARCHAR(24) NOT NULL,
    message_type VARCHAR(32) NOT NULL,
    template_id VARCHAR(64) NOT NULL,
    template_version VARCHAR(16) NOT NULL,
    rendered_hash CHAR(64) NOT NULL,
    actor_user_id UUID,
    provider_message_id VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT whatsapp_audit_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT whatsapp_audit_delivery_fk FOREIGN KEY (business_id, delivery_id)
        REFERENCES public.whatsapp_message_deliveries (business_id, id),
    CONSTRAINT whatsapp_audit_event_check CHECK (event_type IN ('PREVIEW_CREATED', 'QUEUED', 'SENT', 'FAILED')),
    CONSTRAINT whatsapp_audit_message_type_check CHECK (message_type IN
        ('DEBT_STATEMENT', 'DEBT_CREATED', 'PAYMENT_CONFIRMED',
         'PARTIAL_PAYMENT_CONFIRMED', 'PAYMENT_REMINDER', 'PROMISE_TO_PAY',
         'PAYMENT_AGREEMENT')),
    CONSTRAINT whatsapp_audit_hash_check CHECK (length(rendered_hash) = 64)
);

CREATE INDEX whatsapp_previews_expiry_idx ON public.whatsapp_previews (business_id, expires_at);
CREATE INDEX whatsapp_deliveries_pending_idx
    ON public.whatsapp_message_deliveries (business_id, status, available_at);
CREATE INDEX whatsapp_audit_created_idx ON public.whatsapp_delivery_audit (business_id, created_at, id);

ALTER TABLE public.whatsapp_previews ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.whatsapp_previews FORCE ROW LEVEL SECURITY;
CREATE POLICY whatsapp_previews_business_isolation ON public.whatsapp_previews
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE public.whatsapp_message_deliveries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.whatsapp_message_deliveries FORCE ROW LEVEL SECURITY;
CREATE POLICY whatsapp_deliveries_business_isolation ON public.whatsapp_message_deliveries
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE public.whatsapp_delivery_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.whatsapp_delivery_audit FORCE ROW LEVEL SECURITY;
CREATE POLICY whatsapp_audit_business_isolation ON public.whatsapp_delivery_audit
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

GRANT SELECT, INSERT, DELETE ON TABLE public.whatsapp_previews TO tino_app;
GRANT SELECT, INSERT, UPDATE ON TABLE public.whatsapp_message_deliveries TO tino_app;
GRANT SELECT, INSERT ON TABLE public.whatsapp_delivery_audit TO tino_app;
