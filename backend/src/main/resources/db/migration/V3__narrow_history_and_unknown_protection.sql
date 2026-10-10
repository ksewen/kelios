-- Deliberately no Product usage latch, identity trigger, or generic purge trigger.
CREATE FUNCTION reject_asset_event_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'AssetEvent is append-only' USING ERRCODE='23514'; END;
$$;
CREATE TRIGGER asset_event_append_only BEFORE UPDATE OR DELETE ON asset_event
 FOR EACH ROW EXECUTE FUNCTION reject_asset_event_mutation();
CREATE TRIGGER asset_event_no_truncate BEFORE TRUNCATE ON asset_event
 FOR EACH STATEMENT EXECUTE FUNCTION reject_asset_event_mutation();
CREATE FUNCTION protect_unknown_catalog_row() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.id = TG_ARGV[0]::uuid THEN
  IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'Protected UNKNOWN' USING ERRCODE='23514'; END IF;
  IF NEW.id IS DISTINCT FROM OLD.id OR NEW.name IS DISTINCT FROM OLD.name
   OR NEW.visibility IS DISTINCT FROM OLD.visibility OR NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
   OR NEW.deleted_at IS DISTINCT FROM OLD.deleted_at OR NEW.deleted_by IS DISTINCT FROM OLD.deleted_by THEN
   RAISE EXCEPTION 'Protected UNKNOWN' USING ERRCODE='23514';
  END IF;
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER category_unknown BEFORE UPDATE OR DELETE ON category FOR EACH ROW
 EXECUTE FUNCTION protect_unknown_catalog_row('00000000-0000-0000-0000-000000000001');
CREATE TRIGGER manufacturer_unknown BEFORE UPDATE OR DELETE ON manufacturer FOR EACH ROW
 EXECUTE FUNCTION protect_unknown_catalog_row('00000000-0000-0000-0000-000000000002');
