# Deployment CLI v1 — approved product requirements and repository-fit design

**Current O1 execution boundary — 2026-10-13:** [O1 Email bootstrap/recovery plan](DEPLOYMENT_CLI_O1_EMAIL_RECOVERY_PLAN.md) is HUMAN REVIEWED / ACCEPTED for this narrow handoff only. O1.1–O1.7 and overall O1 are HUMAN ACCEPTED / CLOSED, including the shared Email input/property correction, isolated verification and documentation/evidence closeout. The original CLI v1 design and [verification](../verification/DEPLOYMENT_CLI.md) below remain historical; no original results are rewritten and no fresh live Docker recovery or Email-delivery claim is added.

## 1. Status, authority and gate

Approved design; implementation explicitly authorized on 2026-10-11. Phases 0–4 are accepted by the user. Later UI redesign is committed at `2795bccbc8bc944a31f562f0d584870d76ea3e36`; the working tree also contains subsequent UI rendering-stability changes. Those changes are preserved and are not CLI work. Historical pending-review sentences in phase documents are not new acceptance gates. There is no Phase 5. Real IServ interoperability remains **MANUAL VERIFICATION PENDING**.

Authority: `AGENTS.md`, `ARCHITECTURE.md`, `DOMAIN.md`, `CODEX_IMPLEMENTATION_TASK.md`; Phase 0–4 plans and verification reports; ADR 0001–0005; current deployment, runtime and authentication implementation. Supporting operational contracts are `README.md`, `docs/AUTHENTICATION.md`, `docs/RUNTIME_BRANDING.md`, `docs/RESTORE_VERIFICATION.md`, `docs/verification/PHASE_2_ISERV_MANUAL.md`, and the post-roadmap `docs/UI_REDESIGN_PLAN.md` / `docs/verification/UI_REDESIGN.md`. This plan does not rewrite accepted specifications or evidence. The user approved this design and its operator-supplied local HTTPS decision; implementation is authorized.

The transport decision in §3 is resolved. The implementation preserves the existing HTTPS authentication contract.

## 2. Actual repository deployment baseline

| Area | Current implementation and implications |
|---|---|
| Stack | `deploy/compose.yaml`: PostgreSQL 17 Alpine, backend, frontend; named `postgres-data` volume; default project `kelios-phase0`. PostgreSQL and backend have no host-published ports. Backend waits for PostgreSQL health; frontend waits for backend health. |
| Builds | `backend/Dockerfile`: Maven 3 Wrapper / Java 25 multi-stage build, non-root UID 100 JRE, no runtime Maven. `frontend/Dockerfile`: Node 24 build, independent nginx-unprivileged UID 101 runtime. Existing `:phase0` local tags contain whichever source was built, not a release guarantee. |
| HTTP | Base Compose publishes `127.0.0.1:${KELIOS_HTTP_PORT:-8080}:8080`. `frontend/docker/default.conf` proxies `/api/` to `backend:8080`; root-path SPA, no cross-origin/subpath support. The bind address is currently hard-coded. |
| HTTPS/auth mounts | `deploy/compose.auth.yaml` supplies authentication environment, read-only key directory, TLS config and externally supplied certificate directory; it additionally publishes loopback HTTPS. It is an existing separate overlay, not CLI-managed production ingress. |
| Database | `backend/src/main/resources/application.yaml`: JDBC URL/user variables, password from `configtree:/run/secrets/` (`db-password`) or password environment; Flyway startup migrations V1–V6; Hibernate validates; Flyway clean disabled. CLI must not implement migrations. |
| Readiness | Backend Docker health uses internal port 8081 `/actuator/health/readiness`; readiness includes database connectivity. Frontend Docker health uses `/healthz` HTTP 8080. PostgreSQL health uses `pg_isready`. None independently proves a successful browser login, correct crypto keys, or OIDC interoperability. |
| Branding | Read-only JSON selected by `KELIOS_CONFIG_FILE`; startup entrypoint symlinks it to `/tmp/kelios/config.json`; nginx serves `/runtime/config.json` with no-store. Logos are mounted at `/runtime/logos/`. React loads/validates branding before rendering. |
| Recovery | `AdministratorRecoveryCommand` invokes `AuthenticationService.bootstrap` in explicit non-web mode; private UTF-8 password file, exact content without appended newline. Backend owns guard, password validation/hashing, transaction and audit. |
| Security material | Mounted RSA signing private/public pair and base64 AES key; database password file. AES key also supports CSRF/flow protection and encrypted OIDC client secrets. Optional provider trust settings are not ordinary v1 config menu items. |

