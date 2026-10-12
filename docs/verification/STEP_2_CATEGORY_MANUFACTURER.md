# Step 2 — Category and Manufacturer backend verification

**Implementation complete; awaiting human acceptance.** M1 is HUMAN ACCEPTED / CLOSED. This is the separately authorized backend-only Category/Manufacturer vertical refactor covering their applicable Q1, M2 and R1b requirements. Product/Step 3 and every later resource remain unstarted. Frontend integration is explicitly pending and was not modified or tested.

## Implemented surface

| Capability | Category and Manufacturer behavior |
|---|---|
| Create | Established root and Tenant-context POST routes; strict typed name command; ADMIN; authoritative scope/actor/time; 201 ID and resource Location |
| Collection | Established root/context routes; controller defaults page 0/size 20, maximum 100; scoped count and SQL paging; normal soft-deleted rows excluded |
| Search/filter | Stripped name-only literal case-insensitive substring q, blank means no search; maximum 128 Java UTF-16 units after stripping; %, _ and backslash escaped; exact visibility GLOBAL/TENANT |
| Sorting | Only name/visibility selectable; asc/desc; selected sort defaults asc; direction without sort rejected; default createdAt DESC is not selectable |
| Detail | Existing UUID route; independent authorized read; safe richer DTO, readable Tenant ID/name and metadata; retained/deleted detail remains readable in authorized scope |
| Edit/delete | Existing PUT/DELETE; persisted ownership and locks, retained-reference and UNKNOWN guards; soft deletion only, no undelete/hard-delete API |
| Publication | Existing GLOBAL ADMIN same-ID operation; pre-publication Tenant audit attribution preserved |
| Preview/Merge | Existing GLOBAL ADMIN UUID commands; advisory preview, fresh locked execution, existing visibility/UNKNOWN/reference guards; atomic reference redirects/source deletion/AuditLog/AssetEvent |

No arbitrary field query, universal repository/CRUD framework, reflection mapper, association graph loading or frontend current-page filtering/sorting was introduced. The SQL query retains the existing UUID tie ordering as an internal implementation detail for predictable page boundaries; it does not establish a mandatory application-level UUID secondary-order rule or a selectable id field.

## Boundary and scope design

- `CatalogController` and `CatalogLabelDtos` own transport parsing, strict allowlists/defaults and explicit mapping. Typed create/edit and Merge DTOs reject unknown metadata/ownership fields and JSON type coercion. Empty delete/publication commands still use the existing empty-command validator.
- `CatalogLabelOperations` owns CurrentIdentity, ACTIVE/ADMIN/GLOBAL ADMIN checks and effective Tenant resolution. For TENANT, supplied Tenant URL IDs are ignored, not compared as proof; the signed Tenant is the only scope. This includes root create: a TENANT ADMIN creates an own-Tenant label rather than a GLOBAL label. GLOBAL context paths validate the supplied Tenant. The root GLOBAL collection remains GLOBAL-only; no aggregate Catalog endpoint was invented.
- Detail and mutation targets are constrained by authoritative persisted ownership. Foreign/missing TENANT detail/edit targets share the existing NOT_FOUND contract. Knowing a UUID creates no authority. GLOBAL labels remain readable but are not writable by TENANT ADMIN.
- `CatalogLabel`/`CatalogLabelQuery` are serialization-independent application models/closed criteria. `CatalogLabelReads.Row` is an explicit private SQL persistence projection mapped to the model. Existing Category/Manufacturer JPA persistence entities are not returned through HTTP.
- List/detail DTOs differ deliberately. Both project safe Tenant ID/name without per-row lookups and server-owned metadata. Only detail includes retained deletion facts. Neither exposes Product fields, credentials or secrets. The reserved M1 actor's display label is resolved without fabricating a User lookup.
- Existing `CatalogService`/`CatalogMergeService` remain the trusted transactional writers and guard owners. Creation metadata, immutable creation attribution, update metadata, sanitized AuditLog and redirected Asset history are preserved. The older secured facade delegates label commands to the authoritative boundary; Product and unrelated paths retain their existing implementation.

Query execution is authoritative scope -> all requested predicates -> full server order -> SQL LIMIT/OFFSET, with count over the same authorized predicates. Empty/beyond-end pages return requested page/current count. No generic unpaged escape hatch or ordinary deleted criterion remains on these endpoints. Count and rows use the existing read-transaction isolation; no new concurrent snapshot guarantee or lock policy is claimed.

## API and persistence consequences

OpenAPI updates are limited to the 12 Category/Manufacturer path entries and five new resource DTO schemas: CatalogLabelItem, CatalogLabelDetail, CatalogLabelPage, CatalogLabelNameCommand and CatalogLabelMergeCommand. Existing shared safe BusinessMetadata/MergePreview contracts are reused. Unknown/deleted/unpaged query inputs are validation errors; list metadata is now a safe nested projection rather than exposing persistence columns. Detail has explicit visibility/Tenant/deletion projections. No compatibility API or duplicate endpoints were introduced.

