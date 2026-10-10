CREATE TABLE tenant (id uuid PRIMARY KEY, name text NOT NULL CHECK (btrim(name) <> ''));
CREATE TABLE app_user (
 id uuid PRIMARY KEY, scope text NOT NULL CHECK(scope IN ('GLOBAL','TENANT')),
 tenant_id uuid REFERENCES tenant(id), role text NOT NULL CHECK(role IN ('ADMIN','USER')),
 status text NOT NULL CHECK(status IN ('PENDING','ACTIVE','DISABLED')),
 display_name text NOT NULL CHECK(btrim(display_name) <> ''),
 CHECK ((scope='GLOBAL' AND tenant_id IS NULL) OR (scope='TENANT' AND tenant_id IS NOT NULL))
);
CREATE TABLE category (
 id uuid PRIMARY KEY, name text NOT NULL CHECK(btrim(name) <> ''),
 visibility text NOT NULL CHECK(visibility IN ('GLOBAL','TENANT')), tenant_id uuid REFERENCES tenant(id),
 deleted_at timestamptz, deleted_by uuid,
 CHECK ((visibility='GLOBAL' AND tenant_id IS NULL) OR (visibility='TENANT' AND tenant_id IS NOT NULL)),
 CHECK ((deleted_at IS NULL) = (deleted_by IS NULL))
);
CREATE TABLE manufacturer (LIKE category INCLUDING ALL);
ALTER TABLE manufacturer ADD FOREIGN KEY(tenant_id) REFERENCES tenant(id);
CREATE TABLE product (
 id uuid PRIMARY KEY, name text NOT NULL CHECK(btrim(name) <> ''), manufacturer_number text,
 category_id uuid NOT NULL REFERENCES category(id), manufacturer_id uuid NOT NULL REFERENCES manufacturer(id),
 visibility text NOT NULL CHECK(visibility IN ('GLOBAL','TENANT')), tenant_id uuid REFERENCES tenant(id),
 deleted_at timestamptz, deleted_by uuid,
 CHECK ((visibility='GLOBAL' AND tenant_id IS NULL) OR (visibility='TENANT' AND tenant_id IS NOT NULL)),
 CHECK ((deleted_at IS NULL) = (deleted_by IS NULL)), UNIQUE(id,category_id,manufacturer_id)
);
CREATE TABLE location (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenant(id), name text NOT NULL CHECK(btrim(name) <> ''),
 deleted_at timestamptz, deleted_by uuid, UNIQUE(id,tenant_id),
 CHECK ((deleted_at IS NULL) = (deleted_by IS NULL))
);
CREATE TABLE owner (LIKE location INCLUDING ALL);
ALTER TABLE owner ADD FOREIGN KEY(tenant_id) REFERENCES tenant(id);
CREATE TABLE asset_tag_counter (registration_date date PRIMARY KEY, last_value bigint NOT NULL CHECK(last_value>0));
CREATE TABLE asset (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenant(id), asset_tag text NOT NULL UNIQUE CHECK(btrim(asset_tag)<>''),
 category_id uuid NOT NULL REFERENCES category(id), manufacturer_id uuid NOT NULL REFERENCES manufacturer(id),
 product_id uuid, serial_number text, external_inventory_number text,
 location_id uuid NOT NULL, owner_id uuid,
 lifecycle text NOT NULL CHECK(lifecycle IN ('REGISTERED','IN_STOCK','DEPLOYED','DEREGISTERED')),
 condition text NOT NULL CHECK(condition IN ('UNKNOWN','WORKING','DEFECTIVE','UNUSABLE')),
 loanable boolean NOT NULL, loaned_to text CHECK(loaned_to IS NULL OR btrim(loaned_to)<>''),
 loaned_at timestamptz, expected_return_at timestamptz, loan_note text, note text,
 version bigint NOT NULL CHECK(version>=0), created_at timestamptz NOT NULL, created_by uuid NOT NULL,
 updated_at timestamptz NOT NULL, updated_by uuid NOT NULL, deleted_at timestamptz, deleted_by uuid,
 UNIQUE(id,tenant_id),
 FOREIGN KEY(location_id,tenant_id) REFERENCES location(id,tenant_id),
 FOREIGN KEY(owner_id,tenant_id) REFERENCES owner(id,tenant_id) MATCH SIMPLE,
 FOREIGN KEY(product_id,category_id,manufacturer_id) REFERENCES product(id,category_id,manufacturer_id)
 MATCH SIMPLE ON UPDATE NO ACTION DEFERRABLE INITIALLY DEFERRED,
 CHECK(loaned_to IS NOT NULL OR (loaned_at IS NULL AND expected_return_at IS NULL AND loan_note IS NULL)),
 CHECK ((deleted_at IS NULL) = (deleted_by IS NULL))
);
CREATE TABLE asset_event (
 id uuid PRIMARY KEY, asset_id uuid NOT NULL, tenant_id uuid NOT NULL,
 type text NOT NULL CHECK(type IN ('REGISTERED','LIFECYCLE_CHANGED','CONDITION_CHANGED','LOCATION_CHANGED','OWNER_CHANGED','LOANED','RETURNED','DEREGISTERED','UPDATED')),
 occurred_at timestamptz NOT NULL, created_at timestamptz NOT NULL, actor_id uuid NOT NULL,
 actor_display_name text NOT NULL CHECK(btrim(actor_display_name)<>''), data jsonb NOT NULL CHECK(jsonb_typeof(data)='object'), note text,
 FOREIGN KEY(asset_id,tenant_id) REFERENCES asset(id,tenant_id)
);
CREATE TABLE audit_log (
 id uuid PRIMARY KEY, actor_id uuid NOT NULL, actor_display_name text NOT NULL CHECK(btrim(actor_display_name)<>''),
 action text NOT NULL CHECK(btrim(action)<>''), target_type text NOT NULL CHECK(btrim(target_type)<>''),
 target_id uuid NOT NULL, occurred_at timestamptz NOT NULL, data jsonb NOT NULL CHECK(jsonb_typeof(data)='object')
);
CREATE INDEX product_category_ref ON product(category_id);
CREATE INDEX product_manufacturer_ref ON product(manufacturer_id);
CREATE INDEX asset_category_ref ON asset(category_id);
CREATE INDEX asset_manufacturer_ref ON asset(manufacturer_id);
CREATE INDEX asset_product_ref ON asset(product_id);
CREATE INDEX asset_location_ref ON asset(location_id);
CREATE INDEX asset_owner_ref ON asset(owner_id);
CREATE INDEX asset_tenant ON asset(tenant_id);
CREATE INDEX asset_event_asset ON asset_event(asset_id,occurred_at);
CREATE INDEX event_product_before ON asset_event ((data #>> '{before,productId}'));
CREATE INDEX event_product_after ON asset_event ((data #>> '{after,productId}'));
INSERT INTO category(id,name,visibility) VALUES('00000000-0000-0000-0000-000000000001','UNKNOWN','GLOBAL');
INSERT INTO manufacturer(id,name,visibility) VALUES('00000000-0000-0000-0000-000000000002','UNKNOWN','GLOBAL');
