# M1 — business metadata, authoritative attribution and Place notes

Status: implemented and verified on 2026-10-11; **awaiting human review**. Step 2 is not authorized by this result.

## Backend order and scope

1. M1 shared metadata/attribution/schema foundation and Location/Owner note.
2. Category + Manufacturer resource/query/detail/command completion.
3. Product completion.
4. Location + Owner resource completion.
5. Asset approved query/detail/explicit commands (no deletion).
6. User/identity administration; I1 only with explicit approval and resolved decisions.
7. Tenant approved create/list/detail (no invented edit/delete).
8. Tenant IServ/OIDC singleton (no invented deletion/reset).
9. Audit query/read completion after administrative writers stabilize.

Only item 1 was implemented. The active [implementation plan](../plans/COLLECTION_QUERY_IMPLEMENTATION_PLAN.md) retains the separate approval gates. No query sorting/default-size/deleted-filter rollout, resource completion, M2, Email or frontend migration occurred.

## Schema and clean-state requirement

[V7](../../backend/src/main/resources/db/migration/V7__business_metadata_and_place_note.sql) adds non-null UUID created_by/updated_by and timestamptz created_at/updated_at to tenant, app_user, category, manufacturer, product, location, owner and tenant_oidc_config. Location/Owner gain nullable text note. Asset already has all four metadata fields and needs no schema change. There are no fallback/default creators, historical backfills, transitional nullable metadata, new identity tables or speculative indexes. V1–V6 source hashes remain unchanged.

V7 first rejects populated pre-M1 non-seed business tables with an actionable clean-state error. Such development databases **require a separately human-authorized reset/clean cutover before deployment**. This task neither applies V7 to nor clears/resets the manual environment.

In a clean state V7 recreates only the two protected UNKNOWN infrastructure Catalog seeds; their timestamp is the actual new INSERT statement timestamp and initial created/updated metadata agrees. It temporarily removes/reinstates their existing protection triggers inside Flyway's transactional migration. It never attributes historical non-seed business rows to the system actor. Failure is transactional: the integration test verifies legacy row values, schema and migration checksums remain unchanged after rejected migration. Existing historical V4/V5/V6 upgrade tests retain their explicit historical targets rather than attempting an unsupported populated V7 upgrade.

## Actors and time

`SystemActor.ID = 00000000-0000-0000-0000-000000000003`, stable display name `KELIOS system/seed`. It is an Actor attribution UUID/displayName only, with no app_user, credential, membership, Tenant or authorization. A database app_user check rejects collision; credential ownership still requires a real User. Safe BusinessMetadata and JDBC collection projections recognize its name without a live User join. Human metadata remains UUID-authoritative; this work does not reconstruct historical human display names from mutable Users.

Authoritative create writers sample the injected UTC Clock once and use the same actor/time for initial creation and update fields. Updates preserve creation fields and set update actor/time inside their existing operation transaction. Catalog deletion and Merge source deletion share their update/deletion timestamp. Seed infrastructure uses PostgreSQL's actual statement timestamp, not the application test Clock or a guessed historical timestamp.

Administrator bootstrap and OIDC JIT attribute their User creation to the newly created real User, consistent with their existing audit actor. Administrative GLOBAL User creation uses the acting administrator. Trusted internal UserService infrastructure creation uses the explicit nonhuman Actor; it is not an HTTP provisioning route. No real-actor authentication path is replaced with system attribution.

## Writer inventory

| Resource | Creation | Updates and consistency |
|---|---|---|
| Tenant | TenantService explicit metadata before persist/flush | No new Tenant update/delete lifecycle; existing creation + AuditLog transaction |
| User | IdentityStore accepts explicit Actor; real bootstrap/admin/JIT actors; internal infrastructure UserService uses reserved Actor | Approve, role, disable, credential reset and own-password change touch User update metadata in the same transaction as operation + required AuditLog; IdentityStore insert/touch require an existing transaction |
| Category/Manufacturer | Catalog createLabel explicit actor/time | Edit, publish, soft-delete, Merge source update; existing guards and AuditLog preserved |
| Product | Catalog createProduct explicit actor/time | Edit, publish, soft-delete, and Merge reference redirection update metadata; no Product Merge or new operation |
| Location/Owner | Place create explicit name/note/metadata | Edit and soft-delete update metadata with existing locks, scope/ref guards and AuditLog |
| Asset | Existing authoritative register metadata retained | Existing explicit commands and Merge Asset updates retained; existing version and AssetEvent atomicity verified |
| Tenant OIDC configuration | Initial singleton configuration actor/time | ON CONFLICT updates only update metadata; creation remains intact; provider version, allowed-group set and AuditLog remain one transaction |

