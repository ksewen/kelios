# Catalog/Product/Merge lock-order correction

Date: 2026-10-12. Implemented correction; pending human review. Scope is the confirmed Product edit/publish versus Category/Manufacturer Merge deadlock. No frontend or later gate was started.

## Production delta

Compared with the pre-correction worktree, only these production files changed:

- `backend/src/main/java/com/github/ksewen/kelios/catalog/CatalogService.java`: resource-specific `lockProduct` reads the current Product reference IDs, locks the union of current/requested Category IDs followed by Manufacturer IDs (deduplicated/sorted within each kind), then locks Product FOR UPDATE. It rereads authoritative Product references and throws the existing optimistic conflict exception if they changed. No additional label locks are acquired after Product is locked. Edit validates requested visibility against prelocked rows; publish validates current associations against prelocked rows. Existing deleted/historical-use/publication guards, metadata and transaction/audit writes remain in their original command paths.
- `backend/src/main/java/com/github/ksewen/kelios/catalog/ProductOperations.java`: edit's preliminary scoped authority check no longer takes Product FOR UPDATE. Delete retains its prior lock. Edit passes signed Tenant restriction into the trusted writer, which checks persisted ownership under the final Product lock; concurrent publication cannot turn an authorized Tenant edit into a GLOBAL edit. The existing trusted internal edit overload remains available without changing unrelated callers.

Invariant: required label locks precede Product mutation lock. A changed reference snapshot fails with existing optimistic conflict/HTTP409 behavior; it is not repaired by acquiring new labels after Product. The mutation reads current Product state under that lock before lifecycle/visibility guards and writes. No retry, generic lock framework, blanket serialization or Merge algorithm change.

## Deterministic PostgreSQL outcomes

`backend/src/test/java/com/github/ksewen/kelios/identity/auth/CatalogProductLockOrderIT.java` replaces the accepted defect-reproduction assertions with eight regression schedules plus a signed-Tenant ownership regression.

| Product command / Merge kind | Product label locks first | Merge label locks first |
| --- | --- | --- |
| Edit / Category | Both commit | Merge commits; Product reference conflict |
| Edit / Manufacturer | Both commit | Merge commits; Product reference conflict |
| Publish / Category | Both commit | Merge commits; Product reference conflict |
| Publish / Manufacturer | Both commit | Merge commits; Product reference conflict |

Tests hold actual service lock prefixes in the same transaction and confirm the competing backend is waiting through `pg_blocking_pids` before releasing the first operation. They then execute the real authorized commands. All eight schedules finish without 40P01, timeout or unexpected abort. When Merge wins, the Product command refuses the changed snapshot rather than overwriting redirected references. When Product wins, Merge subsequently redirects the Product successfully. Assertions cover final association/visibility, exactly one Merge audit, Product audit only on committed Product mutation, unchanged creation metadata and authoritative update actor. A separate regression verifies signed-Tenant edit cannot modify a Product after GLOBAL publication and creates no edit audit.

The original investigation log `.cache/catalog-lock-audit/results.log` remains historical evidence of all four 40P01 cycles; the investigation test no longer asserts a defect as desired behavior. These are isolated PostgreSQL17 Testcontainers fixtures, not operator data or real Merge verification.

## Verification

From `backend`, with repository-local Maven/TMP cache variables:

`./mvnw -B test-compile failsafe:integration-test failsafe:verify -Dit.test=CatalogProductLockOrderIT,ProductIT,CatalogLabelIT,Phase3InventoryIT,DomainFoundationIT`

Final focused run: **281 tests, zero failures/errors/skips; BUILD SUCCESS, 33.947 seconds**. Suite counts: lock-order9, Product41, CatalogLabel62, Phase3Inventory97, DomainFoundation72. Log `.cache/catalog-lock-correction/focused.log`.

Full `./mvnw -B verify`: **74 unit +699 PostgreSQL integration tests =773 total; zero failures/errors/skips; BUILD SUCCESS, 1m30s**. Log `.cache/catalog-lock-correction/verify.log`. Existing migration validation also passed. No frontend suite was run. `git diff --check` and separate whitespace checks of the changed untracked test/report passed.

## Boundaries and limitations

No schema, migration, API/OpenAPI, authorization-policy, frontend, deployment/runtime or operator-data change. Existing Product historical edit freeze, reference guards, actor/time metadata, AuditLog atomicity and Merge semantics are preserved and covered by focused/full regressions. No reset, staging, commit, push or PR. Pre-correction copies of the two production files and concurrency test are under `.cache/catalog-lock-correction/`; this delta is distinct from accepted dirty work.

This fixes the identified four-way interaction; it is not a claim of exhaustive deadlock freedom for every application transaction. Optimistic reference conflicts remain intentional when concurrent operations invalidate prelocked inputs. STOP for human review.
