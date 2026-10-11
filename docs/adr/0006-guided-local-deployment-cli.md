# ADR 0006 — Guided local deployment CLI

Status: Product design approved; implementation authorized, 2026-10-11. Verification and user acceptance are recorded separately.

## Context

Phases 0–4 and subsequent UI redesign remain the application baseline. The approved [CLI plan](../plans/DEPLOYMENT_CLI_PLAN.md) adds operator orchestration, not Domain behavior. The final transport decision permits only the existing local same-origin HTTPS overlay with operator-supplied certificate/private key. Production TLS automation remains excluded.

## Decisions

- One repository-level POSIX shell executable with exactly install/start/stop/status/config/uninstall/recover-admin. Docker Compose and host OpenSSL are prerequisites; Python is used only by verification tooling, never the installer.
- Factor the existing frontend/backend definition from the managed PostgreSQL overlay. External PostgreSQL uses the same runtime without a PostgreSQL service. Retain existing images, health checks, Flyway and private backend/management networking.
- Generated state lives under ignored `.kelios/`, with a private host parent, allowlisted non-executable manifest, file secrets, staged supplied TLS and public runtime branding. Compose interpolation is generated transiently from the validated manifest; there is no independently mutable persistent env configuration. A narrow local lock prevents competing mutating commands.
- Preserve accepted exact HTTPS origin, Secure/HttpOnly cookies, CSRF, JWT/OIDC and authorization. Supplied certificate must match the configured browser host and private key. No issuance, insecure fallback or TLS rotation command.
- Recovery invokes the existing non-web AdministratorRecoveryCommand with an exact private mounted password file. Backend owns policy, zero-usable-admin guard, hashing, transaction and GLOBAL_ADMIN_BOOTSTRAPPED audit. Shell never issues account SQL or adds recovery semantics.
- Default uninstall removes only recorded Compose runtime resources and preserves named database volume/configuration/keys. Explicit managed database deletion requires separate exact installation-specific typed confirmation and verified volume ownership. External PostgreSQL is never deleted.
- Existing branding fields only; directory mounts permit atomic config/logo replacement without rebuilding images. Port/bind changes require start to recreate affected runtime; HTTPS port changes update public origin while JWT issuer remains stable.

## Consequences

Local installation becomes guided while application architecture remains unchanged. The host private parent protects runtime-readable secret children required by non-root containers. Original AES keys must accompany an existing database containing encrypted OIDC configuration. No update/backup/restore/rotation/remote deployment subsystem, migrations or Phase 5. [Operator guide](../DEPLOYMENT_CLI.md) documents lifecycle and limitations; [CLI evidence](../verification/DEPLOYMENT_CLI.md) records actual verification.
