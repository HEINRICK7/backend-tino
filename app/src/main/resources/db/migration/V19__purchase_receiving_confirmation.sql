CREATE TABLE public.purchase_receipts (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    purchase_document_id UUID NOT NULL,
    preview_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    confirmed_by UUID NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT purchase_receipts_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT purchase_receipts_document_fk FOREIGN KEY (business_id, purchase_document_id)
        REFERENCES public.purchase_documents (business_id, id),
    CONSTRAINT purchase_receipts_preview_fk FOREIGN KEY (business_id, preview_id)
        REFERENCES public.receiving_purchase_previews (business_id, id),
    CONSTRAINT purchase_receipts_status_check CHECK (status IN ('CONFIRMED','CANCELLED')),
    CONSTRAINT purchase_receipts_business_id_unique UNIQUE (business_id, id),
    CONSTRAINT purchase_receipts_document_unique UNIQUE (business_id, purchase_document_id),
    CONSTRAINT purchase_receipts_preview_unique UNIQUE (business_id, preview_id)
);

CREATE TABLE public.purchase_receipt_items (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    receipt_id UUID NOT NULL,
    line_number INTEGER NOT NULL,
    product_id UUID,
    match_status VARCHAR(32) NOT NULL,
    raw_description VARCHAR(500) NOT NULL,
    external_code VARCHAR(200),
    gtin VARCHAR(32),
    quantity NUMERIC(24,9),
    unit VARCHAR(32),
    unit_price NUMERIC(24,9),
    total_price NUMERIC(24,9),
    stock_quantity NUMERIC(24,9),
    base_unit VARCHAR(32),
    conversion_factor NUMERIC(24,9),
    CONSTRAINT purchase_receipt_items_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT purchase_receipt_items_receipt_fk FOREIGN KEY (business_id, receipt_id)
        REFERENCES public.purchase_receipts (business_id, id),
    CONSTRAINT purchase_receipt_items_product_fk FOREIGN KEY (business_id, product_id)
        REFERENCES public.products (business_id, id),
    CONSTRAINT purchase_receipt_items_status_check CHECK
        (match_status IN ('EXACT_MATCH','HIGH_CONFIDENCE_MATCH','REVIEW_REQUIRED','NEW_PRODUCT','IGNORED')),
    CONSTRAINT purchase_receipt_items_unique UNIQUE (business_id, receipt_id, line_number)
);

CREATE TABLE public.purchase_price_observations (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    product_id UUID NOT NULL,
    receipt_id UUID NOT NULL,
    issuer_tax_id VARCHAR(32),
    unit_price NUMERIC(24,9) NOT NULL,
    quantity NUMERIC(24,9),
    unit VARCHAR(32),
    observed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT purchase_price_observations_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT purchase_price_observations_product_fk FOREIGN KEY (business_id, product_id)
        REFERENCES public.products (business_id, id),
    CONSTRAINT purchase_price_observations_receipt_fk FOREIGN KEY (business_id, receipt_id)
        REFERENCES public.purchase_receipts (business_id, id)
);

CREATE TABLE public.receiving_events (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    receipt_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT receiving_events_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT receiving_events_receipt_fk FOREIGN KEY (business_id, receipt_id)
        REFERENCES public.purchase_receipts (business_id, id)
);

ALTER TABLE public.inventory_movements ALTER COLUMN receipt_id DROP NOT NULL;
ALTER TABLE public.inventory_movements ADD COLUMN purchase_receipt_id UUID;
ALTER TABLE public.inventory_movements ADD CONSTRAINT inventory_movements_purchase_receipt_fk
    FOREIGN KEY (business_id, purchase_receipt_id) REFERENCES public.purchase_receipts (business_id, id);
ALTER TABLE public.inventory_movements ADD CONSTRAINT inventory_movements_one_receipt_check
    CHECK ((receipt_id IS NOT NULL) <> (purchase_receipt_id IS NOT NULL));
CREATE UNIQUE INDEX inventory_movements_purchase_receipt_product_unique
    ON public.inventory_movements (business_id, purchase_receipt_id, product_id);

CREATE INDEX purchase_receipts_business_created_idx ON public.purchase_receipts (business_id, created_at DESC);
CREATE INDEX purchase_price_observations_product_idx ON public.purchase_price_observations (business_id, product_id, observed_at DESC);
CREATE INDEX receiving_events_receipt_idx ON public.receiving_events (business_id, receipt_id, created_at);

ALTER TABLE public.purchase_receipts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.purchase_receipts FORCE ROW LEVEL SECURITY;
CREATE POLICY purchase_receipts_business_isolation ON public.purchase_receipts
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);
ALTER TABLE public.purchase_receipt_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.purchase_receipt_items FORCE ROW LEVEL SECURITY;
CREATE POLICY purchase_receipt_items_business_isolation ON public.purchase_receipt_items
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);
ALTER TABLE public.purchase_price_observations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.purchase_price_observations FORCE ROW LEVEL SECURITY;
CREATE POLICY purchase_price_observations_business_isolation ON public.purchase_price_observations
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);
ALTER TABLE public.receiving_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.receiving_events FORCE ROW LEVEL SECURITY;
CREATE POLICY receiving_events_business_isolation ON public.receiving_events
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE ON TABLE public.purchase_receipts TO tino_app;
GRANT SELECT, INSERT, UPDATE ON TABLE public.purchase_receipt_items TO tino_app;
GRANT SELECT, INSERT ON TABLE public.purchase_price_observations TO tino_app;
GRANT SELECT, INSERT ON TABLE public.receiving_events TO tino_app;
GRANT UPDATE ON TABLE public.inventory_movements TO tino_app;
