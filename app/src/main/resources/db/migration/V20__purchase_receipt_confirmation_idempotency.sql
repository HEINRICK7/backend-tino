CREATE TABLE public.purchase_receipt_confirmation_idempotency (
    business_id UUID NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    preview_id UUID NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    receipt_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT purchase_receipt_confirmation_idempotency_pk PRIMARY KEY (business_id, idempotency_key),
    CONSTRAINT purchase_receipt_confirmation_idempotency_preview_fk FOREIGN KEY (business_id, preview_id)
        REFERENCES public.receiving_purchase_previews (business_id, id),
    CONSTRAINT purchase_receipt_confirmation_idempotency_receipt_fk FOREIGN KEY (business_id, receipt_id)
        REFERENCES public.purchase_receipts (business_id, id)
);

ALTER TABLE public.purchase_receipt_confirmation_idempotency ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.purchase_receipt_confirmation_idempotency FORCE ROW LEVEL SECURITY;
CREATE POLICY purchase_receipt_confirmation_idempotency_business_isolation
    ON public.purchase_receipt_confirmation_idempotency
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);
GRANT SELECT, INSERT ON TABLE public.purchase_receipt_confirmation_idempotency TO tino_app;
