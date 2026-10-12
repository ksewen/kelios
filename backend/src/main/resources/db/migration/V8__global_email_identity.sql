-- Clean target cutover only. No fabricated Email/verification provenance for disposable old identities.
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM local_global_credential) THEN
    RAISE EXCEPTION 'GLOBAL Email cutover requires a clean credential state; separately authorize clearing/resetting development data. No backfill is supported.';
  END IF;
END $$;
ALTER TABLE local_global_credential DROP COLUMN login_name;
ALTER TABLE local_global_credential ADD COLUMN email text NOT NULL
  CHECK (length(email) BETWEEN 3 AND 254 AND email = lower(btrim(email)) AND position('@' in email)>1);
ALTER TABLE local_global_credential ADD COLUMN email_verified_at timestamptz;
CREATE UNIQUE INDEX local_global_credential_email_unique ON local_global_credential(lower(email));
