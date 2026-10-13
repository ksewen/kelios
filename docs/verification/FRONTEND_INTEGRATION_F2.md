# F2 — paging, User Page and bounded business lookup

2026-10-12. **IMPLEMENTED / PENDING HUMAN ACCEPTANCE — authorized business scope only.** D03/D10/D11/D22 business behavior and D20 business lookup implemented; anonymous routing subset BLOCKED by separately unauthorized B1/D29. No human acceptance implied. Committed baseline:392f7dd4e59ad6e68bddb41735b0d3e2d31a0777 then31b336eb47225556da56c73954e832bb615e2c0c. F0/F1a/F1b and U1/U2/Tenant-read decisions remain accepted/closed. Backend remains unchanged.

## Implemented contract

| Consumer | Actual F2 behavior |
|---|---|
| Categories/Manufacturers/Products/Locations/Owners | Explicit page0,size20; six size choices10/20/30/40/50/100; server Page metadata drives controls; pending Name remains unapplied |
| Assets | Same sizes/metadata; existing applied query/filter/sort preserved in every size/page request; no new sort/filter field or client sorting |
| Users administration | GET /api/v1/users?page=N&size=S decoded as Page<UserItem>; renders items, count and pager; selected row refresh reads data.items; no array compatibility |
| Audit | Existing applied criteria retained; explicit size20/six choices and response metadata; no new actor/date/order controls |
| Owning-Asset history | Explicit bounded20; returned metadata drives Next/Previous; no size selector, new filters or selectable sorting |
| Business entity references | Immediate server lookup,size20; scroll/ArrowDown continuation and accessible Load more append; no Filter/size selector/Previous-page replacement |
| Tenant collection / OIDC singleton | Remain unpaged/singleton, no artificial pager |
| Anonymous organizations routing | UNCHANGED25/manual paging; not part of completed scope |

Pager retains stable mounted controls/valid rows during reads. While an updated query is pending, an explicit page action uses current requested page/size and applied criteria; latest-response guards prevent obsolete sort/page results replacing it. Once settled, returned page/size/count controls boundaries. A beyond-end empty Page remains on its returned index/count, not silently clamped. Changing applied search/filter/sort/size resets page0; pending edits do not reset paging or leak into requests. No local slicing/filtering/sorting or persisted query state added.

Literal q uses trim; blank omitted; %, _ and backslash preserved/URL encoded, not interpreted client-side. Name/identifier filter and business combobox inputs have128 maxlength (normal browser UTF-16 input bound); backend validation remains authoritative. Business fields/passwords are not normalized by this helper. No speculative query framework.

Reference continuation is keyed by endpoint, signed session/context, normalized query, explicit refresh and reset state. New query/context starts page0; stale continuation cannot append to a different key. Duplicate UUIDs are removed within the same lookup append, preserving already rendered UUID nodes; no arbitrary business-list filtering. Next availability derives from server page metadata, not accumulated row length. Existing options remain selectable during same-query continuation; options belonging to an obsolete replacement query remain disabled. Controlled UUID, input query and selected-readable cache remain separate. Explicit reset clears local lookup state without React remount keys; parent form owns UUID reset. Empty/error/initial states remain distinct and ordinary refresh stays quiet. D21 independent detail-label hydration is not implemented here.

## Verification commands and results

Commands from frontend, Node24:

```sh
npm test -- --run src/inventory.test.ts src/api-contracts.test.ts
npm test
npm run build
PLAYWRIGHT_BROWSERS_PATH="$PWD/../.cache/playwright" KELIOS_BASE_URL=http://127.0.0.1:12180 npm run test:smoke -- f2-paging-lookup.spec.ts list-filtering.spec.ts collection-query.spec.ts merge-combobox.spec.ts form-lifecycle.spec.ts search-stability.spec.ts dialog-toolbar.spec.ts sidebar-layout.spec.ts administration-context.spec.ts
PLAYWRIGHT_BROWSERS_PATH="$PWD/../.cache/playwright" KELIOS_BASE_URL=http://127.0.0.1:12180 npm run test:smoke -- f2-paging-lookup.spec.ts
PLAYWRIGHT_BROWSERS_PATH="$PWD/../.cache/playwright" KELIOS_BASE_URL=http://127.0.0.1:12180 npm run test:smoke -- f2-paging-lookup.spec.ts -g 'pager/lookup responsive focus'
git diff --check
```

