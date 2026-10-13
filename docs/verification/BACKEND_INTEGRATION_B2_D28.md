# B2 / D28 — Product optimistic conflict error-contract correction

Date: 2026-10-13. Status: **IMPLEMENTED / PENDING HUMAN ACCEPTANCE**, backend-only. [Authoritative integration plan](../plans/FRONTEND_BACKEND_INTEGRATION_PLAN.md). Accepted Product/Merge locking and Domain behavior remain closed; only the separately authorized semantic error mapping is corrected.

## Previous and corrected public contract

CatalogService.lockProduct still throws ObjectOptimisticLockingFailureException(Product.class,id) if its prelocked reference snapshot differs from the Product acquired for mutation. Previously BusinessProblems.stale fell through to an Asset response. The correction adds exactly one Product-class branch calling the existing concurrency response; no new machine vocabulary/framework.

| Failing resource/path | HTTP | Machine code | Detail |
| --- | --- | --- | --- |
| Product reference race, previous | 409 | STALE_ASSET_VERSION | Asset changed; review its current version before resubmitting |
| Product reference race, corrected | 409 | CONCURRENT_OPERATION | Concurrent operation; review current state before resubmitting |
| Asset version, unchanged | 409 | STALE_ASSET_VERSION | Asset changed; review its current version before resubmitting |
| AssetService.ProductSelection dependency race, unchanged | 409 | CONCURRENT_OPERATION | Product dependencies changed during reference validation; review before resubmitting |

CONCURRENT_OPERATION already exists in the reviewed public Problem contract and the neutral concurrency handler. HTTP status, RFC9457/application/problem+json, no-store, sanitization and review-before-resubmitting semantics are retained. No automatic retry. Product does not have a new version field/command/API; nothing in its edit/publish/lifecycle, references, authorization or locking changed.

## Exact task manifest against pre-B2 worktree

1. backend/src/main/java/com/github/ksewen/kelios/platform/security/BusinessProblems.java
2. backend/src/test/java/com/github/ksewen/kelios/platform/security/BusinessProblemsTest.java
3. backend/src/test/java/com/github/ksewen/kelios/identity/auth/ProductIT.java
4. docs/api/openapi.yaml
5. docs/plans/FRONTEND_BACKEND_INTEGRATION_PLAN.md
6. docs/plans/COLLECTION_QUERY_IMPLEMENTATION_PLAN.md
7. docs/verification/FRONTEND_INTEGRATION_F2_ANONYMOUS_ROUTING.md (append-only human acceptance)
8. docs/verification/BACKEND_INTEGRATION_B2_D28.md

Removing only the new Product-class branch exactly reconstructs the pre-gate BusinessProblems hash. Asset/ProductSelection/other exception mappings remain byte-identical. CatalogService, ProductOperations, CatalogMergeService, InventoryOperations, AssetService, lock-order tests, schemas/migrations and all frontend production/test files are unchanged. Prior dirty accepted B1 and anonymous frontend work are preserved; this is not a diff merely against HEAD.

OpenAPI delta: two sentences documenting HTTP409 CONCURRENT_OPERATION specifically for Product edit and publish. Existing Problem vocabulary/schema and every other operation remain unchanged. An initial endpoint-description assertion caught the sentence matched to Product create; corrected before final checks. No creation semantics were changed or documented as changed.

## Focused PostgreSQL/HTTP evidence

ProductIT adds six deterministic Merge-first schedules: Category/Manufacturer × Product edit, Product publish, Asset registration using ProductSelection. Each starts a Merge transaction holding label locks, dispatches a real secured MockMvc HTTP request on a separate connection, observes PostgreSQL pg_blocking_pids and the waiting FOR SHARE query, then lets Merge redirect Product references/commit before the waiting command proceeds.

- Edit/publish responses are409/CONCURRENT_OPERATION with the existing neutral detail, never STALE_ASSET_VERSION or Asset-changed wording.
- Registration dependency races retain409/CONCURRENT_OPERATION and the existing Product-dependencies detail.
- Failed commands leave the entire Product row/metadata equal to committed Merge state. Name/manufacturer number and Tenant visibility are not changed by aborted commands.
- No PRODUCT_EDITED/PRODUCT_PUBLISHED audit is emitted for the failing Product; exactly one successful Merge audit remains. Asset rows/history counts do not change on failed registration.
- A separate actual stale Asset metadata HTTP request preserves409/STALE_ASSET_VERSION and its exact detail, leaves the Asset row unchanged and emits no extra AssetEvent.

BusinessProblemsTest extends its existing sanitized HTTP classifier with Product.class and verifies unchanged Asset and ProductSelection detail semantics. This unit layer is mocked classification only; the seven ProductIT cases use real isolated PostgreSQL transactions/security/HTTP, not mocked service races.

## Verification

Commands run from backend with MAVEN_USER_HOME="$PWD/../.cache/maven-home" and TMPDIR="$PWD/../.cache/tmp", Maven3/Java25:

```sh
./mvnw -B -Dtest=BusinessProblemsTest '-Dit.test=ProductIT#productReferenceRaceHasResourceCorrectHttpConflict+assetVersionConflictRemainsAssetSpecific' verify
./mvnw -B verify
```

