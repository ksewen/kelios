# F5 — Audit criteria and client-local time

Status: IMPLEMENTED / PENDING HUMAN ACCEPTANCE, D16/D17 only. F4 unblocked scope remains HUMAN ACCEPTED / CLOSED, 2026-10-13, including human manual inspection. F4 evidence remains byte-identical; exact Product optimistic-conflict subset B2/D28 and anonymous routing B1/D29 remain BLOCKED / NOT AUTHORIZED.

## Implementation

Audit uses endpoint-owned AuditItem/AuditQuery contracts: actor UUID; exact action, targetType and target UUID; GLOBAL ADMIN Tenant/globalOnly narrowing mutually exclusive; independently optional inclusive from/until. TENANT ADMIN never sends GLOBAL scope criteria or loads the Tenant collection. No q, actor-name search, live User joins or username inference. Only occurredAt ASC/DESC is exposed as a keyboard-operable header with aria-sort, default DESC. All criteria are applied atomically; typing/mode changes remain pending without requests. Changed Apply, sort or size resets page0; page/sort/size use applied filters. Semantically unchanged Apply avoids a new request. Rows/counts/order remain server-authoritative.

Client-local datetime inputs convert to absolute ISO UTC with Z. Date-only mode for either bound is independent: from = local day start, until = last PostgreSQL-representable microsecond before the next local day starts. Local-calendar arithmetic, not a fixed24-hour duration, handles DST23/25-hour days and midnight DST gaps. Skipped datetime clock values and invalid/reversed ranges are rejected before dispatch. Browser-native repeated-hour resolution chooses the earlier occurrence, explicitly explained in DE/EN help; UTC bound preview shows the result. This interface does not offer a separate offset/fold selector. Whole-day mode covers both occurrences. No backend precision/timezone/stored-timestamp change.

Shared timestamp display uses the client's timezone with localized date/time and timezone indication, including metadata, User Email verification, Audit list/detail, Asset loan/history and known history change timestamps. Source ISO values and raw immutable technical JSON remain unchanged; no recursive snapshot rewriting.

Loan expectedReturnAt uses optional local datetime input; blank sends null, valid input sends an absolute UTC Instant, skipped clock input disables submission and shows validation feedback. Existing expectedVersion, borrower/note, dirty/discard, failure retention, successful clearing and no-replay behavior remain. All loan writes are intercepted. Owning-Asset history query remains bounded and fixed-order without new filters/sorts.

## Exact task file manifest

1. docs/plans/FRONTEND_BACKEND_INTEGRATION_PLAN.md
2. docs/verification/FRONTEND_INTEGRATION_F5.md
3. frontend/src/features/inventory/pages.tsx
4. frontend/src/i18n.ts
5. frontend/src/i18n.test.ts
6. frontend/src/local-time.ts
7. frontend/src/local-time.test.ts
8. frontend/src/locales/en.json
9. frontend/src/locales/de.json
10. frontend/tests/f5-audit-time.spec.ts
11. frontend/tests/inventory.spec.ts

The last file's excluded real-backend loan test is aligned with the new local input label/value, but was NOT executed. No other source/test/normative/backend/deployment files changed by F5.

## Verification

Commands run in frontend with Node24 on PATH. Intercepted browser baseURL http://127.0.0.1:12180, separate fixture Vite server; PLAYWRIGHT_BROWSERS_PATH="$PWD/../.cache/playwright".

