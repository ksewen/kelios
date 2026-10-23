# Managed backup and disaster recovery

This procedure covers an initialized KELIOS installation using its **managed PostgreSQL** service. A recovery bundle contains the database, configuration, database password, authentication and encryption keys, the active TLS certificate and key, and referenced logos. Treat the whole bundle as highly sensitive. [Deployment](deployment.md) covers installation and routine commands; [deployment recovery](deployment-recovery.md) covers other failures.

## Make and store a backup

Run as the ordinary account that owns the installation and has approved local Docker access. Use the same `--state-dir` on every command if the installation is outside the default state directory. In a clean source checkout, choose a **new** absolute bundle path:

```sh
STATE="${XDG_DATA_HOME:-$HOME/.local/share}/kelios/default"
BACKUP_ROOT="$HOME/kelios-backups"
umask 077
mkdir -p "$BACKUP_ROOT"
chmod 700 "$BACKUP_ROOT"
BUNDLE="$BACKUP_ROOT/kelios-$(date -u +%Y%m%dT%H%M%SZ)"
./kelios --state-dir "$STATE" status
./kelios --state-dir "$STATE" --output "$BUNDLE" backup
```

`backup` requires a healthy managed runtime. It validates the PostgreSQL dump and complete bundle before publishing it. The final path must be absent; an existing directory or file is never reused or overwritten. Record the bundle path and confirm the command succeeded. Its `manifest.json` contains the required source release and integrity metadata. Do not edit bundle files or use a database dump alone for recovery.

Store an encrypted copy off the deployment host under your organization's access and retention policy. Restrict the bundle directory to its owner (`0700`) and its files to owner read/write (`0600`); preserve those permissions when copying. For an SSH-accessible recovery host, one transfer option is:

```sh
RECOVERY_HOST=operator@recovery.example.internal
ssh "$RECOVERY_HOST" 'umask 077; mkdir -p "$HOME/kelios-backups"; chmod 700 "$HOME/kelios-backups"'
scp -rp "$BUNDLE" "$RECOVERY_HOST:kelios-backups/"
```

Replace the example account and host with your approved destination. Protect the off-host copy with approved encryption and access controls; SSH transport alone does not encrypt stored files. Do not put bundles in Git, shared logs, support tickets or browser downloads. Copy the **entire** directory, including `manifest.json`. The restore command rechecks the transferred bundle before using it.

## Restore on a fresh target

Use a fresh Linux host with Docker Engine, `docker compose`, Git, OpenSSL, Python 3.9 or newer and the required host networking. Use an ordinary operator account with approved local Docker access; Docker socket access is high-privilege host access. The target state directory must be absent, and no Docker resource may already claim the bundle's project identity. Do not run `install` or generate new authentication keys for the backed-up database.

Place the private bundle at `$HOME/kelios-backups/<bundle-name>` on the target. Decide **before restoring** whether the browser hostname or HTTPS port must change; if so, prepare the restore config described below and use the config variant of the restore command. Check out the **exact source release** in the manifest. The recorded release is KELIOS's 12-character Git commit identity; if that commit is unavailable, stop and recover it rather than selecting a newer release:

```sh
BUNDLE="$HOME/kelios-backups/<bundle-name>"
STATE="${XDG_DATA_HOME:-$HOME/.local/share}/kelios/default"
SOURCE="$HOME/kelios-recovery-source"
RELEASE=$(python3 -c 'import json,re,sys; value=json.load(open(sys.argv[1]))["release"]; assert re.fullmatch(r"[0-9a-f]{12}",value); print(value)' "$BUNDLE/manifest.json")
git clone https://github.com/ksewen/kelios.git "$SOURCE"
git -C "$SOURCE" switch --detach -- "$RELEASE"
test "$(git -C "$SOURCE" rev-parse --short=12 HEAD)" = "$RELEASE"
cd "$SOURCE"
./kelios --state-dir "$STATE" --bundle "$BUNDLE" restore
./kelios --state-dir "$STATE" status
./kelios --state-dir "$STATE" start
./kelios --state-dir "$STATE" status
```

For a changed hostname or HTTPS port, **replace only the `restore` line** above with the configured restore command in the next section; run the same `status`, `start`, `status` lines once. The `test` and `restore` commands must both succeed. Restore verifies all files, hashes, release, PostgreSQL dump and migration history before target state or volume creation. It imports the database while the backend is stopped, preserves the backed-up authentication/encryption keys exactly, and leaves application startup to `start`. It does **not** update the application or run a migration from a newer source release.

### Hostname, origin and TLS on a replacement host

