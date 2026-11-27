-- Persistent policies can affect all browser profiles/users on Linux. Ask again
-- rather than silently escalating old launch-only or system-PAC consent.
UPDATE blocking_policy
SET websites_enabled = FALSE,
    website_consent_at = NULL,
    website_routing_method = 'SYSTEM_PROXY',
    version = version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE websites_enabled = TRUE OR website_consent_at IS NOT NULL OR website_routing_method <> 'SYSTEM_PROXY';
