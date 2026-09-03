ALTER TABLE public.otp_challenges
    DROP CONSTRAINT otp_challenges_status_check;

ALTER TABLE public.otp_challenges
    ADD CONSTRAINT otp_challenges_status_check
    CHECK (status IN ('PENDING', 'VERIFIED', 'EXPIRED', 'LOCKED', 'CONSUMED', 'DELIVERY_FAILED', 'CANCELLED'));
