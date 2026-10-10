-- Additive authentication structures; no deployment secrets or credentials.
ALTER TABLE app_user ADD CONSTRAINT app_user_id_scope_unique UNIQUE (id, scope);
ALTER TABLE app_user ADD CONSTRAINT app_user_identity_tenant_unique UNIQUE (id, scope, tenant_id);
CREATE INDEX app_user_tenant_role_status_idx ON app_user(tenant_id, role, status);
CREATE TABLE local_global_credential (
  user_id uuid PRIMARY KEY,
  user_scope text NOT NULL DEFAULT 'GLOBAL' CHECK (user_scope = 'GLOBAL'),
  login_name text NOT NULL UNIQUE CHECK (login_name ~ '^[a-z0-9._-]{3,64}$'),
  password_hash text NOT NULL CHECK (btrim(password_hash) <> ''),
  FOREIGN KEY(user_id, user_scope) REFERENCES app_user(id, scope)
);
CREATE TABLE tenant_oidc_config (
  tenant_id uuid PRIMARY KEY REFERENCES tenant(id),
  issuer text NOT NULL CHECK (btrim(issuer) <> ''),
  client_id text NOT NULL CHECK (btrim(client_id) <> ''),
  encrypted_client_secret text NOT NULL CHECK (btrim(encrypted_client_secret) <> ''),
  enabled boolean NOT NULL,
  version bigint NOT NULL CHECK (version >= 0)
);
CREATE TABLE tenant_oidc_allowed_group (
  tenant_id uuid NOT NULL REFERENCES tenant_oidc_config(tenant_id),
  group_id uuid NOT NULL,
  PRIMARY KEY(tenant_id, group_id)
);
CREATE TABLE external_identity (
  issuer text NOT NULL CHECK (btrim(issuer) <> ''),
  subject text NOT NULL CHECK (btrim(subject) <> ''),
  user_id uuid NOT NULL UNIQUE,
  user_scope text NOT NULL DEFAULT 'TENANT' CHECK (user_scope = 'TENANT'),
  tenant_id uuid NOT NULL REFERENCES tenant(id),
  PRIMARY KEY(issuer, subject),
  FOREIGN KEY(user_id, user_scope, tenant_id) REFERENCES app_user(id, scope, tenant_id)
    DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX external_identity_tenant_idx ON external_identity(tenant_id);
CREATE TABLE global_admin_recovery_guard (id integer PRIMARY KEY CHECK (id = 1));
INSERT INTO global_admin_recovery_guard(id) VALUES(1);
