-- Which partner sells which brand. This is the brand-level isolation line: every check of
-- "may this partner earn from this brand" is one index probe here.
CREATE TABLE channel_relationships (
    id             uuid        PRIMARY KEY,
    brand_org_id   uuid        NOT NULL REFERENCES organizations (id),
    partner_org_id uuid        NOT NULL REFERENCES organizations (id),
    partner_type   varchar(16) NOT NULL
                   CONSTRAINT ck_channel_partner_type CHECK (partner_type IN ('DISTRIBUTOR', 'RETAILER')),
    parent_org_id  uuid        REFERENCES organizations (id),
    region         varchar(50),
    status         varchar(16) NOT NULL DEFAULT 'ACTIVE'
                   CONSTRAINT ck_channel_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ENDED')),
    joined_at      timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now(),
    created_by     uuid,
    updated_at     timestamptz NOT NULL DEFAULT now(),
    updated_by     uuid,
    version        integer     NOT NULL DEFAULT 0,
    CONSTRAINT ux_channel UNIQUE (brand_org_id, partner_org_id),
    CONSTRAINT ck_channel_self CHECK (brand_org_id <> partner_org_id),
    CONSTRAINT ck_channel_parent CHECK (parent_org_id IS NULL OR partner_type = 'RETAILER')
);

CREATE INDEX ix_channel__brand ON channel_relationships (brand_org_id, status, partner_type);
CREATE INDEX ix_channel__partner ON channel_relationships (partner_org_id, status);
CREATE INDEX ix_channel__parent ON channel_relationships (parent_org_id) WHERE parent_org_id IS NOT NULL;


-- Uploaded KYC proof. The file stays in a private bucket; only its object key is stored.
CREATE TABLE kyc_documents (
    id           uuid         PRIMARY KEY,
    org_id       uuid         NOT NULL REFERENCES organizations (id),
    doc_type     varchar(24)  NOT NULL
                 CONSTRAINT ck_kyc_doc_type CHECK (
                     doc_type IN ('GST_CERTIFICATE', 'PAN_CARD', 'CANCELLED_CHEQUE', 'SHOP_LICENSE', 'OTHER')),
    object_key   varchar(512) NOT NULL UNIQUE,
    content_type varchar(100) NOT NULL,
    size_bytes   bigint       NOT NULL
                 CONSTRAINT ck_kyc_size CHECK (size_bytes > 0 AND size_bytes <= 10485760),
    status       varchar(16)  NOT NULL DEFAULT 'PENDING'
                 CONSTRAINT ck_kyc_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUPERSEDED')),
    reviewed_by  uuid         REFERENCES users (id),
    reviewed_at  timestamptz,
    remarks      varchar(500),
    created_at   timestamptz  NOT NULL DEFAULT now(),
    created_by   uuid
);

CREATE INDEX ix_kyc__org ON kyc_documents (org_id, created_at DESC);
CREATE INDEX ix_kyc__queue ON kyc_documents (created_at) WHERE status = 'PENDING';


-- Append-only record of who did what. No foreign keys on purpose: it points at rows in several
-- tables and must outlive them.
CREATE TABLE audit_logs (
    id            bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at   timestamptz NOT NULL DEFAULT now(),
    actor_user_id uuid,
    actor_org_id  uuid,
    actor_type    varchar(16)
                  CONSTRAINT ck_audit_actor_type CHECK (
                      actor_type IN ('ADMIN', 'BRAND', 'DISTRIBUTOR', 'RETAILER', 'CONSUMER')),
    action        varchar(60) NOT NULL,
    entity_type   varchar(40) NOT NULL,
    entity_id     uuid,
    scope_org_id  uuid,
    changes       jsonb,
    ip            varchar(45),
    request_id    varchar(40)
);

-- BRIN, not B-tree: rows arrive in time order, and this index is a fraction of the size.
CREATE INDEX ix_audit__time ON audit_logs USING brin (occurred_at);
CREATE INDEX ix_audit__entity ON audit_logs (entity_type, entity_id);
CREATE INDEX ix_audit__scope ON audit_logs (scope_org_id, occurred_at DESC);
CREATE INDEX ix_audit__actor ON audit_logs (actor_user_id, occurred_at DESC);
