# F4 — Independent resource details and mutation integration

Status: IMPLEMENTED / PENDING HUMAN ACCEPTANCE for authorized unblocked D02/D08/D09/D18/D19/D21/D23/D25/D26 scope. Exact Product optimistic-conflict feedback remains BLOCKED under B2/D28, NOT AUTHORIZED. F3/D04/D12–D15 acceptance recorded as 2026-10-12 without changing its historical evidence.

## Scope and implementation

- Canonical UUID details independently GET Category, Manufacturer, Product, Location, Owner and User. Existing independent Tenant and Asset destinations remain. Category/Manufacturer/Location/Owner use a route-backed modal only over a currently live same-context list; direct entry/refresh uses standalone detail. Product/User remain standalone. Backend detail authorization, not list membership, determines disclosure.
- Asset Item/Detail and Dashboard adopt safe nested readable associations, retaining authoritative IDs. Product/Asset edit selections use owning-detail ID/name projections; ID-only references have a bounded resource-specific independent detail hydration path. Candidate labels include readable context and UUID disambiguation. No name-to-ID inference, generic mapper or N+1 lookup when owning projections supply labels.
- Place create/edit includes nullable note. Name-only editing preserves the current detail note; explicit clear submits null. Owner note-only changes receive the existing REFERENCED guard; Location edits remain allowed by backend. No note search/sort/filter or Merge added.
- GLOBAL User creation, Email edit and credential reset send exact Email DTOs. Safe independent User detail exposes GlobalAccount only for GLOBAL identities. Self-protection and signed-own administrator controls remain; no TENANT credentials, arbitrary profile/status PATCH, deletion or reactivation.
- Password creation/reset/change visibly checks 12–128 Unicode code points and uppercase/lowercase/digit/special rules, with native validity, accessible labels/description and unchanged literal password bytes. Existing current password is not subjected to the replacement composition rule. Backend validation remains authoritative.
- OIDC DELETE uses current version, separate final confirmation, disabled duplicate dispatch and empty success. Cancellation dispatches nothing and preserves the same editing session. Success clears configuration/secret/groups/version and dirty/problem state explicitly, invalidates the stale local read snapshot and permits initial configuration only with a fresh secret. Disabled existing configuration remains editable/deletable. 404/409 failure preserves input/version and never replays. No backend generation, tombstone, reset or history added.
- Readonly metadata/account/association projections are rendered without writable metadata or secret fields. Mutation bodies explicitly whitelist user input. Detail transient reload failures retain the same mounted active form/input; 401/403/404 clear protected read state. Domain guards, reviewable conflict and unknown dispatched outcome have different safe feedback. Reload never automatically resubmits. Successful resource edits and User Email/role commands explicitly clear local write values; reopen initializes from current authoritative detail.
- Product's actual `409 / STALE_ASSET_VERSION` is retained but given neutral Product-context conflict wording. No resource-correct server code is fabricated. B2 remains open; exact Product optimistic feedback is not claimed complete.

## Exact F4 file manifest against the pre-gate worktree

1. docs/plans/FRONTEND_BACKEND_INTEGRATION_PLAN.md
2. docs/verification/FRONTEND_INTEGRATION_F4.md
3. frontend/src/app/Dashboard.tsx
4. frontend/src/app/Shell.tsx
5. frontend/src/components/ui/dropdown-menu.tsx
6. frontend/src/features/inventory/pages.tsx
7. frontend/src/resource-contracts.ts
8. frontend/src/resource-contracts.test.ts
9. frontend/src/locales/en.json
10. frontend/src/locales/de.json
11. frontend/tests/api-foundation.spec.ts
12. frontend/tests/dialog-toolbar.spec.ts
13. frontend/tests/form-lifecycle.spec.ts
14. frontend/tests/f2-paging-lookup.spec.ts
15. frontend/tests/f4-resource-detail.spec.ts
16. frontend/tests/merge-combobox.spec.ts

Existing fixtures were aligned narrowly: independent Catalog/User detail responses, Email creation input, policy-valid replacement passwords, and nested readable Tenant projections in Merge candidates. Existing dirty/form/paging assertions remain. No compatibility response fallback was added.

## Verification

Commands run in `frontend`, using Node 24 on PATH and isolated Vite fixture at `http://127.0.0.1:12180`:

