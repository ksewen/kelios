# F1b — administration capabilities and signed Tenant context

2026-10-12. Scope: D05–D07 only. **IMPLEMENTED / PENDING HUMAN ACCEPTANCE**; no later gate authorized. Backend baseline939c737 plus separately HUMAN ACCEPTED Tenant GLOBAL USER read correction2026-10-12. U1/U2 HUMAN RESOLVED / CLOSED. Original Tenant-read evidence retains45 focused distinct cases and74+701=775 full backend passes/limitations; this gate did not rerun backend tests.

## Actual behavior

- Small explicit capability predicates separate ACTIVE GLOBAL Tenant read from ADMIN destinations. GLOBAL USER/ADMIN asynchronously load existing complete unpaged Tenant collection; initial normal login is Global. Dropdown uses authoritative UUID/name and route navigation for business context only; no identity mutation or anonymous routing lookup. Eager async load was chosen (lazy optional, not required).
- TENANT principals never fetch Tenant collection or get a switching control. ADMIN context name comes from independently authorized signed-own detail; USER uses signed Tenant UUID label because Identity contains no name and ordinary Tenant detail is denied. No new projection/endpoint or name→UUID guessing.
- GA Tenant administration remains list/create; row Open now canonical /tenants/{id}. TA navigation opens own canonical detail without needing collection. GLOBAL USER can directly read safe detail but has no administration navigation/action. Detail GET independent of list/Row state; displays safe name/UUID/provenance/time projection only, no mutation.
- Tenant detail is not operating context: /tenants/{id} (including trailing slash) does not change GLOBAL context; contextual business routes /tenants/{id}/{page} do. TENANT signed Tenant overrides transport; foreign business route redirects before mounting/requesting foreign collection. Foreign TA detail goes to independently scoped GET and safe404; never a list lookup.
- OIDC navigation/routing now reaches existing singleton form for GA concrete Tenant or TA signed own Tenant. Existing GET/PUT/version/secret/group/enabled body and write-session behavior are retained; no DELETE UI. USER administration routes do not mount User/OIDC/Audit consumers. Backend authority unchanged.
- Dashboard no longer independently fetches Tenant collection for labels/count. Receives safe current label; Tenant count only GA. Existing User Page/count work remains F2/F3: added Array.isArray guard solely to avoid crashing the F1b Dashboard on current Page response, not a Page conversion or counting loaded items. Pending-account metric remains unavailable (`—`) for current Page; no false total.
- Existing useRead latest-response/session guards and Shell semantic navigation/identity structure retained. Scope/role/status change removes inaccessible UI; late singleton response cannot reappear after demotion. No new remount key, generalized fetch/mapper or arbitrary-page filtering/sorting.

## Files and tests

Production: frontend/src/app/capabilities.ts, Shell.tsx, Dashboard.tsx, frontend/src/features/inventory/pages.tsx (Tenant destination/detail only), frontend/src/locales/de.json/en.json (createdBy/updatedBy labels). Units: capabilities.test.ts12 role/scope/status combinations plus context/detail distinction (13 cases). Browser: new administration-context.spec.ts35 tests with actual Tenant/Provider/User Page projections; updated only obsolete capability/name fixtures/assertions in sidebar-layout.spec.ts. Existing Email/F0/search/sidebar regressions run unchanged otherwise.

Acceptance/status documentation: ARCHITECTURE.md, DOMAIN.md, docs/AUTHENTICATION.md, backend collection plan, frontend integration plan, U1/U2 decision record, read-authority audit and Tenant correction evidence. Evidence acceptance header/note only; original counts, failures, commands and live limitations retained. Full task manifest is `.cache/frontend-f1b/files-changed.txt`.

## Verification

Commands from frontend, Node24 on PATH:

```sh
npm test -- --run src/app/capabilities.test.ts src/auth.test.ts src/api-contracts.test.ts
npm test
npm run build
PLAYWRIGHT_BROWSERS_PATH="$PWD/../.cache/playwright" KELIOS_BASE_URL=http://127.0.0.1:12180 npm run test:smoke -- administration-context.spec.ts sidebar-layout.spec.ts search-stability.spec.ts email-login.spec.ts api-foundation.spec.ts
```

Focused units60 passed; complete units94 passed (6 files), zero failures. Production TypeScript/Vite build passed. Final Chromium **116 passed,0 failures/skips** (35 F1b,27 Sidebar,16 search stability,20 Email,18 F0); log `.cache/frontend-f1b/browser.log`. Local Vite fixture server only, not deployment; Chromium APIs intercepted. Tests authentication.spec.ts/inventory.spec.ts remain excluded by configuration (KELIOS_TEST_TLS unset); not counted as passed/skipped/unexecuted totals.

Initial sandboxed Chromium launch failed before behavioral verification; rerun with approved execution permissions. First runnable browser batch111:104passed/7failed (obsolete GU admin-nav, TA own-nav/name, TU name expectations; invalid DISABLED successful-session fixture). Corrected fixtures/assertions to approved policy, preserving geometry/identity expectations. Next114:113passed/1failed; new create test selected the first POST (session refresh) rather than Tenant POST. Corrected endpoint-specific assertion, not implementation. Added exact initial configure/late-response and DE/EN mobile TA tests. Final run116 passed,0 failures/skips. Initial failures remain documented; not counted as successful verification.

DE/EN1440/1024/768/390 Tenant-detail/context checks, keyboard dropdown focus restoration, page overflow checks and existing navigation/collapse/node/focus/scroll regressions. Eight screenshots `.cache/frontend-f1b/context-{language}-{width}.png`; visually inspected DE390 and EN1440. Dev-rendered fixture screenshots, not live production/operator evidence.

## Live evidence and limitations

Live/current-backend GA/TA/USER verification NOT PERFORMED: no configured KELIOS_BASE_URL/operator Email/password-file/TLS context or established suitable current-backend account. No bootstrap, deploy, credentials/data creation or reset to manufacture evidence. All write paths intercepted. Mock request gating is frontend behavior evidence, not proof of SQL/backend authorization. Real-provider interoperability and U3 limitations remain; no assistive-technology runtime evidence claimed.

User Page rendering/admin forms and Dashboard pending-count semantics remain F2/F3/F4; F1b claims navigation/request capabilities, not completion of those consumers. Other six UUID detail routes remain F4. OIDC DELETE UI, password/Email administration, history/local-time migration, Product409 mapping B2/D28, public routing B1/D29, PhaseC/D,390px Product UX and export/delivery untouched.

No backend/OpenAPI/schema/test changes, deployment/operator data or staged/committed material. Final diff/link/preservation checks and status recorded below. STOP for F1b human acceptance.

## Final checks/status

Production build/focused60/full94 unit/final116 browser verification passed. git diff --check and relative documentation link checks passed. Pre-gate hashes restrict delta to18 listed task files; backend/OpenAPI/schema and unrelated frontend/deployment/history files unchanged. Tenant correction evidence changed only acceptance header/note, all original verification results/limits retained. Exact manifest `.cache/frontend-f1b/files-changed.txt`; exact git status --short `.cache/frontend-f1b/final-status.txt` includes intentionally uncommitted pre-existing work. Staged set empty. Fixture server stopped after tests. No operator data, credentials, deployment/reset, commit/push/PR. F1b/D05–D07 pending human acceptance; STOP.

Dev-server log also emitted react-i18next NO_I18NEXT_INSTANCE warnings during intercepted/module-harness tests; localized UI assertions/screenshots passed. Warning provenance was not investigated in this bounded gate, and no claim of warning-free live production behavior is made. No unrelated i18n refactor was performed.
