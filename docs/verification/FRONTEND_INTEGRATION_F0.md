# F0 — shared endpoint contracts and error/client foundation

Date: 2026-10-12. **IMPLEMENTED / PENDING HUMAN ACCEPTANCE.** The integration plan is HUMAN ACCEPTED; only F0 was authorized. Backend baseline remains `939c7373552d676822d0f9d0fe92798fa8618100`, unchanged. D24 is implemented/pending acceptance; no later finding/gate is closed by these types.

## Exact F0 delta against the pre-F0 worktree

1. `frontend/src/api-contracts.ts` — new explicit current endpoint transport types: Identity/SessionResponse, generic Page value shape, safe ID/name Reference/Metadata, Catalog/Place/Product/User/Tenant Item/Detail and command state distinctions, safe GLOBAL account projection, sanitized OIDC singleton and public routing projections, Asset state/read associations/history, Audit and Merge preview. Closed command/query types contain supported fields only, including current wire sort=tag. There is no mapper, generic fetching engine or persistence exposure. Email request types describe the accepted API; the login client/UI is deliberately not migrated.
2. `frontend/src/api-contracts.test.ts` — new representative typed current User Page/count/beyond-end, Product association, Place detail-note, User safe account and singleton fixtures; compile-time projection/input/query exclusions. Type checks are not claimed as universal runtime validation or backend authorization proof.
3. `frontend/src/auth.ts` — Identity/Session use shared types. Session installation rejects missing/non-object envelopes and missing identity fields with sanitized INVALID_AUTH_RESPONSE. Request errors retain authoritative HTTPstatus and a valid optional machinecode; absent/malformed code remains undefined. Raw detail/body is not retained/displayed. Existing memory access, cookie/CSRF, refresh/logout generation, read refresh-once, empty200/204 and201 response handling and no mutation replay remain. Login payload still sends login, password; organizations still explicitly request25. F1a/B1 remain open.
4. `frontend/src/auth.test.ts` — current code-less statuses, malformed/unknown error payloads, safe failures, malformed session envelopes, empty success/createdID, no replay and explicit non-migration of login body. Existing tests preserved.
5. `frontend/src/inventory.ts` — Page aliases the shared Page<T> contract; existing loose Row consumers remain for their separately owned migration. No page-size/query/pager behavior change.
6. `frontend/src/i18n.ts` — FeedbackError retains status+optionalcode. Known business/client code uses its existing translation; otherwise status400/401/403/404/409/429/503 selects safe localized fallback. Unknown code is retained at transport boundary without being rendered as raw text. Other/unavailable statuses use REQUEST_FAILED. No raw server detail; no reinterpretation of backend Product mapping.
7. `frontend/src/i18n.test.ts` — DE/EN fallback/code-priority/sanitization tests.
8. `frontend/src/locales/en.json` and `frontend/src/locales/de.json` — one new client presentation key HTTP_CONFLICT with generic review wording. This is not a fabricated server errorcode; ApiFailure.code stays absent when the server supplies none. No Email/password-policy label migration.
9. `frontend/src/features/inventory/pages.tsx` — shared useRead/ReadError/Command and delete-error feedback retain the ApiFailure object instead of reducing it to its code. Same-context reads, current form/input nodes, invalidated option guards, confirmed-discard/success resets and no-op confirmation remain unchanged. No resource screen migration, route change or new operation-specific conflict workflow.
10. `frontend/tests/api-foundation.spec.ts` — 18 intercepted Chromium cases: seven code-less statuses in two locales on the existing authentication password command; known REFERENCED on actual Catalog edit; code-less scoped read denial. Each mutation case verifies one dispatch, same mounted form/input and retained active values; no private response detail is displayed. Current Page20 and safe association/metadata fixtures are used without migrating consumers.
11. `docs/plans/FRONTEND_BACKEND_INTEGRATION_PLAN.md` — planning acceptance/F0-only authorization and actual completion/status/D24 evidence. Approved scope, owners, dependency graph and invariants unchanged.
12. This evidence file.

