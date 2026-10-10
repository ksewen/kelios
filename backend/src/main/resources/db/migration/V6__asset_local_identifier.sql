-- Optional organization-assigned metadata. Existing registrations naturally receive NULL.
ALTER TABLE asset ADD COLUMN local_identifier text;
-- PostgreSQL UTF-8 char_length counts Unicode code points, not UTF-16 units.
-- Explicit Cc ranges (U+0000 is not representable in PostgreSQL text), plus line separators.
ALTER TABLE asset ADD CONSTRAINT asset_local_identifier_length CHECK (local_identifier IS NULL OR char_length(local_identifier) <= 128);
ALTER TABLE asset ADD CONSTRAINT asset_local_identifier_characters CHECK (local_identifier IS NULL OR local_identifier !~ U&'[\0001-\001F\007F-\009F\2028\2029]');
-- No uniqueness, index, inference, historical rewrite or normalization trigger.
