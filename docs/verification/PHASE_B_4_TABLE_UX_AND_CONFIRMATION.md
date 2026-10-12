# Phase B.4 — server-authoritative collections and confirmation UX

Status: scoped frontend implementation and capability audit verified; **Phase B remains open / not human accepted**. Phase A remains frozen. No Phase C, backend/API/schema/auth changes or persistent business-data creation is authorized/performed.

## A. Backend collection capability matrix

Inspected implementation, not inferred from screenshots. `ListInput.java:5–14` validates bounded zero-based `page` (default 0), `size` (default 25, maximum 100); `Page.java:3` returns `items,page,size,totalElements`. Existing UI derives Next availability from total/size, not a fabricated client slice. SQL filters and scoped counts precede ordering and LIMIT/OFFSET.

| Resource / collection endpoint | Paging / metadata | Current combined filters | Server sort / direction | One filter+sort+page query? | Current frontend / supported columns | Frontend-only final behavior? / mandatory gap |
|---|---|---|---|---|---|---|
| Categories `/api/v1/[tenants/{id}/]catalog/categories` | Real bounded page; totalElements | Name `q`, visibility GLOBAL/TENANT, deleted false/true/all; authorized scope | Fixed name ASC,id ASC; no selectable field/direction | Filters + fixed order + paging yes; selectable sort no | Name/Visibility; explicit Name filter + real Pager; no sortable headers | Partial only; mandatory selectable server Name/Visibility sort/direction |
| Manufacturers same path suffix `manufacturers` | Same | Same | Same fixed order | Same | Name/Visibility; Name filter + Pager | Partial; mandatory selectable Name/Visibility sort/direction. Manufacturer number is Product data, not a Manufacturer column |
| Products same path suffix `products` | Same | Same; no Category/Manufacturer/manufacturer-number filters | Same fixed order | Same | Name/Visibility/Manufacturer number; Name filter + Pager | Partial; mandatory supported column sorts/direction and useful combined reference/manufacturer-number filters |
| Locations `/api/v1/tenants/{id}/locations` | Real bounded page; totalElements | Name `q`, deleted; concrete authorized Tenant | Fixed name ASC,id ASC | Filters + fixed order + paging yes | Name; explicit Name filter + Pager | Partial; mandatory selectable Name order/direction, preserve Tenant scope |
| Owners `/api/v1/tenants/{id}/owners` | Same | Same | Same | Same | Name; explicit Name filter + Pager | Partial; same mandatory sort gap |
| Assets `/api/v1/assets`, `/api/v1/tenants/{id}/assets` | Real bounded page; totalElements | `q` searches tag/localIdentifier/serialNumber/externalInventoryNumber; lifecycle, condition, locationId, ownerId, categoryId, manufacturerId, productId, loaned; authorized context (aggregate narrowing handled by adapter) | `tag`, `createdAt`, `lifecycle`, `condition`; asc/desc; UUID same-direction tie-breaker | **Yes**, one SQL filter/count/order/page path | Tenant (aggregate), Tag, local identifier, Product, Location, Lifecycle, Condition, Loan recipient, Created at; four supported headers now sortable | Supported existing controls yes. Unsupported columns remain non-sortable |
| Users `/api/v1/users` | **Unbounded array**, no page/size/total contract | Authorized GLOBAL ADMIN all / same-Tenant ADMIN only; no collection filter contract | No selectable sort/direction | **No** | Display name/scope/Tenant/role/status; no fake Pager/filter/sort | **No**; mandatory bounded paging + combined filters/sort/metadata |
| Tenants `/api/v1/tenants` | Small authorized array; paging **not required** | GLOBAL visible set / TENANT own Tenant | Fixed ID order; no selectable sort/direction | Not a paginated query | Name; no invented search/paging | Retain; no mandatory parity backend work |
| IServ `/api/v1/tenants/{id}/oidc` | Single provider configuration; paging **not required** | Authoritative configured Tenant | Not a collection sort | N/A | Provider form, not data grid | Retain. Public organization login lookup is separate bounded routing-only search |
| Audit `/api/v1/audit-log` | Real bounded page; totalElements | action, targetType, targetId; GLOBAL ADMIN optional tenantId OR globalOnly; Tenant ADMIN fixed captured Tenant; no `q`, actor/date filter | Fixed occurredAt DESC,id DESC; no selectable sort/direction | Existing combined filters + fixed order + paging yes; selectable sort no | Action/actor display snapshot/target type/time; specialized explicit applied-filter object + Pager | Partial; mandatory configurable server order, useful combined actor/date/action/target/context filters and stable paging |

Sources: `CatalogController.java`, `CatalogService.java:160–169`; `PlaceController.java`, `PlaceService.java:63–69`; `AssetController.java`, `InventoryOperations.java`, `InventoryReads.java:25–42`; `AuditController.java`, `AuditReads.java:12–23`; `IdentityAdminController.java:17–26`, `AuthenticationService.java:60–63`, `TenantService.java:24–27`. Identity/session and target ownership remain separate. No backend test suite rerun is claimed for this frontend-only change; the capability audit inspected existing executable SQL and validators.

