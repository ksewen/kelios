# KELIOS

Phase 4 stabilization, Local identifier and bilingual inventory/administration on the accepted authentication/domain foundation: independent Spring Boot and React applications, PostgreSQL, runtime branding, immutable Asset history, GLOBAL local credentials and database-configured Tenant IServ OIDC. Explicit Asset workflows, Catalog/place management and target-owned administrative audit are available. Real IServ interoperability is pending manual verification.

Read `ARCHITECTURE.md`, `DOMAIN.md`, and `CODEX_IMPLEMENTATION_TASK.md` for the approved contracts. Technical selections are recorded in [ADR 0001](docs/adr/0001-phase-0-foundation.md) [ADR 0002](docs/adr/0002-phase-1-domain-persistence.md), and [ADR 0003](docs/adr/0003-phase-2-authentication-and-authorization.md). Actual verification is tracked in [Phase 0 evidence](docs/verification/PHASE_0.md) [Phase 1 evidence](docs/verification/PHASE_1.md), and [Phase 2 evidence](docs/verification/PHASE_2.md).

Documentation locations: phase plans use `docs/plans/PHASE_N_PLAN.md`, verification evidence uses `docs/verification/PHASE_N.md`, and ADRs use `docs/adr/`. The [Phase 0 plan](docs/plans/PHASE_0_PLAN.md) is preserved as approved. The revised [Phase 1 plan](docs/plans/PHASE_1_PLAN.md) was explicitly approved. The [Phase 2 plan](docs/plans/PHASE_2_PLAN.md) was separately approved for implementation. Phases 0–3 are accepted. Phase 4 implementation is explicitly approved; Phase 4 user acceptance is pending review. See [inventory operations](docs/INVENTORY.md), [ADR 0004](docs/adr/0004-phase-3-inventory-and-target-owned-audit.md) and [Phase 3 evidence](docs/verification/PHASE_3.md).

## Local builds

Requirements: Java 25 LTS, Node 24 LTS, Docker with Compose/BuildKit, and network access for the initial dependencies. Maven 3.9.16 is downloaded by the supplied Wrapper; do not use Gradle or Maven 4. Keep dependency caches inside this repository:

```sh
mkdir -p .cache/maven-home .cache/npm .cache/tmp
export TMPDIR="$PWD/.cache/tmp"
cd backend
MAVEN_USER_HOME="$PWD/../.cache/maven-home" ./mvnw -B -Dmaven.repo.local="$PWD/../.cache/maven-repository" verify
cd ../frontend
npm ci --cache ../.cache/npm
npm test
npm run build
```

Maven `verify` runs engine/technical/authentication unit tests and required domain/identity PostgreSQL integration tests using Testcontainers/Failsafe. Docker must be available; the integration suite is never silently skipped. No H2 substitute is used. The isolated Docker builder explicitly uses `-DskipITs verify` because it has no Docker daemon; it runs unit verification and does not replace the full host verification gate.

`pom.xml` enforces Java 25 and Maven 3.9.x. Wrapper distribution validation uses SHA-256. `mvnw`, `mvnw.cmd`, and `.mvn/wrapper/maven-wrapper.properties` belong in version control; no commit is created automatically.

## Compose deployment

Create a local secret file inside this repository (never commit it), then build and start:

```sh
umask 077
mkdir -p deploy/secrets
chmod 700 deploy/secrets
openssl rand -hex 32 > deploy/secrets/db-password
chmod 444 deploy/secrets/db-password
docker compose -f deploy/compose.yaml config --quiet
docker compose -f deploy/compose.yaml build
docker compose -f deploy/compose.yaml up -d --wait
curl -fsS http://127.0.0.1:8080/api/v1/status
```

The secret directory is private to its host owner. Its mounted file is readable by the non-root backend; local Compose file secrets preserve host permissions rather than implementing managed-secret ownership/mode settings. Do not place secrets in a publicly accessible directory. Production should use a managed secret provider.

Open `http://127.0.0.1:8080`. `KELIOS_HTTP_PORT` changes the loopback port. Backend and PostgreSQL are not published to the host. The frontend proxies `/api/` to backend port 8080. Internal backend management port 8081 exposes only health; readiness checks PostgreSQL and liveness does not. Frontend `/healthz` checks its HTTP server. Flyway preserves V1 and applies V2 domain foundation V3 narrow history/UNKNOWN protection, and V4 dedicated authentication structures, recording each in PostgreSQL schema history. Hibernate validates the resulting mappings rather than creating tables. A failed migration prevents backend startup.

