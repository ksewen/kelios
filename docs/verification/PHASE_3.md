# Phase 3 verification — inventory and target-owned administrative audit

Date: 2026-10-10. Phase 3 implementation and P1–P8 were explicitly authorized. User acceptance: **PENDING REVIEW**. Phase 3 implementation: **COMPLETE / PASSED**. All implementation verification gates below completed successfully.

Real IServ interoperability: **MANUAL VERIFICATION PENDING**. Documentation-supported production integration and deterministic fixture regression remain separate from real-provider evidence. No real IServ credentials were requested or real compatibility claimed. The [accepted manual procedure](PHASE_2_ISERV_MANUAL.md) is unchanged.

## Change inventory and implemented behavior

[Exact grouped file manifest](phase3/FILES.md): 49 source/spec/test/script files; supporting evidence lives only in phase3/. Backend changes reuse existing persistence, reference locks and state engine, add explicit workflow orchestration, scoped SQL reads, secured concrete HTTP adapters and typed safe problem classification. Frontend reuses React/TanStack/shadcn and one AuthSession; no dependency, router, repository/controller framework or authentication redesign was introduced.

Implemented registration (Product derivation or required direct references), scoped/all-Tenant browsing/search/filter/sort/offset paging, detail/history, text/Catalog/loanability commands, approved Lifecycle/Condition transitions, Location/Owner changes, embedded loan/Return, Catalog/place create/edit/delete, publication, Category/Manufacturer Merge with advisory preview, Tenant/user/OIDC/password administration UI and authorized administrative audit browsing. No Asset delete UI/API, Product/Owner/Location Merge or Phase 4 feature.

Mutable Asset commands require expectedVersion and atomically append history. Deregistration emits exactly one reason-bearing DEREGISTERED event and retains references/current loan. Outstanding loans do not alter either graph. Return checks only current loan and clears four loan fields with RETURNED. Same-version competitors roll back without lost updates/duplicate events or silent retry. A fixed-clock no-change metadata command still advances version through a distinguishable server timestamp.

Browser detail routes use `/inventory/assets/{id}` to preserve existing nginx `/assets/` static-file handling; API remains `/api/v1/assets/{id}`. Global Tenant URLs are operating context, not JWT/membership/session mutation. Drafts survive conflicts and manual reload; uncertain mutations are never automatically replayed. Session generation and effect cleanup prevent stale logout/Tenant reads restoring old data. History uses captured before/after labels, not live metadata, rendered as safe text.

OpenAPI now has **52 paths / 73 operations**, including 54 Phase 3 operations while preserving the 19 Phase 2/status operations. Closed command inputs reject server-owned/unknown fields. RFC 9457 errors have safe stable codes; stale versions, Product-reference races, lock conflicts, integrity failures and unavailable transaction/database infrastructure are classified by exception type, not message strings.

## V5 and audit attribution

Only `V5__audit_tenant_attribution.sql` was added. It deletes only disposable pre-deployment audit_log rows, adds nullable historical tenant_id UUID without live Tenant FK and creates `(tenant_id, occurred_at DESC, id DESC)`. V1–V4 are byte-preserved. No production backfill, inference, repair, AuditScope or third attribution state exists. KELIOS has not been deployed.

All writers must explicitly supply known target ownership; the old attribution-less AuditService overload was removed. Newly created target ownership is captured for creation; loaded pre-operation ownership for mutation; pre-publication Tenant for publication; source pre-operation ownership for Merge. Actor UUID/display remains independent. Merge emits exactly one administrative audit while affected Assets retain their UPDATED events.

Coverage assertion records **28 action strings / 30 supported writer variants**: [actual action counts](phase3/audit-actions.json). Tenant creation, OIDC configuration, both GLOBAL/JIT User creation paths, User approval/role/disable, administrator/own credential changes, bootstrap/recovery, each Catalog kind create/edit/delete/publish, both Merge types and each Location/Owner create/edit/delete are exercised. Tests assert target ownership before/after publication and all five legal Merge scope combinations; sanitized audit contains no fixture credential/client secret.

ACTIVE GLOBAL ADMIN reads all audit, optionally narrowed. ACTIVE TENANT ADMIN rows, count and detail use authenticated tenant_id equality in SQL before pagination. USER/PENDING are denied; foreign/global/nonexistent details share NOT_FOUND, and forged scope filters cannot widen Tenant access. No live actor/target ownership joins or per-request User-revocation check was added.

## Commands actually executed

Final host verification, from backend:

```sh
MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw -B verify > ../.cache/phase3-verify.log 2>&1
```

