CREATE TABLE public.identity_phone_bindings (
    phone_hash CHAR(64) PRIMARY KEY,
    external_subject VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

GRANT SELECT, INSERT, UPDATE ON TABLE public.identity_phone_bindings TO tino_app;

CREATE INDEX identity_phone_bindings_subject_idx
    ON public.identity_phone_bindings (external_subject);