- `npm test -- src/local-time.test.ts src/i18n.test.ts`: **38 passed / 2 files, zero failed/skipped**.
- Initial unit run189: 188 passed / 1 failed because an old assertion required UTC display. Updated that assertion to client-local Intl formatting; final results follow.
- `npm test`: 190 passed / 9 files, zero failed/skipped. Includes22 new timezone/conversion unit cases, executed in isolated Node processes with explicit TZ; UTC, Europe/Berlin, America/New_York, America/Los_Angeles, America/Santiago. 12/24-hour locale formatting, cross-calendar-day display, optional/reversed/invalid boundaries, literal UTC conversion, microsecond upper bounds, spring/fall DST and repeated/skipped hour handling.
- `npm run build`: PASS, TypeScript + Vite production build.
- Focused Chromium initial19: 13 passed / 6 failed due partial accessible selectors and an English12-hour display assumption. Corrected selectors/locale expectations. Intermediate22-case runs20/2 and21/1 exposed an obsolete Next label in new tests and a table-identity assertion crossing a Tenant boundary. The corrected assertion explicitly verifies old-Tenant table invalidation, then same-scope page/sort identity. Focused22 rerun passed. Added midnight-DST coverage afterwards; final full run includes all23 F5 browser cases.
- Full Chromium command: `KELIOS_BASE_URL=http://127.0.0.1:12180 PLAYWRIGHT_BROWSERS_PATH="$PWD/../.cache/playwright" npx playwright test f5-audit-time.spec.ts f4-resource-detail.spec.ts f3-resource-query.spec.ts api-foundation.spec.ts email-login.spec.ts f2-paging-lookup.spec.ts list-filtering.spec.ts collection-query.spec.ts merge-combobox.spec.ts form-lifecycle.spec.ts search-stability.spec.ts dialog-toolbar.spec.ts sidebar-layout.spec.ts administration-context.spec.ts --workers=2 --output=../.cache/frontend-f5/browser-all`. **299 passed, zero failed/skipped**, 4.2 minutes. An earlier broad attempt was interrupted for the midnight-DST correction and is not counted as successful full verification.
- DE/EN at1440/1024/768/390: keyboard Enter applies, actor/time labels, local timeline list/detail, modal Escape/focus, responsive filter controls, no page-level horizontal overflow. Fixture screenshots in ignored .cache/frontend-f5; visually inspected German390. English uses localized12-hour clock, German24-hour. No operator screenshots/records included.
- `authentication.spec.ts` and `inventory.spec.ts` excluded by default Playwright configuration; not counted as passed/skipped. No TLS write suites were enabled against manual data.

## Current-backend read evidence and limits

Used the existing supported GLOBAL ADMIN account at https://localhost:19443 and its trusted local certificate. Credentials/tokens and actual Audit records/actor identifiers were not copied into evidence. Bounded Audit GET results:

| Query | Result |
|---|---|
| Base DESC Page | 200, total4, items/page/size/totalElements |
| Actor UUID + ASC | 200, total4 |
| from only, from captured existing occurrence | 200, total1 |
| until only, until captured existing occurrence | 200, total4 |
| equal inclusive from/until | 200, total1 |
| captured actor/action/target/context + equal bounds | 200, total1 |

Safe Item fields confirmed: id,tenantId,actorId,actorDisplayName,action,targetType,targetId,occurredAt,data. No new User/Tenant/Asset/Audit fixture records were created. No live loan/write/delete/Merge performed. Available live authority was GLOBAL ADMIN only; TENANT scope/foreign/stale behavior uses intercepted fixtures and accepted backend evidence, not a manufactured account. Frontend fixtures do not prove SQL/security. Manual environment remains the separately deployed F4 build; F5 UI was tested from the current worktree on the isolated fixture server, not deployed. No operator environment/database was reset or altered by deployment.

## Preservation and remaining gates

Pre-F5 SHA-256 snapshot in ignored .cache/frontend-f5/before.json; actual delta restricted to the11-file manifest. All unrelated pre-existing tracked/untracked files and F2/F3/F4/historical verification contents preserved byte-for-byte. Shared files retain accepted changes underneath the F5 delta. Backend, OpenAPI, schemas, migrations, deployment, auth/session/client implementation and .idea untouched. Index empty, HEAD unchanged at31b336eb47225556da56c73954e832bb615e2c0c.

No F6/B1/B2/C/D, export/delivery or390px Product-table work started. This report does not mark F5 HUMAN ACCEPTED. STOP for human review.

## Final checks

`git diff --check`, all new/changed-file whitespace and relative documentation links: PASS. Exact11-file manifest verified against pre-F5 hashes; all unrelated contents and F2/F3/F4 evidence unchanged. Index empty, no commit. Separate fixture server stopped after verification; manual environment left running.

Final `git status --short` (includes pre-existing accepted and unrelated work):

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
 M frontend/tests/api-foundation.spec.ts
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
?? frontend/src/local-time.test.ts
?? frontend/src/local-time.ts
?? frontend/src/resource-contracts.test.ts
?? frontend/src/resource-contracts.ts
?? frontend/tests/f2-paging-lookup.spec.ts
?? frontend/tests/f3-resource-query.spec.ts
?? frontend/tests/f4-resource-detail.spec.ts
?? frontend/tests/f5-audit-time.spec.ts
```