## B–C. Pagination requirements and mandatory deferred work

REQUIRED and present: Categories, Manufacturers, Products, Locations, Owners, Audit. Assets existing pagination preserved. REQUIRED and **missing**: Users. NOT REQUIRED currently: Tenants and IServ.

The main [repair plan](../plans/FRONTEND_UX_AND_FUNCTIONAL_DEFECTS.md#deferred-mandatory-collection-query-backend-work--phase-b4-product-gate) now records every required gap, product behavior, why frontend emulation is wrong, logical future backend contract and separate human approval gate. Audit's pagination is present; selectable sorting and useful actor/date multi-condition filtering are absent. Existing stable fixed order is not mislabeled as interactive sorting. Broad uniform table/column-filter migration stops at the backend gate; the existing honest explicit filters remain while supported Assets sorting is improved independently. No client sorting, fake pagination or speculative query parameters were added.

## D. Focus restoration

Button styling already used `:focus-visible`, not unconditional `:focus`. Modal restored focus programmatically with no input-modality policy. Pre-change Chromium inspection reproduced the expected keyboard Escape ring, but the inspected pointer Cancel sequence did **not** reproduce the reported pointer ring; this runtime limitation is explicit. The correction makes restoration deterministic: track pointer/keyboard interaction during the open Modal; annotate a pointer-restored target; Button suppresses only that annotated restoration outline until blur or keyboard interaction. Keyboard restoration retains visible focus. Focus remains restored without scroll reset; outlines were not globally removed. Regression covers pointer Cancel, keyboard Enter/Escape, pointer X after keyboard use and Tab/Shift+Tab focus.

## E. Quiet Preview

The shared Command substituted `ui.submitting` for every pending request, including advisory Merge Preview. Preview opts into a quiet read presentation: text remains localized Preview/Vorschau, disabled + `aria-busy` exposes pending state, and a synchronous in-flight ref prevents duplicate form dispatch. Errors still use existing error semantics. Preview pair-generation validation remains unchanged. Mutation pending text remains meaningful and is retained; search/read hooks were already quiet in B.3. No other ordinary preview Command was found.

## F. Pre-submit confirmation cancellation audit

False confirmation results were converted to `ApiFailure(CANCELLED)` and caught by the shared Command's error/recovery UI. The operation had never dispatched, so this was an incorrect warning.

| Flow | Disposition |
|---|---|
| Category/Manufacturer Merge execution | Return explicit private `noSubmission` sentinel on Cancel or invalidated pair; no request, no success/reset, no warning; same Merge state/preview/session remains |
| Catalog publication | Same no-op result on Cancel; no dispatch/error/reset |
| Asset deregistration | Same no-op result; note/reason/lifecycle remain in the active session |
| User disable | Same no-op result; no warning or request |
| Catalog/Place delete | Already returns before dispatch on Cancel; retained |
| Tenant create / User approval, role or credential commands / own-password / provider configuration | No redundant pre-submit confirmation cancellation branch; submission/dirty-exit behavior retained |
| Dirty exit, route/context/logout discard | Separate abandonment policy; rejecting discard continues session, confirming clears write state; unchanged |
| Dispatched request failure/uncertain mutation | Existing error/reload/recovery semantics retained; not converted into no-op |

The sentinel is private frontend control flow, not an API code or exception suppression. Success callbacks and state cleanup run only for actual success. Entity reference lookup remains immediate-search.

## G. Candidate popup and first X

**CONFIRMED production event trace**: X pointer-down/mousedown at y=136, Close top=121.5 with listbox present; input focusout closes the inline listbox; the vertically centered Dialog shrinks and moves Close top to 176.5; mouseup targets a DIV and click targets BODY. No discard dialog opens. This proves a reflow between down/up, not dirty-guard bypass.

The existing semantic Dialog.Close prevents mousedown's focus-transfer default so the input blur cannot move the close target before its click dispatch. Click reaches the existing close/discard policy. No new close handler, modal or arbitrary position offset. Keyboard Escape remains nested: popup first, then dirty form; button keyboard activation remains intact. Regression rejects discard with same input/session, then confirms first-X discard.

## H–I. Table controls implemented / deliberately deferred

Assets headers for Tag, Created at, Lifecycle and Condition now update supported backend sort/direction; `aria-sort` describes current order and keyboard buttons expose controls. Added existing Created at data column so default createdAt sorting has a real header. Removed detached sort/direction selects. Rows renders server response order unchanged (test deliberately returns Z before A to detect client sorting).

All sort/page requests use the APPLIED filter snapshot; pending typed values stay pending. Sort changes reset page; page changes keep sort and applied filters. Filter Apply combines current pending query/reference fields atomically with current sort and resets page. Latest-response and context/resource guards are untouched. Assets `q` is now honestly labeled Asset identifiers / Asset-Kennungen, not Name.

Broad column-associated filter presentation is **deferred**, together with the coherent final multi-resource table contract. Existing explicit Name filters and specialized Audit filter remain functional/honest. Users does not receive invented filter, sorting or paging controls. Categories/Manufacturers/Products/Locations/Owners fixed server order has no interactive sortable header. Tenants and IServ remain intentionally structurally different. This is a reported B.4 backend gate, not a claim that the final table architecture is complete.

## J. Verification and runtime evidence

| Check | Final result |
|---|---|
| `npm test` | **51 passed**, 4 files; 0 failed |
| `npm run build` | TypeScript and Vite production build passed |
| Focused Chromium matrix | **112 passed, 0 failed, 0 skipped** (collection-query, list-filtering, merge-combobox, dialog-toolbar, form-lifecycle, search-stability, sidebar-layout) |
| Strengthened final filter/Preview assertions | **26 passed, 0 failed, 0 skipped**, rerun of list-filtering + merge-combobox; not 26 additional distinct tests |
| Existing real nginx routing regression | **17 HTTPS + 17 HTTP checks passed**; application routes 200 without redirects; JS/CSS correct; missing static file 404 |
| `git diff --check` | Passed |

`inventory.spec.ts` and `authentication.spec.ts` remain excluded by the non-TLS fixture configuration. They were **not executed**, and are not counted as passed or skipped. No real-backend write suite was enabled against operator data. Initial extra Sidebar-suite failures were obsolete Asset search/action selectors (`searchAssets`, `registerAsset`) from before B.2/B.3; updated only those selectors to the honest identifier label and accepted concise Create action. All original authorization/identity/mobile assertions remain. The final full matrix is green. Earlier premature viewport captures were rejected during visual QA and replaced after waiting for application/header readiness; no blank captures are claimed as visual proof.

Automated tests explicitly cover request-parameter sort toggling, server response order (Z before A), applied/pending separation, page reset, stale sort/page replies, combined lifecycle/condition/loaned Apply, quiet reads, initial/empty/error states, input/table/row identity, focus, context boundaries, immediate reference search, paging, duplicate names, Merge cancellation and first X/Escape. Added cancellation checks for publication, User disable and Asset deregistration. Pending Preview label/width/aria-busy, duplicate guard and error recovery are checked with held deterministic requests.

### Actual production inspection

Frontend-only redeploy of `kelios-local-6a18b7783357`; backend/PostgreSQL container IDs and images are unchanged from B.3. All three containers healthy. Captured **56** ready-rendered page/viewport combinations: Manufacturers, Products, Assets, Users, Tenants, Audit, IServ context prompt × English/German × 1440/1024/768/390. No page-level horizontal overflow. Assets table can scroll horizontally on narrow screens; supported header controls remain accessible. IServ in Global context correctly requests a concrete Tenant; no Tenant/provider was fabricated. Existing Product Name cells wrap heavily at 390px; this pre-existing narrow-column presentation remains a visual limitation, not a claim of a completed new table design.

Both languages: pointer Cancel restores Create focus with computed outline `none`; keyboard Enter/Escape restores focus with `solid` outline. First X with an open Merge listbox opens discard; rejecting preserves the exact input DOM node. Escape closes only popup. Advisory preview using existing operator Categories succeeded once per action, with Preview/Vorschau label. Final execution confirmation Cancel left source/target (`Beamer` → `Notebook`) intact, no alert and **zero mutation requests**. No real Merge execution occurred. Production sort request contained `page=0,size=25,sort=tag,direction=asc` while pending identifier input remained `Unapplied`, and did not send that pending text; `aria-sort=ascending` was observed.

`runtime.json` records **writes=[]**, **errors=[]**. Backend-read state and B.2 dirty-exit behavior remain covered by the fixture suites; business write paths use intercepted responses only. No database reset, persistent fixture creation, operator-data mutation or business deletion. Environment remains running.

### Evidence / preservation / limitations

[Evidence directory](phase-b4/): ready-rendered screenshots, `runtime.json`, redacted pre-fix pointer trace (`before-runtime.json`), `container-identities.txt`, frontend unit/build logs, `chromium.txt`, strengthened-test log, HTTP/HTTPS routing results, final Git status and preservation checks. Production loaded asset names are recorded in `runtime.json`; frontend image is `sha256:0919ee8a43c173411e73a5f976de4873a452ec45103d2a40926d4ef2836ddff7`.

Pre-B.4 hashes confirm backend, deployment/nginx B.3 fix, authentication, Phase A source and previous evidence unchanged. Main plan Issue A accepted section remains byte-identical; only a plan-status clarification and mandatory collection backlog were added. Sidebar regression changes are selector maintenance only, not weakened assertions or Sidebar behavior changes. All prior uncommitted work preserved; no staging/commit/push/PR.

The development fixture server emitted i18next-instance warnings during test harness runs; the passed identity/localization assertions and production German/English inspection are recorded separately. No claim that all development console warnings were eliminated is made. Chromium is the verified browser. Firefox/Safari and physical assistive technology were not verified. Real IServ interoperability remains separately manual pending. Real Tenant-specific provider/Asset data workflows were not fabricated; fixture verification establishes those boundaries. Broad table-column filters and missing backend contracts remain required future work; Phase B is not declared accepted/closed.
