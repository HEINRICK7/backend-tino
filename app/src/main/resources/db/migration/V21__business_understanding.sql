CREATE TABLE public.business_activities (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    activity_code VARCHAR(32) NOT NULL,
    custom_label VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT business_activities_business_fk
        FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT business_activities_unique
        UNIQUE (business_id, activity_code),
    CONSTRAINT business_activities_code_check CHECK (
        activity_code IN ('MERCADINHO', 'ACOUGUE', 'VERDUREIRA', 'PADARIA',
            'CONFEITARIA', 'RESTAURANTE', 'LANCHONETE', 'SALAO_BELEZA',
            'OFICINA', 'ENCOMENDAS', 'OTHER')),
    CONSTRAINT business_activities_other_label_check CHECK (
        (activity_code = 'OTHER' AND custom_label IS NOT NULL AND btrim(custom_label) <> '')
        OR (activity_code <> 'OTHER' AND custom_label IS NULL))
);

CREATE INDEX business_activities_business_idx
    ON public.business_activities (business_id, activity_code);

CREATE TABLE public.business_operating_modes (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    mode_code VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL,
    confidence NUMERIC(5,4),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT business_operating_modes_business_fk
        FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT business_operating_modes_unique
        UNIQUE (business_id, mode_code),
    CONSTRAINT business_operating_modes_code_check CHECK (
        mode_code IN ('RESELLS_GOODS', 'PRODUCES_GOODS', 'PROVIDES_SERVICES', 'BUYS_INPUTS')),
    CONSTRAINT business_operating_modes_source_check CHECK (
        source IN ('USER_DECLARED', 'SYSTEM_INFERRED', 'MIGRATED')),
    CONSTRAINT business_operating_modes_confidence_check CHECK (
        confidence IS NULL OR (confidence >= 0 AND confidence <= 1))
);

CREATE INDEX business_operating_modes_business_idx
    ON public.business_operating_modes (business_id, mode_code);

CREATE TABLE public.business_item_purposes (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    product_id UUID,
    canonical_item_key VARCHAR(500),
    purpose VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL,
    confidence NUMERIC(5,4) NOT NULL,
    evidence_count BIGINT NOT NULL DEFAULT 0,
    first_observed_at TIMESTAMPTZ NOT NULL,
    last_observed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT business_item_purposes_business_fk
        FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT business_item_purposes_product_fk
        FOREIGN KEY (business_id, product_id) REFERENCES public.products (business_id, id),
    CONSTRAINT business_item_purposes_identity_check CHECK (
        (product_id IS NOT NULL AND canonical_item_key IS NULL)
        OR (product_id IS NULL AND canonical_item_key IS NOT NULL AND btrim(canonical_item_key) <> '')),
    CONSTRAINT business_item_purposes_product_unique UNIQUE (business_id, product_id),
    CONSTRAINT business_item_purposes_key_unique UNIQUE (business_id, canonical_item_key),
    CONSTRAINT business_item_purposes_purpose_check CHECK (
        purpose IN ('RESALE', 'PRODUCTION', 'SERVICE_INPUT', 'BUSINESS_USE', 'ASSET', 'UNKNOWN')),
    CONSTRAINT business_item_purposes_source_check CHECK (
        source IN ('USER_CONFIRMED', 'LEARNED', 'SYSTEM_SUGGESTED', 'MIGRATED')),
    CONSTRAINT business_item_purposes_confidence_check CHECK (confidence >= 0 AND confidence <= 1),
    CONSTRAINT business_item_purposes_evidence_check CHECK (evidence_count >= 0)
);

CREATE INDEX business_item_purposes_business_idx
    ON public.business_item_purposes (business_id, updated_at DESC);

ALTER TABLE public.business_activities ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.business_activities FORCE ROW LEVEL SECURITY;
CREATE POLICY business_activities_business_isolation ON public.business_activities
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE public.business_operating_modes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.business_operating_modes FORCE ROW LEVEL SECURITY;
CREATE POLICY business_operating_modes_business_isolation ON public.business_operating_modes
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE public.business_item_purposes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.business_item_purposes FORCE ROW LEVEL SECURITY;
CREATE POLICY business_item_purposes_business_isolation ON public.business_item_purposes
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.business_activities TO tino_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.business_operating_modes TO tino_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.business_item_purposes TO tino_app;
