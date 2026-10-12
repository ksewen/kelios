# Sidebar repair — Phase A verification

> Historical implementation-time verification snapshot. Phase A through A.4 is now **human accepted**. The [final accepted Sidebar contract](../plans/FRONTEND_UX_AND_FUNCTIONAL_DEFECTS.md#5-issue-a--final-accepted-sidebar-contract) governs current behavior; intermediate layouts/widths and pending-review wording below retain their original historical meaning. Evidence and recorded results are unchanged.

2026-10-11. Implementation authorized for Phase A only; user acceptance pending review. No Phase B–E work. Pre-existing rendering-stability work preserved. Dedicated evidence: [sidebar-phase-a](sidebar-phase-a/). A0 diagnostics and accepted Phase 0–4/UI/CLI evidence were not overwritten.

## Repair and scope

Shell Navigation now passes a concrete class string through Radix Tooltip.Trigger asChild, using React Router matchPath with end:true to preserve NavLink matching and retaining the existing Asset-detail special case. No route, permission, key, Tooltip or view-boundary change. Previously Radix Slot converted the functional className into source text, so nav-item/nav-active never matched.

The existing header control has a visible localized desktop Collapse/Expand sidebar label, directional icon, bordered button, type=button, aria-expanded and aria-controls. A matchMedia listener updates desktop/mobile state semantics; mobile still opens/closes the existing Sheet. Collapse remains in-memory. Existing Toggle navigation accessible name and stable button identity remain. No stylesheet change or new dependency.

Production changes: frontend/src/app/Shell.tsx, components/ui/sidebar.tsx, locales/en.json and de.json. New regression: frontend/tests/sidebar-layout.spec.ts (10 cases). Existing search-stability tests unchanged. [Phase-A-only patch](sidebar-phase-a/phase-a-only.patch) compares these files against the pre-Phase-A worktree, not HEAD.

## Commands and actual results

From frontend, Node 24.15.0:

```sh
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm test
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm run build
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm run dev -- --port 12080
PATH=/opt/homebrew/opt/node@24/bin:$PATH KELIOS_BASE_URL=http://127.0.0.1:12080 PLAYWRIGHT_BROWSERS_PATH="$PWD/../.cache/playwright" npm run test:smoke -- tests/sidebar-layout.spec.ts tests/search-stability.spec.ts
```

- Unit: 51 passed, 0 failed, 4 files, Vitest 5.0.3.
- Production TypeScript/Vite 8.3.4 build: passed.
- Final Chromium browser run: 26 passed, 0 failed, 0 skipped (36.1s): 10 sidebar + 16 unchanged stability tests.
- First browser run: 21 passed, 4 failed. Four new mobile assertions queried the background button by accessibility role while Radix intentionally hides background UI for the open modal. Corrected the new test locator to the button's explicit aria-controls attribute, retaining the expanded-state assertion. No production behavior was weakened. Added separate draft/scroll case before final run.
- Initial frontend-only dev server launch was sandbox-blocked (EPERM); approved rerun succeeded. Temporary server was stopped after verification.
- Existing development-only NO_I18NEXT_INSTANCE warnings appeared; previously documented initialization issue is outside Phase A. Production runtime captured no page errors. No claim about every console warning or browser engine is made.

From root, existing maintained Compose path, generated private env contains paths/non-secret settings only:

```sh
docker compose --env-file .cache/sidebar-a/compose.env -p kelios-local-6a18b7783357 -f deploy/compose.yaml -f deploy/compose.postgres.yaml -f deploy/compose.auth.yaml build frontend
docker compose --env-file .cache/sidebar-a/compose.env -p kelios-local-6a18b7783357 -f deploy/compose.yaml -f deploy/compose.postgres.yaml -f deploy/compose.auth.yaml up -d --no-deps --no-build --wait frontend
PATH=/opt/homebrew/opt/node@24/bin:$PATH PLAYWRIGHT_BROWSERS_PATH="$PWD/.cache/playwright" node .cache/sidebar-a/inspect.mjs
git diff --check
```

Frontend Docker build/redeployment and production inspection passed. git diff --check passed. Backend/database not reset/redeployed. No business data created. Production browser used normal HTTPS local authentication with the existing private-file administrator credential; no tokens/passwords copied into this evidence. No backend tests required for this presentation-only change.

## Production evidence

[Asset identities](sidebar-phase-a/assets.json) record image `sha256:af477068f6f9e98c4d793e0c8699673947cba64e4cc97d88dc500d7741d45c91`, served/container CSS `index-gisM_LHw.css` SHA-256 `70e248f24ecd4a5837d987f71bf3ab28c50db810f1fd00fbee11512ee85581ca`, and Shell bundle identity. Same existing green runtime brand, project kelios-local-6a18b7783357, HTTPS localhost:18443. All three services healthy; environment left running.

[Runtime JSON](sidebar-phase-a/runtime.json) records actual anchors, matching stylesheet rules, computed styles and geometry. Expanded anchors contain actual nav-item/nav-active tokens, display:flex, flex-direction:row, align-items:center and 12px gap. At 1440px, icon box x=24,y=119.5,w=18,h=18 and text x=54,y=118.5,h=20: equal vertical centers, label right of icon. Active background/text match runtime brand styling. Collapsed label absent; accessible name remains; Dashboard tooltip observed. Desktop width remains 256/80px.

English and German reviewed at 1440,1024,768,390px. Desktop uses collapse; mobile uses Sheet, one visible navigation tree. Hidden desktop tree plus intentionally mounted mobile tree remain existing responsive architecture; no additional duplicate tree introduced. Screenshots include expanded, collapsed, bilingual desktop and both mobile widths.

Runtime stored-node equality: main=true, page section=true, navigation link=true after collapse and expansion; URL unchanged, focused control remains Toggle navigation, collapse-triggered API request list empty. Browser regressions additionally verify unchanged input/form draft, page scroll, locale-preserved link identity, mobile focus return, active Asset-detail semantics and existing async/context isolation. Intentional layout resizing is not a remount or a promise of unchanged content wrapping.

## Preservation and limits

Pre-Phase-A hash comparison changed only the four authorized source files. Repair plan, backend, migrations, auth, Merge/create/audit functionality, existing stability tests and other source/specification files unchanged. No staging/commit/push/PR. No business fixtures seeded; intercepted browser fixtures are presentation-only and create no application records.

Original stacked-navigation defect is fixed in the inspected production Chromium build. Firefox/Safari and physical assistive-technology use were not tested. Other reported repair issues remain outside Phase A. Real IServ interoperability remains MANUAL VERIFICATION PENDING.
