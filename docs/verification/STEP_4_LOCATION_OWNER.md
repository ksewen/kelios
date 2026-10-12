# Step 4 — Location and Owner backend verification

**Implementation complete; awaiting human acceptance.** M1, Step 2 Category/Manufacturer and Step 3 Product are HUMAN ACCEPTED / CLOSED. This is the separately authorized backend-only Location/Owner vertical refactor covering their applicable Q1, M2 and R1d requirements. Asset Step 5 and every later module remain unstarted. Frontend integration is outside scope.

## Implemented resource surface

| Capability | Location and Owner behavior |
|---|---|
| Create | Existing concrete-Tenant POST routes; strict typed name/nullable-note replacement command; ADMIN; service-derived concrete Tenant and authoritative M1 metadata; 201 ID/resource Location; atomic AuditLog |
| Collection | Existing Tenant GET routes; controller page 0/size 20/max 100; scoped count and SQL pagination; normal soft-deleted rows always excluded |
| Search | Name-only stripped literal case-insensitive substring; blank means no search; 128 UTF-16 units after stripping; %, _ and backslash literal; note never participates |
| Sorting | Only name selectable, asc/desc, selected name defaults asc; direction without sort rejected; default createdAt DESC not selectable |
| Detail | Existing persistent-UUID GET route, independently scoped; safe Tenant ID/name, name/note, creation/update and retained deletion metadata; authorized retained/deleted detail remains readable |
| Edit | Existing replacement PUT; nonblank name; omitted/null/blank note clears; Location/Owner guards remain distinct |
| Soft deletion | Existing DELETE; any retained Asset row with the reference blocks deletion, including DEREGISTERED and soft-deleted rows |
| Merge/undelete/hard-delete | Not added; no Location/Owner Merge |

No aggregate Place collection, generic CRUD/query/mapping framework, arbitrary SQL field access or current-page client filtering/sorting is introduced. The retained SQL ID tie order is an implementation detail for predictable page boundaries, not a mandatory Domain secondary order or selectable field.

## Location/Owner distinction preserved

| Mutation | Location | Owner |
|---|---|---|
| Unreferenced name/note edit | Allowed | Allowed |
| Referenced name edit | Allowed by stable ID | Prohibited |
| Referenced unchanged-name/note-only edit | Allowed | Prohibited; no note exception |
| Referenced soft deletion | Prohibited | Prohibited |
| Explicitly resolved all retained current references | Ordinary guarded editing/deletion allowed | Ordinary guarded editing/deletion allowed |

Reference scans remain over all retained Asset rows, with no lifecycle/deletion exclusion. Location editing does not change an Asset reference, state/version/metadata or create an AssetEvent. Owner note has exactly the same guard as Owner name; it is not a separate mutation bypass. Notes retain accepted M1 blank-to-null/strip and replacement semantics. Immutable historical evidence is neither rewritten nor counted as a live relational reference after explicit reference resolution.

## Boundaries and authority

