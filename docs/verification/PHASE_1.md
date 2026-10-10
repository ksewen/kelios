# Phase 1 verification — 2026-10-10

Status: Phase 1 internal domain foundation implemented and verified in the local macOS ARM64 / Docker Desktop environment. The user explicitly approved the revised [plan](../plans/PHASE_1_PLAN.md) before implementation. [ADR 0002](../adr/0002-phase-1-domain-persistence.md) records the accepted technical decisions. Phase 2 was not started.

## Implementation and schema

- JPA mappings for Tenant, User, Category, Manufacturer, Product, Location, Owner, Asset, AssetEvent and AuditLog. Hibernate validates Flyway's schema; open-in-view and schema generation are disabled.
- V1 is unchanged. `V2__domain_foundation.sql` adds the ten entity tables, atomic daily counter, required checks/FKs/composite keys/indexes, and stable GLOBAL UNKNOWN seeds. `V3__narrow_history_and_unknown_protection.sql` adds only two trigger functions with four bindings: AssetEvent UPDATE/DELETE and TRUNCATE protection; reserved Category and Manufacturer row guards.
- Composite FKs protect Asset–Location/Owner same-Tenant relationships and AssetEvent ownership. The deferred Product/Category/Manufacturer FK allows explicit Merge updates but rejects inconsistent committed Asset facts. Visibility and business reference predicates are validated by the supported transactional services.
- Internal Tenant/User creation, Catalog creation/edit/deletion/publication, Location/Owner creation/edit/deletion, Asset registration/reference/text edits/soft deletion, and Category/Manufacturer Merge. No public business or authentication endpoints.
- Asset registration generates UUID/tag, derives Product dependencies server-side, requires explicit loanable, initializes REGISTERED/UNKNOWN with no loan, and normalizes optional blanks. Asset mutation/history and Merge/product redirects/Asset versions/history/source deletion/audit are atomic.
- Immutable event before/after IDs and display snapshots retain Product usage evidence without `ever_asset_referenced`. Actor UUID/display snapshots have no live User FK. Audit records retain server-derived administrative before/after context.
- Normal READ COMMITTED transactions, relevant parent FOR SHARE / guarded FOR UPDATE locks, post-lock fresh guard queries, Asset @Version, local Merge ordering and explicit conflict/rollback behavior. No universal SERIALIZABLE or automatic retry framework. Pool lock timeout is 10 seconds; integration tests locally override it where necessary.
- Generic Java-only state-machine graph engine and the two approved inventory graphs. No transition/loan workflow services, authentication, authorization workflows, business REST/UI, Product/Owner/Location Merge, automatic duplicates/Merge, Redis, import, labeling or procurement features.

## Commands actually executed

From `backend/`, with caches and temporary files inside KELIOS:

```sh
MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw -B -DskipITs verify > ../.cache/phase1-build.log 2>&1
MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw -B verify > ../.cache/phase1-verify.log 2>&1
MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw --version > ../docs/verification/phase1/toolchain.txt
```

The first command was an explicit compile/unit-only development check, not PostgreSQL evidence. The final full `verify` returned exit code 0; Failsafe ran all required PostgreSQL tests. [Full Maven log](phase1/maven-verify.txt) and [toolchain](phase1/toolchain.txt): Maven 3.9.16, Java 25.0.2; Spring Boot 4.1.1, Testcontainers 2.0.5 and PostgreSQL 17.11 were observed during verification.

From the repository root:

```sh
PATH=/opt/homebrew/opt/node@24/bin:$PATH TMPDIR="$PWD/.cache/tmp" npm --prefix frontend test > .cache/phase1-frontend-test.log 2>&1
PATH=/opt/homebrew/opt/node@24/bin:$PATH TMPDIR="$PWD/.cache/tmp" npm --prefix frontend run build > .cache/phase1-frontend-build.log 2>&1
sh -n scripts/smoke-phase1.sh
BUILDX_CONFIG="$PWD/.cache/buildx" ./scripts/smoke-phase1.sh > .cache/phase1-regression.log 2>&1
```

Both npm commands and the final smoke script returned exit code 0. The script executes the existing Phase 0 image/Compose checks, then independently runs each brand's Playwright suite and writes only new Phase 1 evidence. The script now sets its own repository-local Buildx cache as well. It inherits Node 24 on PATH (the Homebrew prefix above is local-environment-specific).

## Tests and results

| Suite | Executed cases | Result |
| --- | ---: | --- |
| StateMachineTest (all 16 Lifecycle pairs, all 16 Condition pairs, graph ownership and engine boundary) | 34 | Passed |
| Existing StatusControllerTest | 2 | Passed |
| DomainFoundationIT, JUnit + real PostgreSQL Testcontainers | 71 | Passed |
| Backend total | 107 | Zero failures/errors/skips |
| Existing frontend Vitest branding tests | 11 | Passed |
| TypeScript/Vite frontend production build | 1 build | Passed |
| Blue browser branding/proxy/fallback suite | 6 | Passed |
| Green browser branding/proxy/fallback suite | 6 | Passed |

