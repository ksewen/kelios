# Phase B.3 — Explicit list filtering and SPA routing

Status: implemented; human acceptance pending. Phase C and Audit actor work have not started.

## Final interaction contract

**List filtering:** edit pending filter fields → activate Filter or press Enter → atomically apply filters and reset paging → reconcile the newest response. Typing alone does not request data, reset paging, navigate, or register unsaved business changes. Clearing Name and applying returns the unfiltered collection. Reapplying the same Name is a no-op. Pending/applied Name objects allow later fields without implementing speculative API contracts.

**Entity-reference lookup:** typing still immediately queries bounded server candidates. No Filter button exists inside a lookup. UUID, selected label and query remain separate; retained options remain disabled during pending/error states. Existing latest-response and resource/session/context isolation checks remain unchanged.

Valid same-context read data remains mounted during updates. Initial Skeleton/loading, empty results and request errors remain meaningful. Write-session/discard logic is unchanged. Ordinary refresh has no visible status row or reserved status gap.

## A. Updating-status audit

| Location | Classification and disposition |
| --- | --- |
| `pages.tsx`: Assets, shared Management (five resource lists), Audit, Tenant list | Ordinary background refresh: removed `Updating` component and its calls. |
| Asset history paging | Ordinary read refresh: removed the same visible announcement. |
| Public `OrganizationPicker.tsx` | Removed only the ordinary refresh paragraph. Retained initial Skeleton, 150 ms debounce, disabled pending organizations, errors, opaque provider routing and authorization independence. The authenticated operating-context selector was not changed. |
| `searchable-combobox.tsx` | Searching text now represents **initial loading with no available data**, not ordinary refresh. `Pick` supplies `initialLoading` from the actual read state; an already-loaded empty page also refreshes quietly, retaining its empty-state row during the request. `aria-busy` and disabled stale options remain. |
| `redesign.updatingResults` in both locales | Dormant translation retained for absence assertions in existing regression tests; no production rendering reference remains. |
| Mutation submission/progress, errors, empty states, initial loading | Retained. No blanket deletion of loading translations or mutation feedback. |
| Dashboard and other frontend search/status renderers | No additional ordinary-refresh announcement found. No new hidden live announcement introduced. |

## B. List/backend capability audit

| Page | Existing contract | B.3 disposition |
| --- | --- | --- |
| Categories / Manufacturers / Products | Global or concrete-Tenant `/catalog/{kind}`; `q`, zero-based `page`, bounded `size`; name search, fixed name/UUID order. API also supports visibility/deleted filters, not added here. | Explicit pending/applied Name filtering in shared Management. |
| Locations / Owners | Concrete-Tenant collection; `q`, zero-based paging, fixed name/UUID order; existing deleted filter. | Same explicit Name filtering, existing concrete-Tenant and capability requirements retained. |
| Assets | Global or concrete-Tenant collection; `q`, paging, sort/direction, lifecycle/condition/loaned and reference UUID filters. `q` matches Asset Tag/local identifier/serial/external inventory identifier, rather than an invented Asset name. | Explicit Name-labelled query input using the existing `q` contract. Additional filter fields are pending until Filter; the existing filter modal can also apply them. Its opener is labelled More filters / Weitere Filter to distinguish it from the Filter apply action. Sorting remains an explicit select interaction using **applied** filters. No pending Name leaks into a sorting request. |
| Audit | Action, target type, target ID, privileged Tenant/global-only filters; fixed time/UUID order. Backend explicitly rejects text `q`. | Explicit apply for existing specialized fields. No fabricated Name search. Tenant/global-only changes remain retention boundaries enforced by `useRead`. |
| Tenants / Users | Existing array collection endpoints without supported text-search/paging UI. | Retained; no invented client-only/server Name filtering. Tenant refresh announcement removed as above. |
| IServ administration | Single configured Provider for a concrete Tenant, not a searchable provider table. | Retained; not a list-search interaction. |
| Dashboard, details/history, account/security forms | Specialized read views or mutation forms, not Name-filtered entity collections. | Retained; no speculative filtering controls. |

Evidence: `CatalogController`, `PlaceController`, `AssetController`, `AuditController`, `InventoryOperations`; `CatalogService` lines 160–169, `PlaceService` lines 63–69, `InventoryReads` lines 25–42, `AuditReads` lines 12–23, and `ListInput`. No backend/API/schema changes were necessary.

The compact toolbar uses an inline semantic Name label/input form followed by Filter, then authorized Create/Merge actions. The input flexes into remaining width; CSS wraps on smaller screens. Enter submits the filter form once. Mutation dialogs remain separate forms/Portals; query state never marks them dirty. No empty action placeholders, JavaScript viewport calculations, router changes, or new dependencies were added.

## C. Entity-reference audit

| Lookup | Result |
| --- | --- |
| Category/Manufacturer Merge source and target | Immediate `Pick` search retained; preview/execution semantics unchanged. |
| Product Category and Manufacturer | Immediate `Pick` search retained; UUID/paging/latest-response regression coverage retained and extended to both resources. |
| Asset Category, Manufacturer, Product, Location, optional Owner | Same immediate adapter; added explicit automatic-query coverage for every reference. |
| Asset reassignment/location/owner actions | Same unchanged `Pick` search adapter and authorization; no Filter action introduced. |
| Public organization discovery | Its intentional 150 ms debounce retained. Not a business list filter. |
| Tenant operating context / enums / status selects | Not entity-reference search; unchanged. |

