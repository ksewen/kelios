# Step 7 — Tenant backend resource completion

**Status:** backend implementation and verification complete, pending human review. I1 GLOBAL Email identity is HUMAN ACCEPTED / CLOSED. Scope is applicable M2/R1f Tenant backend only; no Step 8, frontend or later resource implementation.

## Resource/API contract

| Endpoint | Result and authority |
|---|---|
| GET `/api/v1/tenants` | Intentionally unpaged array of ID/name/metadata. ACTIVE GLOBAL ADMIN/USER see the complete authorized set; ACTIVE TENANT ADMIN/USER see only their signed Tenant. Approved default createdAt DESC, no mandated UUID tie. No search/filter/sort/page/deleted query contract; unexpected parameters return 400. |
| GET `/api/v1/tenants/{id}` | Independently authorized persistent UUID detail, same safe field family in a distinct DTO. GLOBAL active readers may read any Tenant; TENANT SQL requires both requested ID and authenticated Tenant ID. Foreign and absent both 404 with the same detail. No collection search/cache requirement or unscoped existence probe. |
| POST `/api/v1/tenants` | Existing GLOBAL ADMIN-only creation; strict name-only input, required nonblank name stripped at the existing writer, generated UUID response. Duplicate names remain allowed. Server-owned M1 creation/update attribution and TENANT_CREATED AuditLog atomically persist. |
| PUT/PATCH/DELETE `/api/v1/tenants/{id}` | Not implemented; authorized callers get 405. Rename/general update/deletion require separate Domain decisions. No ordinary soft-delete browsing. |

PENDING/DISABLED remain denied by authoritative service guards; unauthenticated detail returns 401 and malformed UUID 400. Shared security routing admits the new authenticated detail path without changing provider routing or approval/role policy. Existing signed Access snapshot semantics remain intact; no per-request identity-reloading/revocation model is invented.

The detail/read DTO includes ID/name and createdBy/createdAt/updatedBy/updatedAt plus the existing nullable recognizable system-actor display names. No credential, Email, OIDC secret/issuer/client/claims/group list or identity authority data is exposed. Absolute timestamps remain Instant/UTC; human actors are not reconstructed through live User joins. Reads produce no permanent AuditLog/AssetEvent.

## Boundaries and transactions

TenantOperations owns CurrentIdentity, active/read/global-admin authority and effective scope. Caller-supplied ownership cannot determine the Tenant principal's scope. TenantReads binds UUIDs in a scoped SQL predicate before projection, with explicit selected columns/private Row. TenantModel uses plain application records with no HTTP/JPA dependencies. TenantDtos explicitly maps transport item/detail/metadata and strict creation input; it never exposes the persistence entity or blindly persists request fields.

TenantService retains its existing JPA creation writer, internal lock and require helpers. The old entity-loading list/View path had only the HTTP controller as caller and was removed in favor of the explicit projection. Creation remains in the application operation's transaction; the existing writer records the authoritative actor and one actual creation/update instant, then transaction-local TENANT_CREATED audit. Audit failure rolls back Tenant and metadata. No new writer, update semantics, reference lifecycle, lock/concurrency framework or generic administration abstraction was added.

UUID-to-createdAt list ordering and strict rejection of unsupported query/overposting fields implement the approved target, not artificial pagination. No deviation from the approved backend contract; frontend detail integration remains separately pending.

## Verification

Full command from backend, with repository-local Maven cache/temp directory:

```sh
MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw -B verify
```

| Check | Final result |
|---|---|
| Maven3/Java25 compile, package, verify | BUILD SUCCESS; 1m22s |
| Surefire | 74 passed; 0 failures/errors/skips; 5 suites |
| PostgreSQL/integration Failsafe | 581 passed; 0 failures/errors/skips; 11 suites |
| Combined full run | 655 passed; 0 failures/errors/skips |
| TenantResourceIT | 35 tests, all passed |
| OpenAPI YAML/internal references | 218 references resolved; Tenant array/detail/strict input contracts checked |
| git diff --check |PASS |

TenantResourceIT covers service scope/status matrix across both roles/scopes, independent safe detail, foreign/absent non-disclosure, forged transport narrowing, unauthenticated/malformed IDs, unpaged >100 results, createdAt order, unsupported query fields, metadata/ownership/provider overposting, duplicate names, name validation, server attribution/UUID response/captured audit Tenant, audit rollback, and absent edit/delete handlers.

After the full run, the ordering fixture was made independent of equal timestamps by advancing its test Clock before creating the newest Tenant. The final exact test source was verified separately:

```sh
MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw -B test-compile failsafe:integration-test failsafe:verify -Dit.test=TenantResourceIT
```

Result: 35 passed; 0 failures/errors/skips, BUILD SUCCESS (10.872s). These are repeat verification, not additional distinct tests; no unit tests execute in this focused command. Logs remain ignored at `.cache/step7/verify.log` and `.cache/step7/tenant-final.log`; PostgreSQL17 Testcontainers fixtures are isolated from the manual environment. No test was excluded/weakened and no real-data claim is made.

## Exact Step 7 delta

Relative to the captured PRE-Step-7 worktree, not HEAD:

- `backend/src/main/java/com/github/ksewen/kelios/platform/security/IdentityAdminController.java`
- `backend/src/main/java/com/github/ksewen/kelios/platform/security/SecurityConfiguration.java`
- `backend/src/main/java/com/github/ksewen/kelios/platform/security/TenantDtos.java`
- `backend/src/main/java/com/github/ksewen/kelios/tenancy/TenantModel.java`
- `backend/src/main/java/com/github/ksewen/kelios/tenancy/TenantOperations.java`
- `backend/src/main/java/com/github/ksewen/kelios/tenancy/TenantReads.java`
- `backend/src/main/java/com/github/ksewen/kelios/tenancy/TenantService.java`
- `backend/src/test/java/com/github/ksewen/kelios/identity/auth/TenantResourceIT.java`
- `docs/api/openapi.yaml`
- `docs/plans/COLLECTION_QUERY_IMPLEMENTATION_PLAN.md`
- `docs/verification/STEP_7_TENANT.md`

## Preservation, limitations and stop

PRE-Step-7 hash checks confirm all frontend, identity/Email implementation, migration sources, provider services and other resource files remain unchanged. Reversing only Tenant edits in the shared IdentityAdminController reconstructs its exact baseline hash, proving User/Email/provider handlers are byte-identical. SecurityConfiguration changes only admit/classify the Tenant item route. Existing dirty/untracked approved work is preserved.

No schema/index/migration change, compatibility work, development-data reset, deployment, manual runtime mutation, persistent business test data, staging/commit/push/PR or unrelated cleanup occurred. API ordering/strict input/detail integration may require later frontend work; no such work was performed. Existing M1/I1 operational cutover prerequisites remain unchanged. Tenant edit/delete are undefined; provider configuration/routing completion belongs to separately approved Step 8. No real IServ interoperability or manual runtime Tenant verification is claimed.

STOP for human review before IServ/OIDC Step 8, Audit and all later work.
