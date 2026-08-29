-- M6 stores accepted event claims, immutable changes, transactional outbox
-- records, and safe rejection records. All four tables are tenant-owned.
CREATE TABLE public.sync_event_claims (
    business_id UUID NOT NULL,
    event_id UUID NOT NULL,
    store_id VARCHAR(200) NOT NULL,
    device_id VARCHAR(200) NOT NULL,
    aggregate_id VARCHAR(200) NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    schema_version INTEGER NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT sync_event_claims_business_fk
        FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT sync_event_claims_schema_version_check
        CHECK (schema_version > 0),
    CONSTRAINT sync_event_claims_pk
        PRIMARY KEY (business_id, event_id)
);

CREATE TABLE public.sync_changes (
    sequence_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_id UUID NOT NULL,
    event_id UUID NOT NULL,
    store_id VARCHAR(200) NOT NULL,
    device_id VARCHAR(200) NOT NULL,
    aggregate_id VARCHAR(200) NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    schema_version INTEGER NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT sync_changes_business_fk
        FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT sync_changes_event_fk
        FOREIGN KEY (business_id, event_id)
        REFERENCES public.sync_event_claims (business_id, event_id),
    CONSTRAINT sync_changes_schema_version_check
        CHECK (schema_version > 0),
    CONSTRAINT sync_changes_business_event_key
        UNIQUE (business_id, event_id)
);

CREATE INDEX sync_changes_business_sequence_idx
    ON public.sync_changes (business_id, sequence_id);

CREATE TABLE public.sync_outbox (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    event_id UUID NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT sync_outbox_business_fk
        FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT sync_outbox_event_fk
        FOREIGN KEY (business_id, event_id)
        REFERENCES public.sync_event_claims (business_id, event_id),
    CONSTRAINT sync_outbox_business_event_key
        UNIQUE (business_id, event_id)
);

CREATE TABLE public.sync_event_rejections (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    event_id UUID,
    device_id VARCHAR(200) NOT NULL,
    code VARCHAR(64) NOT NULL,
    retryable BOOLEAN NOT NULL,
    message VARCHAR(240) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT sync_event_rejections_business_fk
        FOREIGN KEY (business_id) REFERENCES public.businesses (id)
);

CREATE INDEX sync_event_rejections_business_created_idx
    ON public.sync_event_rejections (business_id, created_at);

ALTER TABLE public.sync_event_claims ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sync_event_claims FORCE ROW LEVEL SECURITY;
CREATE POLICY sync_event_claims_business_isolation
    ON public.sync_event_claims
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE public.sync_changes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sync_changes FORCE ROW LEVEL SECURITY;
CREATE POLICY sync_changes_business_isolation
    ON public.sync_changes
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE public.sync_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sync_outbox FORCE ROW LEVEL SECURITY;
CREATE POLICY sync_outbox_business_isolation
    ON public.sync_outbox
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE public.sync_event_rejections ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sync_event_rejections FORCE ROW LEVEL SECURITY;
CREATE POLICY sync_event_rejections_business_isolation
    ON public.sync_event_rejections
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

GRANT SELECT, INSERT ON TABLE public.sync_event_claims TO tino_app;
GRANT SELECT, INSERT ON TABLE public.sync_changes TO tino_app;
GRANT SELECT, INSERT ON TABLE public.sync_outbox TO tino_app;
GRANT SELECT, INSERT ON TABLE public.sync_event_rejections TO tino_app;
GRANT USAGE, SELECT ON SEQUENCE public.sync_changes_sequence_id_seq TO tino_app;
