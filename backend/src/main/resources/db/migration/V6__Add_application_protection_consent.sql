ALTER TABLE blocking_policy ADD COLUMN applications_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE blocking_policy ADD COLUMN application_consent_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE blocking_policy ADD CONSTRAINT ck_application_protection_consent
    CHECK (NOT applications_enabled OR application_consent_at IS NOT NULL);