- `npm test`: 168 passed, 8 files; zero failed/skipped.
- `npm run build`: PASS (TypeScript and Vite production build).
- Focused initial F4 Chromium run: 29 passed, 3 failed out of 32. Corrected an invalid Email translation key in source/tests and an alert locator targeting an aria-hidden underlying form. Focused rerun: 32 passed, zero failed/skipped.
- Broader initial Chromium attempt interrupted after detecting obsolete policy-invalid password fixtures. Not counted as successful verification. Those fixtures now exercise errors with a policy-valid password and independent detail projections.
- Final broad Chromium command: `KELIOS_BASE_URL=http://127.0.0.1:12180 PLAYWRIGHT_BROWSERS_PATH="$PWD/../.cache/playwright" npx playwright test f3-resource-query.spec.ts api-foundation.spec.ts email-login.spec.ts f2-paging-lookup.spec.ts list-filtering.spec.ts collection-query.spec.ts merge-combobox.spec.ts form-lifecycle.spec.ts search-stability.spec.ts dialog-toolbar.spec.ts sidebar-layout.spec.ts administration-context.spec.ts f4-resource-detail.spec.ts --output=../.cache/frontend-f4/browser-all`.
- Additional focused unknown-outcome command: same environment, `npx playwright test f4-resource-detail.spec.ts --grep 'dispatched unknown' --output=../.cache/frontend-f4/browser-unknown`: 1 passed, zero failed/skipped. It asserts retained input/DOM, review warning, one dispatch and no replay after reload.
- First completed broad result: 271 passed / 4 failed (275 executed); the four Merge cases used obsolete ID-only Tenant fixtures while asserting their raw UUID as display context. Updated those fixtures to the current safe Tenant ID/name projection without removing identity/paging/payload assertions.
- Focused Merge + F4 rerun: `npx playwright test merge-combobox.spec.ts f4-resource-detail.spec.ts --output=../.cache/frontend-f4/browser-final-focused`: 55 passed, zero failed/skipped.
- An intermediate two-worker run was stopped for final explicit successful-session reset changes; it is not counted as a passing complete run.
- Final complete rerun uses the listed broad command plus `--workers=2 --output=../.cache/frontend-f4/browser-final-all`; **276 passed, zero failed/skipped**, 3.9 minutes. TLS real-backend `authentication.spec.ts` and `inventory.spec.ts` were excluded by configuration; neither counted as passed/skipped.
- Final detail rerun after correcting the return-to-list label: same environment, `npx playwright test f4-resource-detail.spec.ts --workers=2 --output=../.cache/frontend-f4/browser-detail-final`: **37 passed, zero failed/skipped**, 1.7 minutes. Final unit/build results above were rerun after that label change.
- DE/EN at 1440/1024/768/390: seven canonical details, metadata and password accessibility, no page overflow. Fixture screenshots in ignored `.cache/frontend-f4/`; visually inspected German390 Product and English1440 User. Fixtures, not live operator screenshots. Existing list narrow Product-name issue remains separately deferred.

## Current-backend and manual limitations

Read-only inspection authenticated using the already supported local administrator and trusted local certificate at `https://localhost:19443`. No credential/token/operator record was copied into this evidence. User UUID detail returned 200 with `id/displayName/scope/tenantId/tenant/role/status/metadata/globalAccount`; account fields only `email/emailVerifiedAt`. Existing Category/Manufacturer/Product detail GETs returned 200 with accepted safe fields and readable Product associations. Tenant collection contained zero Tenants; Assets collection was empty. No concrete Tenant was available for Place/configuration detail checks. Non-contextual Place collection requests returned 404; no replacement aggregate endpoint was invented.

Live Email creation/reset/edit/password change, Place writes, provider PUT/DELETE/recreation and Asset/Merge actions were NOT performed. No suitable signed-Tenant/operator dataset/provider exists in this target. All destructive/write/browser evidence is intercepted; it does not prove backend authorization/SQL/concurrency. Deployed manual frontend remains its prior F3 build: F4 browser verification used the separate current-worktree fixture server, not a redeployment. Existing manual Docker environments remain running and unchanged.

## Preservation and remaining gates

Pre-F4 SHA-256 snapshot: ignored `.cache/frontend-f4/before.json`. All unrelated pre-existing tracked/untracked contents, F2/F3 and historical Phase A/B evidence preserved byte-for-byte. Existing accepted F2/F3 modifications are retained beneath F4 changes in shared files. Backend/OpenAPI/schema/migrations/deployment untouched; no runtime business mutation/data reset, staging, commit or push. HEAD remains `31b336eb47225556da56c73954e832bb615e2c0c`; index empty.

B1/D29 and B2/D28 remain BLOCKED / NOT AUTHORIZED. F5/F6, C/D, export/delivery, unsupported CRUD and390px Product-table wrapping are not implemented. STOP for human acceptance of F4 unblocked scope only.

## Final checks

`git diff --check`: PASS. All new-file whitespace checks and relative documentation links: PASS. Exact pre-gate hash comparison limits task changes to the16-file manifest above; all unrelated contents and historical/F2/F3 evidence unchanged. Backend/OpenAPI/migrations, `.idea` and unrelated deployment documentation unchanged against pre-F4. Final index empty; no commit. The isolated fixture server was stopped after verification; existing manual environments were left running.

Final `git status --short` (includes pre-existing accepted F2/F3 and unrelated work):

```text
 M docs/DEPLOYMENT_CLI.md
 M docs/INVENTORY.md
 M docs/plans/DEPLOYMENT_CLI_PLAN.md
 M docs/plans/FRONTEND_BACKEND_INTEGRATION_PLAN.md
 M frontend/src/app/Dashboard.tsx
 M frontend/src/app/Shell.tsx
 M frontend/src/components/ui/dropdown-menu.tsx
 M frontend/src/components/ui/searchable-combobox.tsx
 M frontend/src/features/inventory/pages.tsx
 M frontend/src/inventory.test.ts
 M frontend/src/inventory.ts
 M frontend/src/locales/de.json
 M frontend/src/locales/en.json
 M frontend/tests/api-foundation.spec.ts
 M frontend/tests/collection-query.spec.ts
 M frontend/tests/dialog-toolbar.spec.ts
 M frontend/tests/form-lifecycle.spec.ts
 M frontend/tests/list-filtering.spec.ts
 M frontend/tests/merge-combobox.spec.ts
 M frontend/tests/search-stability.spec.ts
 M frontend/tests/sidebar-layout.spec.ts
?? .idea/
?? docs/verification/FRONTEND_INTEGRATION_F2.md
?? docs/verification/FRONTEND_INTEGRATION_F3.md
?? docs/verification/FRONTEND_INTEGRATION_F4.md
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
?? frontend/src/collection-query.test.ts
?? frontend/src/resource-contracts.test.ts
?? frontend/src/resource-contracts.ts
?? frontend/tests/f2-paging-lookup.spec.ts
?? frontend/tests/f3-resource-query.spec.ts
?? frontend/tests/f4-resource-detail.spec.ts
```
