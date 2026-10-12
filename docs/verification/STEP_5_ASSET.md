# Step 5 — Asset backend verification

**Implementation complete; awaiting human acceptance.** M1 and Steps 2–4 are HUMAN ACCEPTED / CLOSED. This separately authorized backend-only Asset refactor completes its applicable Q1/M2/R1a work. User/identity Step 6 and later modules remain unstarted. Frontend integration is outside scope.

## Resource surface and preserved foundation

| Capability | Result |
|---|---|
| Registration | Established concrete-Tenant POST, product-derived or explicit Category/Manufacturer references, required Location, optional Owner and explicit loanability. Strict HTTP DTO maps to the existing application Registration; authoritative actor/time, tag allocation, initial graphs and one REGISTERED event remain unchanged. |
| Collection | Established Tenant endpoint and GLOBAL-only aggregate endpoint; typed criteria, controller defaults page 0/size 20/max 100, scoped count, complete SQL filtering/ordering before LIMIT/OFFSET. Beyond-end returns empty items/current metadata. |
| Query additions | loanable boolean plus dedicated localIdentifier, serialNumber and externalInventoryNumber literal substring predicates, combined with existing q, enums, reference UUIDs and loaned. |
| Search | q still covers assetTag/localIdentifier/serialNumber/externalInventoryNumber only. Trim outer whitespace; blank means no predicate; 128 UTF-16 units after trimming; %, underscore and backslash safely escaped. Dedicated identifier predicates use the same rules and combine with AND; q combines its fields with OR. |
| Sorting | Existing external `tag`, `createdAt`, `lifecycle`, `condition` fields and asc/desc; default createdAt DESC. `tag` remains the established transport spelling for Domain assetTag. Existing internal SQL ID tie retained, not elevated to a mandatory Domain rule or exposed sort. |
| UUID detail | Independent scoped read, safe Tenant/Category/Manufacturer/Product/Location/Owner ID+name projections, nullable Product/Owner, metadata and richer ordinary fields. Deleted Assets remain undisclosed; DEREGISTERED remains visible. |
| Commands | Existing versioned metadata replacement, Catalog reassignment, loanability, lifecycle, condition, Location, Owner, loan and return endpoints. Strict endpoint-specific DTOs; no arbitrary PATCH or user-facing DELETE/undelete/hard delete. |
| History | Independent owning-Asset authorization, page 0/size 20/max 100, fixed occurredAt DESC (existing SQL ID tie retained), immutable recorded actor/snapshot data. No q, date, actor, sort or general history subsystem. |

`AssetService`, `Asset`, `AssetHistory`, `AssetTagAllocator`, `InventoryGraphs`, accepted Catalog/Place writers and every migration remain byte-identical to PRE-Step-5. Existing version checks, graph/loan independence, structured deregistration reasons, return-after-deregistration, Catalog visibility/parent locks, historical Product-use freeze, retained-reference guards, metadata ownership and event/audit transaction boundaries are preserved. Internal soft deletion is not exposed as a browser command. No duplicate AuditLog or AssetEvent is introduced; detail/list/history reads create neither.

## Boundaries and authorization

- `AssetQuery` and `AssetHistoryQuery` are closed application criteria independent of HTTP serialization; no arbitrary field/query map enters Asset SQL reads.
- `InventoryReads` explicitly maps SQL result rows into application `AssetRead`/`AssetRead.Event` projections. Asset state reuses the established serialization-independent `AssetService.View`; entities remain persistence-only. One joined detail/list statement supplies readable references without per-row association requests.
- `AssetDtos` owns HTTP request/response/query binding. Request DTOs map explicitly into application commands/scalars; they are not persisted. List Item excludes detail note/loanNote/deletion fields; Detail is richer; command State deliberately preserves the existing scalar command result surface. Immutable event snapshot JSON remains historical data inside a typed outer projection.
- `InventoryOperations` remains the authoritative authenticated application boundary and owns operation transactions. TENANT route context is ignored for scope, including another or nonexistent Tenant UUID: effective Tenant comes exclusively from signed identity. GLOBAL concrete context is validated. The established GLOBAL-only aggregate read endpoint retains its role/scope policy and optional validated Tenant narrowing.
- Detail/history and mutation target lookups constrain persisted ownership to signed Tenant before resolving a target. Another Tenant's and unknown Asset UUIDs produce the existing NOT_FOUND/non-disclosure result. `SecuredDomainOperations` uses the same scoped lookup for its internal supported Asset operations and identity-derived registration context.
- ACTIVE/ADMIN write gates, ordinary USER reads, PENDING/DISABLED denial and independently scoped reference validation remain intact. Readable labels never determine an entity UUID or grant authority.
- Strict command binding rejects server-owned metadata, actor, ownership, generated tag, deletion, version overposting and unsupported properties. Existing validation/error/version status semantics remain.

The changed TENANT transport behavior is the explicitly approved service-authority rule, not expanded Tenant access. Legacy foreign-route rejection assertions were strengthened to assert own-Tenant items/counts/attribution. Internal cross-Tenant Asset assertions now require NOT_FOUND rather than a distinguishable authorization error.

## API and schema

OpenAPI changes are restricted to the established Asset paths and response schemas. Both collection routes advertise the approved extra predicates/default 20; owning-Asset history advertises default 20. AssetDetail replaces raw joined-name/deletedBy read fields with safe nested references. AssetPage uses a concise new AssetItem; AssetReference carries id/name. The scalar Asset command schema remains unchanged. No route renaming or compatibility endpoint is introduced.

