# F6 — Combined integration acceptance evidence

Status: IMPLEMENTED / PENDING HUMAN ACCEPTANCE for completed D27 audit/test work only. Combined integration acceptance is BLOCKED by confirmed F6-A01 below; production correction requires separate authorization. This is not unconditional integration closure. B1/D29 anonymous routing and B2/D28 exact Product optimistic-conflict feedback remain BLOCKED / NOT AUTHORIZED. F5/D16/D17 acceptance recorded as 2026-10-13; its evidence/results/runtime limitations remain byte-identical. F4 unblocked manual acceptance remains recorded separately.

## Baseline and conclusion

Backend baseline939c7373552d676822d0f9d0fe92798fa8618100; current HEAD31b336eb47225556da56c73954e832bb615e2c0c. Current worktree includes accepted Phase A/B and F0–F5 modifications. Backend source is unchanged. Backend's accepted775-test result is historical, not a new F6 run.

A confirmed production integration defect was found: F6-A01, missing labels for two supported Audit actions. It was NOT fixed; no production correction is permitted by this F6 contract. F6 corrected test fixtures only, added four fixture-contract units, and synchronized this plan/evidence. Original acceptance history remains unchanged; a narrow correction touching F4-to-F5 Audit presentation must be separately approved before combined acceptance. Production frontend/backend/API/schema code remains unchanged against the pre-F6 worktree. Passing frontend fixtures are not proof of backend SQL/security; available live checks and unavailable evidence are distinguished below.

## Confirmed defect F6-A01 — supported administrative Audit actions lose their identity

**CONFIRMED / MEDIUM / FRONTEND-INTEGRATION.** Backend AuthenticationService.java:146 records GLOBAL_EMAIL_CHANGED for the approved Email command; OidcConfigurationService.java:78 records OIDC_CONFIG_DELETED for physical DELETE. Both keys are absent from frontend/src/locales/en.json and de.json enums. frontend/src/i18n.ts:35–39 treats action as a machine field and returns ui.unavailable for keys absent from en.enums. Audit list/detail in pages.tsx use that formatter. Result: both operations appear as **Unavailable** in English and **Nicht erfasst** in German, rather than identifiable operation labels. Querying and persisted action/actor/Tenant history remain correct; no security/data-integrity failure is inferred.

A read-only browser formatter diagnostic against the current fixture server confirmed all four code/language outputs. This is not a newly passing regression or live operator Audit record; deterministic source/formatter evidence suffices. An initial diagnostic attempt found the previously stopped isolated server unavailable; it was retried only on the separate fixture server, not the manual deployment. No business records created.

Affected accepted scope: F4 D02 Email command and D08 physical DELETE outcomes exposed through F5 D16 Audit presentation; shared F0 localization foundation. Existing i18n action-list test and Audit fixtures omitted these new backend actions, so green suites do not prove complete supported-action coverage. **No production locale/source changes made.**

Smallest proposed separately approved correction: add these two enum labels in both DE/EN bundles; extend known-supported-action unit coverage and intercepted Audit list/detail regressions for both codes. Keep generic unknown-code fallback, backend writers/authority/history, B1/B2 and Phase D unchanged. Preserve prior accepted reports and add focused correction evidence. STOP for human approval before this repair.

## Fixture/API audit and corrections

Actual contracts checked against frontend/src/api-contracts.ts and current backend AssetDtos, AuditDtos/AuditQuery, TenantDtos, identity administration and authentication contracts, plus unmocked HTTP results.

