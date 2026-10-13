# Anonymous F2 / D20 / D22 — Organization routing lookup rollout

Date: 2026-10-13. Separately authorized anonymous subset after human acceptance of B1/D29. [Integration execution plan](../plans/FRONTEND_BACKEND_INTEGRATION_PLAN.md), [accepted B1 backend evidence](BACKEND_INTEGRATION_B1_D29.md), [original accepted F2 business evidence](FRONTEND_INTEGRATION_F2.md). Status: IMPLEMENTED / PENDING HUMAN ACCEPTANCE, anonymous subset only; business F2 and accepted F0–F6-A01 remain unchanged/closed.

## Behavior and boundaries

AuthSession.organizations now requests page=0,size=20,q=normalized text (previously size25). It uses existing endpoint-owned RoutingOrganizations/Page types and preserves response items,totalElements,page,size. It validates bounded Page metadata/routing UUIDs and rejects malformed/null data. Query text is trimmed, all-whitespace becomes empty and normalized input is limited to128 JavaScript UTF-16 units. Native input maxlength128 gives immediate user feedback/prevention; the backend remains authoritative. URL encoding preserves literal %, _ and backslash; the browser does not implement substring matching or escape them into SQL patterns.

OrganizationPicker replaces fixed25 Next/Previous replacement with incremental20 continuation. Reaching the scroll/keyboard boundary or using the localized Load more button requests the next page from returned metadata; appended routing IDs are deduplicated and existing option/input nodes retain identity. End detection uses (returnedPage+1)*returnedSize versus totalElements, including exact20/40 and partial45 fixtures. No normal page-size selector or Filter button is introduced. No automatic full-directory preload.

Query changes reset the cursor to0, including returning to an earlier query. Close/reopen and signed-session/routing-context changes reset cursor/selection and invalidate old results. The150ms initial/search debounce remains; old same-context search results stay mounted but disabled while a changed query is pending. Continuation retains valid options/focus/scroll; errors remain visible and retries are explicit, not automatic mutation replay. Stale initial/continuation responses cannot replace or append into a new query/session/path. Client session-generation checks also reject outdated anonymous reads.

Names are presentation; routingId is the sole stable identity. Duplicate names show their existing public UUIDs for disambiguation. The selected routing ID remains stable during valid continuation and failed-start retry in the same open workflow; there is no saved draft. Selecting calls the unchanged explicit /api/v1/auth/oidc/{routingId}/start. Known /login/tenant/{uuid} shortcuts still bypass discovery. No provider inference/name-to-ID guessing.

This endpoint/selector remains anonymous Provider-routing discovery only. No authenticated Tenant context, membership, identity resolution, Provider administration or authority grant. Tenant context selection remains the accepted independent Tenant collection consumer. OIDC issuer/subject, credentials, authentication lifecycle, authorization and all backend production behavior are unchanged.

## Exact task manifest against pre-rollout worktree

1. frontend/src/auth.ts
2. frontend/src/auth.test.ts
3. frontend/src/app/OrganizationPicker.tsx
4. frontend/tests/anonymous-routing-lookup.spec.ts
5. frontend/tests/email-login.spec.ts (anonymous fixture size20 only)
6. frontend/tests/search-stability.spec.ts (anonymous fixtures now include Page metadata)
7. docs/plans/FRONTEND_BACKEND_INTEGRATION_PLAN.md
8. docs/plans/COLLECTION_QUERY_IMPLEMENTATION_PLAN.md (B1 acceptance/dependent status only)
9. docs/verification/BACKEND_INTEGRATION_B1_D29.md (append-only human acceptance)
10. docs/verification/FRONTEND_INTEGRATION_F2_ANONYMOUS_ROUTING.md

No business paging/combobox implementation, general F2 resource consumers, frontend API types, localization bundles, backend production/test/OpenAPI/schema files or prior accepted frontend evidence changed. Pre-gate hashes are in ignored .cache/frontend-f2-routing/before.json. Prior accepted B1 code is dirty from its previous gate; it is preserved exactly, not part of this rollout delta.

## Verification ladder

Commands in frontend use PATH=/opt/homebrew/opt/node@24/bin:$PATH. Chromium adds PLAYWRIGHT_BROWSERS_PATH="$PWD/../.cache/playwright" and KELIOS_BASE_URL=http://127.0.0.1:12180. Only the disposable fixture Vite process is started/restarted for testing; no manual deployment is updated.

| Command | Final result |
| --- | --- |
| npm test -- src/auth.test.ts | 63 passed /1 file, zero failures/skips |
| npm test | 216 passed /10 files, zero failures/skips |
| npx playwright test anonymous-routing-lookup.spec.ts | 20 passed, zero failures/skips |
| Combined intercepted Chromium command below | 191 passed (2.2m), zero failures/skips |
| npm run build | PASS: TypeScript and Vite production build, final cursor correction included |

Combined command:

