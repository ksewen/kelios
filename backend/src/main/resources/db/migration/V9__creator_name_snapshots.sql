-- Historical labels exist only for creations after this migration. Do not guess old names.
ALTER TABLE tenant ADD created_by_display_name text CHECK (created_by_display_name IS NULL OR btrim(created_by_display_name) <> '');
ALTER TABLE app_user ADD created_by_display_name text CHECK (created_by_display_name IS NULL OR btrim(created_by_display_name) <> '');
ALTER TABLE category ADD created_by_display_name text CHECK (created_by_display_name IS NULL OR btrim(created_by_display_name) <> '');
ALTER TABLE manufacturer ADD created_by_display_name text CHECK (created_by_display_name IS NULL OR btrim(created_by_display_name) <> '');
ALTER TABLE product ADD created_by_display_name text CHECK (created_by_display_name IS NULL OR btrim(created_by_display_name) <> '');
ALTER TABLE asset ADD created_by_display_name text CHECK (created_by_display_name IS NULL OR btrim(created_by_display_name) <> '');
ALTER TABLE location ADD created_by_display_name text CHECK (created_by_display_name IS NULL OR btrim(created_by_display_name) <> '');
ALTER TABLE owner ADD created_by_display_name text CHECK (created_by_display_name IS NULL OR btrim(created_by_display_name) <> '');
