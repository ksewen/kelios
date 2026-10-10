# Phase 4 implementation and verification

Status: **Phase 4 implementation: COMPLETE / PASSED**. Phase 4 user acceptance is **PENDING REVIEW**. Real IServ interoperability remains **MANUAL VERIFICATION PENDING**; no real provider credentials or compatibility claim.

## Scope and implementation

Approved stabilization, one optional organization-assigned Asset localIdentifier, and complete static English/German frontend presentation. No Phase 5, commit, push or PR. See [ADR 0005](../adr/0005-phase-4-stabilization-and-localization.md), [invariant matrix](../analysis/PHASE_4_INVARIANT_COVERAGE.md), [inventory operations](../INVENTORY.md), [localization](../LOCALIZATION.md), [restore procedure](../RESTORE_VERIFICATION.md).

V6 adds only nullable asset.local_identifier text and declarative length/forbidden-character CHECKs. Existing values remain null; V1–V5 unchanged, no inference/backfill/table/index/uniqueness. Server Java strip/blank-null semantics, 128 Unicode code points, preserved case/internal whitespace and Cc/U+2028/U+2029 rejection apply to registration and four-field metadata replacement. Omission/null clear. Existing expectedVersion/@Version and atomic new authoritative REGISTERED/UPDATED/other snapshots include the field; old immutable JSON is unchanged. Existing escaped parameterized q includes the label; no dedicated filter/sort/index. No additional AuditLog action or transfer copying.

Frontend labels distinguish KELIOS Asset Tag, Local identifier/Lokale Kennung, Serial number and External inventory number. Registration/edit submit localIdentifier text without JavaScript normalization so server Java semantics remain authoritative. Static i18next **26.4.3** + react-i18next **17.0.16** are pinned; published peer ranges support current React 19.3.0/TypeScript 7.0.2. Host and independent Docker build verify compatibility. Exactly en/de, 320 matching keys, matching interpolation and required enum/event/audit/problem mappings. Browser supported preference order/regional bases, English fallback, public switch and validated language-only localStorage. Authentication tokens/credentials remain prohibited from local/sessionStorage. User/history data stays literal; stable machine values/UTC contracts unchanged. Locale formatting is display-only; errors use safe localized code mappings/fallback, not raw exception details. Runtime branding remains independent.

## Actual commands

From backend, repository-local caches and temporary files:

```sh
MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw -B -DskipTests -DskipITs compile
MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw -B verify
```

From frontend, Node 24 path:

```sh
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm view i18next version peerDependencies --json --cache ../.cache/npm --fetch-retries=0 --fetch-timeout=20000
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm view react-i18next version peerDependencies --json --cache ../.cache/npm --fetch-retries=0 --fetch-timeout=20000
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm install --save-exact i18next@26.4.3 react-i18next@17.0.16 --cache ../.cache/npm --fetch-retries=0 --fetch-timeout=20000
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm test
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm run build
```

From repository root:

```sh
BUILDX_CONFIG="$PWD/.cache/buildx" ./scripts/smoke-phase4.sh
sh -n scripts/smoke-phase4.sh scripts/smoke-phase4-foundation.sh scripts/smoke-phase4-restore.sh scripts/smoke-phase4-clean.sh
```

Actual redirected host logs remain in .cache/phase4-verify.log, phase4-frontend-test.log, phase4-frontend-build.log and phase4-smoke.log. Sanitized summaries are checked in under [phase4](phase4/). The smoke workflow actually executes independent Compose image builds, health/runtime checks, both mounted brands, `npm run test:smoke` (blue full suite), `npm run test:smoke -- runtime-branding.spec.ts phase4-localization.spec.ts` (green), pg_dump/createdb/pg_restore and clean isolated deployment. It respects the unchanged account throttle by waiting one normal minute between brand suites, with no retry or production rate-limit override.

Versions: Java 25.0.2, Maven Wrapper 3.9.16, Node 24.15.0, npm 11.12.1, PostgreSQL 17.11 (postgres:17-alpine), macOS 26.6.2 arm64. Tool/dependency/Docker versions are in supporting evidence.

## Results

| Gate | Actual result |
|---|---|
| Maven host verification | PASS: 313 tests, 74 unit + 239 real PostgreSQL integration; failures/errors/skips 0 |
| Integration suites | DomainFoundationIT 72; IdentityAuthenticationIT 47; Phase3InventoryIT 97; Phase4MetadataIT 23 |
| Frontend unit | PASS: 47 tests across 4 files; failures/skips 0 |
| Frontend production build | PASS: TypeScript/Vite; static en/de included |
| Browser blue | PASS: 23 Chromium cases |
| Browser green | PASS: 11 Chromium cases; 34 total browser executions, failures/skips 0 |
| Fresh/upgrade migration | PASS: fresh V1→V6 and populated V5→V6, all-data/checksum preservation |
| PostgreSQL concurrency | PASS: independent metadata/metadata and both metadata/Merge orders, observed SQL lock waits, rollback/version/event assertions |
| Docker/HTTPS/branding | PASS: independent non-root/toolchain-free images, health/proxy/security and en/de × blue/green on identical frontend image |
| Disposable restore | PASS: exact all-table digests, readiness, restored HTTPS login/reads, production OIDC secret decrypt/re-encrypt and independent missing-key test |
| Clean isolated deployment | PASS: separate empty volume, V1→V6, zero prior Assets/Users/events, readiness and HTTPS status; isolated services stopped, volume retained |
| Contract/documentation/preservation | PASS: OpenAPI parses, no new paths, four-field replacement, no auth browser storage, shell syntax, historical file hashes |