The current base stack can serve HTTP technical/branding checks without a working authenticated browser. It must not be described as a complete HTTP authentication deployment. Existing smoke scripts generate fixtures and write accepted evidence; an installer must not call them.

## 3. Resolved transport decision

The user approved Alternative 1 and explicitly authorized implementation: reuse the existing local HTTPS/authentication overlay with **operator-supplied certificate and private-key paths**. Stage them privately and mount through the existing mechanism. Missing/invalid TLS material must fail before claiming a usable installation. Status reports the configured HTTPS URL. Port/bind configuration is supported; TLS lifecycle/rotation is not a config feature.

Same-origin HTTPS, Secure cookies, exact Origin/CSRF and OIDC bindings remain unchanged. No automatic certificates, self-signed issuance, ACME, DNS, production ingress or HTTP authentication fallback. Base HTTP remains technical/health access only. The previous HTTP-only conflict is resolved by this explicit decision; the rejected HTTP-only alternatives are not implementation work.

## 4. Approved command surface

Exactly seven commands, no force bypasses or extra public commands. `./kelios` without a command displays concise usage listing these seven; it does not silently install. Unknown commands fail with nonzero exit. Interactive operations require a usable terminal before mutation.

| Command | Planned behavior |
|---|---|
| `install` | Check prerequisites; detect existing state/resources; collect and validate configuration; stage private files; generate/validate keys and DB credential; persist prepared state; build missing local images through existing Dockerfiles; start normal Compose stack; wait for database-dependent readiness; invoke existing recovery with operator password file; finish healthy check and record initialized state; report the actual configured HTTPS URL only after successful initialization. Never create domain rows in shell/SQL. |
| `start` | Load validated existing state; verify secret availability; use normal `up -d --wait --no-build`. Idempotent when running. Recreate removed runtime after default uninstall. No wizard, key generation, bootstrap or admin creation. Missing images fail with clear guidance rather than silently changing versions. |
| `stop` | Stop only recorded project services using Compose stop. Keep containers where Compose normally does, named volume, state, branding and keys. Repeated stop succeeds if already stopped. |
| `status` | Minimal recorded-installation status, service running/health information, URL and managed/external mode. No secrets, logs, metrics, image IDs or business queries. Missing installation is reported explicitly. |
| `config` | Small staged menu: port, bind address, blue/green or existing six-token theme values, local logo; save or discard. Validate before replacement. No generic env editor, database switching or domain administration. Apply publication/bind changes by explicit confirmed runtime recreation, or report pending restart; never silently hot-reload unsupported changes. |
| `uninstall` | Confirm runtime removal; Compose down without volumes, remove project orphan runtime only after ownership validation. Preserve local state, branding, keys and managed volume by default. Optional managed data deletion requires separate installation-specific typed confirmation. External database is never deleted or cleaned. |
| `recover-admin` | Load existing deployment configuration, ensure database available, prompt login/display/password/confirmation without echo, invoke existing non-web backend recovery using a temporary mounted file. Backend refusal at one or more usable admins is final; no force, SQL edits or shell usability calculation. |

## 5. Canonical templates and proposed exact local layout

One installation per repository checkout in v1; no fleet/multiple-installation manager. All generated state remains inside this repository, in an ignored `.kelios/` directory. Propose:

```text
kelios                              # future executable POSIX shell entry point
.kelios/                            # generated host-private parent, mode 0700
  installation.conf                 # allowlisted non-secret fields, mode 0600
  secrets/
    db-password                     # never placed in compose.env
    auth/main.private.pem
    auth/main.public.pem
    auth/main.aes
  branding/
    config.json
    logos/logo.<validated extension>
  temporary/                        # transient non-secret Compose interpolation/recovery input
```

