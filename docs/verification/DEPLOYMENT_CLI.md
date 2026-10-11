# Deployment CLI verification

Date: 2026-10-11. Product design and operator-supplied local HTTPS transport were explicitly approved. Deployment CLI user acceptance remains **PENDING REVIEW**. Real IServ interoperability remains **MANUAL VERIFICATION PENDING**; no real provider was contacted or credentials requested.

## Scope and separation

The implementation adds only guided local deployment orchestration, Compose factoring/config injection, operator documentation and verification. No backend/API/authentication/Domain or migration changes. Existing uncommitted UI rendering-stability changes are preserved separately; the current frontend regression includes that worktree. Accepted Phase 0–4 ADRs/evidence and UI redesign evidence were not rewritten. [Preservation report](deployment-cli/preservation.json) compares tracked historical content with Git, normalizing existing text checkout CRLF when necessary; binary content remains exact.

[File manifest](deployment-cli/FILES.json) identifies CLI files and hashes; generated `.kelios/` / `.cache/` material is not repository evidence. [ADR 0006](../adr/0006-guided-local-deployment-cli.md), [approved plan](../plans/DEPLOYMENT_CLI_PLAN.md) and [operator guide](../DEPLOYMENT_CLI.md) record the design.

## Commands actually executed

From repository root:

```sh
sh -n kelios
./scripts/test-deployment-cli.sh > .cache/deployment-cli/control.log 2>&1
python3 scripts/verify-deployment-cli.py > .cache/deployment-cli/integration.log 2>&1
git diff --check
openssl version
docker version --format '{{.Server.Version}}'
docker compose version --short
docker image inspect kelios-backend:phase0 kelios-frontend:phase0 --format '{{.RepoTags}} {{.Id}} user={{.Config.User}}'
```

From backend:

```sh
MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw -B -DskipITs test > ../.cache/deployment-cli/backend-unit.log 2>&1
```

From frontend:

```sh
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm test > ../.cache/deployment-cli/frontend-test.log 2>&1
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm run build > ../.cache/deployment-cli/frontend-build.log 2>&1
```

The real verifier invokes the executable through a real pseudoterminal in dedicated `.cache/deployment-cli/managed` and `external` checkout fixtures. It copies maintained deployment templates and uses the existing source build contexts. Install runs ordinary `docker compose build`, `up -d --wait --no-build`, and the existing non-web backend recovery command. No smoke/browser/domain fixtures are loaded by the installer. The verifier itself creates accounts through normal HTTPS APIs only to test recovery lifecycle; these are isolated disposable verification data.

Operator-supplied test TLS fixture is created **outside the CLI** with OpenSSL `req -x509`, SAN localhost/127.0.0.1 and a two-day lifetime. CLI never issues certificates. Direct normal HTTPS requests verify the certificate using that supplied fixture as the local test trust anchor, not a TLS trust-all setting.

The verifier runs existing Playwright tests through `npm run test:smoke`, with explicit local TLS test settings, Chromium sandbox enabled, browser cache under `.cache/playwright`, private test password file, and the real deployed HTTPS application:

- Green: `tests/runtime-branding.spec.ts` and `tests/authentication.spec.ts`, selecting the two real login/CSRF cases and all six branding cases.
- Blue: `tests/runtime-branding.spec.ts`, all six cases.

Exact subprocess argument/environment construction is in `scripts/verify-deployment-cli.py`. Password values and tokens are never printed. The two browser logs record individual case names and counts.

## Results

| Check | Final result |
|---|---|
| Shell syntax | `kelios` and every maintained shell script pass `sh -n`. |
| CLI TTY/control tests | **23 passed**, zero failures/errors/skips. |
| Real isolated Docker/PostgreSQL lifecycle | **35 asserted checks passed**, zero failures; these are deployment checks, not Maven integration-test counts. |
| Backend Surefire unit regression | **74 passed**, zero failures/errors/skips. Full Maven PostgreSQL/Failsafe suite was not rerun: no backend application/test/migration surface changed. |
| Frontend unit regression | **51 passed** across four files. |
| Frontend TypeScript/Vite production build | **Passed**. |
| Actual HTTPS Chromium, green | **8 passed**, zero failures/skips. |
| Actual HTTPS Chromium, blue | **6 passed**, zero failures/skips. |
| Whitespace | `git diff --check` passes. |

Links: [control](deployment-cli/control.txt), [real lifecycle](deployment-cli/integration.txt), [check list](deployment-cli/results.json), [fresh migrations](deployment-cli/migrations.json), [cleanup](deployment-cli/cleanup.json), [backend unit](deployment-cli/backend-unit.txt), [frontend unit](deployment-cli/frontend-test.txt), [frontend build](deployment-cli/frontend-build.txt), [green browser](deployment-cli/browser-green.txt), [blue browser](deployment-cli/browser-blue.txt), [images](deployment-cli/images.txt), [Docker](deployment-cli/docker-version.txt), [Compose](deployment-cli/compose-version.txt), [OpenSSL](deployment-cli/openssl-version.txt), [Node](deployment-cli/node-version.txt).

