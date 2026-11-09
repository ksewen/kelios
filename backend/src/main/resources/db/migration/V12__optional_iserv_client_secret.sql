ALTER TABLE tenant_oidc_config ALTER COLUMN encrypted_client_secret DROP NOT NULL;
ALTER TABLE tenant_oidc_config ADD CONSTRAINT tenant_oidc_enabled_requires_secret
  CHECK (NOT enabled OR encrypted_client_secret IS NOT NULL);