```sh
npx playwright test anonymous-routing-lookup.spec.ts email-login.spec.ts api-foundation.spec.ts search-stability.spec.ts f2-paging-lookup.spec.ts merge-combobox.spec.ts list-filtering.spec.ts administration-context.spec.ts sidebar-layout.spec.ts --workers=2 --output=../.cache/frontend-f2-routing/combined-final
```

Focused20 is included in combined coverage, not extra unique passed tests. Units include normalized128-unit/astral-boundary handling, malformed Page data and stale-session reads. Browser coverage proves45 results across20/20/5 batches, exact0/20/40 boundaries, readable duplicate-name/routing-ID safety, explicit selected Provider request, no Bearer/business/admin discovery requests, DOM/focus/scroll identity, late query/continuation/session/route/closed-workflow rejection, page0 query restart, failure/explicit retry, known routing bypass and no-results/error states. DE/EN1440/1024/768/390 cover keyboard selection/continuation/close focus return and no horizontal overflow. Eight screenshots in ignored .cache/frontend-f2-routing/; DE390 and EN1440 inspected visually, fixture views only.

Initial focused run:17 passed/1 failed (new close/reopen cursor was retained). Corrected the new picker reset; subsequent19 and final20 focused cases pass. Code review also found query-return cursor reuse; explicit query/context reset and a new regression correct it within this authorized subset.

Initial combined run:185 passed/5 failed, all direct-module session-harness cases, on the hot-updated Vite server. Fresh fixture server used for final verification. Duplicate auth module instances from Vite hot-update versus direct test imports are the inferred harness cause; no accepted session/business implementation or assertions were changed to obtain green results. Logs retain this attempt and final run separately. Do not count initial failures as passing or hide the attempt.

Existing real-backend authentication.spec.ts/inventory.spec.ts are excluded by configuration. Data-creating ui-redesign.spec.ts/phase4-localization.spec.ts are not selected. Their previously recorded modernization debt remains outside authorization, including the old ui-redesign anonymous fixture omitting Page metadata. Unexecuted suites are not counted as passed/skipped. Frontend fixtures do not prove backend SQL/authorization or real IServ interoperability.

## Runtime evidence and limitations

Read-only anonymous GET attempted against existing https://localhost:19443/api/v1/auth/oidc/organizations with curl -k, connect timeout3/max5 seconds. Exit7, connection failed. Current-backend lookup verification therefore NOT PERFORMED successfully. No restart/deploy/bootstrap/account/Tenant/Provider/data creation to manufacture it. All routing/search/paging writes/start requests above are intercepted fixtures, not operator writes or real Provider authentication. Existing backend B1 PostgreSQL evidence is preserved, not rerun or strengthened.

B1/D29 human acceptance is recorded independently. This anonymous F2/D20/D22 subset must be IMPLEMENTED / PENDING HUMAN ACCEPTANCE after successful verification, not self-accepted. B2/D28 remains BLOCKED / NOT AUTHORIZED.390px Product wrapping, C/D, export/delivery, deployment, excluded real-write modernization and all residual gates remain untouched. No stage/commit/push/PR/reset/operator data or next gate.

## Final preservation/status checks

Anonymous F2/D20/D22 IMPLEMENTED / PENDING HUMAN ACCEPTANCE. B1/D29 HUMAN ACCEPTED / CLOSED, 2026-10-13. Final Chromium exit0; focused units/full units/build exit0. No claim of human frontend acceptance.

Pre-gate hash comparison verifies exactly10 listed files changed; all backend production/tests/OpenAPI/migrations and unrelated tracked/untracked content remain unchanged. B1 evidence is append-only and all prior frontend evidence unchanged. Index empty. git diff --check, new-file whitespace, relative documentation links and final-diff review pass. Final status below includes preserved B1/unrelated dirty work and is not this rollout manifest.

65 relative documentation link occurrences resolve. Final new/tracked-file whitespace and git diff --check pass. Restoring only the organizations method/type import exactly reproduces the pre-gate auth session client; login/refresh/logout/request/start behavior unchanged. B1 production and all unrelated hashes unchanged. The disposable fixture Vite server was stopped after verification; operator environment untouched.

Final git status --short:

```text
 M backend/src/main/java/com/github/ksewen/kelios/identity/auth/OidcConfigurationService.java
 M backend/src/main/java/com/github/ksewen/kelios/platform/security/AuthController.java
 M backend/src/test/java/com/github/ksewen/kelios/identity/auth/IdentityAuthenticationIT.java
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
?? docs/verification/FRONTEND_INTEGRATION_F2_ANONYMOUS_ROUTING.md
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
?? frontend/tests/anonymous-routing-lookup.spec.ts
```

## Human acceptance — 2026-10-13

Anonymous F2/D20/D22 routing lookup subset HUMAN ACCEPTED / CLOSED by explicit human decision. All historical results, failed attempts and unavailable live-lookup limitation above remain exactly as recorded; no stronger live evidence is claimed. B1/D29 remains HUMAN ACCEPTED / CLOSED. Separate B2 backend-only authorization does not reopen this frontend scope or authorize Product-feedback changes.
