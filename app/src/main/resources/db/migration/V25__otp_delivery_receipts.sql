ALTER TABLE public.otp_challenges
    ADD COLUMN provider_message_id VARCHAR(200);

CREATE UNIQUE INDEX otp_challenges_provider_message_key
    ON public.otp_challenges (provider_message_id)
    WHERE provider_message_id IS NOT NULL;

ALTER TABLE public.otp_challenges
    DROP CONSTRAINT otp_challenges_status_check;

ALTER TABLE public.otp_challenges
    ADD CONSTRAINT otp_challenges_status_check
    CHECK (status IN ('PENDING', 'DELIVERED', 'VERIFIED', 'EXPIRED', 'LOCKED', 'CONSUMED', 'DELIVERY_FAILED', 'CANCELLED'));

CREATE TABLE public.otp_delivery_events (
    provider_event_id VARCHAR(200) PRIMARY KEY,
    challenge_id UUID NOT NULL REFERENCES public.otp_challenges(id) ON DELETE CASCADE,
    provider_message_id VARCHAR(200) NOT NULL,
    recipient_phone_e164 VARCHAR(16) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT otp_delivery_events_type_check
        CHECK (event_type IN ('AUTH_DELIVERED', 'AUTH_DELIVERY_FAILED')),
    CONSTRAINT otp_delivery_events_phone_check
        CHECK (recipient_phone_e164 ~ '^\+55[1-9][0-9](9[0-9]{8}|[2-5][0-9]{7})$')
);

CREATE INDEX otp_delivery_events_challenge_idx
    ON public.otp_delivery_events (challenge_id, received_at DESC);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.otp_delivery_events TO tino_app;
