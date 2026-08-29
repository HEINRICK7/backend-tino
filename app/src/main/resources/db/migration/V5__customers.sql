-- M8 minimal tenant-owned customer model: no CPF, address, documents or enrichment.
CREATE TABLE public.customers (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    nickname VARCHAR(100),
    phone VARCHAR(32),
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT customers_business_fk
        FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT customers_status_check
        CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT customers_name_not_blank
        CHECK (length(btrim(name)) > 0),
    CONSTRAINT customers_nickname_not_blank
        CHECK (nickname IS NULL OR length(btrim(nickname)) > 0),
    CONSTRAINT customers_phone_not_blank
        CHECK (phone IS NULL OR length(btrim(phone)) > 0)
);

CREATE INDEX customers_business_updated_idx
    ON public.customers (business_id, updated_at, id);

CREATE TABLE public.customer_idempotency_keys (
    business_id UUID NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    customer_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT customer_idempotency_pk
        PRIMARY KEY (business_id, idempotency_key),
    CONSTRAINT customer_idempotency_business_fk
        FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT customer_idempotency_customer_fk
        FOREIGN KEY (customer_id) REFERENCES public.customers (id)
);

ALTER TABLE public.customers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.customers FORCE ROW LEVEL SECURITY;
CREATE POLICY customers_business_isolation ON public.customers
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE public.customer_idempotency_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.customer_idempotency_keys FORCE ROW LEVEL SECURITY;
CREATE POLICY customer_idempotency_business_isolation ON public.customer_idempotency_keys
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.customers TO tino_app;
GRANT SELECT, INSERT ON TABLE public.customer_idempotency_keys TO tino_app;