Earlier compile/unit checks also executed `./mvnw -B -DskipTests -DskipITs compile` and `./mvnw -B -DskipITs test`, with the same repository-local cache/TMPDIR variables and corresponding phase3-compile.log / phase3-unit.log destinations.

From frontend:

```sh
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm test > ../.cache/phase3-frontend-test.log 2>&1
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm run build > ../.cache/phase3-frontend-build.log 2>&1
```

From repository root:

```sh
BUILDX_CONFIG="$PWD/.cache/buildx" ./scripts/smoke-phase3.sh > .cache/phase3-smoke.log 2>&1
sh -n scripts/smoke-phase3.sh scripts/smoke-phase3-foundation.sh
```

The new workflow runs the separate Phase 3 foundation script: Compose config/build/up/health, non-root/toolchain absence checks, five migrations, status/proxy/unknown API/missing-static checks and both mounted brands. It then uses the accepted HTTPS auth overlay, reuses private disposable local fixtures, verifies recovery refusal, runs `npm run test:smoke` with blue and `npm run test:smoke -- runtime-branding.spec.ts` with green, compares the frontend image ID without rebuilding between brands, and records schema/image/health logs. Docker builders run Maven verification with `-DskipITs` inside the isolated builder; host full verification supplies the mandatory real PostgreSQL gate.

Tool inspection also actually ran `./mvnw --version` from backend with MAVEN_USER_HOME, `/opt/homebrew/opt/node@24/bin/node --version`, Node-24 PATH `npm --version`, `uname -m`, `docker version`, and `docker image inspect kelios-backend:phase0 kelios-frontend:phase0 --format '{{.RepoTags}} {{.Id}} user={{.Config.User}} architecture={{.Architecture}}'`. Python parsed OpenAPI/schema references/unique operation IDs, checked document links and forbidden endpoints, parsed JUnit XML, counted action coverage, scanned evidence for local fixture credential leakage and compared preservation SHA-256 values.

## Actual test results

| Suite | Passed | Failures / errors / skipped |
|---|---:|---|
| StateMachineTest | 34 | 0 / 0 / 0 |
| AuthPolicyTest | 16 | 0 / 0 / 0 |
| JwtAndBrowserTest | 16 | 0 / 0 / 0 |
| StatusControllerTest | 2 | 0 / 0 / 0 |
| BusinessProblemsTest (HTTP serialization/classification, injected failures only) | 6 | 0 / 0 / 0 |
| DomainFoundationIT, PostgreSQL | 72 | 0 / 0 / 0 |
| IdentityAuthenticationIT, PostgreSQL + HTTPS OIDC fixture | 47 | 0 / 0 / 0 |
| Phase3InventoryIT, PostgreSQL + secured HTTP adapters | 97 | 0 / 0 / 0 |
| **Backend total** | **290 (74 unit + 216 integration)** | **0 / 0 / 0** |
| Frontend Vitest, 3 files | **33** | **0 failed / 0 skipped** |

[JUnit-derived counts](phase3/test-counts.json), [Maven verify](phase3/maven-verify.txt), [frontend tests](phase3/frontend-test.txt), [frontend build](phase3/frontend-build.txt). Frontend TypeScript/Vite production build passed. PostgreSQL 17.11 Testcontainers actually ran; no H2 substitution or skipped PostgreSQL suites.

Final Docker/HTTPS workflow exited successfully: **24 browser tests passed (18 blue + 6 green), 0 failures / 0 skipped**. Both brands used frontend image `sha256:b26dd7596cf04f5714d50aff789dfd25dadadec38400dc86c5379eaf2fd517da` without rebuilding. [Final HTTPS/browser log](phase3/https-browser-smoke.txt), [foundation Docker checks](phase3/docker-smoke.txt), [image identities](phase3/images.txt), [Compose health](phase3/compose-health.txt). The final suite includes 5 existing authentication tests, 7 inventory/administration tests and 6 branding tests with blue, then the same 6 branding cases with green. Two inventory cases and one existing pending-authentication case are explicitly UI-only protocol-response fixtures; they do not establish database security or real IServ compatibility. Core inventory, management, identity/OIDC configuration and stale-version workflows use the actual HTTPS backend/PostgreSQL. Backend role/query/protocol tests provide independent authoritative security evidence.

## PostgreSQL migration and concurrency evidence

