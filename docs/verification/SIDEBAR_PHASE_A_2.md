# Sidebar final information architecture — Phase A.2

> Historical implementation-time verification snapshot. Phase A through A.4 is now **human accepted**. The [final accepted Sidebar contract](../plans/FRONTEND_UX_AND_FUNCTIONAL_DEFECTS.md#5-issue-a--final-accepted-sidebar-contract) governs current behavior; intermediate layouts/widths and pending-review wording below retain their original historical meaning. Evidence and recorded results are unchanged.

2026-10-11. Phase A.2 implementation/verification complete; human acceptance and sidebar closure pending. No Phase B work. [Dedicated evidence](sidebar-phase-a2/) preserves earlier A0/A/A.1 and accepted evidence unchanged.

## Final presentation

Overview (German Übersicht, English Dashboard) is a standalone first navigation entry without a heading. Workspace contains Assets, Categories, Manufacturers, Products, Locations, Owners in that order. Administration contains Users, Tenants, IServ, Log in that order, filtered by existing permission predicates. Exactly two headings for GLOBAL ADMIN; empty groups omitted. German navigation-only labels are IServ and Protokoll; English IServ and Log. Page titles, API concepts and routes remain descriptive/unchanged. No extra separators or intermediate groups.

The same desktop collapse control remains in the Workspace heading row. Sidebar primitives/CSS and collapse mechanism are unchanged from A.1. Concrete nav-item/nav-active classes still cross the Radix Slot boundary; semantic group/item keys remain language/state-independent.

The existing whole-brand React Router Link retains its context-aware destination: /dashboard for GLOBAL all-Tenant context; /tenants/{tenantId}/dashboard for selected GLOBAL context or the authenticated TENANT context. Added explicit localized Home accessible name and focus ring. No image click handler/document reload; the same topbar/brand node survives collapse and application navigation. Normal draft blocking and authentication are unchanged.

Authorization visibility verified with intercepted read-only frontend fixtures for all four combinations:

| Principal | Administration navigation |
| --- | --- |
| GLOBAL ADMIN | Users, Tenants, IServ, Log |
| GLOBAL USER | Tenants |
| TENANT ADMIN | Users, Log |
| TENANT USER | None; heading omitted |

Workspace/Overview remain visible under the existing rules. Target routes and existing Tenant-context requirements are unchanged; menu visibility does not replace backend enforcement. No backend/security change, so no new claim of backend authorization testing.

## Scope/preservation

Pre-A.2 hash comparison changed exactly Shell.tsx, locales/de.json, locales/en.json and tests/sidebar-layout.spec.ts. [Phase-only diff](sidebar-phase-a2/phase-a2-only.patch) compares those files to the pre-A.2 worktree. Existing rendering-stability work, sidebar primitive, search-stability suite, repair plan and prior evidence untouched. New dedicated verification artifacts only. No new dependencies, data, migrations, staging, commit, push or PR.

## Commands/results

From frontend, Node 24.15.0:

```sh
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm test
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm run build
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm run dev -- --port 12080
PATH=/opt/homebrew/opt/node@24/bin:$PATH KELIOS_BASE_URL=http://127.0.0.1:12080 PLAYWRIGHT_BROWSERS_PATH="$PWD/../.cache/playwright" npm run test:smoke -- tests/sidebar-layout.spec.ts tests/search-stability.spec.ts
```

- **51 unit tests passed, 0 failed**, four files, Vitest 5.0.3.
- **TypeScript/Vite 8.3.4 production build passed**.
- **35 Chromium browser tests passed, 0 failed, 0 skipped (46.2s)**: 19 sidebar + 16 unchanged rendering-stability cases.
- git diff --check **passed**.

Existing sidebar tests strengthened: all semantic links remain identical through collapse/locale changes, exact final bilingual heading/item order/routes, four-role visibility, GLOBAL/selected-Tenant/TENANT Home keyboard routing and document/shell identity. Existing A/A.1 geometry, active classes, focus, draft/scroll and Sheet assertions retained. Intercepted browser responses create no persistent records.

From root:

```sh
docker compose --env-file .cache/sidebar-a/compose.env -p kelios-local-6a18b7783357 -f deploy/compose.yaml -f deploy/compose.postgres.yaml -f deploy/compose.auth.yaml build frontend
docker compose --env-file .cache/sidebar-a/compose.env -p kelios-local-6a18b7783357 -f deploy/compose.yaml -f deploy/compose.postgres.yaml -f deploy/compose.auth.yaml up -d --no-deps --no-build --wait frontend
PATH=/opt/homebrew/opt/node@24/bin:$PATH PLAYWRIGHT_BROWSERS_PATH="$PWD/.cache/playwright" node .cache/sidebar-a2/inspect.mjs
docker ps --filter label=com.docker.compose.project=kelios-local-6a18b7783357 --format '{{.Names}} {{.ID}} {{.Status}}'
docker image inspect kelios-frontend:phase0 --format '{{.Id}}'
git diff --check
```

Frontend-only Docker rebuild/redeploy passed. All services healthy. Existing backend/PostgreSQL IDs unchanged; no database reset or business records created. Production browser used normal HTTPS authentication with existing private-file administrator credential. No credentials/tokens in evidence. Temporary frontend dev server stopped after testing; production environment remains running with existing green branding.

## Production visual/runtime evidence

[Image/CSS identity](sidebar-phase-a2/assets.json), [runtime measurements](sidebar-phase-a2/runtime.json), bilingual screenshots at 1440/1024/768/390px include expanded/collapsed desktop and mobile Sheet.

Visual inspection confirms standalone Overview, two concise groups, Workspace-row toggle, horizontal expanded links, active styling, icon-only collapsed links/tooltips, and mobile navigation with no competing desktop toggle. Logo remains 160×36px before/after collapse; full-width topbar remains 64px high. Sidebar widths 256/80px. Mobile topbar controls fit at 390px.

Stored DOM identity checks all true: main/page/topbar/brand/image/control/all navigation anchors. Collapse causes zero API requests and no route change; control retains focus. Locale changes preserve all navigation identities in automated coverage. Production Home keyboard activation navigates from Assets to /dashboard with the same document/topbar/brand/navigation. Mobile Sheet has one visible navigation tree and final grouping; regression tests verify close focus return.

First diagnostic script completed visual capture but its added separate page.goto to localhost/assets failed with ERR_CONNECTION_REFUSED. Changed that diagnostic step to normal in-app Assets link navigation, consistent with the operation under review; full rerun succeeded, zero page errors. No application/security change made for the diagnostic failure. Existing development NO_I18NEXT_INSTANCE warnings remain outside scope. Firefox/Safari/physical assistive technology and real IServ provider not tested. Real IServ interoperability remains MANUAL VERIFICATION PENDING.

No known remaining sidebar defect in the verified Chromium matrix. Ready for human acceptance/closure; Phase B requires separate approval.
