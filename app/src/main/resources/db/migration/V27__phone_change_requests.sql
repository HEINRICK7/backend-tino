CREATE TABLE public.phone_change_requests (
    challenge_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    business_id UUID NOT NULL,
    new_phone_e164 VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT phone_change_requests_challenge_fk
        FOREIGN KEY (challenge_id) REFERENCES public.otp_challenges (id),
    CONSTRAINT phone_change_requests_user_fk
        FOREIGN KEY (user_id) REFERENCES public.users (id),
    CONSTRAINT phone_change_requests_business_fk
        FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT phone_change_requests_status_check
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    CONSTRAINT phone_change_requests_completion_check
        CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL))
);

CREATE INDEX phone_change_requests_user_business_idx
    ON public.phone_change_requests (user_id, business_id, created_at DESC);

CREATE INDEX phone_change_requests_pending_phone_idx
    ON public.phone_change_requests (new_phone_e164, created_at DESC)
    WHERE status = 'PENDING';

GRANT SELECT, INSERT, UPDATE ON TABLE public.phone_change_requests TO tino_app;
