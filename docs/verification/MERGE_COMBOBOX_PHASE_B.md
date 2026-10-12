# Phase B — Searchable Merge combobox verification

Status: implementation and focused automated verification complete; **human acceptance pending**. Scope is Category and Manufacturer explicit Merge only. Phase A is human accepted and unchanged. Phases C, D and E were not started.

Verified on 2026-10-11 with Node v24.15.0, npm 11.12.1 and the repository-pinned Playwright 1.64.0 / Chromium. No dependency was added.

## Implementation

`frontend/src/components/ui/searchable-combobox.tsx` supplies an inline, non-modal input/listbox. The opened bounded list stays in the Dialog layout so its scroll container cannot clip floating candidates or paging controls; opening/closing is a deliberate control-state transition, while same-context pending refresh retains the existing options. `MergePick` in `frontend/src/features/inventory/pages.tsx` connects each controlled participant to the existing `useRead` and collection API. Only the two Merge `Pick` instances were replaced. All registration, reference and Product pickers retain their existing implementation.

Each participant has separate query text, selected UUID and captured display label. Selecting an option stores its UUID and label. Focusing a selected value selects its text; editing clears the old UUID immediately and starts a new query, resetting page to zero. Paging or same-context result refresh does not clear selection or require the selected UUID to occur in the current page. Explicit-ID entry displays that UUID when no captured label is available.

Search remains server-backed, literal and bounded: `size=25`, `page`, encoded `q`, on the existing authorized Category/Manufacturer collection. Previous/Next buttons in the popup expose further pages without fetching the full collection. Paging returns focus to the input before disabled pending buttons can lose focus. No debounce, client-only filtering, new caching or cancellation infrastructure was added.

The original `useRead` code is unchanged. Its context/resource guards immediately discard unauthorized-context results, retain same-context data and ignore obsolete request completions. The existing Shell context/route identity boundary resets resource-local selection when session, user, authorization, Tenant or resource changes. Pending retained options are visually subdued, `aria-disabled`, excluded from keyboard activation, and guarded against mouse selection. Errors are localized through the existing problem-code mapping; an error is distinct from an empty result and does not expose prior candidates as current results.

Options use UUID keys and expose name, existing Tenant UUID or Global context, and a short UUID suffix. Names and other Domain data are not translated. Locale changes retain the input and selected UUID.

Both UUIDs must be present and different before preview or confirmation can be submitted. The opposite selected UUID is non-selectable. Existing backend validation and GLOBAL ADMIN-only visibility are unchanged. Changing either participant clears preview immediately; a generation guard also rejects a late preview for an obsolete pair. Execution checks selection stability across confirmation and still submits only `sourceId` and `targetId` to the existing backend, whose fresh validation remains authoritative.

The existing advanced explicit-ID section is retained, collapsed by default. It remains useful for entering already-known UUIDs outside a currently browsed candidate page. It shares the same selection/preview invalidation and equal-ID guards, grants no authority, and does not duplicate the ordinary search/select pair.

## Accessibility and identity

The input has a localized label, `role=combobox`, `aria-autocomplete=list`, expanded state, listbox association and active-option association. Options have listbox/option semantics and selected/disabled state. ArrowDown/ArrowUp navigate valid options; Enter selects; Tab/Shift+Tab use the existing Dialog focus flow. Escape closes the open popup first, leaving the Merge dialog mounted. Because Radix listens at document capture, a narrowly scoped window capture handler intercepts Escape only from the open combobox subtree; no nested modal or focus trap was added.

Tests preserve actual retained option, input and surrounding Merge dialog DOM nodes across search, result arrival, paging and execution. Focus survives delayed requests. Locale changes preserve the semantic input and selected UUID. The existing rendering-stability suite covers resource/workspace/session isolation, ordinary tables, pending reference options and organization search, and remained unchanged.

## Commands and final results

Commands executed from `frontend/` (Node 24 path selected):

```sh
npm test
npm run build
PLAYWRIGHT_BROWSERS_PATH="$PWD/../.cache/playwright" \
  KELIOS_BASE_URL=http://127.0.0.1:12080 npm run test:smoke -- \
  tests/merge-combobox.spec.ts tests/search-stability.spec.ts tests/inventory.spec.ts
```

After adding a stronger retained-option DOM assertion, only the two affected tests were rerun with the same browser environment:

```sh
npm run test:smoke -- tests/merge-combobox.spec.ts --grep 'pending, late responses'
```

The temporary Vite server was started with `npm run dev -- --port 12080`. The browser tests used intercepted API responses, never the manual database.

| Check | Result |
| --- | --- |
| Unit tests | 51 passed in 4 files; 0 failed/skipped |
| Production build | Passed: TypeScript and Vite |
| Focused Chromium tests | 30 passed; 0 failed/skipped among selected tests |
| New Merge suite | 14 passed |
| Additional retained-option identity checks | 2 passed; 0 failed/skipped |
| Unmodified search-stability suite | 16 passed |
| `git diff --check` | Passed |

