CREATE TABLE tenant_oidc_role_mapping (
    tenant_id uuid NOT NULL REFERENCES tenant_oidc_config(tenant_id) ON DELETE CASCADE,
    display_name text NOT NULL CHECK (length(display_name) BETWEEN 1 AND 256 AND display_name = btrim(display_name)),
    kelios_role text NOT NULL CHECK (kelios_role IN ('USER', 'ADMIN')),
    PRIMARY KEY (tenant_id, display_name)
);