| Area | Finding/disposition |
|---|---|
| User collections | Ordinary browser consumers use Page.items/page/size/totalElements. Removed an obsolete users[] response in excluded inventory fixtures. Corrected GLOBAL User fixture ownership to tenantId/tenant null; TENANT remains signed-own. Approval fixtures return User State, not only{id}. |
| Email | Removed remaining login request-field payloads in excluded authentication/inventory tests; current Email-only DTO used. Updated their credential Email selectors/values where directly affected. These write suites remain unexecuted, not certified. |
| Asset responses | Added explicit typed Item/Detail/State fixtures with nested ID/name associations on reads and distinct flat command State. No obsolete flat*Name HTTP fields. Private assetView aliases and localization tests mentioning categoryName are intentionally view-layer tests, not transport fixtures. |
| Metadata | Typed complete metadata; ordinary actor display-name fields null, matching actual SystemActor-only projection. Corrected reserved seed UUID00000000-0000-0000-0000-000000000003 and exact KELIOS system/seed label in the representative F4 fixture. No fabricated app_user lookup. |
| Catalog | Added current label projections/visibility/ownership/metadata to older dialog and stability fixtures; visibility TENANT agrees with authoritative Tenant projection. |
| Tenant authorization | Existing dynamic/scope fixtures now deny TENANT collection, deny inactive/USER sensitive administration, and preserve signed-own detail/foreign404 behavior. GLOBAL ACTIVE ADMIN/USER Tenant read exception remains. TA foreign OIDC returns404. No production authorization change. |
| Create responses | Category/Manufacturer/Product/Place201{id}; Tenant200 JSON UUID; Asset201 State; User200 State; other command empty success kept distinct. No blanket200{id} create fixture. |
| Paging | Ordinary defaults0/20, requested size and metadata remain explicit. Removed business size25 and omitted Page metadata in excluded fixtures. Scroll fixture uses20 rows for size20 while preserving its scrolling assertions. |
| Routing25 | Auth organizations25 is an explicit current public-routing request/response, not an ordinary omitted-size default. Intentionally retained under B1/D29; no claim of20/normalization closure. |
| Detail/unknown routes | Merge reference detail fixtures now provide the independently authorized DTO rather than an array fallback; unknown routes404. No client compatibility fallback introduced. |
| Sort tests | Server-sentinel order tests deliberately verify that frontend does not rearrange responses. They are not SQL sorting proofs. |
| Audit/business history distinction | Replaced invented ASSET_REGISTERED/ASSET Audit rows in F2/F5 fixtures with actual CATEGORY_CREATED/CATEGORY administrative rows. Asset REGISTERED remains business history, not a duplicate administrative Audit action. |
| Historical partial fixtures | Small UI-focused fixtures can omit unused attributes; representative typed/current resource fixtures and live field checks provide contract coverage. This is not a strict JSON-schema certification of every historical test. |

backend-fixtures.ts is test-only and resource-specific: metadata, Tenant/Catalog label and explicit Asset projections. No production generic mapper/fetch/query framework added. Existing assertions were retained; scope-invalid data is deliberately invalidated, not kept to make node tests green.

## Combined invariants verified

| Boundary | Evidence |
|---|---|
| Email/session | Exact Email payload, case handling, refresh/logout, memory-only tokens, safe errors, pending/disabled and no mutation replay: auth units/Email browser suite; live existing-account mixed-case login/refresh/logout. |
| Provider identity/routing | Explicit unknown-provider selection and signed pending identity fixtures; UUID session identity independent of label/context. External issuer/subject matching is backend authority; no real paired-provider login/JIT evidence available. |
| Authorization/context | GA/GU/TA/TU/status request matrix, own/foreign Tenant/detail, no sensitive USER reads, demotion/stale responses and context selection; live GA only, not a newly created lower-role principal. |
| Collections | Applied criteria, six sizes/default20, scope/paging bounds, actual server result order, query-specific fields, totals and pending isolation across User/Catalog/Place/Asset/Audit. Live all six available paged endpoints default0/20 when omitted. |
| Detail/projections | Independent canonical UUID details/direct entry/refresh, readable associations and selected labels, note/account/metadata projection. Live User/Catalog/Product/Audit detail; no live Tenant/Place/Asset/OIDC instance. |
| Forms/commands | Success/discard clear; failed/rejected discard same nodes/input; guarded Owner note, Location edits, Email/password, OIDC DELETE/Cancel/recreate and unknown outcome. All writes intercepted. |
| Merge/combobox | Immediate bounded candidates, append/pending/UUID safety, keyboard/focus, same-dialog preview, obsolete pair rejection, quiet duplicate protection, first X/nested Escape and zero-request confirmation cancellation. No real Merge. |
| Time/history | Client-local display, optional inclusive absolute UTC bounds, DST23/25-hour/midnight transitions, local loan Instant, owning-Asset immutable history query. Intercepted loan only. |
| Rendering/Phase A/B | Semantic nodes/keys, focus/selection/scroll, same-context table preservation, role/Tenant boundary clearing, locale/sidebar/Home and four widths. No layout refactor or Product wrapping repair. |