## D. Direct `/assets` root cause and repair

Confirmed against the repository-managed production nginx server before editing:

- `GET https://localhost:18443/assets` returned **301**, `Location: https://localhost:8443/assets/`.
- The served root contains Vite's physical `/usr/share/nginx/html/assets` directory.
- Both nginx server templates used `try_files $uri $uri/ /index.html`. The `$uri/` directory check selected the directory before the SPA fallback. nginx then canonicalized its missing trailing slash using the internal TLS listen port 8443.

The minimal repair removes **only `$uri/`** from `location /` in `frontend/docker/default.conf` and `deploy/nginx/phase2-tls.conf`:

```nginx
location / { try_files $uri /index.html; }
```

Dedicated `/assets/` file serving, immutable caching, missing-file 404s, same-origin API proxying, HTTPS/cookie/Origin/CSRF security, runtime branding, ports and application routes are unchanged. No port hard-coding, React workaround, CLI redesign or authentication fallback was introduced.

## E. Verification

- `npm test`: **51 passed**, 4 test files, 0 failures.
- `npm run build`: passed TypeScript and production Vite build.
- Focused Chromium suites: **76 passed**, 0 failures, 0 skips. Files: `list-filtering`, `dialog-toolbar`, `form-lifecycle`, `merge-combobox`, `search-stability`, plus requested `inventory` argument. The real-backend `inventory.spec.ts` remains **excluded by configuration** in this non-TLS intercepted-fixture run; its unexecuted tests are not counted as passes or skips.
- Tests cover no typing requests, click/Enter apply, clear/apply, same-filter no-op, applied paging/reset, advanced Asset filters, latest-response ordering, retained table/input/row/scroll identity, quiet refresh, separate Audit apply, immediate reference queries, disabled stale options, bounded paging, keyboard/focus, Merge preview/confirmation and B.2 dirty exits/reset/failure behavior.
- `scripts/test-frontend-routing.py`: **17 checks passed on HTTPS** and **17 on HTTP** against the real nginx server. It rejects route redirects, compares SPA documents, verifies JS/CSS MIME/content, and asserts missing static files remain 404. TLS trust bypass is explicitly restricted to loopback **test invocation**; authenticated runtime security is unchanged.
- `git diff --check`: passed.

An initial sandbox-restricted Chromium launch was not a product result. A development run recorded 72 passes/1 initial-load failure while source was changing through HMR. A subsequent run passed. Another run recorded 75 passes/1 locale-harness failure: the application imported `/src/i18n.ts?t=1791727363281`, but the test imported `/src/i18n.ts`, creating a separate instance in the warm HMR module graph. Restarting Vite clears that test-only module mismatch; no production i18n or assertion was weakened. The final clean-server 76-test matrix passed without failures. Production inspection scripts were corrected to respect the open combobox's Escape handling; those script retries are not counted as application-test passes.

## F. Production review and evidence

Environment: `kelios-local-6a18b7783357`, existing HTTPS URL `https://localhost:18443`. Only the frontend was rebuilt/recreated; backend and PostgreSQL container identities remained unchanged. No database reset, seed, persistent business creation, real Merge execution or operator-record mutation was performed.

Production screenshots and measured read-only results are in [phase-b3/](phase-b3/). See `runtime.json` for German/English widths 1440/1024/768/390, toolbar geometry/overflow, request counts, retained DOM, dirty-form observations, route results and console errors. See `baseline-check.json` for pre-B.3 preservation checks, `build.txt`, `unit.txt`, `browser.txt`, `routing.txt`, `routing-http.txt`, and the redacted pre-repair nginx/redirect evidence. Asset/image identity and the exact B.3 file delta are recorded separately in that folder.

Observed production results: both languages at all four widths had no horizontal page overflow. Typing Product Name generated **0** list requests; applying Filter generated **1**. Input and table nodes were retained, and no updating text rendered. Rejecting Product discard retained the same input/session; confirmed discard followed by reopen yielded an empty Name. All 12 recorded direct routes and their browser refreshes served HTTP 200 with the authenticated shell. An unauthenticated direct `/assets` visit displayed normal login before successful HTTPS authentication. There were zero uncaught page exceptions and zero business writes. Console network errors were limited to the expected initial unauthenticated refresh 401 and the deliberately nonexistent detail/history 404s.

Locations/Owners production review in a concrete Tenant context remains pending: the environment has no selectable operator-created Tenant. Their responsive/permission/filter behavior is automated with intercepted fixtures. The representative production detail deep link uses a nonexistent UUID and verifies the authenticated route/error view, without creating an Asset. No claim of a populated real Asset detail workflow is made.

A production inspection pointer click on Merge X while its inline candidate popup was open did not complete the exit. Escape to close the popup followed by X correctly prompted discard. The cause of that first-click interaction is not established; no additional focus/layout repair was made under B.3. This remains a Phase B review item, separate from the proven list/request and routing repairs.

Chromium was verified; Safari/Firefox and physical assistive technology were not manually verified. Real IServ interoperability remains separate/manual. Phase A sources/tests/evidence and earlier B evidence were preserved. Phase B remains open for human review.

## Git and change attribution

Branch `main`, unchanged HEAD `7872f5e192d6cf9c6e19c98cb99df07166699b9f`; index empty. See [exact B.3 manifest](phase-b3/file-manifest.json) and [full current status](phase-b3/git-status.txt). Pre-existing implementation and historical evidence were preserved; nothing was staged, committed, pushed or opened as a PR. The manual deployment remains running.