[Backend counts](phase4/backend-tests.txt), [Maven summary](phase4/maven-summary.txt), [PostgreSQL details](phase4/postgresql-evidence.txt), [frontend tests](phase4/frontend-test.txt), [frontend build](phase4/frontend-build.txt), [contract review](phase4/contract-consistency.txt) and [preservation hashes](phase4/preservation.txt) support these results.

## Images, browser and deployment

[kelios-backend:phase0] sha256:35b06ddc18fc0a925519f5b96175a6fb78fefcf24ca8baa121e6e3843b23e8a0 user=kelios architecture=arm64
[kelios-frontend:phase0] sha256:1884b553e9570cfa216359dffebafdc50b6ce7a84a6246093eb934ad7cc7f0d4 user=101 architecture=arm64

[Browser results](phase4/browser-results.txt) distinguish real backend workflows from explicitly UI-only fixtures. [Branding matrix](phase4/branding-matrix.txt) and screenshots blue-en/blue-de/green-en/green-de show all four combinations on sha256:1884b553e9570cfa216359dffebafdc50b6ce7a84a6246093eb934ad7cc7f0d4. [Smoke workflow](phase4/smoke-workflow.txt), [clean schema](phase4/clean-fresh-schema.txt), [clean health](phase4/clean-compose-health.txt), [restore authentication](phase4/restore-authentication.txt), and source/restored digest files record actual deployment/restore execution. The main isolated green stack remains at https://localhost:9443; fresh-check services were stopped without deleting data.

## Migration, concurrency and restore

The V5 structural fixture populates business, Tenant/User, credentials, encrypted OIDC configuration/groups, external identities, AssetEvents, AuditLog and tag counter. V6 preserves all old rows and V1–V5 checksums, adds null values and no index. Fresh databases migrate normally. No V5 cutover is repeated and no historical reconstruction occurs.

Independent PostgreSQL connections observe actual pg_stat_activity Lock waits. Competing same-version metadata commands have one winner/one stale rollback/no extra event. Merge-first makes the old-version metadata command stale; metadata-first lets Merge discover the committed value and preserve it in its own authoritative snapshot. No new locks, global protocol, SERIALIZABLE or automatic replay.

Real pg_dump -Fc/pg_restore tests compare all 17 table contents exactly. External-key decryption succeeds, missing AES key fails closed (503), restoring the same key succeeds. The deployment drill uses only isolated disposable data, retains its original source database, compares sorted all-table hashes/counts before any new restored writes, then checks restored readiness/HTTPS GLOBAL login/Asset reads and production OIDC secret decrypt/re-encrypt using external keys. Private dumps/keys/passwords stay under protected ignored .cache directories; evidence contains sanitized status/digests only. No backup service or retention/RPO/RTO/HA policy.

## Regression and preservation

Full Phase 0–3 Maven/HTTP/frontend/browser suites are rerun, including independent generic graphs, retained/deleted references, Product historical freeze, UNKNOWN, composite/deferred constraints, tags/UTC, scoped SQL, target-owned AuditLog writers/reads, real authorization/security, absolute JWT refresh, CSRF/Origin/cookies/logout and documented IServ OIDC fixture. Historic evidence and ADR 0001–0004 are not rewritten. Preservation hashes verify 66 accepted migration/ADR/plan/evidence files byte-for-byte. Source tests changed only where current migration version/column shape or intentionally localized UI labels required adaptation; no invariant weakened.

## Failures encountered and corrections

- Sandboxed npm metadata access failed DNS/network access; narrowly approved package requests/install succeeded. Sandbox remained configured; caches/writes stay repository-local.
- Initial sandboxed Maven run hit Mockito JVM self-attach restriction (6 unit errors); narrowly approved Maven/PostgreSQL execution passed. No tests skipped or H2 substitution.
- i18next 26 typing uses initAsync rather than the older initImmediate option; corrected initialization and production build passed.
- New PostgreSQL tests initially used JDBC-sensitive JSON `?` and accidentally excluded Flyway's null-version schema marker in checksum comparison; switched to jsonb_exists and included the marker. Seed calls were corrected to the existing UserService signature. Final full suite passed.
- A completeness test initially depended on root OpenAPI outside the independent frontend Docker context; replaced that dependency with explicit supported-code coverage and separate root contract review. Independent build passed.
- Playwright ESM JSON imports required type=json; corrected. Duplicate-label assertions were scoped by immutable unique Asset Tag. Response bodies are read immediately to avoid CDP capture races.
- Rapid two-brand suites hit the existing ten-login/minute policy. The harness waits its normal window; production throttling stays unchanged and no request is blindly retried.
- Review found JavaScript trimming could erase NBSP that Java preserves. localIdentifier is now sent raw to authoritative server validation; real PostgreSQL includes NBSP and Unicode case-insensitive search coverage.
- Mistargeted early local npm/version invocations failed due missing package/cache location; corrected working directories/repository-local caches. These failed invocations are not success evidence.

## Limits and acceptance boundary

Local macOS arm64 / Docker Desktop / PostgreSQL 17 / Chromium / same-origin self-signed HTTPS fixture envelope only. No amd64, Firefox/Safari, production ingress, cross-origin/subpath, managed backup/HA/performance-capacity certification. Some explicit UI-only pending/role/stale-response fixtures supplement, rather than replace, real backend/PostgreSQL authorization and deterministic OIDC tests. Bilingual administration screen coverage is representative; every translated sentence is not individually asserted. Completeness tests cover all supported static keys/codes/placeholders.

**Real IServ interoperability: MANUAL VERIFICATION PENDING.** The accepted [manual procedure](PHASE_2_ISERV_MANUAL.md) is untouched. No real-provider credentials requested. Documented protocol fixtures passed; they do not establish real-provider interoperability.

Phase 5 not started. No commit, push or PR. Implementation verification does not imply user acceptance.