## Exact F6 file manifest

1. docs/plans/FRONTEND_BACKEND_INTEGRATION_PLAN.md
2. docs/verification/FRONTEND_INTEGRATION_F6.md
3. frontend/src/fixture-contracts.test.ts
4. frontend/tests/backend-fixtures.ts
5. frontend/tests/administration-context.spec.ts
6. frontend/tests/authentication.spec.ts
7. frontend/tests/dialog-toolbar.spec.ts
8. frontend/tests/f2-paging-lookup.spec.ts
9. frontend/tests/f3-resource-query.spec.ts
10. frontend/tests/f4-resource-detail.spec.ts
11. frontend/tests/f5-audit-time.spec.ts
12. frontend/tests/form-lifecycle.spec.ts
13. frontend/tests/inventory.spec.ts
14. frontend/tests/merge-combobox.spec.ts
15. frontend/tests/search-stability.spec.ts

## Verification results

Commands in frontend use Node24 on PATH. Intercepted baseURL http://127.0.0.1:12180; PLAYWRIGHT_BROWSERS_PATH="$PWD/../.cache/playwright". Separate fixture server, no deployment update.

- `npm test`: **194 passed / 10 files, zero failed/skipped**. Four new fixture-contract tests; all accepted auth/query/time/projection/error/localization units included.
- `npm run build`: **PASS**, TypeScript + Vite. Rerun after fixture changes.
- Complete intercepted command: `KELIOS_BASE_URL=http://127.0.0.1:12180 PLAYWRIGHT_BROWSERS_PATH="$PWD/../.cache/playwright" npx playwright test f5-audit-time.spec.ts f4-resource-detail.spec.ts f3-resource-query.spec.ts api-foundation.spec.ts email-login.spec.ts f2-paging-lookup.spec.ts list-filtering.spec.ts collection-query.spec.ts merge-combobox.spec.ts form-lifecycle.spec.ts search-stability.spec.ts dialog-toolbar.spec.ts sidebar-layout.spec.ts administration-context.spec.ts --workers=2 --output=../.cache/frontend-f6/browser-final-all`: **299 passed, zero failed/skipped**, 4.2 minutes. Earlier combined299 passed too; counts are not added as distinct coverage.
- Final permissible Audit-fixture correction rerun: F2 + F5 **52 passed**, zero failed/skipped, after replacing nonexistent administrative Asset actions with real Category actions. These overlap the combined suite; the299 run preceded that final test-data-only replacement. No assertion was weakened.
- Intermediate focused fixture reruns: Merge/forms/dialog/admin/F3/search **136 passed**; F2 paging/lookup **29 passed**; admin request matrix **35 passed**. Same cases later covered by final299, not additional unique tests.
- Read-only actual deployed branding: `KELIOS_BASE_URL=https://localhost:19443 KELIOS_TEST_TLS=true KELIOS_EXPECTED_BRAND=green PLAYWRIGHT_BROWSERS_PATH="$PWD/../.cache/playwright" npx playwright test runtime-branding.spec.ts --output=../.cache/frontend-f6/browser-runtime-branding-final`: **6 passed**, zero failed/skipped. Tests cover real mounted logo/palette/proxy plus browser-intercepted config/logo failure variants. Initial incorrect blue expectation produced5 passed/1 failed; public configuration confirmed existing green palette. Corrected test invocation, no code/environment change or weakened assertion. Green checked, no deployment/theme-switch matrix fabricated.
- Routing: `SSL_CERT_FILE="$PWD/.cache/manual-inspection-20261013/keys/certificate.pem" python3 scripts/test-frontend-routing.py https://localhost:19443`: **17 passed**; no internal-port redirect, static JS/CSS valid, missing asset404, SPA routes/deep link serve correctly. Read-only existing server, not Vite fallback.
- DE/EN1440/1024/768/390 included in the combined suites: login/context/query/detail/time/modal/sidebar keyboard and overflow checks. Fixture screenshots are ignored cache artifacts; no operator business screenshots. Actual screenreader and other browser engines not tested.