This list names13 files (the locale bullet contains two). Pre-existing modifications were preserved. No changes to Authentication.tsx, Shell/navigation, reference picker implementation, Users Page rendering, pager sizes, provider administration, backend/OpenAPI/tests/migrations or manual environment.

## Verification commands and exact results

Commands run from `frontend` with `PATH=/opt/homebrew/opt/node@24/bin:$PATH` (Node24):

| Command | Result |
|---|---|
| `npm test -- --run src/auth.test.ts src/inventory.test.ts src/api-contracts.test.ts src/i18n.test.ts` | Final65 tests passed in4 files; no failed/skipped tests |
| `npm test` | Final76 tests passed in5 files; no failed/skipped tests |
| `npm run build` | PASS: TypeScript noEmit and Vite production build |
| `PLAYWRIGHT_BROWSERS_PATH="$PWD/../.cache/playwright" KELIOS_BASE_URL=http://127.0.0.1:12180 npm run test:smoke -- tests/api-foundation.spec.ts tests/search-stability.spec.ts tests/dialog-toolbar.spec.ts tests/merge-combobox.spec.ts` |83 passed, zero failed/skipped,1.7m;18 new foundation +65 existing regressions |
| `PLAYWRIGHT_BROWSERS_PATH="$PWD/../.cache/playwright" KELIOS_BASE_URL=http://127.0.0.1:12180 npm run test:smoke -- tests/api-foundation.spec.ts` | Final18 passed, zero failed/skipped,19.6s after refining the coded conflict fixture to its actual Catalog endpoint; this rerun adds no distinct tests to83 |
| `git diff --check` and separate changed-untracked-file whitespace/link/preservation checks | PASS |

Logs: `.cache/frontend-f0/focused-unit.log`, `unit.log`, `build.log`, `browser.log`, `browser-foundation-final.log`. Initial Chromium setup pointed at `frontend/.cache/playwright`, which had no executable:83 browser-launch failures before behavioral execution, not application/test assertion regressions. Correct repository cache resolved setup. Initial sandbox server bindEPERM was resolved by approved isolated-test-server access. No browser/dependency installation occurred.

Browser target was a temporary local source test server on12180, not the operator/manual deployment; all API traffic was intercepted. It was stopped after verification. Existing search/dialog/Merge suites protect pending/response ordering, same-context DOM/focus/scroll, dirty exits, confirmed discard, quiet preview and Cancel=no-op. Some unchanged legacy fixtures intentionally still model not-yet-migrated consumers; their passes do not prove later User/Page/association/authorization integration. Final foundation coded-business case uses Catalog edit, not an auth endpoint that never emits that code.

Default configuration excludes authentication.spec.ts and inventory.spec.ts unless KELIOS_TEST_TLS=true. Those excluded suites were not run or counted as skipped/passed. No backend suite was rerun; historical773 passed remains accepted backend evidence.

## Limitations and stop boundary

No live/current-backend compatibility, real IServ interoperability, operator Asset data, physical assistive-tech or new responsive-layout acceptance claim. F0 changes shared contracts/errors, not resource layout. Existing DE/EN browser regression coverage remains its actual evidence. Existing development-only NO_I18NEXT_INSTANCE warnings appeared during the source-server regressions; this accepted historical warning was not investigated/refactored in F0. No unexpected capability/security/contract contradiction required broadening F0. Known D28/B2 Product-specific error mapping and D29/B1 routing default/search remain separate; F0 preserves actual returned codes and does not fix them.

No F1a or later implementation/authorization, B1/B2, U1/U2 decision, Phase C/D,390px repair, export/delivery, deployment/reset/operator data, staging/commit/push/PR. All other file hashes remain identical to the pre-F0 inventory. Exact final status/changed-file manifest: `.cache/frontend-f0/final-status.txt`, `.cache/frontend-f0/files-changed.txt`. STOP for human F0 acceptance.
