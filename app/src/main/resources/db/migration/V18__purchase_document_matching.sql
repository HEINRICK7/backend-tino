ALTER TABLE public.receiving_purchase_preview_items
    ADD COLUMN match_status VARCHAR(32) NOT NULL DEFAULT 'REVIEW_REQUIRED',
    ADD COLUMN matched_product_id UUID,
    ADD COLUMN candidate_name VARCHAR(500),
    ADD COLUMN base_unit VARCHAR(32),
    ADD COLUMN match_confidence NUMERIC(8,4),
    ADD COLUMN requires_user_action BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE public.receiving_purchase_preview_items
    ADD CONSTRAINT purchase_preview_items_match_status_check
        CHECK (match_status IN ('EXACT_MATCH','HIGH_CONFIDENCE_MATCH','REVIEW_REQUIRED','NEW_PRODUCT')),
    ADD CONSTRAINT purchase_preview_items_product_fk
        FOREIGN KEY (business_id, matched_product_id)
        REFERENCES public.products (business_id, id);

CREATE INDEX purchase_preview_items_match_idx
    ON public.receiving_purchase_preview_items (business_id, preview_id, match_status);
