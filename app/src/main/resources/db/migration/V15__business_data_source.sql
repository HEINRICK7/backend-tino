ALTER TABLE public.businesses
    ADD COLUMN data_source_type VARCHAR(16) NOT NULL DEFAULT 'TINO_NATIVE';

ALTER TABLE public.businesses
    ADD CONSTRAINT businesses_data_source_type_check
        CHECK (data_source_type IN ('TINO_NATIVE', 'EXTERNAL_API'));

-- Existing external connections are the only historical signal available during
-- this one-time migration. Future writes use businesses.data_source_type explicitly.
UPDATE public.businesses b
SET data_source_type = 'EXTERNAL_API'
WHERE EXISTS (
    SELECT 1
    FROM public.external_business_connections c
    WHERE c.business_id = b.id
      AND c.source_type = 'EXTERNAL_API'
);

GRANT UPDATE ON TABLE public.businesses TO tino_app;
