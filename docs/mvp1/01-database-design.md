# MVP 1 — Database Design (7 tables)

**MVP 1 is:** bring all five user types onto the platform (brand, distributor, retailer, consumer, platform admin), log them in by OTP to their registered phone or email, capture their details and KYC, keep each brand's partners isolated, and log every action.

**MVP 1 is not:** points, wallets, schemes, redemption, payouts, notifications. Nothing here stores those, and nothing here blocks them.

**DB:** PostgreSQL 17 · one database `intiq` · schema `public` · Flyway migrations.

---

## 1. The 7 tables

| # | Table | Holds |
|---|---|---|
| 1 | `users` | Every login on the platform: one per business, plus every consumer, plus the platform admins |
| 2 | `otp_challenges` | OTP codes sent to a phone or email (short-lived) |
| 3 | `refresh_tokens` | Active logins, so logout and suspension take effect immediately |
| 4 | `organizations` | Every business: the platform, brands, distributors, retailers |
| 5 | `channel_relationships` | Which partners belong to which brand. This is the brand-level isolation line |
| 6 | `kyc_documents` | Uploaded KYC files and their review result |
| 7 | `audit_logs` | Who did what, including every status change |

Plus `flyway_schema_history`, created by Flyway.

### 1.1 One business, one login

A business logs in through the phone or email it was registered with. There are no per-person staff accounts and no per-person roles inside a business. So there is no membership table: `users.org_id` points at the business this login belongs to, and it is null for consumers.

What a login can do follows from the organisation's type:

| `org_id` | `organizations.org_type` | Login acts as |
|---|---|---|
| set | `PLATFORM` | Platform admin |
| set | `BRAND` | Brand |
| set | `DISTRIBUTOR` | Distributor |
| set | `RETAILER` | Retailer |
| null | — | Consumer |

There is no `role` column, because the type already answers it. If the client later wants two people at one brand, or a cashier with reduced rights, we add rows with the same `org_id` and introduce a `role` column then. **No table has to change to support that**, which is why this is safe to simplify now.

One login per business is enforced in the service layer rather than by a unique index, so the platform organisation can keep two or three admin logins. Cover that with a test.

### 1.2 One login can also be a consumer

A retailer's phone number can also be enrolled as a consumer, for example the shop owner shopping elsewhere. That is the **same** `users` row: `org_id` points at their shop, and `consumer_enrolled_at` is also set. At login they choose which side they are using, and `refresh_tokens.context_type` records the choice.

### 1.3 There is no self sign-up

Every account is created top-down by someone already on the platform:

| Creator | Creates | Rows written |
|---|---|---|
| Platform admin | A brand | `organizations` (BRAND) + `users` (UNVERIFIED, `org_id` = that brand) |
| Brand | Its distributor or retailer | `organizations` (PARTNER) + `users` (UNVERIFIED, `org_id` = that partner) + `channel_relationships` (ACTIVE) |
| Platform admin | Another brand's partner, or a fix | Same as above, on the brand's behalf |
| Retailer | A consumer | `users` (UNVERIFIED, `consumer_enrolled_at` set, `enrolled_by_org_id` = the retailer) |

The new login enters an OTP sent to the phone or email that the creator typed. The first successful OTP flips `users.status` to `ACTIVE` and stamps `phone_verified_at` or `email_verified_at`. There is no invite token, no expiry and no accept step, which is why there is no `invitations` table.

**Creating by phone is a find-or-attach, never a blind insert.** `users.phone` is unique and the number may already exist, for example a shop owner whom another store now enrols as a consumer. Look the number up first: if the row exists, update it (set `consumer_enrolled_at` if it is null) rather than inserting.

**Correcting a typo:** while a login is still `UNVERIFIED`, its creator can edit the phone or email, because nobody has proved ownership yet. Once `ACTIVE`, changing the contact needs an OTP to the new one.

---

## 2. What I merged or dropped, and why