All non-Asset parsed paths and all other existing schemas compare equal to PRE-Step-5. Existing shared paging anchors were expanded for unrelated endpoints to preserve their old semantics while changing Asset defaults only. All local OpenAPI references resolve.

**No migration, index, schema, backfill, deployment or development-data reset.** M1 clean-state operational prerequisites remain separately gated. No speculative index is added without evidence. Frontend files/tests are unchanged and not run; new projections/default sizes require a separately approved frontend integration, so no deployment was attempted. No manual business-data or Asset-sort verification is claimed.

## Tests and verification

Maven 3 wrapper/Java 25, fresh isolated PostgreSQL 17 Testcontainers only. No automated test was directed at the manual environment.

New `AssetResourceIT` provides **50 cases** covering approved defaults and sizes, empty/beyond-end metadata, full-result sorting across four pages in both directions, combined predicates, each q identifier, literal wildcard/whitespace/length handling, unsupported sorting/deleted/unpaged criteria, identity-derived context, foreign/unknown detail/history/command non-disclosure, exact reference IDs, retained readable references versus forbidden new assignment, safe projections, bounded immutable history, metadata overposting, real actor attribution, creation preservation/update attribution, rollback and no read-side audit/event writes.

Existing suites retain graph pair/loan independence, source/target reference validation, registration derivation, version conflicts, actual PostgreSQL lock/concurrency tests, history-write rollback, Catalog/Place guards, M1 attribution and security coverage. Three legacy test files change only assertions obsolete under the approved scope/filter/non-disclosure contract, with positive isolation/filtered-result assertions added.

Initial focused run: 74 unit cases passed; 44 integration cases, 42 passed/2 failed, 0 errors/skips. Two new fixture expectations were wrong: `unwanted` contains the literal substring `wanted`; the security layer returns 404 for the absent user-facing DELETE endpoint. Corrected fixture/expectations without production changes. The first full run had 74 unit cases passed and 479 integration cases, 478 passed/1 failed, 0 errors/skips: an old Phase4 assertion forbade the now-approved localIdentifier filter. It now asserts a scoped literal filtered result excluding a deleted Asset while unsupported localIdentifier sorting still fails. Six additional focused cases were subsequently added.

| Final command/check | Result |
|---|---|
| `./mvnw -B verify` | **BUILD SUCCESS**, 01:20 min |
| Unit tests | **74 passed**, 0 failures/errors/skips |
| PostgreSQL integration tests | **485 passed**, 0 failures/errors/skips |
| Final total | **559 passed**, including **50 AssetResourceIT cases** |
| OpenAPI parsing and all 202 local references | Passed |
| Non-Asset OpenAPI paths/schemas preservation | Passed |
| PRE-Step-5 hash/delta and local documentation links | Passed |
| `git diff --check` and new-file whitespace checks | Passed |

Full integration matrix: AssetResourceIT 50, CatalogLabelIT 62, ProductIT 41, PlaceIT 65, Phase3InventoryIT 97, Phase4MetadataIT 23, IdentityAuthenticationIT 49, DomainFoundationIT 72, M1BusinessMetadataIT 26. No excluded tests are counted. Frontend suites were neither modified nor run.

Reproducible command from `backend/`: `MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw -B verify`. The initial unit-only run also passed all 74 cases.

## Exact Step 5 file delta

Modified relative to PRE-Step-5:

1. `backend/src/main/java/com/github/ksewen/kelios/inventory/InventoryReads.java`
2. `backend/src/main/java/com/github/ksewen/kelios/platform/security/AssetController.java`
3. `backend/src/main/java/com/github/ksewen/kelios/platform/security/InventoryOperations.java`
4. `backend/src/main/java/com/github/ksewen/kelios/platform/security/SecuredDomainOperations.java`
5. `backend/src/test/java/com/github/ksewen/kelios/identity/auth/IdentityAuthenticationIT.java`
6. `backend/src/test/java/com/github/ksewen/kelios/identity/auth/Phase3InventoryIT.java`
7. `backend/src/test/java/com/github/ksewen/kelios/identity/auth/Phase4MetadataIT.java`
8. `docs/api/openapi.yaml`
9. `docs/plans/COLLECTION_QUERY_IMPLEMENTATION_PLAN.md`

Added:

10. `backend/src/main/java/com/github/ksewen/kelios/inventory/AssetQuery.java`
11. `backend/src/main/java/com/github/ksewen/kelios/inventory/AssetHistoryQuery.java`
12. `backend/src/main/java/com/github/ksewen/kelios/inventory/AssetRead.java`
13. `backend/src/main/java/com/github/ksewen/kelios/platform/security/AssetDtos.java`
14. `backend/src/test/java/com/github/ksewen/kelios/identity/auth/AssetResourceIT.java`
15. `docs/verification/STEP_5_ASSET.md`

## Limitations and checkpoint

No remaining known Step-5 backend defect. No operator dataset, manual runtime/deployment or manual sorting evidence was created. Future performance/index decisions require representative isolated measurements. Frontend integration, standard page-size selector and incremental lookup behavior remain outside this backend task. Shared size-25 defaults for unmigrated resources, Users/Email and Audit completion remain separately gated. No new schema or historical-data compatibility machinery is required by Step 5.

PRE-Step-5 hashes/delta, OpenAPI semantic comparison, exact Git status and local Maven logs are retained under ignored `.cache/step5/`; the accepted prior dirty worktree was preserved. No staging/commit/push/PR, database reset or persistent test data. **STOP for human review before Step 6.**
