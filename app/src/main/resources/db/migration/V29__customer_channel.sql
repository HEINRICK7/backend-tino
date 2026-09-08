CREATE TABLE public.customer_channels (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    activated_at TIMESTAMPTZ,
    last_access_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT customer_channels_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT customer_channels_customer_fk FOREIGN KEY (business_id, customer_id)
        REFERENCES public.customers (business_id, id),
    CONSTRAINT customer_channels_pair_unique UNIQUE (business_id, customer_id),
    CONSTRAINT customer_channels_status_check CHECK (status IN ('INVITED', 'ACTIVE', 'SUSPENDED', 'REVOKED')),
    CONSTRAINT customer_channels_business_id_unique UNIQUE (business_id, id)
);

CREATE INDEX customer_channels_business_status_idx ON public.customer_channels (business_id, status);

CREATE TABLE public.customer_invites (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    customer_channel_id UUID NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT customer_invites_token_unique UNIQUE (token_hash),
    CONSTRAINT customer_invites_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT customer_invites_channel_fk FOREIGN KEY (business_id, customer_channel_id)
        REFERENCES public.customer_channels (business_id, id)
);

CREATE INDEX customer_invites_channel_state_idx ON public.customer_invites
    (business_id, customer_channel_id, expires_at);

CREATE TABLE public.customer_sessions (
    id UUID PRIMARY KEY,
    customer_channel_id UUID NOT NULL,
    business_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    session_token_hash VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT customer_sessions_token_unique UNIQUE (session_token_hash),
    CONSTRAINT customer_sessions_channel_fk FOREIGN KEY (business_id, customer_channel_id)
        REFERENCES public.customer_channels (business_id, id),
    CONSTRAINT customer_sessions_customer_fk FOREIGN KEY (business_id, customer_id)
        REFERENCES public.customers (business_id, id)
);

CREATE INDEX customer_sessions_channel_state_idx ON public.customer_sessions
    (customer_channel_id, expires_at);

-- Activation and cookie authentication must locate a record before a tenant
-- context exists. Every subsequent customer-channel query carries the
-- business/customer/channel tuple resolved from the opaque server-side token;
-- financial tables remain protected by their existing RLS policies.
GRANT SELECT, INSERT, UPDATE ON TABLE public.customer_channels TO tino_app;
GRANT SELECT, INSERT, UPDATE ON TABLE public.customer_invites TO tino_app;
GRANT SELECT, INSERT, UPDATE ON TABLE public.customer_sessions TO tino_app;
