CREATE TABLE public.goods_receipt_previews (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    document_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT previews_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT previews_document_fk FOREIGN KEY (business_id, document_id) REFERENCES public.nfe_documents (business_id, id),
    CONSTRAINT previews_business_id_key UNIQUE (business_id, id),
    CONSTRAINT previews_status_check CHECK (status IN ('DRAFT','REVIEW_REQUIRED','READY','CONFIRMED','CANCELLED')),
    CONSTRAINT previews_document_unique UNIQUE (business_id, document_id)
);
CREATE TABLE public.goods_receipt_preview_items (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    preview_id UUID NOT NULL,
    line_number INTEGER NOT NULL,
    resolution_status VARCHAR(24) NOT NULL,
    product_id UUID,
    candidate_name VARCHAR(500) NOT NULL,
    purchase_unit VARCHAR(32) NOT NULL,
    purchase_quantity NUMERIC(24,9) NOT NULL,
    base_unit VARCHAR(32),
    conversion_factor NUMERIC(24,9),
    unit_cost NUMERIC(24,9) NOT NULL,
    CONSTRAINT preview_items_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT preview_items_preview_fk FOREIGN KEY (business_id, preview_id) REFERENCES public.goods_receipt_previews (business_id, id),
    CONSTRAINT preview_items_product_fk FOREIGN KEY (business_id, product_id) REFERENCES public.products (business_id, id),
    CONSTRAINT preview_items_status_check CHECK (resolution_status IN ('MATCHED','NEW_CANDIDATE','NEEDS_REVIEW','IGNORED')),
    CONSTRAINT preview_items_unique UNIQUE (business_id, preview_id, line_number)
);
CREATE TABLE public.goods_receipts (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    document_id UUID NOT NULL,
    preview_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    confirmed_by UUID NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT receipts_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT receipts_document_fk FOREIGN KEY (business_id, document_id) REFERENCES public.nfe_documents (business_id, id),
    CONSTRAINT receipts_preview_fk FOREIGN KEY (business_id, preview_id) REFERENCES public.goods_receipt_previews (business_id, id),
    CONSTRAINT receipts_business_id_key UNIQUE (business_id, id),
    CONSTRAINT receipts_status_check CHECK (status IN ('CONFIRMED','CANCELLED')),
    CONSTRAINT receipts_document_unique UNIQUE (business_id, document_id),
    CONSTRAINT receipts_preview_unique UNIQUE (business_id, preview_id)
);
CREATE TABLE public.goods_receipt_items (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    receipt_id UUID NOT NULL,
    line_number INTEGER NOT NULL,
    product_id UUID,
    purchase_quantity NUMERIC(24,9) NOT NULL,
    conversion_factor NUMERIC(24,9),
    stock_quantity NUMERIC(24,9),
    unit_cost NUMERIC(24,9) NOT NULL,
    CONSTRAINT receipt_items_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT receipt_items_receipt_fk FOREIGN KEY (business_id, receipt_id) REFERENCES public.goods_receipts (business_id, id),
    CONSTRAINT receipt_items_product_fk FOREIGN KEY (business_id, product_id) REFERENCES public.products (business_id, id),
    CONSTRAINT receipt_items_unique UNIQUE (business_id, receipt_id, line_number)
);
CREATE TABLE public.inventory_movements (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    product_id UUID NOT NULL,
    receipt_id UUID NOT NULL,
    quantity NUMERIC(24,9) NOT NULL,
    unit_cost NUMERIC(24,9) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT inventory_movements_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT inventory_movements_product_fk FOREIGN KEY (business_id, product_id) REFERENCES public.products (business_id, id),
    CONSTRAINT inventory_movements_receipt_fk FOREIGN KEY (business_id, receipt_id) REFERENCES public.goods_receipts (business_id, id),
    CONSTRAINT inventory_movements_receipt_product_unique UNIQUE (business_id, receipt_id, product_id)
);
CREATE TABLE public.inventory_balances (
    business_id UUID NOT NULL,
    product_id UUID NOT NULL,
    quantity NUMERIC(24,9) NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT inventory_balances_pk PRIMARY KEY (business_id, product_id),
    CONSTRAINT inventory_balances_business_fk FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT inventory_balances_product_fk FOREIGN KEY (business_id, product_id) REFERENCES public.products (business_id, id),
    CONSTRAINT inventory_balances_nonnegative CHECK (quantity >= 0)
);
CREATE INDEX previews_business_updated_idx ON public.goods_receipt_previews (business_id, updated_at DESC);
CREATE INDEX inventory_movements_product_idx ON public.inventory_movements (business_id, product_id, created_at);

ALTER TABLE public.goods_receipt_previews ENABLE ROW LEVEL SECURITY; ALTER TABLE public.goods_receipt_previews FORCE ROW LEVEL SECURITY;
CREATE POLICY previews_business_isolation ON public.goods_receipt_previews USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid) WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);
ALTER TABLE public.goods_receipt_preview_items ENABLE ROW LEVEL SECURITY; ALTER TABLE public.goods_receipt_preview_items FORCE ROW LEVEL SECURITY;
CREATE POLICY preview_items_business_isolation ON public.goods_receipt_preview_items USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid) WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);
ALTER TABLE public.goods_receipts ENABLE ROW LEVEL SECURITY; ALTER TABLE public.goods_receipts FORCE ROW LEVEL SECURITY;
CREATE POLICY receipts_business_isolation ON public.goods_receipts USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid) WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);
ALTER TABLE public.goods_receipt_items ENABLE ROW LEVEL SECURITY; ALTER TABLE public.goods_receipt_items FORCE ROW LEVEL SECURITY;
CREATE POLICY receipt_items_business_isolation ON public.goods_receipt_items USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid) WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);
ALTER TABLE public.inventory_movements ENABLE ROW LEVEL SECURITY; ALTER TABLE public.inventory_movements FORCE ROW LEVEL SECURITY;
CREATE POLICY inventory_movements_business_isolation ON public.inventory_movements USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid) WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);
ALTER TABLE public.inventory_balances ENABLE ROW LEVEL SECURITY; ALTER TABLE public.inventory_balances FORCE ROW LEVEL SECURITY;
CREATE POLICY inventory_balances_business_isolation ON public.inventory_balances USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid) WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);
GRANT SELECT, INSERT, UPDATE ON TABLE public.goods_receipt_previews TO tino_app;
GRANT SELECT, INSERT, UPDATE ON TABLE public.goods_receipt_preview_items TO tino_app;
GRANT SELECT, INSERT, UPDATE ON TABLE public.goods_receipts TO tino_app;
GRANT SELECT, INSERT, UPDATE ON TABLE public.goods_receipt_items TO tino_app;
GRANT SELECT, INSERT ON TABLE public.inventory_movements TO tino_app;
GRANT SELECT, INSERT, UPDATE ON TABLE public.inventory_balances TO tino_app;
