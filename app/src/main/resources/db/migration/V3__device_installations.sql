-- M4 registers logical application installations as tenant-owned records.
CREATE TABLE public.device_installations (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    installation_external_id VARCHAR(200) NOT NULL,
    status VARCHAR(16) NOT NULL,
    registered_by_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT device_installations_business_fk
        FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT device_installations_registered_by_user_fk
        FOREIGN KEY (registered_by_user_id) REFERENCES public.users (id),
    CONSTRAINT device_installations_status_check
        CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT device_installations_external_id_key
        UNIQUE (installation_external_id)
);

CREATE INDEX device_installations_business_id_idx
    ON public.device_installations (business_id);

-- Device installations are tenant-owned.  The transaction-local context is
-- required for both reads and writes, including for the table owner.
ALTER TABLE public.device_installations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.device_installations FORCE ROW LEVEL SECURITY;
CREATE POLICY device_installations_business_isolation
    ON public.device_installations
    USING (
        business_id = nullif(current_setting('app.business_id', true), '')::uuid
    )
    WITH CHECK (
        business_id = nullif(current_setting('app.business_id', true), '')::uuid
    );

GRANT SELECT, INSERT ON TABLE public.device_installations TO tino_app;
