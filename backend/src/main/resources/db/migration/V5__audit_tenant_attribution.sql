-- KELIOS is pre-deployment. Only disposable development audit rows are cleared.
DELETE FROM audit_log;
ALTER TABLE audit_log ADD COLUMN tenant_id uuid;
-- Historical organizational UUID: deliberately no live Tenant foreign key.
CREATE INDEX audit_tenant_time ON audit_log(tenant_id, occurred_at DESC, id DESC);
