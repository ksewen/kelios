# Step 3 — Product backend verification

**Implementation complete; awaiting human acceptance.** M1 and Step 2 Category/Manufacturer are HUMAN ACCEPTED / CLOSED. Step 3 implements the separately authorized Product portions of Q1, M2 and R1c only. Location/Owner Step 4 and all later modules remain unstarted. Frontend integration is explicitly outside this task.

## Product resource contract

| Capability | Implemented behavior |
|---|---|
| Create | Established root and concrete-Tenant POST routes; strict typed name/reference/optional-number command; ADMIN; service-derived scope; authoritative M1 metadata and atomic AuditLog; 201 UUID + Location |
| Collection | Existing root/context GET routes; page 0/size 20/max 100; scoped count and SQL pagination; normal soft-deleted Products excluded |
| q | Literal case-insensitive substring of name OR manufacturerNumber; association names do not participate |
| Structured filters | Exact visibility enum, exact categoryId/manufacturerId UUIDs, dedicated manufacturerNumber literal substring; all combine with AND and authorized scope |
| Search normalization | Strip leading/trailing whitespace; blank means no text criterion; 128 Java UTF-16 units after stripping; %, _ and backslash escaped literally |
| Sorting | name, manufacturerNumber, visibility only; asc/desc, selected sort defaults asc; direction without sort rejected; default createdAt DESC not selectable |
| Nullable sorting | NULL manufacturerNumber last in both directions; no Category.name/Manufacturer.name sorting |
| Detail | Existing persistent-UUID route; independently authorized safe richer DTO with Category/Manufacturer ID + name and authorized Tenant ID + name; metadata; retained/deleted detail still readable in scope |
| Edit | Existing replacement PUT; optional number omitted/null/blank clears; permanent freeze after historical Asset use remains authoritative, even after reference resolution |
| Soft delete | Existing DELETE; any retained Asset row storing productId blocks deletion, including DEREGISTERED and soft-deleted Assets; immutable history alone does not block after current references are explicitly resolved |
| Publication | Existing GLOBAL ADMIN same-ID operation; GLOBAL usable dependencies required; separately validated even after historical use; pre-publication Tenant audit attribution preserved |
| Merge | Not supported; no Product Merge/preview route, service or generic merge framework added |

Existing SQL UUID tie ordering is retained only as an internal ordering detail for predictable page boundaries. No mandatory Domain secondary UUID sort or client-selectable id field is introduced.

## Architectural boundaries

`ProductDtos` owns explicit HTTP commands/list/detail projections and parsing/defaults. It rejects unknown/writable metadata fields and type coercion, including noncanonical UUID strings. `ProductModel` and `ProductQuery` are serialization-independent application models/closed criteria. `ProductReads.Row` is an explicit private SQL persistence read projection; existing JPA Product entities are not HTTP responses or request objects.

`ProductOperations` owns CurrentIdentity, ACTIVE/admin checks, effective scope and transactional orchestration. TENANT transport Tenant IDs are ignored, not compared as authority: effective Tenant is signed identity exclusively, including root creation. GLOBAL context IDs are validated. Root GLOBAL collections remain GLOBAL-only; concrete context contains GLOBAL plus that Tenant. No aggregate Catalog endpoint is invented.

Product detail/mutation selection constrains UUIDs to authorized persisted ownership before exposure/commands. Foreign/absent TENANT targets use the existing NOT_FOUND non-disclosure contract; GLOBAL labels remain readable but not writable by TENANT ADMIN. PENDING/DISABLED remain denied. Reference-filter UUIDs narrow authorized Products; unknown/foreign reference IDs do not expand authority and produce no authorized matches. Querying a reference is distinct from validating a new assignment. Services load referenced entities and validate visibility/new-assignment inside the existing transaction; no associated label is trusted as identity.

Reads use bounded joins for Category/Manufacturer and Tenant projections, not per-item association queries or full entity graph loading. List/detail DTOs deliberately differ: list omits deletion metadata; detail retains safe deletion facts. M1 metadata is explicit and server-owned; reserved system attribution remains recognizable without a fabricated User lookup. No secret projection is present.

`CatalogController` routes Product through the new boundary; its accepted Category/Manufacturer endpoint methods remain unchanged. The secured internal facade delegates Product commands and scopes its internal Product read. Obsolete Product-specific HTTP adapter methods were removed from InventoryOperations; unrelated inventory/place/audit paths are unchanged. The accepted CatalogService/CatalogMergeService and persistence entities are untouched: they remain trusted transactional writers and domain guard owners, not HTTP DTOs.

## Lifecycle, consistency and history

- `CatalogService.editProduct` retains the permanent historical-use guard from `InventoryCatalogUsage.productEverUsed`, including current references and immutable AssetEvent before/after Product IDs. No ordinary edit implicitly changes Asset facts.
- Deletion retains its current-reference guard over **all retained Asset rows**, with no lifecycle/deleted filter. Deregistration/deletion of an Asset does not clear Product references.
- Creation/edit/publication/deletion retain operation locks/reference validation, M1 creation preservation/update attribution and required administrative AuditLog atomicity. Product commands do not invent duplicate AssetEvents.
- Publication remains independent of ordinary-edit freeze and requires valid GLOBAL dependencies. Category/Manufacturer Merge continues its existing explicit reference-redirection exception; it is not ordinary Product editing.
- Query order is scope -> all requested predicates -> full-result sort -> SQL LIMIT/OFFSET, with count using the same scope/predicates. Beyond-end returns requested page/empty items/current count, without redirection. Existing READ COMMITTED count/row concurrency semantics remain; no universal lock/snapshot redesign is claimed.