**Exclusion:** the existing Playwright configuration excludes `inventory.spec.ts` in non-TLS fixture runs. Its seven real-backend tests were not executed, despite its inclusion in the command. They authenticate and create business records, and must not be run against the empty manual environment. Its obsolete Merge select assertions were updated to use the combobox. A real-backend inventory-suite rerun remains unverified in this task; it needs a separately isolated fixture deployment. This exclusion is not counted as seven passing tests or seven Playwright skips.

Focused fixtures verify Category and Manufacturer source/target controls in English/German, mouse and keyboard selection, arrow navigation, Escape, Tab/Shift+Tab, initial/pending/empty/error states, out-of-order responses, disabled stale candidates, duplicate-name distinction, paging beyond the first page, page reset on query edits, selected UUID retention, deterministic selection edits, equal-ID denial, both participant preview invalidations, late preview rejection, execution payloads, DOM/focus identity and all four role presentations. They do not prove backend security; backend code and authorization are unchanged.

## Failures encountered and corrections

- The first build invocation used the wrong npm working directory; it failed before building. Subsequent commands ran in `frontend/`.
- Sandboxed Chromium launch failed; browser tests ran with approved execution permissions. This was infrastructure, not an application failure.
- Initial popup Escape tests exposed Radix document-capture dismissal of the parent Dialog. The combobox now consumes popup Escape at the correct boundary.
- A Shift+Tab test originally invoked the key through the source locator, which refocused that input first. It now uses the page keyboard to exercise actual focus return.
- An additional paging/Escape test exposed disabled paging-button focus loss. Paging now restores input focus before the pending transition. Assertions were preserved and final verification rerun.
- Screenshot review exposed clipping of the initially floating list by the existing Dialog scroll boundary. The bounded list now participates in the Dialog layout; a rendered geometry assertion checks that paging remains inside the visible Dialog.

## Production runtime inspection

Only the frontend was rebuilt/redeployed in existing disposable project `kelios-local-6a18b7783357`, using its existing Compose files and private configuration:

```sh
docker compose --env-file .cache/sidebar-a/compose.env \
  -p kelios-local-6a18b7783357 -f deploy/compose.yaml \
  -f deploy/compose.postgres.yaml -f deploy/compose.auth.yaml build frontend
docker compose --env-file .cache/sidebar-a/compose.env \
  -p kelios-local-6a18b7783357 -f deploy/compose.yaml \
  -f deploy/compose.postgres.yaml -f deploy/compose.auth.yaml \
  up -d --no-deps --no-build --wait frontend
```

Read-only Playwright inspection of `https://localhost:18443` used the existing administrator through normal HTTPS login. It inspected Category and Manufacturer dialogs in English/German, searched for a deliberately non-matching string, and checked empty-state rendering and popup Escape. Each dialog rendered two comboboxes and zero native selects, with input focus retained. No browser runtime errors or non-authentication API writes were observed. No business records were created; no database was reset. Existing backend/PostgreSQL containers were preserved.

**AUTOMATED VERIFIED:** representative Merge workflows against deterministic intercepted fixtures, with duplicate-name screenshots labelled `*-fixture.png`.

**RUNTIME INSPECTED:** actual production controls, empty searches and localization, labelled `*-empty.png`; these screenshots may show migration-provided UNKNOWN catalog rows in the background, not manually seeded data.

**MANUAL VERIFICATION PENDING:** real Category/Manufacturer selection, preview and execution against operator-created records. No real Merge was executed in the manual environment.

[Supporting evidence](merge-combobox-phase-b/) contains final unit/build/browser outputs, production runtime observations and asset hashes, image/container identities, changed-source hashes and screenshots. Private bootstrap input and deployment configuration remain outside evidence. The production environment remains running; the temporary Vite server is stopped.

## Limitations and scope protection

Chromium was verified; Firefox/Safari and physical assistive technology were not tested. The existing development-only `NO_I18NEXT_INSTANCE` warnings were observed during Vite fixture/harness runs and were not changed in this phase; production inspection recorded no page exceptions. Real IServ interoperability remains separately **MANUAL VERIFICATION PENDING**. Backend tests were not rerun because no backend/API/schema/authentication/authorization changes occurred. No new persistent business fixtures were created.

A pre-B file-hash comparison confirmed that existing files changed only in `pages.tsx`, `locales/en.json`, `locales/de.json` and `tests/inventory.spec.ts`. All Phase A source, tests, consolidated plan and verification documents remained unchanged. Prior uncommitted rendering-stability work is preserved. No Git staging, commit, push or PR occurred.