| Execution | Passed | Failures / errors / skips |
| --- | --- | --- |
| Focused BusinessProblemsTest | 7 unit | 0 /0 /0 |
| Focused ProductIT | 7 PostgreSQL/HTTP | 0 /0 /0 |
| Complete Surefire | 75 unit,5 current suites | 0 /0 /0 |
| Complete Failsafe | 727 integration,14 suites | 0 /0 /0 |
| Complete total | **802** | **0 /0 /0** |

Both commands BUILD SUCCESS; focused14.645s, full1m29s. Focused cases are included in full totals, not additional unique passes. Affected complete suites include ProductIT48, CatalogLabelIT62, CatalogProductLockOrderIT9, Phase3InventoryIT97 and AssetResourceIT50 (all subsets of727). Counts come from current execution logs, not stale XML directories. Ignored logs: .cache/backend-b2/focused.log and verify.log.

OpenAPI YAML parses;219 internal references resolve; targeted assertions confirm edit/publish CONCURRENT_OPERATION descriptions and no added create claim. Compile/package/migrations/security/resource/transaction/history tests run in the complete backend suite. All PostgreSQL/business fixtures are isolated Testcontainers databases; no manual environment, deployment, credentials or operator data used or reset. No frontend tests needed/run for this backend-only correction.

## Acceptance and remaining work

Anonymous F2/D20/D22 human acceptance recorded2026-10-13 in the plan and appended to its report; original results and unavailable live-lookup limitation unchanged. B1/D29 remains HUMAN ACCEPTED / CLOSED.

B2/D28 is IMPLEMENTED / PENDING HUMAN ACCEPTANCE only. Exact frontend Product-feedback subset remains separately pending; after B2 human acceptance it needs bounded inspection/review and separate authorization if rollout is required. Backend correction alone does not close F4 feedback/tests. No frontend production/test modernization or automatic retry was introduced.

390px Product wrapping, Phase C/D, export/delivery, deployment, excluded real-write modernization and residual work remain outside scope. No schema, authorization, Product lifecycle/locking/ref-validation change; no stage/commit/push/PR/reset/operator data or next gate. STOP for human review.

## Final diff/preservation checks

PASS: git diff --check;68 relative documentation links and all changed/new-file whitespace; exact8-file delta against .cache/backend-b2/before.json; original anonymous verification is an exact prefix with acceptance appended; all frontend production/tests, B1 work, locking/Asset code, migrations and unrelated tracked/untracked files unchanged. OpenAPI reversal of only its two edit/publish sentences restores the exact pre-gate hash. Index empty, HEAD unchanged; no commit.

Final git status --short (includes prior accepted/unrelated dirty work, not this task manifest):

```text
 M backend/src/main/java/com/github/ksewen/kelios/identity/auth/OidcConfigurationService.java
 M backend/src/main/java/com/github/ksewen/kelios/platform/security/AuthController.java
 M backend/src/main/java/com/github/ksewen/kelios/platform/security/BusinessProblems.java
 M backend/src/test/java/com/github/ksewen/kelios/identity/auth/IdentityAuthenticationIT.java
 M backend/src/test/java/com/github/ksewen/kelios/identity/auth/ProductIT.java
 M backend/src/test/java/com/github/ksewen/kelios/platform/security/BusinessProblemsTest.java
 M docs/DEPLOYMENT_CLI.md
 M docs/INVENTORY.md
 M docs/api/openapi.yaml
 M docs/plans/COLLECTION_QUERY_IMPLEMENTATION_PLAN.md
 M docs/plans/DEPLOYMENT_CLI_PLAN.md
 M docs/plans/FRONTEND_BACKEND_INTEGRATION_PLAN.md
 M frontend/src/app/OrganizationPicker.tsx
 M frontend/src/auth.test.ts
 M frontend/src/auth.ts
 M frontend/tests/email-login.spec.ts
 M frontend/tests/search-stability.spec.ts
?? .idea/
?? docs/verification/BACKEND_INTEGRATION_B1_D29.md
?? docs/verification/BACKEND_INTEGRATION_B2_D28.md
?? docs/verification/FRONTEND_INTEGRATION_F2_ANONYMOUS_ROUTING.md
?? docs/verification/merge-combobox-phase-b/
?? docs/verification/phase-b1/
?? docs/verification/phase-b2/
?? docs/verification/phase-b3/
?? docs/verification/phase-b4/
?? docs/verification/sidebar-phase-a/
?? docs/verification/sidebar-phase-a1/
?? docs/verification/sidebar-phase-a2/
?? docs/verification/sidebar-phase-a3/
?? docs/verification/sidebar-phase-a4/
?? frontend/tests/anonymous-routing-lookup.spec.ts
```

## Human acceptance — 2026-10-13

B2/D28 HUMAN ACCEPTED / CLOSED by explicit human decision. All historical verification/results and limitations above remain unchanged. The corrected Product409/CONCURRENT_OPERATION contract is authoritative; Asset and ProductSelection mappings remain unchanged. The exact dependent frontend Product-feedback subset was separately authorized for inspection and minimum correction/regression only; no new backend verification or stronger runtime evidence claimed.