### Exclusions/unexecuted tests

Default configuration excludes authentication.spec.ts and inventory.spec.ts unless KELIOS_TEST_TLS=true. They were NOT run and NOT counted as passed/skipped; only their directly identified DTO/fixture debt was corrected. For the branding-only live invocation TLS=true selected only runtime-branding.spec.ts, not those suites.

ui-redesign.spec.ts and phase4-localization.spec.ts were not selected: they create real Tenant/Asset data. Their historical write/selector/draft-persistence assumptions require isolated-suite modernization/verification before future use. Some legacy real inventory create/detail selectors also remain uncertified. This is stale test/coverage debt, not a confirmed accepted production defect or authority to mutate manual data. Runtime branding was selected separately and its six actual outcomes recorded. No unexecuted suite is labeled skipped/passed. Backend Maven/PostgreSQL suite was not rerun.

## Current-backend evidence

Existing supported local GLOBAL ADMIN, trusted local certificate, https://localhost:19443. Credentials/tokens/actual record names/UUIDs not copied here. Final HTTP script executed **28 API requests plus CSRF acquisition**, with no business mutation:

- Mixed-case Email login200; refresh200 with identical authoritative identity; own-session logout200; anonymous User collection401.
- User Page200 total1; Category200 total2; Manufacturer200 total2; Product200 total1; Assets200 total0; Audit200 total4. All responses items/page/size/totalElements, explicit20.
- Omitting page/size on each of those six endpoints gives page0/size20, not25/unpaged.
- Available User/Catalog/Product/Audit UUID detail200, safe actual field projections; no password/credential/provider-secret/token/raw-claims fields.
- Tenant collection200 unpaged with zero rows. No concrete Tenant/Place/OIDC or Asset instance could be inspected; no data created to fill that gap.
- Audit actor/action/target/context + equal inclusive bounds + ASC200, existing captured occurrence included.
- Missing Tenant404 without machine code; unsupported Product/Audit sort400 with code; submicrosecond Audit bound400. Confirms code-less identity versus coded business error handling requirements, not all possible conflict outcomes.

Unmocked HTTP readings and six live deployed-branding checks occurred successfully against the existing F4 deployment/current backend. They are not proof that deployment serves F5. Current F0–F5 worktree UI was verified through intercepted299-case browser suite.

An additional current-build/static-asset-overlay browser attempt was **not successful and not counted**: CSRF/network requests failed; a later retry preserving the real document returned net::ERR_CONNECTION_REFUSED, and a separate trusted HTTPS reachability probe also failed. The environment became unreachable from these probes; cause UNKNOWN. No production defect is inferred from that verification failure, and no restart/deploy/reset was performed. Do not claim current-build/live-API browser completion. Earlier successful checks remain point-in-time evidence.

## Remaining work and decision boundaries

- **F6-A01:** confirmed supported-action localization defect, separate repair approval required; combined acceptance BLOCKED. Passing suites do not close it.

- **B1/D29 BLOCKED / NOT AUTHORIZED:** public routing default/search/batch alignment; F2 anonymous subset still open.
- **B2/D28 BLOCKED / NOT AUTHORIZED:** exact Product optimistic-conflict HTTP mapping; F4 neutral current-status/code preservation remains accepted, exact subset not closed.
- **U3:** reviewed with explicit limits. Human F4 manual acceptance recorded; live TA/GU/TU/foreign-account matrix, external OIDC/JIT/provider interoperability, current-build F5 UI/live API, real Asset sorting/loan/Merge/delete and assistive checks remain unavailable/unperformed. No operator data/account fabrication.
- **390px Product-name wrapping:** separate FRONTEND-UX, not fixed/authorized.
- **C/D, export/delivery and other deferred lifecycle gates:** not started.
- **Documentation-only:** ARCHITECTURE.md:3 still says frontend integration has not started in its pre-integration backend-status header. Current integration plan supplies actual accepted statuses. No architectural rule/code conflict inferred; narrow future status synchronization requires separate scope. Historical backend reports untouched.
- **Excluded real-write suite modernization:** stale assertions/selectors noted above; requires a separately approved isolated test target before those suites can supply fresh evidence.