PostgreSQL cases include fresh migrations, V1-to-V3 upgrade and validation; minimal identity/scope constraints; same-Tenant FKs; deferred Product equality; complete 27-case Product visibility matrix; publication dependencies; protected UNKNOWN; immutable event DML/TRUNCATE rejection; all retained lifecycle references, soft-deleted Assets/Products and explicit reference resolution; Product edit freeze after removal; Location metadata edits without AssetEvents; duplicate-name independence; absence of unsupported Merge operations; historical actor and administrative snapshots; stale and concurrent Asset versions; UTC date boundary, sequence width beyond 999, uniqueness, bigint overflow rollback and 24 concurrent registrations across two Tenants.

Merge tests cover both entity types, retained Product/Asset redirection, version increments, before/after IDs, stable Asset identity/tag, UNKNOWN as target, invalid visibility rollback, and test-only injected history/audit failures rolling back the entire command. Temporary failure-injection triggers are removed in `finally` and are not production migrations.

Independent transaction/connection tests force guard-versus-incoming-reference races, Product-assignment-versus-empty-Catalog guards, deletion winning before assignment, first Product usage, both Merge/assignment orders, stale Product dependency acquisition after Merge, already-loaded Asset edits versus Merge, simultaneous optimistic edits, explicit lock timeout and deadlock rollback. `pg_stat_activity` confirms actual lock waits rather than assuming scheduling order. An unrelated Location metadata edit succeeds while a Catalog row is locked; there is no universal parent lock protocol.

## Docker and Phase 0 regressions

[Container log](phase1/docker-smoke.txt), [browser log](phase1/branding-regression.txt), [backend startup](phase1/backend-startup.txt), [frontend unit log](phase1/frontend-test.txt), and [frontend build log](phase1/frontend-build.txt).

- Separate images build through Compose/BuildKit. The preceding final-source backend rebuild executed 36 unit cases during Maven verification. The last successful regression rerun reused cached build/verification layers.
- The isolated backend builder explicitly runs `./mvnw -B -DskipITs verify`; no Docker socket or nested/privileged Docker is introduced. This is unit/build verification only. Required database verification comes from the successful full host Maven run above.
- Backend image: `sha256:1f5e493ae9f8c43e9190188369bc3e0c7ca74d6c0f159ce6c9f49c5d9e2f8a6d`.
- Both blue/green frontend runs: `sha256:5142572498844abf9f78e880995b4138b7f342ec6d58d593f81f98dc9bb8fb76`. Mounted branding changes required no rebuild.
- Compose services healthy; backend readiness/liveness and frontend health pass. Non-root runtimes, absent backend Maven/compiler and absent frontend Node/npm verified. API proxy/status pass, and business/missing API/static paths remain 404.
- Initial deployment applied V2/V3 to the existing V1 database; subsequent startup validates all migrations and JPA mappings.
- Rendered evidence: [blue](phase1/blue.png), [green](phase1/green.png). Existing Phase 0 screenshots/logs/evidence were not overwritten.
- SHA-256 comparison confirmed preserved frontend sources, ADR 0001, V1 and all pre-existing verification artifacts: [preservation check](phase1/preservation-check.json).

## Failures encountered and resolutions

1. Initial dependency download failed under sandbox DNS restrictions. A narrowly approved Maven command downloaded dependencies into repository-local caches; the compile/unit check passed.
2. An append command used a repository-root path while its working directory was backend, leaving the test file temporarily incomplete. Corrected the path and completed the source before PostgreSQL verification.
3. Sandboxed Testcontainers could not access Docker. Required integration checks failed rather than skip. Narrow approval for the exact Maven verification command enabled the real container run.
4. The first real PostgreSQL run had two failed exception-type assertions (37 cases, two failures, zero errors/skips). JPA emitted OptimisticLockException, and SQLState translation left PostgreSQL 55P03 uncategorized. Added explicit Asset optimistic-conflict translation and Spring's PostgreSQL error-code translator; all expanded suites then passed. [Initial failure log](phase1/initial-postgres-failures.txt).
5. Docker Buildx initially tried to update its activity cache outside the repository. Moved BUILDX_CONFIG into `.cache/buildx`. Docker socket access then required scoped regression-command approval; no sudo or sandbox disablement was used.
6. Updating the smoke script's cache setting while it was running disrupted its final shell parsing/log collection after browser checks. Validated the final script with `sh -n`, reran it unchanged, and confirmed the final complete run exited 0.

## Limits and stop boundary

Verified locally on macOS ARM64, Java 25 / Node 24, Docker Desktop and PostgreSQL 17.11. No remote CI, x86 deployment, managed production PostgreSQL, real IServ provider, cross-origin/subpath deployment or production operational load was tested. Test containers use disposable database credentials; these are not an authentication implementation. Service-level Tenant/reference isolation is verified, but HTTP authentication/authorization is deliberately deferred.

Administrative SQL/DDL is outside supported business mutation boundaries; cross-row visibility/edit guards depend on the documented application-service locking handshake. The scoped history/UNKNOWN triggers and declarative constraints provide their separate database protections. No privileged purge/privacy policy was invented.

The local Compose stack remains running with the green sample, preserving its PostgreSQL volume. Phase 0 evidence is unchanged. Phase 2, commits, pushes and external repository writes were not performed.
