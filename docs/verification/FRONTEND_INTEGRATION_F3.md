# F3 — resource-specific applied queries and scoped counts

2026-10-12. **IMPLEMENTED / PENDING HUMAN ACCEPTANCE**. Owned findings: D04, D12, D13, D14, D15 only. Backend baseline remains `939c7373552d676822d0f9d0fe92798fa8618100`; current HEAD `31b336eb47225556da56c73954e832bb615e2c0c` on accepted Phase A/B commit `392f7dd4e59ad6e68bddb41735b0d3e2d31a0777`. Accepted F2 business work was already present in the uncommitted pre-F3 worktree and preserved. F2 business acceptance recorded in the plan on 2026-10-12; its evidence remains byte-identical. B1/D29 anonymous routing subset remains BLOCKED / NOT AUTHORIZED.

## Actual behavior and endpoint evidence

| Finding / resource | Implemented frontend contract | Executable backend evidence |
|---|---|---|
| D04 Dashboard | ADMIN reads `/api/v1/users?page=0&size=1&status=PENDING`; uses `totalElements`, never counts loaded rows. GLOBAL ADMIN concrete business context adds `tenantId`; aggregate omits it; TENANT ADMIN omits narrowing and relies on signed backend scope. Ordinary USER still makes no administrative User request. | `platform/security/UserDtos.query`, `identity/UserQuery`, `identity/auth/AuthenticationService.list` |
| D12 Categories/Manufacturers | q name, optional exact visibility; Name/Visibility header toggles send supported sort + direction. Default query omits sort/direction, preserving createdAt DESC backend default. | `platform/security/CatalogLabelDtos.query`, `catalog/CatalogLabelQuery` |
| D13 Products | q Name OR manufacturerNumber (compact Search label with explicit readable hint), visibility; optional Category/Manufacturer lookup criteria submit UUIDs; separate manufacturerNumber literal substring. Name/manufacturerNumber/Visibility header sorts only. | `platform/security/ProductDtos.query`, `catalog/ProductQuery` |
| D14 Locations/Owners | Existing explicit Name q; Name header sort asc/desc only. No note query/filter/sort. | `platform/security/PlaceDtos.query`, `inventory/PlaceQuery` |
| D15 Assets | Existing q/four sorts preserved, wire `tag` unchanged. Adds loanable and dedicated localIdentifier/serialNumber/externalInventoryNumber criteria. Five reference criteria use readable bounded comboboxes and authoritative IDs. GLOBAL aggregate can narrow Tenant by accepted Tenant UUID/name collection. | `platform/security/AssetController.all/list`, `AssetDtos.query`, `inventory/AssetQuery` |
| Scoped Users | DisplayName q, status, four supported header sorts. GLOBAL ADMIN additionally scope/role/Tenant narrowing. TENANT ADMIN has no scope/role/Tenant widening controls, transport excludes these criteria. Backend signed-Tenant/ordinary-USER restriction remains authoritative. | `platform/security/UserDtos.query`, `identity/UserQuery`, accepted Step 6 service authority |

Paths under `platform/security`, `identity`, `catalog`, `inventory` above refer to `backend/src/main/java/com/github/ksewen/kelios/`. Inspected executable DTO query contracts before implementation; no backend/API/OpenAPI changes.

Assets has no aggregate Place lookup endpoint. Location/Owner selectors therefore require the current concrete business Tenant or an explicitly selected GLOBAL Tenant narrowing. Opening an aggregate filter shows a localized explanation; choosing a Tenant supplies existing `/api/v1/tenants/{id}/locations|owners` lookups. The two new Asset/User Tenant criteria selectors append the complete UUID only for duplicate readable Tenant names (Tenant name uniqueness is not enforced by `V2__domain_foundation.sql`). They never resolve names into IDs or change the accepted Shell dropdown. Changing pending narrowing clears pending reference IDs without changing the applied query until Apply. TENANT principals receive no Tenant switching control. No invented `/locations` collection, manual UUID/name guessing or anonymous-provider discovery.

## Query, identity and boundary preservation