Do not create these files in this review. Installation config contains a schema version, installation/project identity, database mode, bind, port, database connection fields excluding password, key IDs/paths, branding selection and completion state. A stable generated project name is `kelios-local-<safe-random-id>`; persist it before any container/volume creation. Do not reuse phase/smoke project names. Compose receives the recorded project via `-p`, never implicit directory defaults. Managed volume is recorded/validated by Compose labels before destructive removal.

Do not `source` editable configuration as executable shell code. Parse only known keys, validate lengths/characters/ranges, reject duplicate/unknown fields and safely serialize. Hostnames and addresses are data, not commands. All paths are quoted. Resolve paths relative to the repository/state directory independent of caller cwd; refuse symlinked secret/state destinations that escape it. Reject unsafe installation-name/path characters. Generated files are never sourced as arbitrary shell programs.

Maintained Compose direction: **one common runtime definition plus a managed-PostgreSQL overlay**. Adapt existing `deploy/compose.yaml` as the canonical common frontend/backend template; add `deploy/compose.postgres.yaml` for the existing PostgreSQL service, volume and backend dependency. External mode uses only common runtime; managed adds the overlay. Parameterize database URL/user, secret path, branding directory, project and bind/port. Existing authentication overlay is reused with supplied TLS as resolved in §3. Update existing script/document callers to include the managed overlay during eventual implementation so their behavior is preserved. Do not generate per-installation stacks, copy nginx/Dockerfiles, or introduce conditional optional dependencies requiring novel Compose behavior. A generated resolved Compose document is unnecessary and could expose interpolated secret environment values; do not persist one by default.

Existing profiles/optional-dependency alternatives could reduce moved lines but make required managed health dependencies conditional and version-sensitive. The explicit overlay is easier to inspect and test. This is a small deployment prerequisite, not a new architecture. Preserve all existing service runtime behavior and build/image separation.

Add `.kelios/` to `.gitignore` at implementation time, before generating any installation state. Existing ignored `.env` is not the canonical state file and no password goes there. No implementation or ignore-rule edit occurs now.

## 6. Installation existence and lifecycle

Existence means any `.kelios/` installation state, including prepared/incomplete state, or resources bearing that recorded project identity. An incomplete directory is not treated as permission to overwrite. Validate the manifest against actual project labels; an inconsistent/missing manifest with matching resources fails safely rather than adopts or deletes them. Other checkout/smoke resources are never adopted automatically.

States are operator deployment state, not Domain database state: absent, prepared/incomplete, initialized (running or stopped), runtime removed with persistent state preserved, and managed data explicitly deleted. No new application table/state is needed.

- Install refuses every existing installation, including default-uninstalled or partially prepared state, with guidance; no overwrite prompt that silently regenerates keys.
- Default uninstall leaves initialized state recoverable. Later start recreates runtime using the same volume/configuration/keys, with no bootstrap.
- Destructive managed-volume deletion records data absent. Later start refuses to pretend the old installation remains complete. The preserved directory must be explicitly archived/removed by the operator before a fresh install; do not add reset/reinstall commands or automatically erase surviving keys. A new install then generates a distinct project/state.
- External uninstall always remains data-preserving and restartable. It never changes external data lifecycle.
- Partial install preserves configuration/keys and any created data. Report stage and retry guidance; do not delete a database, rerun a wizard or silently mark installed. A repeated install refuses. Start may resume prepared runtime startup with an explicit incomplete notice; operator then uses recover-admin if needed. Successful recovery plus healthy services can finish initial prepared state. Recover-admin on an already initialized installation does not reset deployment state.

No elaborate rollback manager or automatic database rollback. Trap interrupts to restore terminal echo and remove transient password material; keep persistent keys required by any already-written encrypted data.

## 7. Database modes and networking

Managed default: existing PostgreSQL 17 image, persistent project-scoped named volume, fixed internal host `postgres:5432`, database/user `kelios`, cryptographically generated password, existing health-gated backend startup. No PostgreSQL or management port published. Stop/down without volumes preserves data.