- `PlaceController`/`PlaceDtos` own endpoint parsing/defaults, explicit command/list/detail DTOs and mapping. Unknown fields, writable actor/time/ownership/deletion fields and JSON non-text coercion are rejected. Empty deletion commands retain the existing empty-command validator.
- `PlaceModel`/`PlaceQuery` are transport/JPA-independent application projections/closed criteria; no note filter/sort or caller-owned Tenant criterion exists.
- `PlaceReads.Row` is an explicit private persistence query projection mapped into the model. Bound SQL scope/search/count/order/LIMIT/OFFSET form one coherent query. Tenant ID/name uses a bounded join, not full graph loading or per-row lookups. List DTOs deliberately omit note and deletion facts; richer detail supplies them. Existing persistence entities are not HTTP responses/request DTOs.
- `PlaceOperations` owns CurrentIdentity, ACTIVE/admin checks, effective Tenant and transactional orchestration. TENANT scope comes only from signed identity: transport Tenant path IDs, even foreign/nonexistent ones, are ignored rather than compared as proof. GLOBAL collection/create validates and requires one concrete Tenant. No Global aggregate write/read collection is invented.
- UUID detail/mutation selection is constrained by signed Tenant for TENANT, independent of collection state. Foreign and missing targets follow the existing NOT_FOUND non-disclosure contract. GLOBAL permitted reads/writes use authoritative persisted ownership, not request copies. PENDING/DISABLED/USER mutation restrictions remain intact.
- Accepted `PlaceService` is unchanged and remains the trusted transactional writer/guard owner. Its operation-specific locks re-load the target under authoritative immutable Tenant ownership. There is no new universal concurrency/snapshot framework. Same-transaction metadata/audit changes retain M1 behavior; required actors/timestamps are never accepted from callers. Reserved system metadata labels use the existing UUID/display-name resolver without a User lookup.
- The secured internal facade delegates Place commands and constrains its internal Place detail. Obsolete Place HTTP adapter methods are removed from InventoryOperations. Every non-Place operation in both shared facades was compared with PRE-Step-4 source and remains verbatim unchanged.

Read count and rows retain the existing READ COMMITTED behavior; no new concurrent snapshot guarantee is claimed. Beyond-end returns requested page/empty items/current authoritative count rather than redirecting. Existing internal trusted read helpers are not exposed as ordinary deleted-data browser endpoints.

## API, schema and integration

Only four established OpenAPI path entries change: Tenant locations collection/create, Location UUID detail/edit/delete, Tenant owners collection/create and Owner UUID detail/edit/delete. Four existing resource schemas are updated: PlaceRow, PlaceDetail, PlacePage, PlaceCommand. All non-Place parsed paths and all other schemas remain identical to PRE-Step-4; YAML/local references are valid. Legacy deleted/q anchors disappear with the obsolete Place query parameters, without changing any other endpoint.

API changes: default size 25 -> 20, selectable server name sorting, stripped literal name search, strict unsupported/deleted query rejection and endpoint-specific safe nested metadata/Tenant association projections. Detail supplies note; concise list does not. URLs and existing command/error/status semantics remain. TENANT shared transport context behavior now consistently follows the approved service-authority rule, as already accepted for Catalog/Product.

**No migration, index, backfill, compatibility layer, deployment or data reset is introduced.** Accepted M1 V7 already supplies note and metadata; source migrations and writers remain unchanged. Its clean-state operational prerequisite for a pre-M1 manual environment remains separately gated, not executed here. No speculative index is added without isolated query evidence.

Frontend behavior/tests were not modified or run. Frontend query/detail/note integration remains pending under separate approval; no compatibility workaround was added.

## Verification

Executed from `backend/` with committed Maven 3 wrapper/Java 25 and isolated PostgreSQL 17 Testcontainers only. No test targeted the running manual environment.

| Command/check | Result |
|---|---|
| `./mvnw -B -Dit.test=PlaceIT verify` | BUILD SUCCESS; 74 unit + 65 focused integration cases, zero failures/errors/skips; 23.092 s |
| Final `./mvnw -B verify` | BUILD SUCCESS; 01:07 min |
| Unit tests | **74 passed**, 0 failures/errors/skips |
| Integration tests | **435 passed**, 0 failures/errors/skips |
| Final total | **509 passed**, including **65 new PlaceIT cases** |
| OpenAPI YAML/local-reference and non-Place preservation checks | Passed |
| PRE-Step-4 hash/preservation and local documentation-link checks | Passed |
| `git diff --check` | Passed |

Full integration matrix: PlaceIT 65, CatalogLabelIT 62, ProductIT 41, Phase3InventoryIT 97, Phase4MetadataIT 23, IdentityAuthenticationIT 49, DomainFoundationIT 72, M1BusinessMetadataIT 26.