- List typing changes pending presentation state only. Filter button or Enter commits normalized criteria atomically with page0. Same criteria avoids a request; clear + Apply removes predicates. Reference candidate lookup remains immediate and incremental20; those candidate requests are distinct from collection-filter requests.
- Sort/page/size use applied criteria only. Sort direction toggles and sort changes reset page0; six accepted page-size controls remain. Server Page metadata/count/order is rendered unchanged. No production array sorting, filtering or slicing of collection results.
- Catalog and User query serializers have explicit resource allowlists, not a universal mapping/query engine. `normalizeCriteria` is only a small query-text trim helper, not a business-input or persistence normalization layer. Wildcards remain literal user characters; backend escaping/validation remains authoritative.
- Existing `useRead` latest-response/context boundary unchanged. Valid rows/semantic inputs/table nodes remain mounted; no remount keys, persisted query state or mutation auto-replay. Filter modal inputs are not dirty business forms.
- Existing User create/reset/password forms and Asset association rendering are still F4 integration work; intercepted lifecycle cases verify mounted-session behavior, not live Email administration compatibility.
- No F4 note/association/detail/Email mutation/credential UI, F5 Audit time/actor UI, F6, backend B1/B2, Phase C/D, export/delivery or Product 390px table-name wrapping work. Existing Asset flat-name presentation deficiencies remain F4; this task aligns queries, not row projection rendering.

## Exact F3 task manifest (against pre-F3 worktree)

1. `docs/plans/FRONTEND_BACKEND_INTEGRATION_PLAN.md` — F2 acceptance, F3 status/evidence.
2. `docs/verification/FRONTEND_INTEGRATION_F3.md` — this record.
3. `frontend/src/app/Dashboard.tsx` — scoped PENDING Page count.
4. `frontend/src/features/inventory/pages.tsx` — resource filters/sorts, scoped User criteria, readable Asset references, narrow query-input maximum/hint support.
5. `frontend/src/inventory.ts` — explicit Catalog/User query serialization and query-only normalization.
6. `frontend/src/locales/de.json` — Product search/hint and aggregate Place-filter context explanation.
7. `frontend/src/locales/en.json` — matching English strings.
8. `frontend/src/collection-query.test.ts` — 40 resource-query unit cases.
9. `frontend/tests/f3-resource-query.spec.ts` — 25 focused query/count/scope/stale/viewport cases.
10. `frontend/tests/dialog-toolbar.spec.ts` — expected new filter button; retain permission/geometry/focus/create assertions.
11. `frontend/tests/list-filtering.spec.ts` — Product query label; count Asset collection requests independently from newly available candidate lookup requests.

12. `frontend/tests/search-stability.spec.ts` — Product query-label selector only; table identity assertions retained.

No other pre-existing tracked/untracked file changed by F3. Prior F2 changes in overlapping files remain; full `git diff HEAD` contains those accepted changes and unrelated dirty documentation and is not the F3 manifest.

## Verification

Final commands/results are recorded after completion below. Commands execute from `frontend`, Node24 on PATH. All browser APIs intercepted; temporary Vite server only, no manual environment modification. No backend suite rerun was needed for frontend-only integration.

Focused unit command: `npm test -- --run src/collection-query.test.ts`.
Complete unit command: `npm test`.
Build command: `npm run build` (TypeScript and Vite).

Final Chromium command:

```sh
PLAYWRIGHT_BROWSERS_PATH="$PWD/../.cache/playwright" KELIOS_BASE_URL=http://127.0.0.1:12180 npm run test:smoke -- f3-resource-query.spec.ts api-foundation.spec.ts email-login.spec.ts f2-paging-lookup.spec.ts list-filtering.spec.ts collection-query.spec.ts merge-combobox.spec.ts form-lifecycle.spec.ts search-stability.spec.ts dialog-toolbar.spec.ts sidebar-layout.spec.ts administration-context.spec.ts --output=../.cache/frontend-f3/results-final
```

Tests prove supported query fields/directions, defaults, combined literal/UUID criteria; server-provided Z-before-A order; pending exclusion; page/size reset; clear/unfiltered; stale sort/page rejection; scoped counts91 with bounded items; TA controls/no forbidden Tenant fetch; aggregate Tenant narrowing/Place paths; unchanged authentication, capabilities, Page/incremental lookup, Merge, dirty exit and node/focus/scroll behavior. Frontend fixtures do not prove PostgreSQL ordering, counts or authorization.

DE/EN 1440/1024/768/390 checks cover Category, Manufacturer, Product, Location, Owner, Assets and Users. Filter modals are keyboard-operable; sort headers expose aria-sort; page-level overflow checks retained. Screenshots: `.cache/frontend-f3/{en|de}-{width}-{resource}.png` (56 captures; local ignored artifacts). No claim of physical-device/assistive-tool or manual business-data verification.

