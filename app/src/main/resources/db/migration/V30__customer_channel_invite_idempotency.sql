CREATE TABLE public.customer_channel_invite_idempotency (
    business_id UUID NOT NULL,
    operation VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    customer_channel_id UUID NOT NULL,
    response_status VARCHAR(16),
    delivery_status VARCHAR(16),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT customer_channel_invite_idempotency_pk
        PRIMARY KEY (business_id, operation, idempotency_key),
    CONSTRAINT customer_channel_invite_idempotency_operation_check
        CHECK (operation = 'CUSTOMER_CHANNEL_INVITE'),
    CONSTRAINT customer_channel_invite_idempotency_fingerprint_check
        CHECK (length(request_fingerprint) = 64),
    CONSTRAINT customer_channel_invite_idempotency_response_check
        CHECK (response_status IS NULL OR response_status = 'INVITED'),
    CONSTRAINT customer_channel_invite_idempotency_delivery_check
        CHECK (delivery_status IS NULL OR delivery_status IN ('QUEUED', 'FAILED'))
);

ALTER TABLE public.customer_channel_invite_idempotency ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.customer_channel_invite_idempotency FORCE ROW LEVEL SECURITY;
CREATE POLICY customer_channel_invite_idempotency_business_isolation
    ON public.customer_channel_invite_idempotency
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE ON TABLE public.customer_channel_invite_idempotency TO tino_app;
