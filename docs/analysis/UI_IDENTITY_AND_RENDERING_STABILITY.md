# UI identity and rendering stability audit

Date: 2026-10-11. Authority: ARCHITECTURE.md §12, approved user instruction. This audit concerns the post-roadmap frontend; accepted Phase 0–4 specifications/evidence and historical ADRs are not rewritten. No backend/Domain/JWT policy changes are required.

## Audited implementation

Reviewed all frontend React key expressions, request/data lifecycle (`useRead`, OrganizationPicker and AuthSession), page/route keys, Dashboard, resource tables, history, pickers, administrative forms, dialog/tab/sidebar primitives, initial lazy/Suspense boundaries, locale switching, and CSS animation/loading behavior. Re-rendering and new JSON object references are not treated as defects. The prior focused search fix remains part of the uncommitted working state and is incorporated in this audit.

| Area | Finding / cause | Resolution / disposition |
| --- | --- | --- |
| Resource search, filters, sort, pagination | Original `useRead` cleared data on every query URL change, removing table/pager and replacing them with skeletons | Prior focused fix retains same-context data with a fixed-height updating label, latest-request guards and explicit initial/error/empty states. Tests observe DOM identity during pending/equivalent responses |
| Organization discovery | Originally cleared results before an existing 150 ms debounce | Prior focused fix retains disabled pending choices, preserves debounce and rejects obsolete responses. No routing/authority changes |
| Ordinary token renewal | The focused fix invalidated data on every notification/new Identity object, even when the same session and authorization remained unchanged | Read identity uses existing login/logout generation plus User UUID/scope/Tenant/role/status. Same-context token renewal retains content and does not issue a redundant refetch. A read-only `AuthSession.contextVersion()` exposes the existing counter for presentation; no token/request/authentication semantics change |
| Session/authorization changes | Hook invalidation alone did not reset independently stored selected rows/drafts in a still-mounted parent | Shell view boundary includes session/User/scope/Tenant/role/status, workspace and actual pathname. Genuine context changes intentionally replace the logical view; token renewal, query strings, language and request state are absent from its key |
| Dashboard metrics | Key was translated title, so a language change recreated cards and their read hooks | Stable semantic metric IDs; translated titles are display-only |
| Sidebar groups / collapse | Group key was translated heading. Collapse switched each link between a `div` wrapper and Tooltip subtree | Stable group IDs and one persistent Tooltip/Trigger/Link tree. Tooltip content and text visibility change without recreating navigation links |
| History field rows | Key was localized field label | Key is original machine field name; stored data and labels remain separately rendered |
| Merge reference pickers | Keys included refresh revision, recreating picker input/search state after Merge refresh | Stable source/target keys; refresh revision is an explicit read dependency, not component identity. Successful Merge still intentionally clears executed source/target selections |
| Merge blocker list | Used array-index keys for identifiable blocker values | Stable blocker text keys; no new API/machine mapping |
| OIDC form | Every new response object reapplied fields, potentially replacing an existing edit draft | Initialize draft fields once per mounted context; subsequent reads update authoritative configuration version without resetting input values |
| Rows and table cells | Persistent entity UUIDs, column IDs and cell IDs; TanStack owns a stable mounted instance | Matches invariant. New response objects may recompute rows without remounting unchanged entities. No equality cache/memoization added |
| Asset/registration fields, reference options, language choices, Tenant menus, AssetEvent list | Keys are fixed field/machine identifiers, reference UUIDs, locale codes, Tenant UUIDs and event UUIDs | Matches invariant |
| Dashboard/read placeholders | Conditional skeletons depend on absence of valid data, not a standalone pending flag | With corrected read lifecycle they cover initial/context loading. Error replacement remains an explicitly failed read, not successful current data |
| Dialogs, mobile Sheet and tabs | Opening/closing deliberately mounts/unmounts affected content | Intentional interaction transitions. No search effect closes them. Existing draft values live in their application owners. Representative modal/tab stability is tested; all permutations are not claimed |
| App/Authentication lazy Suspense | Lazy components declared at module scope; router is stable during normal application use; branding initialized once before mount | Initial unavailable code/view fallback is appropriate. Query updates do not re-lazy-load the view. Logout, PENDING/ACTIVE changes and actual page navigation deliberately change logical views |
| CSS | Skeleton pulse; normal hover/focus transitions; reduced-motion override | No route/query animation key found. Keeping result DOM mounted avoids restarting loading animations; no visual redesign required |

## Retention and reconciliation contract

Within one logical resource and authorization context, query changes update request state immediately while keeping valid displayed data. Stable entity keys reconcile new responses in place. Equivalent responses do not require deep equality comparisons. Pending picker controls are disabled; retained options are not asserted to match a new query. On failed update, the existing failed-read policy clears results and displays a localized error. Empty success installs the empty collection.

Context changes discard results before rendering them in the new context, and response guards reject late results from prior requests/contexts. The context includes the existing login/logout generation and signed User authorization facts. An ordinary refresh with identical facts is not a new session. Resource paths distinguish Tenant/collection/target; Audit `tenantId`/`globalOnly` changes also change workspace context. Backend authorization remains authoritative; these are frontend state-isolation measures.

## Verification executed

From `frontend`, using Node 24:

```sh
npm test
npm run build
npm run dev -- --port 12080
KELIOS_BASE_URL=http://127.0.0.1:12080 PLAYWRIGHT_BROWSERS_PATH="$PWD/../.cache/playwright" npm run test:smoke -- tests/search-stability.spec.ts
```

From repository root: `git diff --check`.

- Unit suite: **51 passed**, four files; includes unchanged-session refresh versus login/logout generation behavior and locale resource completeness.
- Production TypeScript/Vite build: **passed**.
- Chromium focused browser suite: **16 passed**. Uses explicitly test-only intercepted HTTP responses against the real frontend components; no production bypass or database deployment. Chromium sandbox remains enabled. Temporary frontend server is stopped after checks.
- Browser coverage includes initial loading; mounted tables/pagers/input focus during pending/equivalent requests; newest-request ordering; empty/error states; all five Catalog/place collections; Audit filters; disabled stale reference/organization options; exact virtual-clock debounce; workspace/resource/session/role/account/logout boundaries; dashboard/sidebar localization and collapse; same-context token renewal; persistent history field rows/tab selection; equivalent-response row DOM identity, horizontal table scroll, page scroll and browser text selection.
- The earlier search runs encountered sandbox browser-launch and test-harness clock/import/response-release issues; these were corrected before the passing suite. Expanded invariant tests passed on their first run.

## Limits and remaining observations

This is a complete static audit of the current frontend files, with representative deterministic runtime coverage, not a proof of every browser/UI permutation. Merge picker refresh identity and OIDC draft initialization are structurally corrected but do not yet have dedicated end-to-end regression cases in this run. Safari/Firefox, actual assistive technology, layout under every dataset/zoom/font, every dialog/control state and production network timings are unverified. Browser fixtures prove presentation behavior, not backend permission correctness or real IServ interoperability. No accepted historical verification matrix was recreated; real IServ remains manual verification pending.

The dev server emitted an existing early `NO_I18NEXT_INSTANCE` warning; visible English/German labels and locale completeness tests passed. Its startup/module-order cause was not changed as part of the identity audit, and no claim is made that all runtime warnings have been resolved.

No caching/fetching framework, blanket memoization, new dependencies, backend policy changes, deployment CLI or Phase 5 work is introduced. No commit, push or PR is performed.
