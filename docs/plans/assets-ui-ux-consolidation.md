# Assets UI/UX consolidation

Status: **APPROVED; implementation prerequisite unresolved. No gate is IN PROGRESS.** HUMAN approved the proposed Plan direction on 2026-10-19 by requesting implementation. Gate execution remains subject to the exact-gate authorization and prerequisite below. [Implementation checkpoint](../../IMPLEMENTATION_STATUS.md); [remaining work](remaining-work.md).

## Baseline, ownership and prerequisite

Baseline for planning: HEAD `c564f24cd52cb0ef86710cdca3bb760efcf5cd60` plus the preserved dirty worktree on 2026-10-19. The provenance audit identified 89 predecessor paths with mixed prior-Plan changes in shared Asset UI, tests and status documentation, plus this separate Assets Plan. HUMAN accepted the outstanding Manufacturer collection-reset, final collection/detail and Tenant/User integration reviews on 2026-10-19, preserving their recorded evidence and limitations. HUMAN selected **resolve prior work first** rather than starting this Plan in an isolated worktree and separately authorized one combined predecessor/Assets planning commit. Gate A still requires that committed baseline and exact-gate authorization; no ambiguous hunk reconstruction or Asset implementation is implied.

The frontend owns Asset presentation and entry points; `DOMAIN.md` owns Asset policy, `ARCHITECTURE.md` owns durable UI/technical boundaries, and `docs/api/openapi.yaml` owns transport. This Plan changes no backend command boundary, DTO, schema, event type or audit semantics. Product Merge, other resource redesign, operator data and local deployment are excluded.

## Inspected current behavior and decisions

- `frontend/src/features/inventory/pages.tsx` uses shared `Rows`, `Command` and `Pick`. The current Asset columns put Tenant first in Global context and omit it in concrete-Tenant context. Asset Tag is already an accessible detail link. The existing Asset read projection supplies all nine requested columns and the detail's readable Product, Manufacturer, Category, Location and Owner names. Keep server filtering, sorting, paging and authorized collection scope unchanged.
- Detail currently places Edit, Exit and Reload above status badges. Overview uses a generic field grid and puts separate command buttons below. Retain current-loan facts in a compact separate section when a loan exists; preserve the History tab and metadata.
- `SearchableCombobox` opens on input focus; Radix dialog autofocus can therefore open the Standort/Eigentümer picker immediately. Reproduce this in an intercepted browser before correction. The intended shared control behavior is closed on focus, open on click, typing or arrow keys, with Escape, Tab and listbox keyboard behavior preserved.
- `AssetService.changeLocation` and `changeOwner` reject equal IDs after version and reference checks. Current forms permit unchanged Save. HUMAN has explicitly excluded backend no-op, AssetEvent, audit and version changes from this Plan. Disable unchanged Save in the UI. If an independent backend/Domain breach remains, report evidence and stop that affected work for a separate HUMAN decision.
- Bearbeiten remains only the existing four-field text metadata operation. Standort, Eigentümer, Ausleihbarkeit, Lebenszyklus and Zustand retain separate versioned commands and backend guards. Their new entry points must not imply new permissions. Standort is required and same-Tenant; Eigentümer is optional and same-Tenant; loanability rejects outstanding loans; terminal transition and deregistration guards remain intact.

## Gates and checkpoints

Each gate requires explicit authorization for that exact gate. Implement only that gate, record exact baseline, commands, counts, failures, repairs and limitations here and in `IMPLEMENTATION_STATUS.md`, then stop with **Review: pending human acceptance**. After explicit HUMAN acceptance, commit only its verified manifest if separately authorized, report hash and remaining status, and stop before the next gate. No automatic stage, commit, push, deployment, database reset or test/operator data creation.

### Gate A — Asset list and detail presentation

In `pages.tsx`, show **Tag | Lokale Kennung | Mandant | Produkt | Standort | Lebenszyklus | Zustand | Entleiher | Erstellt am** in both Global and Tenant contexts. Keep Tag link, stable row IDs, existing server query and pagination. Remove Reload; move Edit and Exit to the bottom detail action area without duplication. Remove the Asset-Überblick heading and render the requested four Overview rows with wrapping, readable missing values and existing loan details retained separately. Expected test changes: `assets-toolbar.spec.ts`, `form-lifecycle.spec.ts` and relevant detail/navigation assertions; localization only if a removed key is unused.

