-- The platform organisation and its first admin login.
-- Everything else on the platform is created through the API by this account, so it has to exist
-- before anything can be tested. Email and phone come from Flyway placeholders, set in
-- application.yml from PLATFORM_ADMIN_EMAIL / PLATFORM_ADMIN_PHONE, so different values can be
-- used per environment.
--
-- All statements are guarded by NOT EXISTS, so running them against an already-seeded database
-- does nothing.

INSERT INTO organizations (id, code, org_type, legal_name, display_name,
                           status, kyc_status, created_at, updated_at, version)
SELECT gen_random_uuid(), 'PLATFORM', 'PLATFORM', 'INTIQ Rewards', 'INTIQ Rewards',
       'ACTIVE', 'NOT_STARTED', now(), now(), 0
WHERE NOT EXISTS (SELECT 1 FROM organizations WHERE org_type = 'PLATFORM');

-- Seeded as ACTIVE with both contacts verified: the admin can log in with an email OTP or a
-- phone OTP straight away, with no onboarding step in front of it.
INSERT INTO users (id, phone, email, full_name, org_id, status,
                   phone_verified_at, email_verified_at, created_at, updated_at, version)
SELECT gen_random_uuid(), '${platformAdminPhone}', lower('${platformAdminEmail}'), 'Platform Admin', o.id, 'ACTIVE',
       now(), now(), now(), now(), 0
FROM organizations o
WHERE o.org_type = 'PLATFORM'
  AND NOT EXISTS (
      SELECT 1 FROM users WHERE email = lower('${platformAdminEmail}') OR phone = '${platformAdminPhone}'
  );