## Verified deployment/security behavior

- Empty managed PostgreSQL migrates V1–V6 via normal Flyway; no new migration. One GLOBAL/ADMIN/ACTIVE is created by the existing protected deployment command. Normal HTTPS login verifies exact private input, not merely credential creation.
- Repeated recovery refuses. A second user is created/promoted through ordinary authorized APIs. Two administrators disable each other using the approved naturally valid short-lived Access behavior, reaching zero active GLOBAL admins without SQL credential edits or self-disable. The same deployment command then creates a recovered administrator; normal login passes and exactly two GLOBAL_ADMIN_BOOTSTRAPPED audits exist.
- Start/stop repeated calls are safe. Status shows running healthy versus stopped/degraded and the configured HTTPS URL. Changing port/bind works while keeping JWT issuer stable. Original keys remain byte-identical through stop/default uninstall/start.
- Branding changes from green to blue and a different logo while frontend image identity remains unchanged. Mounted palettes/logos and missing/malformed/unsupported/timeout/broken-logo fallbacks pass actual browser checks.
- Default uninstall preserves managed volume/config/keys; start recreates runtime with the recovered account. Incorrect destructive typed confirmation preserves the volume; exact `DELETE <project>` removes only the labeled managed volume. Data-deleted state refuses later start rather than silently recreating an installation.
- External PostgreSQL runs as a separately owned disposable verifier fixture. CLI creates no managed PostgreSQL service. External uninstall preserves its existing User row and server; start logs in again. Supplied versioned encryption-key files are retained. Missing encryption key prevents start. Bad external password and unreachable hostname fail safely.
- Existing unrelated containers are compared before/after and remain untouched. The verifier removes only its own runtime projects and separately created external fixture/server/volume; no global prune.
- TTY tests verify non-echoed password entry, echo restoration on interrupt, safe non-executable manifest parsing, duplicate/unknown fields, symlink rejection, invalid IPv4/IPv6/port, missing TLS before state creation, canceled edits, inherited secret-variable export removal and recovery-input cleanup. Fake Docker tests prove control flow only; real deployment evidence remains separate.
- Browser verifies memory-only access transport, Secure/HttpOnly/path/SameSite Refresh cookie, reload refresh/logout, wrong/missing CSRF and wrong Origin rejection, private business authentication and absence of permissive CORS. No accepted security rule is weakened.

## Failures encountered and fixes

1. macOS `mv` prompted when replacing an existing read-only branding file during a TTY install. Changed known-file replacement to noninteractive atomic rename; final install/config/start pass.
2. Sandbox restrictions blocked password `stty` ioctl and Mockito JVM-agent attachment (six unit errors). Narrowly approved execution outside the sandbox passed the unchanged security tests; no production bypass or altered test assertions.
3. A verifier rerun could not overwrite its own read-only external-password fixture. The verifier now makes only that dedicated fixture writable before replacing it; CLI secret behavior is unchanged.
4. An attempted post-cleanup external migration inspection found the fixture container already removed. No migration evidence was claimed from that command; the final verifier records both managed and external V1–V6 while the databases are still running.

Additional review ensured saved state rejects nested symlinks, interrupted configuration uses atomic files, initialized managed startup refuses a missing volume, ownership is checked before volume deletion, supplied versioned keys survive import, and inherited exported shell variables cannot export private input. Focused control coverage exercises these paths; no backend changes were needed.

## Limits and deviations

No material scope deviation. Compose env is generated transiently from the single validated manifest rather than preserved as a second independently editable configuration file; it is removed on exit. Recovery uses Compose's narrow `run -v` file mount rather than generating a YAML override. Install always invokes the existing build (cache reuse), while start explicitly does not build or pull replacement images. These simplify the approved orchestration design.

Verified on local macOS ARM64/Docker Desktop/OpenSSL 3. No production ingress/TLS issuance/rotation, remote Linux or x86 deployment, every bind-address family, port-reservation race, hostile concurrent host filesystem access, or load guarantee is claimed. Browser hostname input supports DNS/IPv4; literal IPv6 is supported for bind only. Database identifiers and repository paths have documented conservative validation; no arbitrary JDBC options. Public images/release distribution and upgrades are outside this version.

Health does not prove IServ/provider compatibility. No real IServ test occurred. Existing full Phase 0–4 and UI redesign evidence remains accepted and preserved; the entire historical browser/Failsafe matrix was not rerun for this shell/Compose change. Permanent data backup/restore is not a CLI feature.

All disposable verification containers, networks and database volumes are gone; private ignored fixture files remain under `.cache/deployment-cli/`. No commit, push or PR. No Phase 5, migration or backend Domain/API/authentication change. Existing frontend stability changes remain separate.
