-- Customer Web Push subscriptions and a durable, non-financial dispatch outbox.
-- The outbox stores only routing references; notification content is created
-- at dispatch time and never contains balance or amount data.
CREATE TABLE public.customer_push_subscriptions (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    customer_channel_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    endpoint TEXT NOT NULL,
    p256dh_key TEXT NOT NULL,
    auth_key TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    last_success_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT customer_push_subscriptions_channel_fk
        FOREIGN KEY (business_id, customer_channel_id)
        REFERENCES public.customer_channels (business_id, id),
    CONSTRAINT customer_push_subscriptions_customer_fk
        FOREIGN KEY (business_id, customer_id)
        REFERENCES public.customers (business_id, id),
    CONSTRAINT customer_push_subscriptions_endpoint_check
        CHECK (length(endpoint) BETWEEN 1 AND 2048),
    CONSTRAINT customer_push_subscriptions_p256dh_check
        CHECK (length(p256dh_key) BETWEEN 80 AND 100),
    CONSTRAINT customer_push_subscriptions_auth_check
        CHECK (length(auth_key) BETWEEN 16 AND 32),
    CONSTRAINT customer_push_subscriptions_unique_endpoint
        UNIQUE (business_id, customer_channel_id, endpoint)
);

CREATE INDEX customer_push_subscriptions_customer_idx
    ON public.customer_push_subscriptions (business_id, customer_id)
    WHERE revoked_at IS NULL;

ALTER TABLE public.customer_push_subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.customer_push_subscriptions FORCE ROW LEVEL SECURITY;
CREATE POLICY customer_push_subscriptions_business_isolation
    ON public.customer_push_subscriptions
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE ON TABLE public.customer_push_subscriptions TO tino_app;

CREATE TABLE public.customer_push_outbox (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    activity_id UUID NOT NULL,
    kind VARCHAR(8) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    available_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    last_error VARCHAR(240),
    CONSTRAINT customer_push_outbox_kind_check
        CHECK (kind IN ('CREDIT', 'DEBIT')),
    CONSTRAINT customer_push_outbox_attempts_check CHECK (attempts >= 0),
    CONSTRAINT customer_push_outbox_unique_activity UNIQUE (business_id, activity_id)
);

CREATE INDEX customer_push_outbox_due_idx
    ON public.customer_push_outbox (available_at, locked_until, created_at)
    WHERE delivered_at IS NULL;

-- The dispatcher is an internal backend worker. Its table has no financial
-- payload and is readable only by the application role; API code never gets a
-- route to it. The IDs are created immediately after the ledger insert in the
-- same transaction, so FKs here would unnecessarily block the existing test
-- and maintenance TRUNCATE routines. Subscription and financial tables keep
-- their tenant isolation and strong FKs.
GRANT SELECT, INSERT, UPDATE ON TABLE public.customer_push_outbox TO tino_app;