New cases cover both resources: controller defaults/min/max/all six intended UI sizes, empty/beyond-end/count metadata, strict criteria/deleted/unpaged/SQL-field/length validation, trim/blank/literal search, note exclusion, full server ASC/DESC duplicate-name ordering over more than three pages, signed-Tenant scope despite forged path IDs, foreign/missing UUID detail/edit/delete, safe richer projection/read-without-audit, authoritative metadata and creation preservation, note creation/edit/clearing/replacement, overposting/type rejection, distinct retained-reference guards for registered/deregistered/deleted Asset rows, reference resolution, no Asset state/history changes from Location edit, roles/status, absent Merge/no writes and create/edit/delete audit-failure rollback.

### Legacy test alignment and first full run

The first full run executed 74 unit + 435 integration cases, with one integration failure, zero errors/skips: `M1BusinessMetadataIT.ownerAndLocationForeignTenantCannotBeMutated` still expected a TENANT create with a foreign Tenant path to return 403. The approved service contract explicitly ignores TENANT caller Tenant scope inputs; the correct create result is 201 in the signed own Tenant, never a foreign write. Production behavior was not weakened to satisfy the historical transport assumption.

Only that existing test was updated: it still asserts foreign UUID edit denial and now also foreign UUID delete denial; a forged Tenant-path create must yield a row owned by the signed own Tenant with the signed actor's creation/update metadata and matching audit attribution. Foreign target row and foreign-Tenant row count must remain unchanged. All other existing tests remain byte-identical. This is an explicitly recorded contract migration, not reduced authorization coverage or a new authority grant.

Initial full failure evidence: `.cache/step4/verify-before-test-update.log`. Successful logs: `.cache/step4/focused.log`, `.cache/step4/verify.log`. Preservation evidence: `.cache/step4/baseline.json`, `.cache/step4/delta.json`; exact dirty status: `.cache/step4/git-status.txt`. These are local working evidence, not runtime/business-data evidence.

## Exact Step 4 delta

Modified relative to PRE-Step-4:

1. backend/src/main/java/com/github/ksewen/kelios/platform/security/PlaceController.java
2. backend/src/main/java/com/github/ksewen/kelios/platform/security/InventoryOperations.java
3. backend/src/main/java/com/github/ksewen/kelios/platform/security/SecuredDomainOperations.java
4. backend/src/test/java/com/github/ksewen/kelios/identity/auth/M1BusinessMetadataIT.java — one directly affected Tenant-authority assertion strengthened/aligned
5. docs/api/openapi.yaml
6. docs/plans/COLLECTION_QUERY_IMPLEMENTATION_PLAN.md

Added:

7. backend/src/main/java/com/github/ksewen/kelios/inventory/PlaceModel.java
8. backend/src/main/java/com/github/ksewen/kelios/inventory/PlaceQuery.java
9. backend/src/main/java/com/github/ksewen/kelios/inventory/PlaceReads.java
10. backend/src/main/java/com/github/ksewen/kelios/inventory/PlaceOperations.java
11. backend/src/main/java/com/github/ksewen/kelios/platform/security/PlaceDtos.java
12. backend/src/test/java/com/github/ksewen/kelios/identity/auth/PlaceIT.java
13. docs/verification/STEP_4_LOCATION_OWNER.md

## Limits and stop

No manual production rendering, deployment or performance/load/EXPLAIN evidence is claimed. No persistent business data or operator environment changes occurred; writes used isolated test fixtures only. No schema/index change was necessary. Frontend/detail integration remains separately pending. There is no deviation from the approved Location/Owner guard/query/resource matrix; list/detail projections deliberately differ under M2.

Accepted Category/Manufacturer/Product code/tests, Asset source and API contracts, normative ARCHITECTURE/DOMAIN, migration history and earlier evidence remain unchanged. Existing dirty/untracked work is preserved except the authorized delta above. No staging, commit, push, PR, reset, clean or stash occurred.

**Step 4 is ready for human review. STOP before Asset Step 5.**