`freshAndPopulatedV4MigrationPreservesEverythingExceptDisposableAudit` migrates a separate populated V4 schema to V5 and compares exact row JSON across Tenant/User, Category/Manufacturer/Product, Location/Owner, Asset/AssetEvent, tag counter, local credentials, OIDC config/groups, external identity and recovery guard. It verifies V1–V4 checksums unchanged, only audit rows cleared, nullable UUID added, no audit FK, successful new attributed writes and a fresh V1→V5 schema. [Live deployed audit schema](phase3/audit-schema.txt) and [migration history](phase3/migrations.txt) record structural results.

`independentConnectionsCompeteWithoutLostUpdate` uses independent transactions and barriers for competing loans, Returns, lifecycle/condition mutation and Merge-versus-Asset mutation. It observes actual pg_stat_activity lock waits, asserts an optimistic loser, one committed version/event advance and unchanged losing state. `lockTimeoutHttpIsSafeConflictAndRollsBack` produces a real 100ms parent lock timeout, safe 409 CONCURRENT_OPERATION and complete rollback. `eventFailureRollsBackWorkflow` injects a test-only event failure and proves no partial lifecycle/version/history. Existing Phase 1 reference/first-use/assignment/Merge/rollback/deadlock/tag-concurrency tests remain passing. No deadlock-freedom or silent retry claim.

All 64 application/HTTP Lifecycle/Condition from/to cases with/without an outstanding loan run, along with all structured reasons, explicit loan guards, terminal-state Return, metadata/reference history, deleted/current-reference rules, role matrices for every mutation family, retained guards, historical Product freeze, UNKNOWN, literal search, scoped audit detail/count/filtering and every legal Merge scope combination. Full Phase 2 deterministic Discovery/UserInfo/UUID groups/state/nonce/PKCE/fail-closed/JIT/status/refresh regression remains passing.

## Failures encountered and resolved

- Initial root-directory Maven/cache and frontend log commands used relative paths unsuitable for that cwd; workspace restrictions prevented those writes. Commands were corrected to backend/frontend cwd and repository-local cache paths. No files outside KELIOS were intentionally changed.
- Initial sandboxed full integration run could not access Docker/HTTPS fixture tooling: three integration suite initialization errors, not skipped success. Exact Maven and new smoke commands received narrowly scoped approval; subsequent real PostgreSQL runs passed. No sudo, sandbox disabling or unrestricted filesystem approval was requested.
- One audit test found forbidden Tenant-scope filters returned 400; the secured boundary now denies GLOBAL-only filters with 403 before querying. SQL Tenant predicates were already present and remain enforced.
- Initial TypeScript compilation used a v8-style getVisibleCells API; existing pinned TanStack v9 getAllCells is now used, without changing dependencies.
- First browser run found inaccessible exact select labels and a stale Merge-source picker. Explicit accessible names plus cleared/refetched Merge choices fixed them.
- Later browser checks found duplicate allowed resource names made a name-only test locator ambiguous, and detail reload collided with nginx static /assets/. Tests/UI use stable IDs; detail browser namespace now avoids static routing. Docker/nginx behavior was preserved.
- Review found picker pagination could submit its enclosing form and equal-clock metadata could omit a version increment. Pager buttons now explicitly use type=button; equal server timestamps are distinguished at PostgreSQL precision, with a real fixed-clock regression test.

Final reports, not intermediate failed runs, determine gate status. No test expectation was weakened to bypass Domain/reference/group validation.

## Preservation and limitations

[Preservation hashes](phase3/preservation.json) compare 53 pre-implementation files: 51 byte-identical, with only Architecture and Phase 3 plan authorization-status updates among those files. Domain, original task, ADR 0001–0003, accepted plans 0–2, all accepted Phase 0–2 evidence/screenshots/manual IServ procedure, analysis and V1–V4 remain unchanged. Existing migration/identity/business tests run alongside Phase 3; only their obsolete unknown-Asset-route/migration-count/controller-allowlist assumptions were updated for authorized endpoints/V5. Internal Asset deletion remains tested and unexposed.

Verified locally on macOS arm64, Java 25.0.2 / Maven 3.9.16, Node 24.15.0 / npm 11.12.1, Boot 4.1.1, PostgreSQL 17.11, Docker Desktop 4.94.0 / Engine 29.8.2. Runtime images are Linux arm64 and independently built, non-root and toolchain-free. No other CPU architecture, production ingress, cross-origin/cross-site auth, real database failover/termination recovery or real IServ installation was tested. HTTP unavailable-error serialization uses injected errors; actual database outage recovery is not claimed. Offset pagination is not snapshot-consistent under writes.

No Phase 4, commit, push or PR occurred. Real IServ remains manual verification pending. Phase 3 user acceptance remains pending review.