| Final check | Exact result |
|---|---|
| Focused units |19 passed,2 files |
| Complete units |102 passed,6 files |
| Production build |TypeScript noEmit + Vite passed |
| Broad Chromium |173 passed:35 administration/context,1 collection query,31 dialog/toolbar,26 initial F2,11 lifecycle,8 explicit list filtering,18 Merge,16 search stability,27 Sidebar |
| Final focused F2 |29 passed; includes3 additional deduplication/session/User-refresh cases |
| Final viewport rerun |8 passed; repeated cases, not additional distinct tests |
| Distinct browser coverage |176 cases across the final passing runs, not210 distinct cases |
| Diff/link/preservation |Passed; exact delta/preservation recorded below |

Zero final failures/skips. Authentication.spec.ts and inventory.spec.ts remain configuration-excluded without KELIOS_TEST_TLS=true; not executed or counted as passed/skipped. No backend suite rerun was necessary or claimed.

First broad attempt:173 executed,160 passed/13 failed. Causes: old size25/User-array fixtures, removed lookup Next selectors, broad regex matching a native20 option, and Asset-option selector matching fixed enum options instead of the listbox. Fixtures/selectors aligned to current contracts; lifecycle/stability assertions retained. Paging actions during pending reads were preserved rather than removing the stale sort/page regression. Final broad run173 passed. New focused cases29 passed and refined viewport captures8 passed. Early TypeScript JSX edit error was corrected before final verification. Logs: .cache/frontend-f2/{focused-unit.log,unit.log,build.log,browser-initial.log,browser.log,browser-focused.log,browser-visual.log}.

Tests verify sizes actually change requests;21/20 Next boundary; >3 User pages; empty/beyond-end metadata; combined applied criteria through size/page/sort; literal/blank q; no requests while list typing; retained table/input/size-select nodes; immediate Product/Asset/Merge lookup; mouse/keyboard UUID submission beyond first batch; append/dedup; selected UUID preservation; stale query/session rejection; current-option continuation safety; no Filter/selector in lookup; initial/empty/error and quiet refresh; User selection updates from Page refresh; form/confirmation/Merge/focus/Sidebar regressions.

## Browser/runtime evidence and limits

Local disposable Vite fixture server at12180 only, with every API intercepted; not deployment or live-backend verification. DE/EN1440/1024/768/390 pager and appended business-reference controls tested, no page-level overflow. Sixteen screenshots .cache/frontend-f2/{pager,pager-lookup}-{language}-{width}.png. Visually inspected German390 pager and English1440 appended list; after viewport capture refinement the pager is explicitly scrolled into view and appended rows are visible. Existing narrow Product-name wrapping remains deferred, not fixed or closed.

Live/current-backend list/wire verification NOT PERFORMED: no configured current-backend target/operator Email/password-file/TLS context. No bootstrap/deploy/account/data creation or reset to manufacture evidence. Fixtures do not prove backend authorization, SQL filtering/sorting/counts or real-provider interoperability. Chromium only; Safari/Firefox/physical assistive technology not verified. Existing dev harness i18next warnings are not claimed eliminated.

F3 resource query controls/Dashboard pending-user totals remain untouched (Dashboard keeps its earlier Page guard, not a new count implementation). F4 details/projections/note preservation/Email create/reset/password-policy work remains pending. Existing intercepted User-create lifecycle tests still exercise the currently unmigrated form; they do not claim compatibility with the accepted Email create API. B2/Product conflict mapping, PhaseC/D, export/delivery and unrelated UI remain excluded.

## B1/D29 boundary

Frontend auth.ts and OrganizationPicker.tsx are byte-identical to pre-F2. Existing public request size25, manual paging and150ms debounce remain honest current wire behavior; no frontend fallback/normalization/default change pretends B1 resolved. Backend/OpenAPI/schema/tests unchanged. Anonymous target20/normalized-query/incremental subset remains BLOCKED and NOT AUTHORIZED. Business lookup completion does not close it.

## Exact delta and preservation

Exact changed-file manifest: .cache/frontend-f2/files-changed.txt. This gate changes only inventory.ts/test, shared business combobox, pages.tsx paging/lookup/User Page, two locale keys, directly affected browser fixtures/tests, integration-plan status and this evidence. No generalized fetching/entity mapper, backend/auth/session/provider behavior or semantic remount keys introduced.

Pre-gate hashes verify all unrelated dirty work and historical verification evidence unchanged. No operator/manual business data, destructive operation, deployment/reset, staging/commit/push/PR. Temporary fixture server stopped after verification. Final git status --short: .cache/frontend-f2/final-status.txt. F2 business findings pending human acceptance; anonymous subset explicitly blocked. STOP.