External: prompt host/port/database/user/password, hide password input, mount the same db-password filename. Use the existing JDBC configuration. Host is reachable **from backend container**, not necessarily host `localhost`; explain that distinction. Validate the restricted JDBC host/database shape and encode supported values safely, rather than accepting arbitrary JDBC options. No driver options, SSL policy editor or credentials embedded in JDBC URLs. An external PostgreSQL server must provide an appropriately authorized dedicated KELIOS database/schema; Flyway applies current migrations normally. The operator must acknowledge that this writes/migrates the chosen database. Existing unrelated schema/migration conflicts fail startup, not shell cleanup. Reattaching an existing KELIOS database also requires its original encryption keys; do not generate replacements and pretend encrypted data remains usable.

Backend readiness verifies actual authenticated database access, unlike `pg_isready` alone. CLI reports unreachable/authentication/configuration failures as database/startup failure with sanitized guidance. Exact causes need not be extracted by parsing arbitrary logs that may contain confidential provider data; do not claim a password error solely from timeout. Never export passwords in ordinary environment or Compose output.

Default bind `127.0.0.1`, default HTTP port `8080`; 1–65535 numeric port, reject malformed input. Validate literal IPv4/IPv6 addresses; Compose serializes IPv6 with brackets. Do not silently fall back to `0.0.0.0`. A wildcard bind explicitly selected requires a local exposure warning, and the displayed browser URL uses loopback rather than an unspecified address. Docker binding is authoritative for availability; a preflight socket check cannot reserve the port. If unsupported address-family or port collision occurs, fail clearly and retain state. No firewall/DNS/proxy automation.

**Origin coupling:** changing the port/host of an authenticated installation also changes the exact trusted public origin/callback. This is not just frontend networking. Under a selected HTTPS solution, derive and validate the deployment origin consistently and warn operators about previously configured OIDC redirect URIs and existing browser JWTs. Do not change the JWT issuer automatically during `config`: keep the installation's stored issuer stable, and reuse accepted issuer validation. The supplied certificate must cover the configured browser host; HTTP remains technical-only.

## 8. Secret generation, mounts and retention

Recommend POSIX shell plus Docker/Compose and host OpenSSL for secure generation/key validation, following existing documented OpenSSL commands. No Python/Node/jq installer dependency. OpenSSL is an explicit proposed prerequisite; do not hide it behind an unaudited helper image or write custom crypto. Existing image builds require Docker BuildKit and initial registry/dependency network access, not host Java/Node/Maven.

| Material | Establishment and existing use | Lifecycle |
|---|---|---|
| Managed DB password | Generate securely, e.g. 32 random bytes represented as hex; Compose file secret `/run/secrets/db-password`, PostgreSQL PASSWORD_FILE and backend configtree. | Keep across stop/default uninstall and volume recovery. |
| External DB password | Supplied privately; same file mount, no password command args/env/config output. | Keep unless operator intentionally removes preserved installation state; CLI never deletes external DB. |
| `main.private.pem` | Generate minimum 3072-bit RSA PKCS8 private key, or validate supplied compatible key. `JwtTokens` selects mounted key by trusted kid. | Keep across stop/default uninstall; do not rotate or regenerate on start. |
| `main.public.pem` | Matching X509 public key, validate pair; used by local JWT verification. | Keep with signing private key. |
| `main.aes` | 32 cryptographic random bytes, base64 encoded; `SecretEnvelope` reads by key ID. | Essential to decrypt existing OIDC secrets and protect flow/CSRF material. Restore requires original database and needed versioned keys. |
| Recovery password | Operator supplied exact UTF-8 content; transient file only. | Remove after success, refusal, failure or interrupt. Not a permanent recovery secret. |

Generated auth keys are the default for a fresh database. Supplied key material is allowed for deliberate existing-database attachment; validate format and key IDs, never show contents. Missing required key is a hard failure, not a regeneration opportunity. Supplied provider trust configuration, if already necessary to restore a deployment, must be retained; ordinary v1 config does not administer IServ/trust settings.

Host-private parent mode 0700 protects files. Existing Compose file secrets preserve host permissions; do not promise Compose uid/gid/mode will fix unreadable host files. Read-only children need runtime-readable modes (existing README uses 0444 behind a 0700 parent; auth mount directory can be 0755 behind that parent). UID 100 backend and UID 101 nginx must be tested. Mount only required files/directories; do not mount the entire private installation into frontend or serve secrets. If platform permissions cannot provide this arrangement, fail and explain rather than chmod the private parent world-readable.

