-- A brand-created partner starts PENDING_APPROVAL and can now be rejected outright, distinct from
-- CLOSED (which ends a business that was actually approved and operating).
ALTER TABLE organizations DROP CONSTRAINT ck_org_status;
ALTER TABLE organizations ADD CONSTRAINT ck_org_status
    CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'SUSPENDED', 'CLOSED', 'REJECTED'));

-- Brings the doc type list in line with KycDocType: AADHAR_CARD and UDYAM_REGISTRATION are real
-- document types the app uses; SHOP_LICENSE was never wired to anything.
ALTER TABLE kyc_documents DROP CONSTRAINT ck_kyc_doc_type;
ALTER TABLE kyc_documents ADD CONSTRAINT ck_kyc_doc_type
    CHECK (doc_type IN ('GST_CERTIFICATE', 'AADHAR_CARD', 'UDYAM_REGISTRATION', 'PAN_CARD', 'CANCELLED_CHEQUE', 'OTHER'));
