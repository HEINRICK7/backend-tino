CREATE TABLE public.customer_channel_activation_idempotency (
    operation VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    business_id UUID,
    customer_channel_id UUID,
    customer_id UUID,
    session_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT customer_channel_activation_idempotency_pk
        PRIMARY KEY (operation, idempotency_key),
    CONSTRAINT customer_channel_activation_idempotency_operation_check
        CHECK (operation = 'CUSTOMER_CHANNEL_ACTIVATION'),
    CONSTRAINT customer_channel_activation_idempotency_fingerprint_check
        CHECK (length(request_fingerprint) = 64),
    CONSTRAINT customer_channel_activation_idempotency_business_fk
        FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT customer_channel_activation_idempotency_channel_fk
        FOREIGN KEY (business_id, customer_channel_id)
        REFERENCES public.customer_channels (business_id, id),
    CONSTRAINT customer_channel_activation_idempotency_customer_fk
        FOREIGN KEY (business_id, customer_id)
        REFERENCES public.customers (business_id, id),
    CONSTRAINT customer_channel_activation_idempotency_session_fk
        FOREIGN KEY (session_id) REFERENCES public.customer_sessions (id)
);

-- Activation happens before a customer session and therefore before a tenant
-- context exists. The table stores only a one-way invite hash and session
-- references; the opaque invite and session tokens never reach the database.
GRANT SELECT, INSERT, UPDATE ON TABLE public.customer_channel_activation_idempotency TO tino_app;
