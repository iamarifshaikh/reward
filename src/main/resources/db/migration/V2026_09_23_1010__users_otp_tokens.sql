-- Every login on the platform: one per business (org_id set), plus every consumer (org_id null).
-- The foreign keys to organizations are added in V2026_09_23_1020, once that table exists.
CREATE TABLE users (
    id                   uuid         PRIMARY KEY,
    phone                varchar(15),
    email                varchar(160),
    full_name            varchar(120),
    org_id               uuid,
    status               varchar(16)  NOT NULL DEFAULT 'UNVERIFIED'
                         CONSTRAINT ck_users_status CHECK (status IN ('UNVERIFIED', 'ACTIVE', 'BLOCKED')),
    phone_verified_at    timestamptz,
    email_verified_at    timestamptz,
    last_login_at        timestamptz,
    consumer_enrolled_at timestamptz,
    gender               varchar(8)
                         CONSTRAINT ck_users_gender CHECK (gender IN ('FEMALE', 'MALE', 'OTHER', 'NA')),
    city                 varchar(80),
    pincode              varchar(6),
    enrolled_by_org_id   uuid,
    created_at           timestamptz  NOT NULL DEFAULT now(),
    created_by           uuid,
    updated_at           timestamptz  NOT NULL DEFAULT now(),
    updated_by           uuid,
    version              integer      NOT NULL DEFAULT 0,
    CONSTRAINT ck_users_contact CHECK (phone IS NOT NULL OR email IS NOT NULL)
);

-- Partial, because either contact may be absent; email is stored lower-cased by the service.
CREATE UNIQUE INDEX ux_users__phone ON users (phone) WHERE phone IS NOT NULL;
CREATE UNIQUE INDEX ux_users__email ON users (email) WHERE email IS NOT NULL;
CREATE INDEX ix_users__org ON users (org_id) WHERE org_id IS NOT NULL;
CREATE INDEX ix_users__enrolled_by ON users (enrolled_by_org_id) WHERE enrolled_by_org_id IS NOT NULL;


-- One OTP in flight. Keyed by destination, not by user, so a code can be sent before we know
-- who owns the contact, and failed attempts never touch the user row.
CREATE TABLE otp_challenges (
    id          uuid         PRIMARY KEY,
    channel     varchar(8)   NOT NULL
                CONSTRAINT ck_otp_channel CHECK (channel IN ('SMS', 'EMAIL')),
    destination varchar(150) NOT NULL,
    purpose     varchar(16)  NOT NULL
                CONSTRAINT ck_otp_purpose CHECK (purpose IN ('LOGIN', 'CONTACT_CHANGE')),
    code_hash   varchar(64)  NOT NULL,
    attempts    smallint     NOT NULL DEFAULT 0,
    expires_at  timestamptz  NOT NULL,
    consumed_at timestamptz,
    request_ip  varchar(45),
    created_at  timestamptz  NOT NULL DEFAULT now()
);

-- Serves both "newest code for this destination" and the rate-limit count.
CREATE INDEX ix_otp__destination_created ON otp_challenges (destination, created_at DESC);
CREATE INDEX ix_otp__ip_created ON otp_challenges (request_ip, created_at DESC) WHERE request_ip IS NOT NULL;


-- One issued refresh token. Rotations of the same login share family_id, so a replayed token
-- can revoke the whole chain.
CREATE TABLE refresh_tokens (
    id           uuid        PRIMARY KEY,
    user_id      uuid        NOT NULL REFERENCES users (id),
    family_id    uuid        NOT NULL,
    token_hash   varchar(64) NOT NULL UNIQUE,
    context_type varchar(8)  NOT NULL
                 CONSTRAINT ck_refresh_context CHECK (context_type IN ('ORG', 'CONSUMER')),
    expires_at   timestamptz NOT NULL,
    revoked_at   timestamptz,
    user_agent   varchar(255),
    ip           varchar(45),
    created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_refresh__user ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_refresh__family ON refresh_tokens (family_id);
