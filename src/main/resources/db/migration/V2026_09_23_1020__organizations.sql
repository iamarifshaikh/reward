-- Every business: the platform itself, brands, distributors and retailers.
-- Its contact details live on its single login in users, so they are not repeated here.
CREATE TABLE organizations (
    id              uuid         PRIMARY KEY,
    code            varchar(20)  NOT NULL UNIQUE,
    org_type        varchar(16)  NOT NULL
                    CONSTRAINT ck_org_type CHECK (org_type IN ('PLATFORM', 'BRAND', 'DISTRIBUTOR', 'RETAILER')),
    legal_name      varchar(200) NOT NULL,
    display_name    varchar(150) NOT NULL,
    gstin           varchar(15)
                    CONSTRAINT ck_org_gstin CHECK (
                        gstin IS NULL OR gstin ~ '^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$'),
    pan_enc         bytea,
    pan_hash        varchar(64),
    status          varchar(20)  NOT NULL DEFAULT 'ACTIVE'
                    CONSTRAINT ck_org_status CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'SUSPENDED', 'CLOSED')),
    kyc_status      varchar(16)  NOT NULL DEFAULT 'NOT_STARTED'
                    CONSTRAINT ck_org_kyc_status CHECK (kyc_status IN ('NOT_STARTED', 'SUBMITTED', 'VERIFIED', 'REJECTED')),
    approved_at     timestamptz,
    approved_by     uuid         REFERENCES users (id),
    logo_object_key varchar(512),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      uuid,
    version         integer      NOT NULL DEFAULT 0
);

-- Exactly one platform organisation can ever exist.
CREATE UNIQUE INDEX ux_org__one_platform ON organizations (org_type) WHERE org_type = 'PLATFORM';

-- A closed business releases its GSTIN for reuse.
CREATE UNIQUE INDEX ux_org__gstin ON organizations (gstin) WHERE gstin IS NOT NULL AND status <> 'CLOSED';

CREATE INDEX ix_org__type_status ON organizations (org_type, status, created_at DESC);

-- Admin KYC queue: partial, so the index holds only rows awaiting review.
CREATE INDEX ix_org__kyc_queue ON organizations (created_at) WHERE kyc_status = 'SUBMITTED';

CREATE INDEX ix_org__pan_hash ON organizations (pan_hash) WHERE pan_hash IS NOT NULL;

-- Fuzzy name search for the admin list.
CREATE INDEX ix_org__name_trgm ON organizations USING gin (display_name gin_trgm_ops);

-- Deferred from V2026_09_23_1010, now that organizations exists.
ALTER TABLE users ADD CONSTRAINT fk_users__org
    FOREIGN KEY (org_id) REFERENCES organizations (id);
ALTER TABLE users ADD CONSTRAINT fk_users__enrolled_by
    FOREIGN KEY (enrolled_by_org_id) REFERENCES organizations (id);