Immutable AuditLog/AssetEvent keep their immutable occurrence/actor snapshot attribution; they do not gain mutable update semantics. Credentials, external-identity mappings, provider-group child rows, counters and recovery/guard rows are technical state, not independently mutable business resources: User/provider metadata covers the relevant supported operation. No generic CRUD/repository/mutation framework or reflective copying was introduced.

## Notes and API projection

Location/Owner create and replacement edit accept name and nullable note only. Optional text uses the existing strip/blank-to-null convention. Omission/null/blank clears note on replacement edit. Notes appear in Place list/detail reads and safe operation/history snapshots. Notes do not participate in q, filters or sorts.

Referenced Location remains name/note editable with stable ID; retained reference deletion guard remains. Referenced Owner rejects **all** edits, including unchanged name + note-only edits. Unreferenced Owner editing succeeds. Existing Tenant/role/visibility boundaries are unchanged.

Directly affected OpenAPI request schemas use PlaceCommand (no server metadata inputs). Safe response additions: BusinessMetadata on Tenant, Catalog detail, Place detail and OIDC configuration; explicit metadata/note fields on Catalog/Place collection rows; optional recognized system attribution labels on JDBC Asset collection/detail projections. No persistence entity is exposed. User session/identity response remains unchanged; broader account/detail DTO completion belongs to its separate gate. Provider secrets remain absent from safe configuration responses.

Strict inventory command allowlists reject metadata overposting. Typed administrative transport DTOs either reject unknown metadata fields or ignore them; they cannot make those fields writable. Focused tests verify authoritative metadata in any successful administrative create/configure response. No frontend integration or compatibility work was performed; existing frontend callers have not been changed to edit notes.

## Verification

Final command (backend working directory):

```sh
MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw -B verify > ../.cache/m1/verify.log 2>&1
```

Maven 3 / Java 25; PostgreSQL 17.11 in isolated Testcontainers. Final result **BUILD SUCCESS**, 49.480 seconds.

| Suite | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| StateMachineTest (Unit) | 34 | 0 | 0 | 0 |
| AuthPolicyTest (Unit) | 16 | 0 | 0 | 0 |
| JwtAndBrowserTest (Unit) | 16 | 0 | 0 | 0 |
| StatusControllerTest (Unit) | 2 | 0 | 0 | 0 |
| BusinessProblemsTest (Unit) | 6 | 0 | 0 | 0 |
| IdentityAuthenticationIT (PostgreSQL integration) | 49 | 0 | 0 | 0 |
| M1BusinessMetadataIT (PostgreSQL integration) | 26 | 0 | 0 | 0 |
| Phase3InventoryIT (PostgreSQL integration) | 97 | 0 | 0 | 0 |
| Phase4MetadataIT (PostgreSQL integration) | 23 | 0 | 0 | 0 |
| DomainFoundationIT (PostgreSQL integration) | 72 | 0 | 0 | 0 |

Totals: **74 unit + 267 PostgreSQL integration = 341 tests passed; 0 failures/errors/skips**. New M1BusinessMetadataIT: **26** executed cases.

Coverage includes every business creation actor/time, system seed name/UUID collision protection, bootstrap/admin/JIT real actors, Catalog edit/publication/deletion, User approval/role/disable/password/reset, provider creation preservation, note create/edit/read/blank/omitted clearing, retained Place guards, foreign-Tenant rejection, q/filter/sort exclusion, create metadata overposting, Merge Product/Asset/source update metadata, audit-failure create/update rollback, AssetEvent failure rollback, Merge audit failure rollback, and transactional migration rejection/fresh seed timestamps. Existing PostgreSQL authorization/concurrency/history/identity tests all ran.

OpenAPI YAML parsing, local reference resolution and Place request allowlists passed. Final git diff --check passed. No frontend tests ran.

