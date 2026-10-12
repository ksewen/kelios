# F1a — GLOBAL Email login

Date: 2026-10-12. **IMPLEMENTED / PENDING HUMAN ACCEPTANCE**, D01 only. F0/D24 are HUMAN ACCEPTED / CLOSED on2026-10-12; their original evidence/limitations are unchanged. Backend remains `939c7373552d676822d0f9d0fe92798fa8618100` and CLOSED.

## Actual request and UI delta

Local POST `/api/v1/auth/local/login` previously sent `{login,password}`. It now sends exactly `{email,password}` using F0 LocalLoginRequest. Email case is preserved for authoritative backend canonical comparison. Password is unchanged, including whitespace; there is no new creation-policy validation at login. No login/loginName compatibility field or mode, TENANT local credential, backend modification or account creation.

Authentication now has an associated visible Email/E-Mail label, type=email, name=email, inputMode=email, required and maxLength254. Autocomplete=username is the standard credential-manager identifier token, not a loginName compatibility mode. Password autocomplete=current-password and prior success/failure clearing behavior are preserved. Native required/email-format/maxlength checks prevent obvious invalid submission; backend remains authoritative for practical ASCII Email/account validity. Existing localized sanitized401/403/429 feedback remains. OIDC unknown-provider selection and known routing shortcut, pending restricted screen, refresh/logout, CSRF and memory-only access semantics are preserved.

## Exact files changed against pre-F1a worktree

- `frontend/src/Authentication.tsx`: Email state/input/label and login invocation only.
- `frontend/src/auth.ts`: email argument and strict typed LocalLoginRequest body only; session/request/refresh/logout/OIDC code unchanged.
- `frontend/src/locales/en.json`, `frontend/src/locales/de.json`: new redesign.email key. The old username translation remains unused by local login; administration terminology belongs to later gates.
- `frontend/src/auth.test.ts`: update login fixtures to Email and exact body assertion; five additional case/boundary/password-preservation/sanitized-failure tests. Existing F0/session tests remain.
- `frontend/tests/email-login.spec.ts`:20 new intercepted cases, covering DE/EN login, format/required/max254, keyboard, refresh/logout, sanitized failures, explicit unknown/known OIDC routing and TENANT pending isolation, plus eight DE/EN four-width label/focus/no-overflow screenshots.
- `frontend/tests/ui-helpers.ts`: login reads KELIOS_TEST_EMAIL and fails clearly if absent, with no fabricated account fallback; Email label. Existing unrelated command helpers preserved.
- `frontend/tests/authentication.spec.ts`: operator login Email input/environment and label assertions only. Its old real create/disable fixture remains D02 scope and was not executed or migrated.
- `frontend/tests/phase4-localization.spec.ts`: login label/key/operator Email environment only; existing later resource/write behavior unchanged and not executed.
- `frontend/tests/runtime-branding.spec.ts`, `frontend/tests/ui-redesign.spec.ts`: login label assertions only; not executed in this gate.
- `docs/plans/FRONTEND_BACKEND_INTEGRATION_PLAN.md`: F0/D24 acceptance/date; F1a-only authorization, actual results/D01 pending acceptance and live limitation. Scope/owners/invariants/graph unchanged.
- This evidence file.

Thirteen files total. Exact manifest/final status: `.cache/frontend-f1a/files-changed.txt`, `final-status.txt`. No source changes to Email administration, password policy, Shell/context, collections/paging/provider configuration, backend/OpenAPI/migrations or deployment. Pre-existing unrelated modifications remain preserved.

## Verification

From `frontend`, with `PATH=/opt/homebrew/opt/node@24/bin:$PATH`:

| Exact command | Result |
|---|---|
| `npm test -- --run src/auth.test.ts src/api-contracts.test.ts src/i18n.test.ts` |63 passed,3 files, zero failures/skips |
| `npm test` |81 passed,5 files, zero failures/skips |
| `npm run build` |PASS: TypeScript noEmit and Vite production build |
| `PLAYWRIGHT_BROWSERS_PATH="$PWD/../.cache/playwright" KELIOS_BASE_URL=http://127.0.0.1:12180 npm run test:smoke -- tests/email-login.spec.ts tests/api-foundation.spec.ts tests/search-stability.spec.ts` |Final54 passed, zero failures/skips,1.1m:20 Email +18 F0 +16 search/stability |
| `git diff --check`, changed-untracked whitespace/link checks and pre-task SHA-256 preservation check |PASS |

Logs: `.cache/frontend-f1a/focused-unit.log`, `unit.log`, `build.log`, `browser.log`. Initial browser run had44 passed/2 failed: the new logout tests checked the request list before the existing asynchronous CSRF/DELETE completed. Tests now wait for actual DELETE response; no logout implementation change or assertion weakening. Final run includes the eight added viewport cases; initial result is not counted as final green evidence. Known source-server NO_I18NEXT_INSTANCE warnings were not refactored.

All API browser traffic was intercepted at an isolated source test server, which was stopped afterward. Successful login fixtures use a Category route to isolate D01 from known later Dashboard/User Page issues; no claim that those consumers are integrated. DE/EN at1440/1024/768/390 passed Email field/focus/overflow checks. Screenshots `.cache/frontend-f1a/login-{en|de}-{1440|1024|768|390}.png`; visually inspected DE390 and EN1440. These are source-server fixture screenshots, not deployed/live-backend evidence.

Default Playwright configuration excludes authentication.spec.ts and inventory.spec.ts without KELIOS_TEST_TLS=true. Those real/manual suites were not run or counted as skipped/passed. Unselected localization/branding/redesign suites also are not counted. No password files were read or persistent test users created.

## Live verification and limitations

**LIVE-BACKEND VERIFICATION NOT PERFORMED / PENDING.** No KELIOS_BASE_URL, KELIOS_TEST_EMAIL, KELIOS_TEST_PASSWORD_FILE or KELIOS_TEST_TLS operator configuration was supplied in the task environment. No suitable current-backend/operator account was established. The test-only URL12180 is not that environment. No deployment, bootstrap, reset, credential creation/edit or operator data access was attempted to manufacture evidence. Mixed-case/boundary login and refresh/logout are browser/client fixture evidence; backend canonical Email/validity remains the accepted backend implementation evidence, not a new live claim.

No unexpected backend/API contradiction or new dependency was discovered. Live login/logout against an already suitable current backend/operator account remains the explicit acceptance limitation. Real IServ interoperability, inaccessible operator datasets and physical assistive technology remain unverified. F1b/U1/U2, F2–F6, B1/B2, D02/D23, Phase C/D,390px table repair, export/delivery remain unauthorized. No staging/commit/push/PR/deployment/reset/business data. STOP for human F1a/D01 acceptance.