All non-label parsed OpenAPI paths and all pre-existing schema objects were compared with the PRE-Step-2 document and are unchanged. Legacy q/deleted YAML anchors were relocated to the Product block solely to retain their unrelated contracts after the Category block stopped defining them.

**No Step 2 schema migration, index, backfill or data reset is required or performed.** Accepted M1 V7 and its clean-state operational precondition are unchanged. A populated pre-M1 manual environment still needs a separately authorized operational reset/cutover; Step 2 does not execute it. Existing migrations and seed attribution remain untouched. No performance index was added without evidence.

## Verification

Commands ran from `backend/`, using committed Maven 3 wrapper, Java 25 and isolated PostgreSQL 17 Testcontainers. No test targeted the manual environment.

| Command | Result |
|---|---|
| `./mvnw -B -DskipTests -DskipITs test-compile` | BUILD SUCCESS |
| `./mvnw -B -Dit.test=CatalogLabelIT verify` | BUILD SUCCESS; 74 unit + 60 focused integration cases, zero failures/errors/skips; 20.470 s |
| `./mvnw -B verify` after final rollback tests | BUILD SUCCESS; 74 unit + 329 integration = **403 tests**, zero failures/errors/skips; 01:01 min |
| OpenAPI YAML parse/local-reference and preservation comparison | Passed; all non-label paths and existing schemas unchanged |
| PRE-Step-2 SHA-256 preservation comparison | Passed; only the files listed below differ/new |
| `git diff --check` | Passed |

The final full integration suite includes 62 new CatalogLabelIT cases plus all 267 pre-existing cases: Phase3InventoryIT 97, Phase4MetadataIT 23, IdentityAuthenticationIT 49, DomainFoundationIT 72 and M1BusinessMetadataIT 26. Existing tests were not weakened or modified.

New coverage exercises both resources: defaults/sizes/invalid parameters; literal/blank/overlong search; combined authorized filters/count/page; ASC/DESC globally ordered duplicate values across more than three pages without mandating UUID tie direction; beyond-end pages; forged/nonexistent transport Tenant IDs; root own-Tenant creation; independent detail/association projection; roles/status; strict command overposting/coercion; metadata; UNKNOWN/retained-reference guards; publication attribution; advisory preview/fresh execution; reference/history redirects; and edit/merge audit/event failure rollback including metadata/reference restoration. Existing PostgreSQL tests retain concurrent locking/security/M1 writer coverage.

An initial focused attempt exposed test-fixture mistakes, not relaxed product expectations: the scope test created additional rows inside its own search prefix; and DISABLED token minting was correctly denied before HTTP dispatch. The fixtures were corrected: newly created rows no longer contaminate that query, and DISABLED is checked both at token minting and directly at the authenticated application boundary. An accidentally copied unrelated transition test was removed from the new fixture; its original existing suite remains unchanged and ran in full. The final full run is authoritative.

Maven logs: `.cache/step2/compile.log`, `.cache/step2/focused.log`, `.cache/step2/verify.log`. Preservation manifest: `.cache/step2/baseline.json` and `.cache/step2/delta.json`. Exact current dirty worktree status: `.cache/step2/git-status.txt`. These are local untracked/ignored working evidence; no credentials are included in this report.

## Exact Step 2 delta

Modified from the PRE-Step-2 worktree:

1. `backend/src/main/java/com/github/ksewen/kelios/platform/security/CatalogController.java`
2. `backend/src/main/java/com/github/ksewen/kelios/platform/security/InventoryOperations.java`
3. `backend/src/main/java/com/github/ksewen/kelios/platform/security/SecuredDomainOperations.java`
4. `docs/api/openapi.yaml`
5. `docs/plans/COLLECTION_QUERY_IMPLEMENTATION_PLAN.md`

Added:

6. `backend/src/main/java/com/github/ksewen/kelios/catalog/CatalogLabel.java`
7. `backend/src/main/java/com/github/ksewen/kelios/catalog/CatalogLabelQuery.java`
8. `backend/src/main/java/com/github/ksewen/kelios/catalog/CatalogLabelReads.java`
9. `backend/src/main/java/com/github/ksewen/kelios/catalog/CatalogLabelOperations.java`
10. `backend/src/main/java/com/github/ksewen/kelios/platform/security/CatalogLabelDtos.java`
11. `backend/src/test/java/com/github/ksewen/kelios/identity/auth/CatalogLabelIT.java`
12. `docs/verification/STEP_2_CATEGORY_MANUFACTURER.md`

## Limits and stop

No frontend files/tests, Product code, migration sources, ARCHITECTURE/DOMAIN requirements or accepted Phase A/B evidence changed. No manual/runtime deployment verification is claimed; the operator environment was not modified. Frontend consumers and incremental reference-picker UI remain separately gated. Performance/load testing was not undertaken. M1's operational clean-state prerequisite remains a deployment gate.

No business records were created in the manual environment; all write-path checks used isolated disposable test databases. No commit, staging, push, PR, reset, clean or stash occurred. Step 2 is ready for human review, not human accepted by this report. **STOP before Product/Step 3.**