Earlier diagnostic runs: initial browser sandbox launch failed, then escalated Chromium ran; focused18 initially16 passed/2 failed from selecting hidden native `<option>` rather than visible listbox options; corrected selectors, rerun18 passed. Expanded focused23 passed. First broad176 had167 passed/9 failed from outdated toolbar/label assertions and counting candidate lookup requests as Asset collection requests. A focused toolbar11 run exposed two Product mobile input-width failures; corrected compact Search/Suche label with Name/manufacturer-number hint, without changing deferred table wrapping. Initial TypeScript JSX syntax failure corrected before successful build. A final duplicate-Tenant rendering addition caught two nullable-property type checks, corrected before the final successful build. The clean237-case suite passed before adding the two explicit duplicate-Tenant tests; the final expanded suite reruns that complete scope. A subsequent237-case run had235 passes/2 failures: one remaining obsolete Product label selector, corrected without changing assertions; and dynamic Merge locale import against the previously hot-updated fixture server. Inspection recorded timestamped application locale-module URL versus plain test import in `.cache/frontend-f3/vite-module-before-restart.txt`. Fresh-server Merge locale verification passed without production changes. Failed/overlapping executions are not counted as unique passes. Existing dev harness i18next and NO_COLOR warnings do not constitute a clean live-production assertion.

## Live/manual limitations and exclusions

Live/current-backend verification **NOT PERFORMED**. No configured current-backend URL, operator Email/password-file or TLS test context; no suitable account/data established. In particular, no operator-created Asset sorting dataset or manual server-order evidence manufactured. No bootstrap, credentials, seed, deployment, reset, operator writes or real Merge.

`authentication.spec.ts` and `inventory.spec.ts` excluded by existing configuration unless `KELIOS_TEST_TLS=true`: not executed or counted as skipped/passed. Other non-selected branding/localization/redesign suites not executed/countable. Chromium only. SQL/security assertions rely on accepted backend evidence, not frontend mocks. B1 routing and B2 Product error mapping remain separate; F4/F5 deficiencies remain explicit.

## Final executed results and preservation

| Verification | Exact result | Log |
|---|---|---|
| Focused query units | 40 passed / 1 file, zero failures/skips | `.cache/frontend-f3/unit-focused.log` |
| Complete frontend units | 142 passed / 7 files, zero failures/skips | `.cache/frontend-f3/unit.log` |
| Production build | PASS: TypeScript + Vite | `.cache/frontend-f3/build.log` |
| Final complete Chromium | 239 passed, zero failures/skips; includes25 new F3 cases | `.cache/frontend-f3/browser-complete.log` |
| Earlier clean full Chromium | 237 passed before two duplicate-Tenant cases; overlapping, not additional unique coverage | `.cache/frontend-f3/browser-clean.log` |
| Fresh Merge/search diagnostic regression | 34 passed; overlapping final239 | `.cache/frontend-f3/browser-fresh-focused.log` |
| Documentation links / whitespace | PASS | Final check below |
| `git diff --check` | PASS | Tracked worktree and new-file whitespace checked |
| Preservation | Exactly12 F3 files; every unrelated pre-gate tracked/untracked file unchanged; F2 evidence byte-identical | `.cache/frontend-f3/before.json`, `files-changed.txt` |

Final browser breakdown: administration35, API foundation18, collection-query1, dialog-toolbar31, Email20, F2 paging29, F3 queries25, form-lifecycle11, list-filtering8, Merge18, search-stability16, Sidebar27 =239 distinct selected cases. No summing repeat runs to inflate coverage. Query-unit40 are included in full142. Screenshot review included EN1440 Product filters and DE390 scrollable Asset filters; automated checks cover all seven resources/four widths/both locales.

Index remains empty; HEAD unchanged at31b336eb47225556da56c73954e832bb615e2c0c. No stage/commit/push/PR, deployment/reset/operator data, backend or later gate. Temporary fixture server stopped after verification. Full final status also recorded at `.cache/frontend-f3/final-status.txt`.

```text
 M docs/DEPLOYMENT_CLI.md
 M docs/INVENTORY.md
 M docs/plans/DEPLOYMENT_CLI_PLAN.md
 M docs/plans/FRONTEND_BACKEND_INTEGRATION_PLAN.md
 M frontend/src/app/Dashboard.tsx
 M frontend/src/components/ui/searchable-combobox.tsx
 M frontend/src/features/inventory/pages.tsx
 M frontend/src/inventory.test.ts
 M frontend/src/inventory.ts
 M frontend/src/locales/de.json
 M frontend/src/locales/en.json
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
?? frontend/tests/f2-paging-lookup.spec.ts
?? frontend/tests/f3-resource-query.spec.ts
```