## API and schema consequences

Only four existing Product OpenAPI path entries change: root collection/create, contextual collection/create, UUID detail/edit/delete and publication. New schemas are ProductReference, ProductItem, ProductDetail and ProductPage; existing ProductCommand is clarified rather than creating a duplicate command schema. Page default changes 25 -> 20; q expands to name OR number; combined reference/number filters and selectable sorts become available; deleted/unpaged/unknown query parameters are rejected. Response projections now include readable associations and safe nested metadata. Established URLs/status/error/mutation semantics remain.

All non-Product parsed OpenAPI paths and every pre-existing schema except ProductCommand were compared to PRE-Step-3 and remain identical. Legacy q/deleted YAML anchor definitions move to the first Place query solely to preserve unchanged unrelated semantics.

**No schema migration, index, backfill, deployment or data reset was performed or required by Step 3.** No compatibility layer was added. Accepted M1 V7 and its separately authorized clean-state operational requirement remain unchanged. Frontend integration with the new backend API remains pending; no compatibility work or frontend modification was made.

The implementation review resolves nullable-number ordering as **NULLS LAST for ASC and DESC**, an API-level presentation choice anticipated by the plan. There is no deviation from the approved Product field/capability matrix or lifecycle.

## Verification and exact results

Executed from backend with committed Maven 3 wrapper/Java 25 and isolated PostgreSQL 17 Testcontainers only. No test targets operator data or the manual environment.

| Check | Result |
|---|---|
| Final `./mvnw -B verify` | **BUILD SUCCESS**, 01:05 min |
| Unit tests | **74 passed**, 0 failures/errors/skips |
| PostgreSQL integration tests | **370 passed**, 0 failures/errors/skips |
| Total final verification | **444 passed**, including **41 new ProductIT cases** |
| OpenAPI YAML/local-reference validation | Passed |
| Non-Product OpenAPI contract preservation comparison | Passed |
| PRE-Step-3 worktree hash preservation and local documentation links | Passed |
| `git diff --check` | Passed |

Existing 329 integration cases all ran unchanged: CatalogLabelIT 62, Phase3InventoryIT 97, Phase4MetadataIT 23, IdentityAuthenticationIT 49, DomainFoundationIT 72, M1BusinessMetadataIT 26. No existing test assertion was weakened or modified.

ProductIT covers defaults/min/max/six UI sizes, beyond-end/empty/count, strict query errors, q name/number OR and dedicated substring/wildcards/blank/length, combined scope/visibility/reference/number predicates, full server ASC/DESC sorting across duplicate values on more than three pages, NULL placement, forged/nonexistent transport Tenant IDs, independently scoped UUID detail/readable projections, duplicate-name ID identity, association names excluded from search, strict overposting/coercion, authoritative actor/time metadata, both reference types' visibility/deleted-assignment guards, historical edit freeze after reassignment, retained deregistered/deleted reference guards, separately allowed publication, roles/status, absent Merge routes/no writes, and create/edit/publish/delete audit-failure rollback including metadata/counts.

An initial focused run executed 74 unit + 38 Product cases, with one failed test expectation: the absent Product Merge endpoint returned 404, not the test's mistakenly expected 400. The test now asserts absent Merge/preview routes plus unchanged mutation/audit counts. Three further projection/reference cases were added, and the final full run passes all 41. Production behavior was not weakened to satisfy the mistaken expectation. An initial sandboxed compile attempt could not access Maven's local repository; the authorized full Maven run succeeded with the normal cache.

Local logs: `.cache/step3/focused.log`, `.cache/step3/verify.log`; PRE-Step-3 manifest and delta: `.cache/step3/baseline.json`, `.cache/step3/delta.json`. Exact current dirty status: `.cache/step3/git-status.txt`. These are local working evidence, not deployment evidence or credentials.

## Exact Step 3 changed files

Modified relative to PRE-Step-3:

1. backend/src/main/java/com/github/ksewen/kelios/platform/security/CatalogController.java
2. backend/src/main/java/com/github/ksewen/kelios/platform/security/InventoryOperations.java
3. backend/src/main/java/com/github/ksewen/kelios/platform/security/SecuredDomainOperations.java
4. docs/api/openapi.yaml
5. docs/plans/COLLECTION_QUERY_IMPLEMENTATION_PLAN.md

Added:

6. backend/src/main/java/com/github/ksewen/kelios/catalog/ProductModel.java
7. backend/src/main/java/com/github/ksewen/kelios/catalog/ProductQuery.java
8. backend/src/main/java/com/github/ksewen/kelios/catalog/ProductReads.java
9. backend/src/main/java/com/github/ksewen/kelios/catalog/ProductOperations.java
10. backend/src/main/java/com/github/ksewen/kelios/platform/security/ProductDtos.java
11. backend/src/test/java/com/github/ksewen/kelios/identity/auth/ProductIT.java
12. docs/verification/STEP_3_PRODUCT.md

## Limitations and stop

No manual production/runtime inspection, frontend tests or load/EXPLAIN performance evidence is claimed. The operator environment was not accessed or changed. Frontend integration and the existing 390px Product-table limitation remain separately open. No speculative index or generic Catalog framework was introduced.

Accepted Category/Manufacturer models/query/operations/DTOs/tests and their endpoint methods remain unchanged; all non-Product OpenAPI paths are identical. All other pre-existing dirty/untracked work is preserved, including frontend, migrations, normative architecture/Domain and earlier verification evidence. No persistent business data, commit/staging/push/PR, deployment, reset, clean or stash occurred.

**Product Step 3 is ready for human review. STOP before Location/Owner Step 4.**
