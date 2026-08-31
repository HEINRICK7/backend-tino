CREATE TABLE public.otp_challenges (
    id UUID PRIMARY KEY,
    phone_e164 VARCHAR(16) NOT NULL,
    phone_hash CHAR(64) NOT NULL,
    request_origin_hash CHAR(64),
    code_verifier CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL,
    resend_count INTEGER NOT NULL DEFAULT 0,
    max_resends INTEGER NOT NULL,
    resend_available_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    verified_at TIMESTAMPTZ,
    consumed_at TIMESTAMPTZ,
    verification_ticket_hash CHAR(64),
    verification_ticket_expires_at TIMESTAMPTZ,
    CONSTRAINT otp_challenges_phone_check
        CHECK (phone_e164 ~ '^\+55[1-9][0-9](9[0-9]{8}|[2-5][0-9]{7})$'),
    CONSTRAINT otp_challenges_status_check
        CHECK (status IN ('PENDING', 'VERIFIED', 'EXPIRED', 'LOCKED', 'CONSUMED', 'DELIVERY_FAILED')),
    CONSTRAINT otp_challenges_attempts_check
        CHECK (attempt_count >= 0 AND max_attempts > 0 AND attempt_count <= max_attempts),
    CONSTRAINT otp_challenges_resends_check
        CHECK (resend_count >= 0 AND max_resends >= 0 AND resend_count <= max_resends),
    CONSTRAINT otp_challenges_ticket_check
        CHECK ((verification_ticket_hash IS NULL) = (verification_ticket_expires_at IS NULL))
);

CREATE INDEX otp_challenges_phone_created_idx
    ON public.otp_challenges (phone_hash, created_at DESC);

CREATE INDEX otp_challenges_origin_created_idx
    ON public.otp_challenges (request_origin_hash, created_at DESC)
    WHERE request_origin_hash IS NOT NULL;

CREATE INDEX otp_challenges_pending_phone_idx
    ON public.otp_challenges (phone_hash, created_at DESC)
    WHERE status = 'PENDING';

CREATE UNIQUE INDEX otp_challenges_ticket_hash_key
    ON public.otp_challenges (verification_ticket_hash)
    WHERE verification_ticket_hash IS NOT NULL;

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.otp_challenges TO tino_app;
