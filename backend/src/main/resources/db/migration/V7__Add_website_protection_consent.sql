ALTER TABLE blocking_policy ADD COLUMN websites_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE blocking_policy ADD COLUMN website_consent_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE blocking_policy ADD CONSTRAINT ck_website_protection_consent CHECK (NOT websites_enabled OR website_consent_at IS NOT NULL);