Passwords are read without echo from the terminal with echo restoration traps, never shell tracing. Use a builtin write without appended newline so exact password policy is preserved. Backend validates GLOBAL login/password policy; shell confirms matching input but does not reimplement hashing or policy. No raw password in subprocess command line, environment, log, captured evidence, resolved Compose output or tracked files. Temporary mounted input and override metadata live under the private directory with exclusive creation; clean only files the invocation created. Do not show application tokens on success.

## 9. Existing recovery invocation

Actual class: `backend/src/main/java/com/github/ksewen/kelios/identity/auth/AdministratorRecoveryCommand.java`. It requires `spring.main.web-application-type=none`, reads at most 2048 bytes from the supplied file, preserves exact content and calls `AuthenticationService.bootstrap`. The latter hashes via supported Argon2id, locks `IdentityStore.recoveryGuard`, freshly counts usable GLOBAL/ADMIN/ACTIVE credential hashes, refuses if any exist, and atomically creates User + credential + `GLOBAL_ADMIN_BOOTSTRAPPED` AuditLog with known global attribution.

Future CLI invokes a one-off existing backend container with the same database/key settings and a narrow temporary password-file mount:

```text
compose run --rm --no-deps backend
  --spring.main.web-application-type=none
  --kelios.recovery.enabled=true
  --kelios.recovery.login=<validated login>
  --kelios.recovery.display-name=<operator display name>
  --kelios.recovery.password-file=/run/secrets/kelios-recovery/password
```

`compose` here means an internal shell helper for the recorded project/env/template files, not an eighth command. Only file path and nonsecret identity fields are arguments. Prefer a temporary overlay mounting the one file over mounting all input material. `--no-deps` requires an available database: guided recovery can start the configured runtime first and disclose that action; do not duplicate DB availability/guard logic. Successful container exit is required; failures/refusal do not become success. No HTTP bootstrap, SQL credential edits or `--force`. Recovery remains valid only at zero usable admins; forgetting a valid password is not credential unusability.

## 10. Branding and config implementation fit

Existing schema fields are exactly `schemaVersion: 1`, optional same-origin `logoUrl`, and six hex-color theme tokens: background, foreground, primary, primaryForeground, accent, accentForeground. No title/tagline/font/dark-mode/product setting exists in that schema. Named blue/green selects current generic samples, not a new branding API. The menu may expose the existing six validated tokens without inventing arbitrary CSS.

Copy a selected readable local logo into installation branding assets and write its same-origin runtime URL; validate type/path/size and never inject SVG markup. Browser logo fallback remains unchanged. Logo/config are public; never mix them with auth material.

Mount branding directories rather than independently bind-mounting config.json when CLI uses atomic rename: this lets nginx observe replacement via the directory mount, avoiding a stale file inode. Config and logos remain read-only inside nginx, readable UID 101. JSON writer uses fixed fields/validated hex colors and controlled asset URL, not unescaped operator JSON fragments. Stage complete config on the same filesystem and rename atomically after validation; canceled/failed edit leaves original state intact.

Readable file content changes become visible on next page load, not instant reactive hot reload. Changed source mount/port/bind requires frontend recreation, never image rebuild. If authenticated origin changes, backend configuration must also be applied coherently under §7. Interrupted edits leave either old or new complete configuration; failed runtime apply reports pending/reapply state rather than claiming success. No restart command is added; `start` reapplies saved desired config.

## 11. Minimal status and ownership-safe uninstall

Status can use `docker compose ps --all --quiet <service>` followed by narrowly formatted Docker inspect state/health, avoiding env dumps and a JSON parser dependency. Known services only. Report running healthy/unhealthy/starting, stopped or absent. Aggregate: running only when expected services are running and healthy, otherwise stopped/degraded. External mode reports `Database: external PostgreSQL`; no invented external container health line. Backend health demonstrates database access, not detailed server status. URL comes from validated stored deployment settings and is not proof of reachability/login. Docker unavailable is an actionable status error, not a false stopped diagnosis.

Default uninstall removes only this recorded Compose project's runtime containers/network, no global prune, images or developer caches. `down` has no `-v`. Before deletion validate exact project labels and managed volume identity; never delete by a broad `kelios*` match. Repeated runtime uninstall is safe.

