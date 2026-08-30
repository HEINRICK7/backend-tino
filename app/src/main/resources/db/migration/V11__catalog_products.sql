CREATE TABLE public.products (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    name VARCHAR(500) NOT NULL,
    base_unit VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT products_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT products_business_id_key UNIQUE (business_id, id),
    CONSTRAINT products_status_check CHECK (status IN ('ACTIVE','ARCHIVED'))
);
CREATE TABLE public.product_identifiers (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    product_id UUID NOT NULL,
    identifier_type VARCHAR(32) NOT NULL,
    identifier_value VARCHAR(64) NOT NULL,
    source VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT product_identifiers_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT product_identifiers_product_fk FOREIGN KEY (business_id, product_id) REFERENCES public.products (business_id, id),
    CONSTRAINT product_identifiers_unique UNIQUE (business_id, identifier_type, identifier_value)
);
CREATE TABLE public.supplier_product_mappings (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    issuer_document VARCHAR(32) NOT NULL,
    supplier_product_code VARCHAR(200) NOT NULL,
    product_id UUID NOT NULL,
    confirmed_by UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT supplier_mappings_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT supplier_mappings_product_fk FOREIGN KEY (business_id, product_id) REFERENCES public.products (business_id, id),
    CONSTRAINT supplier_mappings_unique UNIQUE (business_id, issuer_document, supplier_product_code)
);
CREATE TABLE public.packaging_conversions (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    issuer_document VARCHAR(32) NOT NULL,
    supplier_product_code VARCHAR(200) NOT NULL,
    purchase_unit VARCHAR(32) NOT NULL,
    base_unit VARCHAR(32) NOT NULL,
    conversion_factor NUMERIC(24,9) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT packaging_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT packaging_factor_check CHECK (conversion_factor > 0),
    CONSTRAINT packaging_status_check CHECK (status IN ('CONFIRMED','REVOKED')),
    CONSTRAINT packaging_unique UNIQUE (business_id, issuer_document, supplier_product_code, purchase_unit, base_unit)
);
CREATE INDEX product_identifiers_lookup_idx ON public.product_identifiers (business_id, identifier_type, identifier_value);
CREATE INDEX supplier_mappings_lookup_idx ON public.supplier_product_mappings (business_id, issuer_document, supplier_product_code);
CREATE INDEX packaging_lookup_idx ON public.packaging_conversions (business_id, issuer_document, supplier_product_code, purchase_unit);

ALTER TABLE public.products ENABLE ROW LEVEL SECURITY; ALTER TABLE public.products FORCE ROW LEVEL SECURITY;
CREATE POLICY products_business_isolation ON public.products USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid) WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);
ALTER TABLE public.product_identifiers ENABLE ROW LEVEL SECURITY; ALTER TABLE public.product_identifiers FORCE ROW LEVEL SECURITY;
CREATE POLICY product_identifiers_business_isolation ON public.product_identifiers USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid) WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);
ALTER TABLE public.supplier_product_mappings ENABLE ROW LEVEL SECURITY; ALTER TABLE public.supplier_product_mappings FORCE ROW LEVEL SECURITY;
CREATE POLICY supplier_mappings_business_isolation ON public.supplier_product_mappings USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid) WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);
ALTER TABLE public.packaging_conversions ENABLE ROW LEVEL SECURITY; ALTER TABLE public.packaging_conversions FORCE ROW LEVEL SECURITY;
CREATE POLICY packaging_business_isolation ON public.packaging_conversions USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid) WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);
GRANT SELECT, INSERT, UPDATE ON TABLE public.products TO tino_app;
GRANT SELECT, INSERT, UPDATE ON TABLE public.product_identifiers TO tino_app;
GRANT SELECT, INSERT, UPDATE ON TABLE public.supplier_product_mappings TO tino_app;
GRANT SELECT, INSERT, UPDATE ON TABLE public.packaging_conversions TO tino_app;
