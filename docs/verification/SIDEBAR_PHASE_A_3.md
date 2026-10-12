# Sidebar logo-only Home navigation — Phase A.3

> Historical implementation-time verification snapshot. Phase A through A.4 is now **human accepted**. The [final accepted Sidebar contract](../plans/FRONTEND_UX_AND_FUNCTIONAL_DEFECTS.md#5-issue-a--final-accepted-sidebar-contract) governs current behavior; intermediate layouts/widths and pending-review wording below retain their original historical meaning. Evidence and recorded results are unchanged.

2026-10-11. Implementation and verification complete; final human closure pending. No Phase B work. Dedicated [evidence](sidebar-phase-a3/) does not overwrite A0/A/A.1/A.2 or accepted baseline evidence.

## Change

Removed only the sidebar Overview/Dashboard group/item and its now-unused icon import/navigation label branch in Shell.tsx. Final order: Workspace (Assets, Categories, Manufacturers, Products, Locations, Owners); Administration (Users, Tenants, IServ, Log), under unchanged permission predicates. German labels: Arbeitsbereich / Administration and Assets, Kategorien, Hersteller, Produkte, Standorte, Eigentümer / Benutzer, Mandanten, IServ, Protokoll. No replacement Home icon/item.

Dashboard content and route remain unchanged. The existing whole-brand React Router Link is the normal Home entry; its accessible name, focus style and GLOBAL/TENANT context-aware routing are unchanged. No row is active on Dashboard. Existing control location, semantic group/item keys, concrete Radix/NavLink classes, sidebar primitive, locale resources and mobile Sheet unchanged.

Exactly two pre-existing files changed relative to the pre-A.3 worktree: frontend/src/app/Shell.tsx and frontend/tests/sidebar-layout.spec.ts. [Phase-only patch](sidebar-phase-a3/phase-a3-only.patch) and source hashes record that delta. All previously approved uncommitted work preserved. Dedicated evidence added; no staging/commit/push/PR.

## Tests and commands

From frontend, Node 24.15.0:

```sh
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm test
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm run build
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm run dev -- --port 12080
PATH=/opt/homebrew/opt/node@24/bin:$PATH KELIOS_BASE_URL=http://127.0.0.1:12080 PLAYWRIGHT_BROWSERS_PATH="$PWD/../.cache/playwright" npm run test:smoke -- tests/sidebar-layout.spec.ts tests/search-stability.spec.ts
```

- **51 passed / 0 failed**, four unit-test files, Vitest 5.0.3.
- TypeScript / Vite 8.3.4 production build **passed**.
- **35 passed / 0 failed / 0 skipped (46.3s)** Chromium browser cases: 19 sidebar, 16 unchanged rendering-stability cases.
- git diff --check **passed**.

Updated only obsolete Overview-entry expectations and strengthened final assertions: no bilingual desktop/mobile Dashboard item; Workspace first and Administration second; unchanged ordered resource/admin routes; zero active sidebar rows on Dashboard after brand Home navigation. Existing identity, dimensions, collapse, tooltip, focus, form draft/scroll, active Assets and four-role visibility assertions retained. GLOBAL all-Tenant, selected GLOBAL Tenant and TENANT user Home keyboard routing passed without document/shell replacement. Frontend intercepted read fixtures create no persistent application records.

From root:

```sh
docker compose --env-file .cache/sidebar-a/compose.env -p kelios-local-6a18b7783357 -f deploy/compose.yaml -f deploy/compose.postgres.yaml -f deploy/compose.auth.yaml build frontend
docker compose --env-file .cache/sidebar-a/compose.env -p kelios-local-6a18b7783357 -f deploy/compose.yaml -f deploy/compose.postgres.yaml -f deploy/compose.auth.yaml up -d --no-deps --no-build --wait frontend
PATH=/opt/homebrew/opt/node@24/bin:$PATH PLAYWRIGHT_BROWSERS_PATH="$PWD/.cache/playwright" node .cache/sidebar-a3/inspect.mjs
docker ps --filter label=com.docker.compose.project=kelios-local-6a18b7783357 --format '{{.Names}} {{.ID}} {{.Status}}'
docker image inspect kelios-frontend:phase0 --format '{{.Id}}'
git diff --check
```

Frontend-only rebuild/redeployment passed; all services healthy. Backend/PostgreSQL container IDs unchanged. Existing normal HTTPS login used private-file administrator credential; no passwords/tokens in evidence. No database reset, business-data creation or other setup. Production deployment remains running; temporary dev server stopped.

## Actual production observations

[Runtime measurements](sidebar-phase-a3/runtime.json), [image/asset identity](sidebar-phase-a3/assets.json) and screenshots cover English/German at 1440/1024/768/390px, expanded/collapsed desktop and mobile Sheet.

Workspace is the first visible heading; only Administration follows, for the existing GLOBAL ADMIN. No Home/Dashboard icon or item, including the mobile Sheet. Expanded links remain horizontally aligned; collapsed tooltips/accessible names and Workspace-row control preserved. One visible mobile navigation tree, no desktop-toggle conflict. Existing automated tests confirm Sheet focus return.

Logo remains **160×36px**, topbar **64px**, sidebar **256/80px**. All observed main/page/topbar/brand/image/control/navigation node identities retained during collapse, zero API requests, unchanged route and focused control. Tests also preserve all remaining links through locale changes.

Actual mouse click on the production brand returns from Assets to /dashboard with the same document/topbar/brand/navigation and **zero active sidebar links**. The first diagnostic snapshot sampled immediately after history URL change and before React committed Dashboard, seeing the previous Assets active class. The diagnostic was corrected to wait for the rendered Dashboard heading, then rerun; final measurement is zero. No product change was needed for this measurement race. Production page-error list empty.

## Limits

No known remaining sidebar issue in the verified Chromium matrix. Firefox/Safari and physical assistive technology not tested. Existing development-only NO_I18NEXT_INSTANCE warnings remain outside scope. Backend/domain/auth/authorization, Dashboard content, Merge/create/audit and repair plan untouched. Real IServ interoperability remains MANUAL VERIFICATION PENDING. Phase A ready for final human closure; Phase B not started.