Earlier attempts: sandboxed JVM instrumentation failed before integration execution; rerun outside the sandbox succeeded. The first focused M1 attempt had a test Clock bean-name collision (21 context errors); corrected the test bean name, then the full suite passed. An initial new-test compilation called a nonexistent Asset helper; corrected it to the existing editText command. These were diagnosed without weakening assertions. Intermediate successful full run: 74 unit + 266 integration before one additional administrative overposting test.

## Preservation, operational limits and remaining review

No manual-runtime claim is made: the running environment was not redeployed, read/written or reset, and no persistent business data was created. Integration fixtures/temporary trigger failures/schema migration tests exist only in isolated Testcontainers databases.

Pre-M1 SHA-256 baseline: .cache/m1/baseline.json. All existing frontend, deployment, architecture/domain and other unrelated files remain byte-identical. All old migration sources remain byte-identical. No staging, commit, push, PR, reset/clean/stash, Step 2 or later implementation occurred. The only active plan delta records approved attribution, the implementation order/status and clean-state requirement.

Remaining operational gate: authorize a clean development-database reset/cutover before deploying V7 to a populated pre-M1 database; no backfill is available or intended. Frontend note integration and broader resource/identity DTO completion remain separately approved later work. M1 awaits human review; no further module starts automatically.

## Files changed specifically for this M1 continuation

- `backend/src/main/java/com/github/ksewen/kelios/audit/BusinessMetadata.java`
- `backend/src/main/java/com/github/ksewen/kelios/audit/SystemActor.java`
- `backend/src/main/resources/db/migration/V7__business_metadata_and_place_note.sql`
- `backend/src/test/java/com/github/ksewen/kelios/identity/auth/M1BusinessMetadataIT.java`
- `docs/plans/COLLECTION_QUERY_IMPLEMENTATION_PLAN.md`
- `backend/src/main/java/com/github/ksewen/kelios/catalog/CatalogService.java`
- `backend/src/main/java/com/github/ksewen/kelios/catalog/Category.java`
- `backend/src/main/java/com/github/ksewen/kelios/catalog/Manufacturer.java`
- `backend/src/main/java/com/github/ksewen/kelios/catalog/Product.java`
- `backend/src/main/java/com/github/ksewen/kelios/identity/User.java`
- `backend/src/main/java/com/github/ksewen/kelios/identity/UserService.java`
- `backend/src/main/java/com/github/ksewen/kelios/identity/auth/AuthenticationService.java`
- `backend/src/main/java/com/github/ksewen/kelios/identity/auth/IdentityStore.java`
- `backend/src/main/java/com/github/ksewen/kelios/identity/auth/OidcConfigurationService.java`
- `backend/src/main/java/com/github/ksewen/kelios/identity/auth/OidcProvisioning.java`
- `backend/src/main/java/com/github/ksewen/kelios/inventory/CatalogMergeService.java`
- `backend/src/main/java/com/github/ksewen/kelios/inventory/Location.java`
- `backend/src/main/java/com/github/ksewen/kelios/inventory/Owner.java`
- `backend/src/main/java/com/github/ksewen/kelios/inventory/PlaceService.java`
- `backend/src/main/java/com/github/ksewen/kelios/platform/JdbcRows.java`
- `backend/src/main/java/com/github/ksewen/kelios/platform/security/InventoryOperations.java`
- `backend/src/main/java/com/github/ksewen/kelios/platform/security/PlaceController.java`
- `backend/src/main/java/com/github/ksewen/kelios/platform/security/SecuredDomainOperations.java`
- `backend/src/main/java/com/github/ksewen/kelios/tenancy/Tenant.java`
- `backend/src/main/java/com/github/ksewen/kelios/tenancy/TenantService.java`
- `backend/src/test/java/com/github/ksewen/kelios/identity/auth/IdentityAuthenticationIT.java`
- `backend/src/test/java/com/github/ksewen/kelios/identity/auth/Phase3InventoryIT.java`
- `backend/src/test/java/com/github/ksewen/kelios/identity/auth/Phase4MetadataIT.java`
- `backend/src/test/java/com/github/ksewen/kelios/inventory/DomainFoundationIT.java`
- `docs/api/openapi.yaml`
- `docs/verification/M1_BUSINESS_METADATA.md` (this report)
