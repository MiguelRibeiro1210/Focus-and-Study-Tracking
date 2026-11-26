-- Browser-only consent cannot authorize forwarding traffic from other apps.
-- Preserve rules and application consent; require the new explicit scope.
UPDATE blocking_policy
SET websites_enabled = FALSE,
    website_consent_at = NULL,
    version = version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE websites_enabled = TRUE OR website_consent_at IS NOT NULL;