Optional permanent managed database deletion: separate warning explaining irreversible loss, followed by typed `DELETE <installation-project-name>` after ordinary runtime-removal confirmation. Revalidate volume identity and absence of consumers before explicit volume removal. Fail rather than removing a volume used elsewhere. Keep config/keys, mark data absent, document §6 fresh-install behavior. No force flag. External mode never offers deletion or calls SQL DROP/cleanup. User cancellation leaves data intact.

## 12. Failure handling and idempotency

| Problem | Required response |
|---|---|
| Docker/Compose/OpenSSL missing or daemon inaccessible | Fail before creating installation, name missing prerequisite; no package installation or sudo automation. |
| Existing/incomplete installation | Refuse overwrite; identify preserved state without secrets and give start/recovery guidance. |
| No initialized state | start/stop/config/recovery report not initialized; status reports absent; uninstall does not target guessed resources. |
| Invalid/in-use port or bind | Validate first; Docker final binding may fail; keep previous saved config or clearly mark pending change. |
| Missing logo/config | Refuse requested change; do not silently substitute a different selected logo. Existing frontend safe fallback remains intact. |
| External connection/auth failure | Failed backend readiness/startup; sanitized database guidance, no log/body/password dump or external cleanup. |
| Missing/malformed crypto key | Fail preflight; never generate replacement over existing encrypted data. |
| Unhealthy containers/migration failure | Bounded wait, report failing service and preserved installation; Flyway error does not trigger shell migration/clean. |
| Bootstrap/recovery refused/fails | Nonzero; do not claim admin exists or usable login; no second recovery path; remove temporary password. |
| Partial/interrupted install | Preserve persistent state and report last completed stage; restore echo/remove transient credential; no destructive rollback. |
| Interrupted config | Atomic staged file replacement; coherent manifest/env update with private temporary files and a final commit marker, reject incomplete generation; no runtime mutation until complete validated generation. |
| Managed data exists on uninstall | Preserve by default; explicit second destructive confirmation only for the exact volume. |

Prevent concurrent mutating CLI invocations using a narrow local installation lock directory with ownership metadata, not a database/global locking framework. An abandoned lock fails with explicit manual inspection guidance; no automatic deletion of another invocation's lock. Status may remain read-only. Compose creation/removal and startup are delegated to Docker, not hand-rolled container management.

## 13. Repository-fit answers

1. **Managed/external:** supported with a small common-template/managed-overlay refactor; current unconditional postgres dependency must change. Changing JDBC URL alone is insufficient.
2. **Smallest configuration:** private allowlisted installation manifest + nonsecret generated Compose env + file secrets + public branding, maintained templates; no generated canonical full stack.
3. **Actual branding fields:** logoUrl and six listed theme colors, schemaVersion fixed 1; blue/green fixture selection. No new fields.
4. **No-rebuild branding:** yes, mounted file changes on next page load; source/bind changes require recreate, same image retained.
5. **Health:** existing Docker health/state for three managed services; external DB has no Docker state; readiness checks its connectivity. Health is not login validation.
6. **Recovery:** AdministratorRecoveryCommand / AuthenticationService.bootstrap, non-web one-off existing backend and exact private file.
7. **Secrets:** database credential, RSA private/public signing pair, 32-byte AES base64 key; optional provider trust material when deployed. TLS supplied material only if §3 alternative is approved.
8. **Retention:** DB credential/config/branding and auth keys survive stop/default uninstall; AES keys are indispensable for restored encrypted OIDC data; signing keys preserve token continuity.
9. **Bind/port:** hard-coded loopback publishing needs parameterization; browser origin/callback must track approved transport, not merely frontend port. HTTP auth is incompatible today.
10. **Database representation:** common runtime + managed overlay, external uses common only. No duplicate stack/services, no external database container.
11. **Existence:** private state/manifest including incomplete preparations plus recorded project ownership; never infer installation from health alone.
12. **After default uninstall:** start recreates same runtime/data/keys; install refuses existing state. Explicit data deletion requires explicit fresh-state handling, not automatic recreation.
13. **Prerequisites/conflicts:** Operator-supplied HTTPS resolves the previous HTTP-only blocker; Compose factoring, bind/config/secret-path injection and documented OpenSSL availability are small implementation prerequisites. Existing backend recovery and Domain need no change.