```sh
KELIOS_BRAND=green docker compose -f deploy/compose.yaml up -d --no-build --force-recreate frontend
docker compose -f deploy/compose.yaml ps
docker compose -f deploy/compose.yaml logs backend
docker compose -f deploy/compose.yaml stop
```

`stop` preserves the PostgreSQL volume. Never use `down -v` on data you intend to keep. Public images contain no secret or district branding; PostgreSQL gets its password through a Compose secret and Spring Boot loads the same mounted file via `configtree`. Production uses managed secrets, HTTPS at the ingress, explicit image release tags/digest pins, backups, and restricted management networking. Local Compose credentials are a development facility.

## Configuration and security

Backend configuration: `KELIOS_DATABASE_URL`, `KELIOS_DATABASE_USER`, mounted `/run/secrets/db-password`, or `KELIOS_DATABASE_PASSWORD`. Spring Boot also supports mounted external configuration through `SPRING_CONFIG_ADDITIONAL_LOCATION`. Do not bake credentials into Docker build arguments or frontend files.

Frontend `KELIOS_CONFIG_FILE` chooses the mounted public JSON source. See [runtime branding](docs/RUNTIME_BRANDING.md) for schema, validation, defaults, logo paths, and no-rebuild changes. The checked-in [OpenAPI contract](docs/api/openapi.yaml) covers technical status, authentication, identity administration, explicit inventory commands, Catalog/place operations and scoped audit. Missing API paths remain backend 404s, rather than serving the React app.

Same-origin proxying needs no browser CORS configuration. Cross-origin deployment and subpaths need explicit review before support is claimed. Authentication uses memory-only Access Bearer and Secure HttpOnly host-only Refresh cookie, same-origin HTTPS, explicit signed CSRF/exact Origin and no persistent browser tokens. There is no token/session database, blacklist or revocation subsystem. [Authentication operations](docs/AUTHENTICATION.md) describes mounted keys, the HTTPS Compose overlay, deployment bootstrap/recovery and administration. The official IServ UUID group contract is implemented/tested; [real-provider manual verification](docs/verification/PHASE_2_ISERV_MANUAL.md) remains pending.

## Smoke tests

After full Maven verification and creating the local secret, run Phase 4 inventory/HTTPS/authentication, bilingual branding, restore and Phase 0–3 container/browser regressions from the repository root:

```sh
./scripts/smoke-phase4.sh
```

It reuses the Phase 0 container checks in a separate current-phase workflow, builds both images, starts Compose, checks migration/API/health and runtime image contents, then runs both browser suites against the same frontend image with two mounted brands. It also adds disposable local TLS/key/password fixtures and verifies actual local login/refresh/logout/CSRF/disable and deployment bootstrap refusal. New evidence goes to `docs/verification/phase4/`; accepted Phase 0–3 evidence is preserved. Buildx, Maven/npm and browser caches stay in `.cache/`. It leaves the stack running with the green sample and HTTPS on localhost:9443 in the isolated kelios-phase4 project; use `docker compose -p kelios-phase4 -f deploy/compose.yaml stop` when finished.

Browser assertions run separately against the active deployment:

```sh
cd frontend
PLAYWRIGHT_BROWSERS_PATH="$PWD/../.cache/playwright" npx playwright install chromium
PLAYWRIGHT_BROWSERS_PATH="$PWD/../.cache/playwright" KELIOS_EXPECTED_BRAND=green npm run test:smoke
```

The Phase 4 script recreates only the frontend for each browser sample and verifies identical image IDs. Older scripts write accepted Phase 0–3 evidence; use `smoke-phase4.sh` to preserve that history.

The browser checks rendered colors, logo loading, proxy connectivity, and malformed/missing/timeout branding fallback. Tests and container logs are evidence, not a substitute for the Phase 0 acceptance gate.

Phase 4 documentation: [ADR 0005](docs/adr/0005-phase-4-stabilization-and-localization.md), [localization](docs/LOCALIZATION.md), [disposable restoration](docs/RESTORE_VERIFICATION.md), [invariant coverage](docs/analysis/PHASE_4_INVARIANT_COVERAGE.md) and [verification](docs/verification/PHASE_4.md).