Focused frontend unit/build and intercepted Chromium: exact column order, row/header alignment, sort/filter/paging requests, Global/Tenant ownership, Tag navigation and focus, detail rows/action placement, 1440px/390px, German/English. No backend/API change. **Stop for HUMAN layout and responsive review.**

### Gate B — status indicators and loanability quick action

Use subtle semantic colors with text for the four actual Lifecycle and four Condition states. An indicator is an actionable button only for an authorized administrator with an available transition; it opens the existing dedicated dialog and preserves its graph, reason/confirmation, expectedVersion, errors and audit behavior. Displayed Ausleihbarkeit opens a confirmation showing current and opposite values; send the existing `PUT /assets/{id}/loanability` only on confirmation, and keep outstanding-loan blocking. Expected files: Asset detail UI, existing badge/button styling only if shared reuse is suitable, localization, focused tests.

Intercepted Chromium covers every state, terminal/read-only and role visibility, keyboard/focus, confirmation/cancel request count, exact payload/version, stale and loan-guard errors, German/English and 1440px/390px. Run frontend unit/build and affected focused browser tests. **Stop for HUMAN visual/interaction review.**

### Gate C — Standort/Eigentümer entry points and picker behavior

Make displayed Standort and Eigentümer values accessible entry points to their existing independent dialogs. Initialize each from the latest authoritative Asset UUID/reference; compare selected UUID or null semantically; disable Save and issue no mutation when unchanged. Preserve Owner clearing and required Location. Reproduce the autofocus issue, then remove shared combobox focus-to-open behavior while retaining explicit pointer/keyboard opening and focus return. Expected files: Asset detail UI, `searchable-combobox.tsx`, focused shared-control and Asset tests.

Intercepted Chromium covers initial closed picker, current readable value, mouse/keyboard open, unchanged zero-write, changed/cleared payload, authoritative refresh, stale-version response, Global/Tenant role boundaries, stable input identity, other combobox consumers, German/English and 1440px/390px. Run frontend unit/build and affected browser tests. Backend no-op behavior remains unchanged. **Stop for HUMAN workflow review.**

### Gate D — bottom action cleanup

After B and C replacement paths are accepted, remove standalone Ausleihbarkeit, Standort and Eigentümer action buttons. Keep Katalog neu zuordnen and its backend-derived references; remove its dialog-only explanatory paragraph and label its submit **Bestätigen / Confirm**. Preserve separate Edit, Exit, lifecycle, condition, loan/return and catalog paths. Expected files: Asset detail UI, locales if needed and action/dialog tests.

Intercepted Chromium proves every supported operation is reachable once, action order, catalog request and confirmation, keyboard/focus, German/English and 1440px/390px. Run frontend unit/build and affected browser tests. **Stop for HUMAN final action-hierarchy review.**

### Gate E — audit analysis, durable documentation and final regression

Record the unchanged-command finding and approved frontend-only handling. Recheck it against Domain and architecture without changing backend semantics. If evidence establishes an independent backend contract breach, stop and obtain a separate HUMAN decision before any backend/audit implementation. Synchronize accepted Asset UI rules in `ARCHITECTURE.md`, this Plan, checkpoint and backlog; change `DOMAIN.md` or OpenAPI only under separately approved contract scope.

Run full `cd backend && mvn -B verify`; `cd frontend && npm test`, `npm run build`, and the complete intercepted `npm run test:smoke` suite; OpenAPI parse/local-reference and documentation-link checks; preservation review; `git diff --check`. Record exact counts, initial failures, corrections, reruns, and isolated/intercepted versus live limitations. **Stop for final HUMAN acceptance.**

## Current checkpoint

2026-10-19: Plan approved in principle; no exact implementation gate has started. Prior-work provenance/acceptance/commit prerequisite remains unresolved. No Asset implementation or verification is claimed by this documentation checkpoint.