| Was | Now | Why |
|---|---|---|
| `org_memberships` | `users.org_id` | One login per business, so the person-to-business join collapses to a column |
| `roles`, `permissions`, `role_permissions` | `organizations.org_type` | With no per-person roles, permissions follow from the business type. Three tables and two joins to express a constant |
| `user_credentials` | Nothing (login is OTP-only) | No passwords to store |
| `consumer_profiles` | Columns on `users` | Four columns in MVP 1. Splitting them costs a join on every consumer read |
| `brand_profiles`, `business_profiles` | Columns on `organizations` | Three small brand fields, nothing partner-specific left |
| `consents`, and the `terms_*` / `marketing_opt_in` columns | Dropped | No policy acceptance in the business requirements. See open question 7 before MVP 4 messaging |
| `status_history` | `audit_logs` rows | A status change is an audit event |
| `files` | `organizations.logo_object_key`, columns on `kyc_documents` | MVP 1 uploads exactly two kinds of thing |
| `invitations` | `users.status` | Nothing to park: the creator supplies the contact, and the OTP proves it (§1.3) |
| `organizations.email` / `.phone` / `.owner_name` / `.support_email` / `.support_phone` | `users.email`, `.phone`, `.full_name` | The registered login **is** the business contact. A separate public support contact only matters once a screen shows it to partners or consumers, which is MVP 3 |
| `payout_methods` | MVP 3 | Nothing pays out until redemption exists |
| `platform_settings` | MVP 2 | MVP 1 config lives in `application.yml`. A settings table matters when the client changes values at runtime |
| `event_publication`, `shedlock` | MVP 2 | No cross-module events, and one cleanup job on one instance |

---

## 3. Design rules

| Decision | Choice | Why |
|---|---|---|
| Primary keys | `uuid` v7, generated in the app | Time-ordered, so inserts stay index-friendly. Not guessable in URLs. `audit_logs` uses `bigint`, being append-only and never exposed |
| Human IDs | Short `code` column (`BRD-00042`, `RTL-000123`) from sequences | For ops and support. Never a foreign key |
| Status values | `varchar` + `CHECK` | Adding a value is a one-line migration. A Postgres enum type is not |
| Type-specific columns | Nullable columns on the shared table, validated in the service layer | Nullable columns cost nothing in Postgres. Extra joins on every read do |
| Sensitive data | PAN encrypted (`bytea`) + `pan_hash` for uniqueness and duplicate detection | A DB leak exposes nothing readable. Masked display decrypts in the service layer |
| Deletes | None. A `status` column instead | Audit trail and referential safety |
| Concurrency | `version` column on edited tables | Two admins editing one org: the second gets a 409, not a silent overwrite |
| Audit columns | `created_at`, `created_by`, `updated_at`, `updated_by`, filled by JPA auditing | One mechanism, no triggers |
| Uniqueness | Partial unique indexes | Correct behaviour for optional fields and non-active rows |

---

