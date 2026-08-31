ALTER TABLE public.products ADD COLUMN sale_price NUMERIC(24,9);

CREATE TABLE public.external_business_connections (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    provider VARCHAR(64),
    status VARCHAR(16) NOT NULL,
    source_type VARCHAR(16) NOT NULL,
    last_successful_sync_at TIMESTAMPTZ,
    sync_cursor TEXT,
    last_sync_started_at TIMESTAMPTZ,
    last_sync_finished_at TIMESTAMPTZ,
    last_sync_error_code VARCHAR(64),
    last_sync_received INTEGER NOT NULL DEFAULT 0,
    last_sync_created INTEGER NOT NULL DEFAULT 0,
    last_sync_updated INTEGER NOT NULL DEFAULT 0,
    last_sync_deactivated INTEGER NOT NULL DEFAULT 0,
    last_sync_rejected INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT external_connections_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT external_connections_status_check CHECK (status IN ('CONNECTED','SYNCING','READY','DEGRADED','AUTH_ERROR','FAILED')),
    CONSTRAINT external_connections_source_type_check CHECK (source_type IN ('TINO_NATIVE','EXTERNAL_API')),
    CONSTRAINT external_connections_provider_check CHECK ((source_type = 'EXTERNAL_API' AND provider IS NOT NULL) OR (source_type = 'TINO_NATIVE')),
    CONSTRAINT external_connections_provider_unique UNIQUE (business_id, provider)
);

CREATE TABLE public.external_product_mappings (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    provider_connection_id UUID NOT NULL,
    external_product_id VARCHAR(255) NOT NULL,
    tino_product_id UUID NOT NULL,
    external_updated_at TIMESTAMPTZ NOT NULL,
    last_synced_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT external_mappings_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT external_mappings_connection_fk FOREIGN KEY (provider_connection_id) REFERENCES public.external_business_connections (id),
    CONSTRAINT external_mappings_product_fk FOREIGN KEY (business_id, tino_product_id) REFERENCES public.products (business_id, id),
    CONSTRAINT external_mappings_unique UNIQUE (business_id, provider_connection_id, external_product_id)
);

CREATE TABLE public.external_product_price_options (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    provider_connection_id UUID NOT NULL,
    external_product_id VARCHAR(255) NOT NULL,
    external_option_id VARCHAR(255) NOT NULL,
    label VARCHAR(500) NOT NULL,
    quantity NUMERIC(24,9) NOT NULL,
    unit VARCHAR(64) NOT NULL,
    unit_raw VARCHAR(64) NOT NULL,
    price NUMERIC(24,9) NOT NULL,
    is_default BOOLEAN NOT NULL,
    category_context VARCHAR(500),
    subcategory_context VARCHAR(500),
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT external_options_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT external_options_connection_fk FOREIGN KEY (provider_connection_id) REFERENCES public.external_business_connections (id),
    CONSTRAINT external_options_unique UNIQUE (business_id, provider_connection_id, external_product_id, external_option_id),
    CONSTRAINT external_options_quantity_check CHECK (quantity > 0),
    CONSTRAINT external_options_price_check CHECK (price >= 0)
);

CREATE INDEX external_connections_business_idx ON public.external_business_connections (business_id);
CREATE INDEX external_mappings_lookup_idx ON public.external_product_mappings (business_id, provider_connection_id, external_product_id);
CREATE INDEX external_options_lookup_idx ON public.external_product_price_options (business_id, provider_connection_id, external_product_id);

ALTER TABLE public.external_business_connections ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.external_business_connections FORCE ROW LEVEL SECURITY;
CREATE POLICY external_connections_business_isolation ON public.external_business_connections
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);
ALTER TABLE public.external_product_mappings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.external_product_mappings FORCE ROW LEVEL SECURITY;
CREATE POLICY external_mappings_business_isolation ON public.external_product_mappings
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);
ALTER TABLE public.external_product_price_options ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.external_product_price_options FORCE ROW LEVEL SECURITY;
CREATE POLICY external_options_business_isolation ON public.external_product_price_options
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE ON TABLE public.external_business_connections TO tino_app;
GRANT SELECT, INSERT, UPDATE ON TABLE public.external_product_mappings TO tino_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.external_product_price_options TO tino_app;
