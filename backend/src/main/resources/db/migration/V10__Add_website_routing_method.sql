ALTER TABLE blocking_policy ADD COLUMN website_routing_method VARCHAR(20) NOT NULL DEFAULT 'SYSTEM_PROXY' CHECK (website_routing_method IN ('SYSTEM_PROXY','BRAVE'));
