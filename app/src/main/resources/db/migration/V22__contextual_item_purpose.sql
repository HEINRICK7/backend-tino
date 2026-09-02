ALTER TABLE public.business_item_purposes
    ADD COLUMN usage_context VARCHAR(64),
    ADD COLUMN evidence_classified_by VARCHAR(128),
    ADD COLUMN evidence_reason VARCHAR(500),
    ADD COLUMN evidence_at TIMESTAMPTZ;

UPDATE public.business_item_purposes
SET usage_context = 'LEGACY',
    evidence_classified_by = 'MIGRATION',
    evidence_reason = 'Migrated from the legacy item-purpose model',
    evidence_at = last_observed_at;

ALTER TABLE public.business_item_purposes
    ALTER COLUMN usage_context SET NOT NULL,
    ALTER COLUMN evidence_classified_by SET NOT NULL,
    ALTER COLUMN evidence_reason SET NOT NULL,
    ALTER COLUMN evidence_at SET NOT NULL;

ALTER TABLE public.business_item_purposes
    DROP CONSTRAINT business_item_purposes_product_unique,
    DROP CONSTRAINT business_item_purposes_key_unique;

ALTER TABLE public.business_item_purposes
    ADD CONSTRAINT business_item_purposes_product_unique
        UNIQUE (business_id, product_id, usage_context),
    ADD CONSTRAINT business_item_purposes_key_unique
        UNIQUE (business_id, canonical_item_key, usage_context),
    ADD CONSTRAINT business_item_purposes_usage_context_check
        CHECK (usage_context ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    ADD CONSTRAINT business_item_purposes_evidence_classified_by_check
        CHECK (length(btrim(evidence_classified_by)) BETWEEN 1 AND 128),
    ADD CONSTRAINT business_item_purposes_evidence_reason_check
        CHECK (length(btrim(evidence_reason)) BETWEEN 1 AND 500);
