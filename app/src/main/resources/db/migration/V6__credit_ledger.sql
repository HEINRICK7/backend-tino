-- M9: tenant-owned customer credit accounts and immutable confirmed ledger.
ALTER TABLE public.customers
    ADD CONSTRAINT customers_business_id_unique UNIQUE (business_id, id);

CREATE TABLE public.credit_accounts (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    currency CHAR(3) NOT NULL,
    balance NUMERIC(19, 2) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT credit_accounts_business_fk
        FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT credit_accounts_customer_fk
        FOREIGN KEY (business_id, customer_id)
        REFERENCES public.customers (business_id, id),
    CONSTRAINT credit_accounts_pair_currency_key
        UNIQUE (business_id, customer_id, currency),
    CONSTRAINT credit_accounts_business_id_unique
        UNIQUE (business_id, id),
    CONSTRAINT credit_accounts_currency_check
        CHECK (currency = 'BRL'),
    CONSTRAINT credit_accounts_balance_check
        CHECK (balance >= 0),
    CONSTRAINT credit_accounts_version_check
        CHECK (version >= 0),
    CONSTRAINT credit_accounts_status_check
        CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE TABLE public.credit_ledger_entries (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    account_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    direction VARCHAR(8) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    reason VARCHAR(64) NOT NULL,
    compensates_entry_id UUID,
    actor_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT credit_entries_business_fk
        FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT credit_entries_account_fk
        FOREIGN KEY (business_id, account_id)
        REFERENCES public.credit_accounts (business_id, id),
    CONSTRAINT credit_entries_customer_fk
        FOREIGN KEY (business_id, customer_id)
        REFERENCES public.customers (business_id, id),
    CONSTRAINT credit_entries_compensation_fk
        FOREIGN KEY (business_id, compensates_entry_id)
        REFERENCES public.credit_ledger_entries (business_id, id),
    CONSTRAINT credit_entries_business_id_unique UNIQUE (business_id, id),
    CONSTRAINT credit_entries_direction_check
        CHECK (direction IN ('CREDIT', 'DEBIT')),
    CONSTRAINT credit_entries_amount_check
        CHECK (amount > 0 AND scale(amount) <= 2),
    CONSTRAINT credit_entries_reason_check
        CHECK (length(btrim(reason)) BETWEEN 1 AND 64),
    CONSTRAINT credit_entries_not_self_compensating_check
        CHECK (compensates_entry_id IS NULL OR compensates_entry_id <> id)
);

CREATE TABLE public.credit_idempotency_keys (
    business_id UUID NOT NULL,
    operation VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    entry_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT credit_idempotency_pk
        PRIMARY KEY (business_id, operation, idempotency_key),
    CONSTRAINT credit_idempotency_business_fk
        FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT credit_idempotency_entry_fk
        FOREIGN KEY (business_id, entry_id)
        REFERENCES public.credit_ledger_entries (business_id, id)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT credit_idempotency_operation_check
        CHECK (operation IN ('APPEND_ENTRY', 'COMPENSATE_ENTRY'))
);

CREATE TABLE public.credit_audit_records (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    operation VARCHAR(32) NOT NULL,
    entry_id UUID NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    actor_user_id UUID,
    request_fingerprint CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT credit_audit_business_fk
        FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT credit_audit_entry_fk
        FOREIGN KEY (business_id, entry_id)
        REFERENCES public.credit_ledger_entries (business_id, id),
    CONSTRAINT credit_audit_operation_check
        CHECK (operation IN ('APPEND_ENTRY', 'COMPENSATE_ENTRY'))
);

CREATE INDEX credit_accounts_business_customer_idx
    ON public.credit_accounts (business_id, customer_id);
CREATE INDEX credit_entries_business_customer_created_idx
    ON public.credit_ledger_entries (business_id, customer_id, created_at, id);
CREATE UNIQUE INDEX credit_entries_one_compensation_idx
    ON public.credit_ledger_entries (business_id, compensates_entry_id)
    WHERE compensates_entry_id IS NOT NULL;
CREATE INDEX credit_audit_business_created_idx
    ON public.credit_audit_records (business_id, created_at, id);

CREATE OR REPLACE FUNCTION public.credit_ledger_before_insert()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    account_balance NUMERIC(19, 2);
    account_customer UUID;
    original_direction VARCHAR(8);
    original_amount NUMERIC(19, 2);
    original_account UUID;
    original_customer UUID;
    original_compensation UUID;
BEGIN
    SELECT balance, customer_id
      INTO account_balance, account_customer
      FROM public.credit_accounts
     WHERE business_id = NEW.business_id
       AND id = NEW.account_id
       AND status = 'ACTIVE'
     FOR UPDATE;

    IF NOT FOUND OR account_customer <> NEW.customer_id THEN
        RAISE EXCEPTION 'credit_account_not_found' USING ERRCODE = 'P0001';
    END IF;

    IF NEW.direction = 'DEBIT' AND account_balance < NEW.amount THEN
        RAISE EXCEPTION 'credit_insufficient_balance' USING ERRCODE = 'P0001';
    END IF;

    IF NEW.compensates_entry_id IS NOT NULL THEN
        SELECT direction, amount, account_id, customer_id, compensates_entry_id
          INTO original_direction, original_amount, original_account,
               original_customer, original_compensation
          FROM public.credit_ledger_entries
         WHERE business_id = NEW.business_id
           AND id = NEW.compensates_entry_id
         ;

        IF NOT FOUND
           OR original_compensation IS NOT NULL
           OR original_account <> NEW.account_id
           OR original_customer <> NEW.customer_id
           OR original_amount <> NEW.amount
           OR (original_direction = NEW.direction)
           OR EXISTS (
               SELECT 1 FROM public.credit_ledger_entries
                WHERE business_id = NEW.business_id
                  AND compensates_entry_id = NEW.compensates_entry_id
           ) THEN
            RAISE EXCEPTION 'credit_invalid_compensation' USING ERRCODE = 'P0001';
        END IF;
    END IF;

    UPDATE public.credit_accounts
       SET balance = CASE WHEN NEW.direction = 'CREDIT'
                          THEN balance + NEW.amount
                          ELSE balance - NEW.amount END,
           version = version + 1,
           updated_at = NEW.created_at
     WHERE business_id = NEW.business_id
       AND id = NEW.account_id;

    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION public.credit_immutable_row()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'credit_ledger_is_append_only' USING ERRCODE = 'P0001';
END;
$$;

CREATE TRIGGER credit_ledger_before_insert_trigger
    BEFORE INSERT ON public.credit_ledger_entries
    FOR EACH ROW EXECUTE FUNCTION public.credit_ledger_before_insert();
CREATE TRIGGER credit_ledger_immutable_trigger
    BEFORE UPDATE OR DELETE ON public.credit_ledger_entries
    FOR EACH ROW EXECUTE FUNCTION public.credit_immutable_row();
CREATE TRIGGER credit_audit_immutable_trigger
    BEFORE UPDATE OR DELETE ON public.credit_audit_records
    FOR EACH ROW EXECUTE FUNCTION public.credit_immutable_row();

ALTER TABLE public.credit_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.credit_accounts FORCE ROW LEVEL SECURITY;
CREATE POLICY credit_accounts_business_isolation ON public.credit_accounts
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE public.credit_ledger_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.credit_ledger_entries FORCE ROW LEVEL SECURITY;
CREATE POLICY credit_entries_business_isolation ON public.credit_ledger_entries
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE public.credit_idempotency_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.credit_idempotency_keys FORCE ROW LEVEL SECURITY;
CREATE POLICY credit_idempotency_business_isolation ON public.credit_idempotency_keys
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE public.credit_audit_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.credit_audit_records FORCE ROW LEVEL SECURITY;
CREATE POLICY credit_audit_business_isolation ON public.credit_audit_records
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE ON TABLE public.credit_accounts TO tino_app;
GRANT SELECT, INSERT ON TABLE public.credit_ledger_entries TO tino_app;
GRANT SELECT, INSERT ON TABLE public.credit_idempotency_keys TO tino_app;
GRANT SELECT, INSERT ON TABLE public.credit_audit_records TO tino_app;