## 14. Implementation sequence after final approval

1. Record the approved §3 decision; preserve the approved configuration/tooling/lifecycle recommendations. Record a deployment ADR only when implementation is authorized; preserve ADR 0001–0005.
2. Parameterize/factor existing Compose stack and update its maintained callers without changing behavior; validate both modes and private mounts.
3. Add the shell entry point, safe config parser/serializer, state/ownership handling, private files and ignore rule.
4. Implement start/stop/status first, then guided install and exact existing recovery bridge; do not alter backend classes.
5. Implement staged branding/network config and ownership-safe uninstall/typed data deletion.
6. Add narrowly scoped shell/isolated deployment tests and operator documentation; run existing impacted security/deployment/branding regression, preserving accepted evidence destinations.
7. Review failure/interruption/redaction handling and actual browser authentication under the approved transport. Report evidence separately; no real IServ claim.

## 15. Verification strategy (future, not executed here)

- Shell syntax/portable supported shell execution, argument quoting, invalid inputs, unknown commands, interrupted no-echo prompts, private file modes, safe state parsing, traps and lock contention. Tests use fake Docker command responses only for control-flow edges, not to claim deployment success.
- Real isolated managed PostgreSQL project: empty install, Flyway V1–V6, healthy three-service state, one admin via existing recovery, refusal on repeat/concurrent recovery, stop/start idempotency and unchanged volume/key hashes. Normal browser login/CSRF/refresh must pass under resolved transport.
- Separate disposable external PostgreSQL server: dedicated database, normal migration/start; bad host/password fail clearly; no managed service/volume created; uninstall preserves external rows/server. Never use non-disposable external data.
- Ownership: commands do not touch smoke projects, unrelated volumes/containers/images/caches. Default uninstall preserves data/keys, subsequent start sees existing users; explicit managed deletion requires exact typed confirmation. Cancellation/mismatch never removes data.
- Config: unchanged images under theme/logo switches; all six tokens and same-origin logo validation; next-load versus recreation semantics; port/bind validation/collision; coherent origin and callback handling once transport approved; canceled/interrupted save preserves valid config.
- Secrets: no password args/environment/Compose interpolation/output; no tokens in console/evidence; actual UID100/101 readability; missing/malformed keys fail, existing-database encrypted OIDC data can be read only with retained original AES keys.
- Status: healthy/stopped/absent/degraded and external mode, no business requests or sensitive output; readiness errors cannot produce false successful installation.
- Regression: existing independent image builds/non-root/no runtime toolchain, proxy/private management networking, authentication/authorization/Tenant/audit boundaries, branding and bilingual UI. Do not rerun evidence-writing old smoke scripts into accepted destinations; use new deployment-CLI evidence directory. Full Maven only if changed deployment coverage requires it; no claim that shell tests verify backend invariants.
- No TLS production/ACME, backup/restore service, update/upgrade, remote operations, real IServ verification or Phase 5.

## 16. Expected files during implementation and current-task boundary

Expected new files: repository `kelios`; `deploy/compose.postgres.yaml`; `docs/DEPLOYMENT_CLI.md`; dedicated `scripts/test-deployment-cli.sh` and scoped shell fixtures if justified; new `docs/verification/DEPLOYMENT_CLI.md` / `docs/verification/deployment-cli/`; eventual deployment ADR. Keep helper logic in the entry script unless its actual size justifies one small deployment shell helper, not a framework.

Expected modifications: `.gitignore`, `deploy/compose.yaml`, README deployment instructions; existing smoke scripts only to preserve their managed-mode invocation after factoring (not accepted evidence). `deploy/compose.auth.yaml` / authentication operational docs only if the chosen transport prerequisite requires narrow parameterization. No Dockerfile/application/API/Domain/migration change is presently required. Required backend changes would be a new approval gate, not hidden implementation work.

The original review created only this plan. Subsequent explicit implementation approval permits CLI/deployment/test/document changes described here; backend Domain/API/authentication and migrations remain unchanged, and existing UI stability changes remain separate. Actual implementation verification is recorded in `docs/verification/DEPLOYMENT_CLI.md`. **Approved for implementation with the resolved operator-supplied local HTTPS decision.**
