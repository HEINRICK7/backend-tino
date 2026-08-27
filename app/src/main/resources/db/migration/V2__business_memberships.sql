-- M3 establishes the tenant root and the explicit User-to-Business authorization relation.
CREATE TABLE public.businesses (
    id UUID PRIMARY KEY,
    trade_name VARCHAR(200) NOT NULL,
    vertical VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT businesses_vertical_check
        CHECK (vertical IN ('RETAIL', 'BAKERY', 'RESTAURANT', 'STORE', 'OTHER')),
    CONSTRAINT businesses_status_check
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE public.business_memberships (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT business_memberships_business_fk
        FOREIGN KEY (business_id) REFERENCES public.businesses (id),
    CONSTRAINT business_memberships_user_fk
        FOREIGN KEY (user_id) REFERENCES public.users (id),
    CONSTRAINT business_memberships_unique_business_user
        UNIQUE (business_id, user_id),
    CONSTRAINT business_memberships_role_check
        CHECK (role IN ('OWNER', 'STAFF')),
    CONSTRAINT business_memberships_status_check
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX business_memberships_user_id_idx
    ON public.business_memberships (user_id);

-- These are control-plane tables: authorization queries use explicit user/business predicates.
-- Tenant-owned tables in later milestones receive transaction-local RLS policies.
GRANT SELECT, INSERT ON TABLE public.businesses TO tino_app;
GRANT SELECT, INSERT ON TABLE public.business_memberships TO tino_app;
