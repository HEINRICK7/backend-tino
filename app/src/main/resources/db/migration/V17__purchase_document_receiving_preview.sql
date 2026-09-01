CREATE TABLE public.purchase_documents (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    source VARCHAR(16) NOT NULL,
    document_type VARCHAR(16) NOT NULL,
    access_key VARCHAR(44) NOT NULL,
    issued_at TIMESTAMPTZ,
    issuer_name VARCHAR(500),
    issuer_tax_id VARCHAR(32),
    total NUMERIC(24,9),
    payload_sha256 VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT purchase_documents_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT purchase_documents_business_id_key UNIQUE (business_id, id),
    CONSTRAINT purchase_documents_access_key_key UNIQUE (business_id, access_key),
    CONSTRAINT purchase_documents_source_check CHECK (source IN ('NFCE', 'NFE')),
    CONSTRAINT purchase_documents_type_check CHECK (document_type IN ('NFCE', 'NFE')),
    CONSTRAINT purchase_documents_access_key_check CHECK (length(access_key) = 44),
    CONSTRAINT purchase_documents_total_check CHECK (total IS NULL OR total >= 0)
);

CREATE TABLE public.purchase_document_items (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    document_id UUID NOT NULL,
    line_number INTEGER NOT NULL,
    external_code VARCHAR(200),
    gtin VARCHAR(32),
    raw_description VARCHAR(500) NOT NULL,
    quantity NUMERIC(24,9),
    unit VARCHAR(32),
    unit_price NUMERIC(24,9),
    total_price NUMERIC(24,9),
    CONSTRAINT purchase_items_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT purchase_items_document_fk FOREIGN KEY (business_id, document_id) REFERENCES public.purchase_documents (business_id, id),
    CONSTRAINT purchase_items_line_key UNIQUE (business_id, document_id, line_number),
    CONSTRAINT purchase_items_line_check CHECK (line_number > 0),
    CONSTRAINT purchase_items_quantity_check CHECK (quantity IS NULL OR quantity > 0),
    CONSTRAINT purchase_items_unit_price_check CHECK (unit_price IS NULL OR unit_price >= 0),
    CONSTRAINT purchase_items_total_price_check CHECK (total_price IS NULL OR total_price >= 0)
);

CREATE TABLE public.receiving_purchase_previews (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    document_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT receiving_previews_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT receiving_previews_document_fk FOREIGN KEY (business_id, document_id) REFERENCES public.purchase_documents (business_id, id),
    CONSTRAINT receiving_previews_business_id_key UNIQUE (business_id, id),
    CONSTRAINT receiving_previews_document_key UNIQUE (business_id, document_id),
    CONSTRAINT receiving_previews_status_check CHECK (status IN ('REVIEW_READY', 'CONFIRMED', 'CANCELLED'))
);

CREATE TABLE public.receiving_purchase_preview_items (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    preview_id UUID NOT NULL,
    line_number INTEGER NOT NULL,
    external_code VARCHAR(200),
    gtin VARCHAR(32),
    raw_description VARCHAR(500) NOT NULL,
    quantity NUMERIC(24,9),
    unit VARCHAR(32),
    unit_price NUMERIC(24,9),
    total_price NUMERIC(24,9),
    CONSTRAINT receiving_preview_items_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT receiving_preview_items_preview_fk FOREIGN KEY (business_id, preview_id) REFERENCES public.receiving_purchase_previews (business_id, id),
    CONSTRAINT receiving_preview_items_line_key UNIQUE (business_id, preview_id, line_number),
    CONSTRAINT receiving_preview_items_line_check CHECK (line_number > 0),
    CONSTRAINT receiving_preview_items_quantity_check CHECK (quantity IS NULL OR quantity > 0),
    CONSTRAINT receiving_preview_items_unit_price_check CHECK (unit_price IS NULL OR unit_price >= 0),
    CONSTRAINT receiving_preview_items_total_price_check CHECK (total_price IS NULL OR total_price >= 0)
);

CREATE TABLE public.receiving_purchase_preview_idempotency (
    business_id UUID NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    access_key VARCHAR(44) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    preview_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT receiving_preview_idempotency_pk PRIMARY KEY (business_id, idempotency_key),
    CONSTRAINT receiving_preview_idempotency_preview_fk FOREIGN KEY (business_id, preview_id) REFERENCES public.receiving_purchase_previews (business_id, id),
    CONSTRAINT receiving_preview_idempotency_access_key_check CHECK (length(access_key) = 44)
);

CREATE INDEX purchase_documents_business_created_idx ON public.purchase_documents (business_id, created_at DESC, id);
CREATE INDEX purchase_document_items_document_idx ON public.purchase_document_items (business_id, document_id, line_number);
CREATE INDEX receiving_purchase_previews_business_updated_idx ON public.receiving_purchase_previews (business_id, updated_at DESC, id);

ALTER TABLE public.purchase_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.purchase_documents FORCE ROW LEVEL SECURITY;
CREATE POLICY purchase_documents_business_isolation ON public.purchase_documents
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);
ALTER TABLE public.purchase_document_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.purchase_document_items FORCE ROW LEVEL SECURITY;
CREATE POLICY purchase_items_business_isolation ON public.purchase_document_items
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);
ALTER TABLE public.receiving_purchase_previews ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.receiving_purchase_previews FORCE ROW LEVEL SECURITY;
CREATE POLICY receiving_purchase_previews_business_isolation ON public.receiving_purchase_previews
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);
ALTER TABLE public.receiving_purchase_preview_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.receiving_purchase_preview_items FORCE ROW LEVEL SECURITY;
CREATE POLICY receiving_preview_items_business_isolation ON public.receiving_purchase_preview_items
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);
ALTER TABLE public.receiving_purchase_preview_idempotency ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.receiving_purchase_preview_idempotency FORCE ROW LEVEL SECURITY;
CREATE POLICY receiving_preview_idempotency_business_isolation ON public.receiving_purchase_preview_idempotency
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

GRANT SELECT, INSERT ON TABLE public.purchase_documents TO tino_app;
GRANT SELECT, INSERT ON TABLE public.purchase_document_items TO tino_app;
GRANT SELECT, INSERT, UPDATE ON TABLE public.receiving_purchase_previews TO tino_app;
GRANT SELECT, INSERT ON TABLE public.receiving_purchase_preview_items TO tino_app;
GRANT SELECT, INSERT ON TABLE public.receiving_purchase_preview_idempotency TO tino_app;