## Preservation

Pre-F6 hashes: ignored .cache/frontend-f6/before.json. Exact15-file delta; production/frontend/backend/API/schema/migrations/deployment and all unrelated dirty contents unchanged. Prior F0–F5/A/B evidence unchanged. Index empty; HEAD unchanged31b336eb47225556da56c73954e832bb615e2c0c. No staging/commit/push/PR, environment update, database reset or business-data creation. Only the separate fixture server is stopped after verification. No residual gate starts automatically. STOP for human review of included F6/D27 scope and limitations.

## Final checks

`git diff --check`, new/changed-file whitespace, relative documentation links and exact pre-gate manifest/preservation checks: PASS. Final index empty.

Final `git status --short` (includes accepted pre-existing and unrelated dirty work):

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
 M frontend/src/i18n.test.ts
 M frontend/src/i18n.ts
 M frontend/src/inventory.test.ts
 M frontend/src/inventory.ts
 M frontend/src/locales/de.json
 M frontend/src/locales/en.json
 M frontend/tests/administration-context.spec.ts
 M frontend/tests/api-foundation.spec.ts
 M frontend/tests/authentication.spec.ts
 M frontend/tests/collection-query.spec.ts
 M frontend/tests/dialog-toolbar.spec.ts
 M frontend/tests/form-lifecycle.spec.ts
 M frontend/tests/inventory.spec.ts
 M frontend/tests/list-filtering.spec.ts
 M frontend/tests/merge-combobox.spec.ts
 M frontend/tests/search-stability.spec.ts
 M frontend/tests/sidebar-layout.spec.ts
?? .idea/
?? docs/verification/FRONTEND_INTEGRATION_F2.md
?? docs/verification/FRONTEND_INTEGRATION_F3.md
?? docs/verification/FRONTEND_INTEGRATION_F4.md
?? docs/verification/FRONTEND_INTEGRATION_F5.md
?? docs/verification/FRONTEND_INTEGRATION_F6.md
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
?? frontend/src/fixture-contracts.test.ts
?? frontend/src/local-time.test.ts
?? frontend/src/local-time.ts
?? frontend/src/resource-contracts.test.ts
?? frontend/src/resource-contracts.ts
?? frontend/tests/backend-fixtures.ts
?? frontend/tests/f2-paging-lookup.spec.ts
?? frontend/tests/f3-resource-query.spec.ts
?? frontend/tests/f4-resource-detail.spec.ts
?? frontend/tests/f5-audit-time.spec.ts
```

## F6-A01 follow-up status (2026-10-13)

The separately authorized two-action localization correction is IMPLEMENTED / PENDING HUMAN ACCEPTANCE; see [F6-A01 evidence](FRONTEND_INTEGRATION_F6_A01.md). The original audit, defect discovery, verification counts and limitations above remain historical and unchanged. Combined F6 acceptance remains BLOCKED pending human review of the correction. No F4/F5 evidence was rewritten.

## Human acceptance — 2026-10-13

F6-A01 and F6/D27 combined frontend/backend integration are HUMAN ACCEPTED / CLOSED by explicit human decision. All verification results and U3/runtime/manual limitations above are preserved; limitations were accepted as limitations, not replaced with stronger evidence. Earlier pending/blocked statements describe historical status. B1/D29 and B2/D28 remain BLOCKED / NOT AUTHORIZED; excluded real-write modernization and all residual gates remain separate. This status note adds no new verification or implementation claim.
