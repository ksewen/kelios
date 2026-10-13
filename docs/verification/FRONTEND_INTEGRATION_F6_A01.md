# F6-A01 — supported Audit action localization correction

Date: 2026-10-13. Status: IMPLEMENTED / PENDING HUMAN ACCEPTANCE. Separate human authorization for this narrow correction; combined F6/D27 acceptance remains BLOCKED pending human review. [Execution plan](../plans/FRONTEND_BACKEND_INTEGRATION_PLAN.md); [original F6 audit/evidence](FRONTEND_INTEGRATION_F6.md).

## Scope and exact delta

Backend writers already emit GLOBAL_EMAIL_CHANGED (AuthenticationService.java:146) and OIDC_CONFIG_DELETED (OidcConfigurationService.java:78). The original F6 audit correctly found that neither code had a locale enum entry. No backend/history/API/authorization change is needed or performed.

| Action | English | German |
| --- | --- | --- |
| GLOBAL_EMAIL_CHANGED | GLOBAL Email changed | GLOBAL Email-Adresse geändert |
| OIDC_CONFIG_DELETED | OIDC configuration deleted | OIDC-Konfiguration gelöscht |

Production changes are exactly these two entries in each locale. i18n.ts and its unknown-action fallback are byte-for-byte unchanged against the pre-gate worktree. Unknown codes still display Unavailable / Nicht erfasst rather than being interpreted as supported actions.

Exact task file manifest (compared with the pre-F6-A01 dirty worktree, not HEAD):

- frontend/src/locales/en.json
- frontend/src/locales/de.json
- frontend/src/i18n.test.ts
- frontend/tests/f6-a01-audit-actions.spec.ts
- docs/plans/FRONTEND_BACKEND_INTEGRATION_PLAN.md
- docs/verification/FRONTEND_INTEGRATION_F6.md (append-only status/reference)
- docs/verification/FRONTEND_INTEGRATION_F6_A01.md

Known-action unit enumeration now includes both codes. Two added language-specific unit cases prove both supported labels and the genuinely unknown-code fallback. The new intercepted spec has eight DE/EN × 1440/1024/768/390 cases, each checking both list labels and independent UUID detail requests/headings, historical actor display, Escape close and no page overflow. Two additional cases verify unknown-code list/detail fallback in DE/EN. No business mutation request is permitted by these fixtures; session refresh is intercepted.

## Verification

Commands run from frontend with PATH=/opt/homebrew/opt/node@24/bin:$PATH. Chromium additionally uses PLAYWRIGHT_BROWSERS_PATH="$PWD/../.cache/playwright" and KELIOS_BASE_URL=http://127.0.0.1:12180. A disposable Vite process serves the current worktree on that port for intercepted tests only; the operator/manual environment is not restarted, deployed or modified.

| Command | Result |
| --- | --- |
| npm test -- src/i18n.test.ts | 18 passed, 1 file; zero failures/skips |
| npm test | 196 passed, 10 files; zero failures/skips |
| npx playwright test f6-a01-audit-actions.spec.ts --output=../.cache/frontend-f6-a01/focused | 10 passed; zero failures/skips |
| Combined intercepted Chromium command below | 309 passed (4.4m), zero failures/skips |
| npm run build | PASS: tsc --noEmit and Vite production build |

Combined command:

```sh
npx playwright test f6-a01-audit-actions.spec.ts f5-audit-time.spec.ts f4-resource-detail.spec.ts f3-resource-query.spec.ts api-foundation.spec.ts email-login.spec.ts f2-paging-lookup.spec.ts list-filtering.spec.ts collection-query.spec.ts merge-combobox.spec.ts form-lifecycle.spec.ts search-stability.spec.ts dialog-toolbar.spec.ts sidebar-layout.spec.ts administration-context.spec.ts --workers=2 --output=../.cache/frontend-f6-a01/combined
```

The focused 10 cases are included in the combined run, not additional unique coverage. Existing real-backend authentication.spec.ts and inventory.spec.ts are excluded by configuration. Data-creating ui-redesign.spec.ts and phase4-localization.spec.ts are not selected. These unexecuted suites are not counted as passed or skipped. Backend tests and routing tests are not rerun: this correction changes only labels, no backend or serving contract.

## Preservation and limits

Pre-gate SHA-256 manifest is stored in ignored .cache/frontend-f6-a01/before.json. Only the seven listed task files changed; all unrelated accepted/dirty files remain unchanged and the index remains empty. Original F6 evidence is preserved as an exact prefix with a concise appended correction-status link; F4/F5 evidence is byte-for-byte unchanged. Documentation relative-link, whitespace and final diff checks are recorded below.

All browser evidence is intercepted fixture evidence, not live backend authorization, SQL, mutation or manual business-data evidence. No live action-generating writes were made. No operator account, business data, schema/history, frontend session/authorization logic or deployment was changed. No staging, commit, push, PR, reset or next gate.

B1/D29 anonymous routing and B2/D28 exact Product conflict subset remain BLOCKED / NOT AUTHORIZED. U3 unavailable live/manual/provider/other-role/data coverage and existing F6 environment limitations remain unchanged. Deferred 390px Product-name wrapping, Phase C/D, export/delivery, excluded real-write modernization and unrelated residual work remain outside scope. F6-A01 and combined F6 are not HUMAN ACCEPTED / CLOSED by this report. STOP for human review.

## Final checks and worktree

PASS: git diff --check; 38 relative documentation link occurrences resolve; new untracked evidence/spec have no trailing whitespace; exact seven-file task delta against the pre-gate SHA-256 snapshot; every other tracked/untracked file unchanged; original F6 evidence preserved as an exact prefix; F4/F5 reports unchanged; index empty. Production locale delta reconstruction confirms exactly two added entries per locale and no other locale alteration. Combined Chromium exit 0, unit/build exit 0. Test-only Vite server stopped after verification; operator environment untouched.

Final git status --short (includes preserved pre-existing work, not a task manifest):

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
?? docs/verification/FRONTEND_INTEGRATION_F6_A01.md
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
?? frontend/tests/f6-a01-audit-actions.spec.ts
```

## Human acceptance — 2026-10-13

F6-A01 and F6/D27 combined frontend/backend integration are HUMAN ACCEPTED / CLOSED by explicit human decision. All verification results and U3/runtime/manual limitations above are preserved; limitations were accepted as limitations, not replaced with stronger evidence. Earlier pending/blocked statements describe historical status. B1/D29 and B2/D28 remain BLOCKED / NOT AUTHORIZED; excluded real-write modernization and all residual gates remain separate. This status note adds no new verification or implementation claim.
