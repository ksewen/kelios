-- Target-schema migration, not a historical-data backfill. A populated pre-M1
-- development database requires a separately authorized clean reset first.
-- Check before changing any row/schema. Never guess old creators or timestamps.
DO $$
BEGIN
 IF EXISTS(SELECT 1 FROM tenant) OR EXISTS(SELECT 1 FROM app_user)
 OR EXISTS(SELECT 1 FROM category WHERE id<>'00000000-0000-0000-0000-000000000001')
 OR EXISTS(SELECT 1 FROM manufacturer WHERE id<>'00000000-0000-0000-0000-000000000002')
 OR EXISTS(SELECT 1 FROM product) OR EXISTS(SELECT 1 FROM location)
 OR EXISTS(SELECT 1 FROM owner) OR EXISTS(SELECT 1 FROM asset)
 OR EXISTS(SELECT 1 FROM tenant_oidc_config) THEN
  RAISE EXCEPTION 'M1 requires a clean business state: separately authorize a development database reset; historical metadata will not be fabricated';
 END IF;
END;
$$;

-- Recreate only the two authoritative infrastructure seeds in this clean state.
-- Their creation timestamp is the actual new INSERT time, not a guessed V2 time.
DROP TRIGGER category_unknown ON category;
DROP TRIGGER manufacturer_unknown ON manufacturer;
DELETE FROM category WHERE id='00000000-0000-0000-0000-000000000001';
DELETE FROM manufacturer WHERE id='00000000-0000-0000-0000-000000000002';

ALTER TABLE tenant ADD created_by uuid NOT NULL, ADD created_at timestamptz NOT NULL,
 ADD updated_by uuid NOT NULL, ADD updated_at timestamptz NOT NULL;
ALTER TABLE app_user ADD created_by uuid NOT NULL, ADD created_at timestamptz NOT NULL,
 ADD updated_by uuid NOT NULL, ADD updated_at timestamptz NOT NULL,
 ADD CONSTRAINT app_user_not_system_actor CHECK(id<>'00000000-0000-0000-0000-000000000003');
ALTER TABLE category ADD created_by uuid NOT NULL, ADD created_at timestamptz NOT NULL,
 ADD updated_by uuid NOT NULL, ADD updated_at timestamptz NOT NULL;
ALTER TABLE manufacturer ADD created_by uuid NOT NULL, ADD created_at timestamptz NOT NULL,
 ADD updated_by uuid NOT NULL, ADD updated_at timestamptz NOT NULL;
ALTER TABLE product ADD created_by uuid NOT NULL, ADD created_at timestamptz NOT NULL,
 ADD updated_by uuid NOT NULL, ADD updated_at timestamptz NOT NULL;
ALTER TABLE location ADD note text, ADD created_by uuid NOT NULL, ADD created_at timestamptz NOT NULL,
 ADD updated_by uuid NOT NULL, ADD updated_at timestamptz NOT NULL;
ALTER TABLE owner ADD note text, ADD created_by uuid NOT NULL, ADD created_at timestamptz NOT NULL,
 ADD updated_by uuid NOT NULL, ADD updated_at timestamptz NOT NULL;
ALTER TABLE tenant_oidc_config ADD created_by uuid NOT NULL, ADD created_at timestamptz NOT NULL,
 ADD updated_by uuid NOT NULL, ADD updated_at timestamptz NOT NULL;

INSERT INTO category(id,name,visibility,created_by,created_at,updated_by,updated_at)
 VALUES('00000000-0000-0000-0000-000000000001','UNKNOWN','GLOBAL',
 '00000000-0000-0000-0000-000000000003',statement_timestamp(),
 '00000000-0000-0000-0000-000000000003',statement_timestamp());
INSERT INTO manufacturer(id,name,visibility,created_by,created_at,updated_by,updated_at)
 VALUES('00000000-0000-0000-0000-000000000002','UNKNOWN','GLOBAL',
 '00000000-0000-0000-0000-000000000003',statement_timestamp(),
 '00000000-0000-0000-0000-000000000003',statement_timestamp());

CREATE TRIGGER category_unknown BEFORE UPDATE OR DELETE ON category FOR EACH ROW
 EXECUTE FUNCTION protect_unknown_catalog_row('00000000-0000-0000-0000-000000000001');
CREATE TRIGGER manufacturer_unknown BEFORE UPDATE OR DELETE ON manufacturer FOR EACH ROW
 EXECUTE FUNCTION protect_unknown_catalog_row('00000000-0000-0000-0000-000000000002');
-- Attribution deliberately has no app_user FK: actor UUIDs survive identity deletion,
-- and the reserved nonhuman seed actor never exists as an app_user.
