CREATE TABLE public.nfe_documents (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    access_key VARCHAR(44) NOT NULL,
    retrieval_status VARCHAR(24) NOT NULL,
    fiscal_status VARCHAR(16) NOT NULL,
    issuer_document VARCHAR(32),
    document_number VARCHAR(32),
    series VARCHAR(32),
    provider VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT nfe_documents_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT nfe_documents_business_id_key UNIQUE (business_id, id),
    CONSTRAINT nfe_documents_access_key UNIQUE (business_id, access_key),
    CONSTRAINT nfe_documents_retrieval_status_check CHECK (retrieval_status IN ('PENDING','IN_PROGRESS','SUCCESS','NOT_FOUND','FAILED','OUTCOME_UNKNOWN')),
    CONSTRAINT nfe_documents_fiscal_status_check CHECK (fiscal_status IN ('AUTHORIZED','CANCELLED','DENIED','UNKNOWN'))
);

CREATE TABLE public.nfe_document_versions (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    document_id UUID NOT NULL,
    version_number BIGINT NOT NULL,
    raw_payload JSONB,
    canonical_payload JSONB,
    payload_sha256 VARCHAR(64),
    provider VARCHAR(64) NOT NULL,
    provider_version VARCHAR(64) NOT NULL,
    parser_version VARCHAR(64),
    failure_code VARCHAR(64),
    retrieved_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT nfe_versions_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT nfe_versions_document_fk FOREIGN KEY (business_id, document_id) REFERENCES public.nfe_documents (business_id, id),
    CONSTRAINT nfe_versions_document_version_key UNIQUE (business_id, document_id, version_number)
);

CREATE TABLE public.nfe_items (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    document_id UUID NOT NULL,
    line_number INTEGER NOT NULL,
    supplier_product_code VARCHAR(200),
    gtin VARCHAR(32),
    tax_gtin VARCHAR(32),
    description VARCHAR(500) NOT NULL,
    ncm VARCHAR(32),
    cest VARCHAR(32),
    cfop VARCHAR(32),
    commercial_unit VARCHAR(32) NOT NULL,
    commercial_quantity NUMERIC(24,9) NOT NULL,
    commercial_unit_price NUMERIC(24,9) NOT NULL,
    product_total NUMERIC(24,9) NOT NULL,
    tax_unit VARCHAR(32),
    tax_quantity NUMERIC(24,9),
    tax_unit_price NUMERIC(24,9),
    discount NUMERIC(24,9),
    freight NUMERIC(24,9),
    insurance NUMERIC(24,9),
    other_value NUMERIC(24,9),
    included_in_total BOOLEAN,
    CONSTRAINT nfe_items_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT nfe_items_document_fk FOREIGN KEY (business_id, document_id) REFERENCES public.nfe_documents (business_id, id),
    CONSTRAINT nfe_items_document_line_key UNIQUE (business_id, document_id, line_number)
);

CREATE TABLE public.nfe_retrieval_idempotency_keys (
    business_id UUID NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    access_key VARCHAR(44) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    document_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT nfe_retrieval_idempotency_pk PRIMARY KEY (business_id, idempotency_key),
    CONSTRAINT nfe_retrieval_idempotency_access_key_check CHECK (length(access_key) = 44)
);

CREATE INDEX nfe_documents_business_updated_idx ON public.nfe_documents (business_id, updated_at DESC, id);
CREATE INDEX nfe_items_document_idx ON public.nfe_items (business_id, document_id, line_number);

ALTER TABLE public.nfe_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.nfe_documents FORCE ROW LEVEL SECURITY;
CREATE POLICY nfe_documents_business_isolation ON public.nfe_documents
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);
ALTER TABLE public.nfe_document_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.nfe_document_versions FORCE ROW LEVEL SECURITY;
CREATE POLICY nfe_versions_business_isolation ON public.nfe_document_versions
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);
ALTER TABLE public.nfe_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.nfe_items FORCE ROW LEVEL SECURITY;
CREATE POLICY nfe_items_business_isolation ON public.nfe_items
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);
ALTER TABLE public.nfe_retrieval_idempotency_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.nfe_retrieval_idempotency_keys FORCE ROW LEVEL SECURITY;
CREATE POLICY nfe_retrieval_idempotency_business_isolation ON public.nfe_retrieval_idempotency_keys
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE ON TABLE public.nfe_documents TO tino_app;
GRANT SELECT, INSERT ON TABLE public.nfe_document_versions TO tino_app;
GRANT SELECT, INSERT, UPDATE ON TABLE public.nfe_items TO tino_app;
GRANT SELECT, INSERT ON TABLE public.nfe_retrieval_idempotency_keys TO tino_app;
