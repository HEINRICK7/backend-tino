-- Merchant Pix destination and the authoritative static BR Code payload.
-- The application resolves the tenant before accessing this table; RLS is a
-- second boundary for both merchant writes and customer reads.
CREATE TABLE public.business_pix_configurations (
    business_id UUID PRIMARY KEY,
    pix_key_type VARCHAR(16) NOT NULL,
    pix_key VARCHAR(77) NOT NULL,
    copy_paste TEXT NOT NULL,
    merchant_name VARCHAR(25) NOT NULL,
    merchant_city VARCHAR(15) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT business_pix_key_type_check
        CHECK (pix_key_type IN ('RANDOM', 'EMAIL', 'PHONE', 'CPF', 'CNPJ')),
    CONSTRAINT business_pix_key_check CHECK (length(trim(pix_key)) BETWEEN 1 AND 77),
    CONSTRAINT business_pix_copy_paste_check CHECK (length(trim(copy_paste)) BETWEEN 1 AND 512),
    CONSTRAINT business_pix_merchant_name_check CHECK (length(trim(merchant_name)) BETWEEN 1 AND 25),
    CONSTRAINT business_pix_merchant_city_check CHECK (length(trim(merchant_city)) BETWEEN 1 AND 15)
);

ALTER TABLE public.business_pix_configurations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.business_pix_configurations FORCE ROW LEVEL SECURITY;
CREATE POLICY business_pix_business_isolation
    ON public.business_pix_configurations
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.business_pix_configurations TO tino_app;
