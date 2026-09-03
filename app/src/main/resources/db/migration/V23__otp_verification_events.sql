CREATE TABLE public.otp_verification_events (
    provider_event_id VARCHAR(200) PRIMARY KEY,
    challenge_id UUID NOT NULL REFERENCES public.otp_challenges(id) ON DELETE CASCADE,
    provider_message_id VARCHAR(200) NOT NULL,
    sender_phone_e164 VARCHAR(16) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT otp_verification_events_phone_check
        CHECK (sender_phone_e164 ~ '^\+55[1-9][0-9](9[0-9]{8}|[2-5][0-9]{7})$')
);

CREATE INDEX otp_verification_events_challenge_idx
    ON public.otp_verification_events (challenge_id, received_at DESC);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.otp_verification_events TO tino_app;