## 4. DDL

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE SEQUENCE seq_brand_code;
CREATE SEQUENCE seq_distributor_code;
CREATE SEQUENCE seq_retailer_code;
```

### 4.1 users
```sql
CREATE TABLE users (
  id                   uuid PRIMARY KEY,
  phone                varchar(15),                -- E.164
  email                varchar(160),               -- stored lower-cased by the service
  full_name            varchar(120),               -- owner name for a business login
  org_id               uuid,                       -- null = consumer; FK added in 4.4
  status               varchar(16) NOT NULL DEFAULT 'UNVERIFIED'
                       CHECK (status IN ('UNVERIFIED','ACTIVE','BLOCKED')),
  phone_verified_at    timestamptz,
  email_verified_at    timestamptz,
  last_login_at        timestamptz,
  -- consumer side (null until enrolled as a consumer)
  consumer_enrolled_at timestamptz,
  gender               varchar(8) CHECK (gender IN ('FEMALE','MALE','OTHER','NA')),
  city                 varchar(80),
  pincode              char(6),
  enrolled_by_org_id   uuid,                       -- retailer that enrolled them
  created_at timestamptz NOT NULL DEFAULT now(), created_by uuid,
  updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid,
  version int NOT NULL DEFAULT 0,
  CONSTRAINT ck_users_contact CHECK (phone IS NOT NULL OR email IS NOT NULL)
);
CREATE UNIQUE INDEX ux_users__phone    ON users (phone) WHERE phone IS NOT NULL;
CREATE UNIQUE INDEX ux_users__email    ON users (email) WHERE email IS NOT NULL;
CREATE INDEX ix_users__org ON users (org_id) WHERE org_id IS NOT NULL;
```

### 4.2 otp_challenges
```sql
CREATE TABLE otp_challenges (
  id          uuid PRIMARY KEY,
  channel     varchar(8)   NOT NULL CHECK (channel IN ('SMS','EMAIL')),
  destination varchar(150) NOT NULL,               -- phone in E.164, or email
  purpose     varchar(16)  NOT NULL CHECK (purpose IN ('LOGIN','CONTACT_CHANGE')),
  code_hash   char(64) NOT NULL,                   -- HMAC-SHA256, never the plain code
  attempts    smallint NOT NULL DEFAULT 0,
  expires_at  timestamptz NOT NULL,
  consumed_at timestamptz,
  request_ip  varchar(45),
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_otp__destination_created ON otp_challenges (destination, created_at DESC);
```
One index serves both "latest active OTP for this destination" and the rate-limit count for the last 10 minutes.

### 4.3 refresh_tokens
```sql
CREATE TABLE refresh_tokens (
  id           uuid PRIMARY KEY,
  user_id      uuid NOT NULL REFERENCES users(id),
  family_id    uuid NOT NULL,                      -- all rotations of one login
  token_hash   char(64) NOT NULL UNIQUE,
  context_type varchar(8) NOT NULL CHECK (context_type IN ('ORG','CONSUMER')),
  expires_at   timestamptz NOT NULL,
  revoked_at   timestamptz,
  user_agent   varchar(255),
  ip           varchar(45),
  created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_rt__user   ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_rt__family ON refresh_tokens (family_id);
```

### 4.4 organizations
```sql
CREATE TABLE organizations (
  id              uuid PRIMARY KEY,
  code            varchar(20) NOT NULL UNIQUE,
  org_type        varchar(16) NOT NULL CHECK (org_type IN ('PLATFORM','BRAND','DISTRIBUTOR','RETAILER')),
  legal_name      varchar(200) NOT NULL,
  display_name    varchar(150) NOT NULL,
  gstin           varchar(15),
  pan_enc         bytea,
  pan_hash        char(64),                        -- SHA-256, uniqueness and duplicate detection
  status          varchar(20) NOT NULL DEFAULT 'ACTIVE'
                  CHECK (status IN ('PENDING_APPROVAL','ACTIVE','SUSPENDED','CLOSED')),
  kyc_status      varchar(16) NOT NULL DEFAULT 'NOT_STARTED'
                  CHECK (kyc_status IN ('NOT_STARTED','SUBMITTED','VERIFIED','REJECTED')),
  approved_at     timestamptz,
  approved_by     uuid REFERENCES users(id),
  -- brand only
  logo_object_key varchar(512),
  created_at timestamptz NOT NULL DEFAULT now(), created_by uuid,
  updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid,
  version int NOT NULL DEFAULT 0,
  CONSTRAINT ck_org_gstin CHECK (gstin IS NULL OR gstin ~ '^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$')
);
CREATE UNIQUE INDEX ux_org__one_platform ON organizations (org_type) WHERE org_type = 'PLATFORM';
CREATE UNIQUE INDEX ux_org__gstin        ON organizations (gstin) WHERE gstin IS NOT NULL AND status <> 'CLOSED';
CREATE INDEX ix_org__type_status         ON organizations (org_type, status, created_at DESC);
CREATE INDEX ix_org__kyc_queue           ON organizations (created_at) WHERE kyc_status = 'SUBMITTED';
CREATE INDEX ix_org__name_trgm           ON organizations USING gin (display_name gin_trgm_ops);

ALTER TABLE users ADD CONSTRAINT fk_users__org         FOREIGN KEY (org_id)             REFERENCES organizations(id);
ALTER TABLE users ADD CONSTRAINT fk_users__enrolled_by FOREIGN KEY (enrolled_by_org_id) REFERENCES organizations(id);
```

### 4.5 channel_relationships
```sql
CREATE TABLE channel_relationships (
  id             uuid PRIMARY KEY,
  brand_org_id   uuid NOT NULL REFERENCES organizations(id),
  partner_org_id uuid NOT NULL REFERENCES organizations(id),
  partner_type   varchar(16) NOT NULL CHECK (partner_type IN ('DISTRIBUTOR','RETAILER')),
  parent_org_id  uuid REFERENCES organizations(id),   -- distributor above this retailer, for this brand
  region         varchar(50),
  status         varchar(16) NOT NULL DEFAULT 'ACTIVE'
                 CHECK (status IN ('ACTIVE','SUSPENDED','ENDED')),
  joined_at      timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(), created_by uuid,
  updated_at timestamptz NOT NULL DEFAULT now(), updated_by uuid,
  version int NOT NULL DEFAULT 0,
  CONSTRAINT ux_channel UNIQUE (brand_org_id, partner_org_id),
  CONSTRAINT ck_channel_self CHECK (brand_org_id <> partner_org_id),
  CONSTRAINT ck_channel_parent CHECK (parent_org_id IS NULL OR partner_type = 'RETAILER')
);
CREATE INDEX ix_channel__brand   ON channel_relationships (brand_org_id, status, partner_type);
CREATE INDEX ix_channel__partner ON channel_relationships (partner_org_id, status);
```
This is the "multi-tenant structure with brand-level data isolation" line in the quotation. Every MVP 2 check of "can this partner earn from this brand's scheme?" is one index probe here.

### 4.6 kyc_documents
```sql
CREATE TABLE kyc_documents (
  id           uuid PRIMARY KEY,
  org_id       uuid NOT NULL REFERENCES organizations(id),
  doc_type     varchar(24) NOT NULL
               CHECK (doc_type IN ('GST_CERTIFICATE','PAN_CARD','CANCELLED_CHEQUE','SHOP_LICENSE','OTHER')),
  object_key   varchar(512) NOT NULL UNIQUE,        -- S3 key, uploaded via presigned PUT
  content_type varchar(100) NOT NULL,
  size_bytes   bigint NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 10485760),
  status       varchar(16) NOT NULL DEFAULT 'PENDING'
               CHECK (status IN ('PENDING','APPROVED','REJECTED','SUPERSEDED')),
  reviewed_by  uuid REFERENCES users(id),
  reviewed_at  timestamptz,
  remarks      varchar(500),
  created_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid
);
CREATE INDEX ix_kyc__org ON kyc_documents (org_id, created_at DESC);
```

### 4.7 audit_logs
```sql
CREATE TABLE audit_logs (
  id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  occurred_at   timestamptz NOT NULL DEFAULT now(),
  actor_user_id uuid,
  actor_org_id  uuid,
  actor_type    varchar(16),               -- ADMIN, BRAND, DISTRIBUTOR, RETAILER, CONSUMER; null for scheduled jobs
  action        varchar(60) NOT NULL,      -- ORG_CREATED, KYC_REJECTED, LOGIN_FAILED, PARTNER_SUSPENDED
  entity_type   varchar(40) NOT NULL,
  entity_id     uuid,
  scope_org_id  uuid,                      -- whose data was touched
  changes       jsonb,                     -- {"status":["UNVERIFIED","ACTIVE"]}, PII masked
  ip            varchar(45),
  request_id    varchar(40)
);
CREATE INDEX ix_audit__time   ON audit_logs USING brin (occurred_at);
CREATE INDEX ix_audit__entity ON audit_logs (entity_type, entity_id);
CREATE INDEX ix_audit__scope  ON audit_logs (scope_org_id, occurred_at DESC);
```

---

## 5. Query → index

| Query | Index |
|---|---|
| Login by phone or email | `ux_users__phone` / `ux_users__email` |
| Verify OTP, and enforce 3 per 10 minutes | `ix_otp__destination_created` |
| Load the login's business after OTP | `users.org_id` → `organizations` PK |
| Admin list of orgs by type and status | `ix_org__type_status` |
| Admin KYC queue | `ix_org__kyc_queue` (partial, stays tiny) |
| Admin search by name | `ix_org__name_trgm` |
| Brand's partner list / partner's brand list | `ix_channel__brand` / `ix_channel__partner` |
| "Is this partner active for this brand?" | `ux_channel` |
| Refresh rotation and reuse detection | `token_hash UNIQUE`, `ix_rt__family` |
| Audit of one entity or one tenant | `ix_audit__entity` / `ix_audit__scope` |

---

## 6. Flyway files

```
V2026_09_23_1000__init_extensions_sequences.sql
V2026_09_23_1010__users_otp_tokens.sql     -- users, otp_challenges, refresh_tokens
V2026_09_23_1020__organizations.sql        -- organizations + the two deferred FKs on users
V2026_09_23_1030__channel_kyc_audit.sql
R__seed_platform_org.sql                   -- the single PLATFORM row and its first admin login
```

---

## 7. How MVP 2 attaches without touching these tables

| MVP 2 need | Attaches to |
|---|---|
| Float and wallet accounts | `organizations.id`, `users.id` |
| Schemes and products | `organizations.id` as owner |
| "Can this partner earn from this brand?" | `channel_relationships`, status `ACTIVE` |
| Runtime settings the client can change | New `platform_settings` table |
| Cross-module events | New `event_publication` table (Spring Modulith) |
| Several logins per business, with roles | More `users` rows with the same `org_id`, plus a `role` column. No table changes |

---

## 8. Flows, and the rows each one writes

**Admin creates brand Lux**
`organizations` insert (BRAND, `BRD-00042`, ACTIVE) → `users` insert (email, `org_id` = Lux, UNVERIFIED) → `audit_logs` (`ORG_CREATED`).

**Lux creates retailer Sharma Stores**
`organizations` insert (RETAILER, `RTL-000123`) → `users` insert (phone, `org_id` = Sharma Stores, UNVERIFIED) → `channel_relationships` insert (Lux ↔ Sharma Stores, ACTIVE) → `audit_logs`. If the phone already exists, attach to that user instead of inserting (§1.3).

**Sharma Stores logs in for the first time**
`otp_challenges` insert → SMS → on verify: mark consumed, `users.status` = ACTIVE, stamp `phone_verified_at` and `last_login_at` → `refresh_tokens` insert (new `family_id`, context ORG) → `audit_logs` (`LOGIN_SUCCEEDED`). Permissions come from `organizations.org_type`.

**Sharma Stores submits KYC, admin approves**
Presigned S3 upload → `kyc_documents` insert (PENDING) → `organizations.kyc_status` = SUBMITTED → admin approves → document APPROVED, `kyc_status` = VERIFIED → `audit_logs` (`KYC_APPROVED`, scope = Sharma Stores).

**Retailer creates a consumer** (the MVP 2 earning path starts here)
Look up the phone. If new: `users` insert (`org_id` null, `consumer_enrolled_at` set, `enrolled_by_org_id` = the retailer). If it exists: set `consumer_enrolled_at` if null, leaving any business `org_id` untouched.

---

## 9. Open questions

1. `totp_secret_enc` and `mfa_enabled` are **out** of the DDL above: with OTP-only login, an authenticator app means a second code for very little gain. Say so and I will add them back for platform admins.
2. Is GSTIN unique per organisation? Making it unique stops a chain from registering each store separately under one GSTIN.
3. Is GSTIN optional for retailers? Small shops often have none. Proposed: required for brands and distributors, optional for retailers.
4. Does a brand-created partner need platform-admin approval, or is it active immediately? Proposed: active immediately, since the brand vouches for it. KYC review stays separate.
5. How does a newly created login learn to log in? Until WhatsApp and SMS land in MVP 4, someone tells them.
6. No consent columns, since policy acceptance is not in the requirements. Before MVP 4, check with the client whether promotional WhatsApp and SMS broadcasts need a recorded opt-in. If so, it is one boolean on `users`, but it can only be collected going forward, never backfilled.
7. With no address on `organizations`, partner lists cannot filter by city. `channel_relationships.region` covers the brand's own grouping. Add a city column later if the client asks.
