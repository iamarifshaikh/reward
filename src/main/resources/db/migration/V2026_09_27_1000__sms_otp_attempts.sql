-- SMS OTP is now fully delegated to MSG91's OTP product (SendOTP / Verify OTP): MSG91 generates,
-- stores and verifies the code itself, so we hold no code or hash for this channel.
--
-- This table exists for two reasons that both need the same shape — one row per attempt:
--   1. Rate limiting our own SendOTP calls, so nothing hammers this endpoint and burns MSG91
--      credits with no throttling on our side, independent of whatever limits MSG91 enforces.
--   2. A diagnostic trail: when someone says "I never got a code", this is where the exact
--      request/response MSG91 gave us is visible, since we do not own that state anymore.
CREATE TABLE sms_otp_attempts (
    id                uuid        PRIMARY KEY,
    destination       varchar(15) NOT NULL,
    stage             varchar(8)  NOT NULL
                      CONSTRAINT ck_sms_otp_stage CHECK (stage IN ('SEND', 'VERIFY')),
    outcome           varchar(16) NOT NULL
                      CONSTRAINT ck_sms_otp_outcome CHECK (
                          outcome IN ('ACCEPTED', 'REJECTED', 'VERIFIED', 'EXPIRED', 'INVALID', 'ERROR')),
    provider_response varchar(500),
    request_ip        varchar(45),
    created_at        timestamptz NOT NULL DEFAULT now()
);

-- Rate-limit queries: "how many SEND attempts for this destination/IP in the last N minutes".
CREATE INDEX ix_sms_otp__destination_stage_created ON sms_otp_attempts (destination, stage, created_at DESC);
CREATE INDEX ix_sms_otp__ip_stage_created ON sms_otp_attempts (request_ip, stage, created_at DESC) WHERE request_ip IS NOT NULL;
