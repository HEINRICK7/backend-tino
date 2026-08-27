CREATE TABLE public.users (
    id UUID PRIMARY KEY,
    external_subject VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT users_external_subject_key UNIQUE (external_subject),
    CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'DISABLED'))
);

GRANT SELECT, INSERT ON TABLE public.users TO tino_app;
