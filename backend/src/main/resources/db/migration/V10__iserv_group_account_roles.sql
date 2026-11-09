-- Preserve existing UUID inputs as unmatched account strings until an administrator replaces them.
ALTER TABLE tenant_oidc_allowed_group RENAME COLUMN group_id TO group_act;
ALTER TABLE tenant_oidc_allowed_group ALTER COLUMN group_act TYPE text USING group_act::text;
ALTER TABLE tenant_oidc_allowed_group ADD COLUMN kelios_role text NOT NULL DEFAULT 'USER' CHECK (kelios_role IN ('USER', 'ADMIN'));
ALTER TABLE tenant_oidc_allowed_group ADD CONSTRAINT tenant_oidc_group_act_valid CHECK (length(group_act) BETWEEN 1 AND 256 AND group_act = btrim(group_act));
