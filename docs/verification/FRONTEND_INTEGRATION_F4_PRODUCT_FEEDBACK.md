# Exact F4/D25 Product-feedback closure

Date: 2026-10-13. Status: **IMPLEMENTED / PENDING HUMAN ACCEPTANCE** for this exact subset only. B2/D28 is HUMAN ACCEPTED / CLOSED. Previously accepted unblocked F4 and F6 evidence remains unchanged.

## Scope and implementation conclusion

No production frontend correction was necessary. Existing F0/F4 structured handling naturally consumes the accepted Product reference-race contract: HTTP409, code `CONCURRENT_OPERATION`, backend detail `Concurrent operation; review current state before resubmitting`. The detail is not displayed verbatim. Existing neutral localized concurrent-change feedback appears inside the independently loaded Product workflow. No server code is fabricated, and no Asset-change assertion is made.

This task changes exactly:

- `docs/plans/FRONTEND_BACKEND_INTEGRATION_PLAN.md`
- `docs/plans/COLLECTION_QUERY_IMPLEMENTATION_PLAN.md`
- `docs/verification/BACKEND_INTEGRATION_B2_D28.md` (append-only human acceptance)
- `frontend/src/product-feedback.test.ts` (new regression tests)
- `frontend/tests/product-conflict-feedback.spec.ts` (new intercepted Chromium tests)
- `docs/verification/FRONTEND_INTEGRATION_F4_PRODUCT_FEEDBACK.md`

No production TypeScript/React, locales, backend implementation, backend tests, OpenAPI, migrations or historical frontend evidence changed in this task. Existing dirty B1/B2 and anonymous-routing implementation is preserved, not counted as this task's implementation.

## Existing executable error path

- [auth.ts](../../frontend/src/auth.ts): `AuthSession.request` (lines71–97) preserves actual HTTP status and optional machine code in `ApiFailure`; raw problem detail/internal fields are discarded. Mutations are not automatically replayed.
- [pages.tsx](../../frontend/src/features/inventory/pages.tsx): `Command` (lines103–114) retains the active session on rejection; `MutationError` (lines226–230) renders safe feedback plus actual status/code attributes. Reload is a separate read callback. `BusinessDetail` (lines242–259) supplies Product context and the independent UUID endpoint to edit/publish and reload.
- [resource-contracts.ts](../../frontend/src/resource-contracts.ts): `mutationFeedback` (lines21–29) classifies concurrent409 as reviewable, not uncertain or a permanent domain guard. Existing defensive handling of historical Product/Asset-stale wording remains unchanged.
- [i18n.ts](../../frontend/src/i18n.ts): `errorMessage` (lines45–55): known machine codes map to localized messages; unknown codes use safe status fallback. No unsafe backend detail is displayed.

English: “Another operation conflicted. Reload and review the current state.” German: “Eine andere Aktion stand im Konflikt. Laden und prüfen Sie den aktuellen Stand.” The Product heading, command, UUID endpoint and retained form establish the operation context. Asset `STALE_ASSET_VERSION` retains its existing Asset-specific translation. ProductSelection `CONCURRENT_OPERATION` remains neutral and unchanged.

## Focused coverage

Six new unit cases (DE/EN) prove exact concurrent classification/code preservation, unchanged Asset stale and ProductSelection behavior, permanent guards, unknown/code-less409 and uncertain transport fallback.

Twelve new intercepted browser cases (DE/EN) prove Product edit/publish409 concurrent feedback, exact code/status, sanitized detail, independent UUID reload including changed readable Category/UUID association, retained form/input nodes and values, and exactly one mutation request. Publish has no business input fields; its active form node remains mounted. Asset stale-version tests retain active note/input and expectedVersion while independently reloading. Product guard, unknown409 and transport failure tests retain safe distinctions and never replay. Existing related suites cover other domains, code-less errors, session behavior and accepted form lifecycle.

The test fixture deliberately includes unsafe raw detail/internal fields and an Asset claim to ensure these are not exposed. All write paths and all browser API reads are intercepted. No real Product race, live mutation, operator account or business data was created.

## Verification

- Focused units: `npm test -- src/product-feedback.test.ts src/resource-contracts.test.ts src/i18n.test.ts src/auth.test.ts` — **113 passed / 4 files**, zero failures/skips; six new cases included, not additional to113.
- `npx tsc --noEmit` — PASS.
- Focused Chromium: `npx playwright test product-conflict-feedback.spec.ts` — **12 passed**, zero failures/skips.
- Combined Chromium: `npx playwright test product-conflict-feedback.spec.ts f4-resource-detail.spec.ts api-foundation.spec.ts form-lifecycle.spec.ts` — **78 passed**, zero failures/skips. Focused12 are included, not additional unique cases.
- Complete frontend unit rerun / production build: not required and not run for this tests/documentation-only delta; no production/shared TypeScript or localization infrastructure changed. Type checking passed.

Commands ran in `frontend`, Node24 on PATH, Chromium with repository Playwright cache and a test-only Vite server on12180. No deployment/manual environment changes.

Initial runs exposed test expectation/selector errors, not production defects: units111 passed/2 failed because UNCERTAIN already has its own supported translation; corrected the test to that existing contract. Browser4 passed/8 failed because role queries excluded the background detail under Radix modal aria-hidden; corrected only the selector to include hidden semantic content and retained the refreshed UUID link/text plus same input/form assertions. Final successful results above supersede those test-development failures without erasing them.

## Limitations and preservation

No live-backend Product race or manual feedback evidence is claimed. Accepted B2 PostgreSQL/HTTP evidence independently establishes the backend contract; its original75 unit+727 integration=802 result is preserved, not rerun or counted as frontend verification. Intercepted fixtures do not prove backend authorization, locking or SQL behavior.

No new layout or390px wrapping claim is made. U3/runtime/manual limitations and excluded real-write-suite modernization remain as previously recorded. B1 and anonymous routing are accepted; no new rollout is performed. B2 is accepted, but this exact frontend subset awaits separate human acceptance. Phase C/D, export/delivery, deployment and all residual work remain outside scope.

Pre-gate file hashes, final diff, relative links, whitespace and empty-index checks are recorded below after verification. No staging, commit, push, PR, reset or operator-data change occurred.

## Final checks and repository state

PASS: exact six-file delta against the pre-gate SHA256 snapshot; all other tracked/untracked non-ignored file bytes preserved. PASS: original B2 evidence prefix byte-preserved, earlier frontend evidence untouched, relative documentation links and trailing-whitespace checks. `git diff --check` PASS; index empty. Production frontend/backend and API/schema files are unchanged against the pre-gate worktree, including pre-existing dirty work.

Final `git status --short` (includes preserved earlier B1/B2/anonymous routing and unrelated work; this task owns only the six files above):

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
?? docs/verification/FRONTEND_INTEGRATION_F4_PRODUCT_FEEDBACK.md
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
?? frontend/src/product-feedback.test.ts
?? frontend/tests/anonymous-routing-lookup.spec.ts
?? frontend/tests/product-conflict-feedback.spec.ts
```

STOP for human acceptance of the exact Product-feedback subset. No subsequent gate authorized or started.


## Human acceptance — 2026-10-13

Exact F4/D25 Product-feedback subset HUMAN ACCEPTED / CLOSED by explicit human decision. All original verification results, initial test-development failures, limitations and intercepted-only runtime boundary above remain unchanged. No production correction was required; no stronger live evidence is claimed. No subsequent gate authorized or started.