If the browser hostname and HTTPS port remain the same, restore keeps the saved JWT issuer and active TLS pair. Clients still need to trust that certificate and reach the new host. Restored signing keys do not themselves revoke previously issued tokens; normal token expiry and current account checks still apply.

If the browser hostname or HTTPS port must change, prepare a private `KEY=value` restore configuration before `restore`. Use only `BROWSER_HOST`, `BIND`, `HTTP_PORT`, `HTTPS_PORT`, and optionally **both** `TLS_CERTIFICATE` and `TLS_PRIVATE_KEY` with absolute PEM paths. For example, after replacing the example values with the real host and approved bind address:

```sh
umask 077
cat > "$HOME/kelios-restore.conf" <<'EOF'
BROWSER_HOST=kelios.example.internal
BIND=127.0.0.1
HTTPS_PORT=8443
EOF
chmod 600 "$HOME/kelios-restore.conf"
./kelios --state-dir "$STATE" --config "$HOME/kelios-restore.conf" --bundle "$BUNDLE" restore
```

Use this restore command **instead of** the plain restore command above, then run `status`, `start` and `status`. A changed hostname gets a new 730-day self-signed private certificate unless a matching operator-supplied PEM pair is provided; the original pair remains private in state. A port-only change retains the certificate. Trust the active certificate on every client or provide a trusted PEM pair. There is no automatic public certificate issuance or renewal.

A changed hostname **or HTTPS port** changes the public origin and JWT issuer. Previously issued tokens for the old issuer fail validation, and host-only refresh cookies do not follow a changed hostname. Plan for users to sign in again. Update every iServ/OIDC provider's registered callback to the recovered configuration's exact callback URI, of the form `https://<browser-host>:<https-port>/api/v1/auth/oidc/<tenant-id>/callback`, before enabling those logins. Verify the configured callback in KELIOS and with the provider; a real provider login remains an operator acceptance check.

## Verify recovery

After `start`, require `status` to show healthy backend, frontend and PostgreSQL. From a client that trusts the active TLS certificate, check HTTPS health, for example:

```sh
curl --fail --show-error --silent 'https://<browser-host>:<https-port>/api/v1/status'
```

Expect `"status":"ready"`. Confirm the certificate hostname and trust in a browser, sign in with an existing administrator, and read representative application records that were present at backup time. Check a known record or sentinel, branding/logo, and an existing iServ configuration; where a real provider is available, test its callback and login. Confirm the expected hostname, ports and OIDC registrations before admitting users. Take a new backup before any later `update`.

## Incomplete or refused restore

An invalid bundle, unavailable exact release, existing target, resource collision or unreadable dump is refused. If no target state was created, correct the cause and retry with the same bundle. If `status` reports `restoration-incomplete`, the backend cannot start. Preserve the bundle, inspect the error and private logs, and decide whether the **incomplete target** can be discarded. Only then run:

```sh
./kelios --state-dir "$STATE" status
./kelios --state-dir "$STATE" restore discard-incomplete
./kelios --state-dir "$STATE" --bundle "$BUNDLE" restore
./kelios --state-dir "$STATE" start
```

`discard-incomplete` requires an exact, project-specific interactive confirmation. It removes the incomplete target's owned runtime, managed volume and state, while retaining the bundle. Never use it on an initialized installation. Restore into an existing initialized deployment, destructive replacement, external PostgreSQL backup/restore, implicit release upgrades, and backup scheduling are unsupported.

## Incident checklist

1. Secure the complete off-host bundle and its matching clean Git release; restrict access to the bundle. Do not publish credentials or logs.
2. Prepare a fresh Linux target, ordinary operator account, local Docker Engine/Compose access and required ports. Decide the browser hostname, HTTPS port and certificate trust before restore.
3. Copy the bundle into a private directory. Set `BUNDLE`, `STATE` and `SOURCE` as above, read the release from `manifest.json`, check out that exact commit, and verify the 12-character identity.
4. Run **one** `./kelios --state-dir "$STATE" --bundle "$BUNDLE" restore`, adding `--config "$HOME/kelios-restore.conf"` only for an intentional origin/TLS remap. Stop on any refusal; do not run `install` over the backup.
5. Run `status`, `start`, `status`; verify trusted HTTPS `/api/v1/status`, administrator login, representative data, branding, and encryption-dependent iServ configuration. Update provider callback registrations if the origin changed.
6. If restoration is incomplete, inspect first and use the exact-confirmed `restore discard-incomplete` procedure only for that disposable partial target. Keep the original bundle for another attempt.
