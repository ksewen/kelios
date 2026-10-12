# Sidebar/topbar ownership — Phase A.1 verification

> Historical implementation-time verification snapshot. Phase A through A.4 is now **human accepted**. The [final accepted Sidebar contract](../plans/FRONTEND_UX_AND_FUNCTIONAL_DEFECTS.md#5-issue-a--final-accepted-sidebar-contract) governs current behavior; intermediate layouts/widths and pending-review wording below retain their original historical meaning. Evidence and recorded results are unchanged.

2026-10-11. Phase A.1 implemented; human visual acceptance pending. No Phase B work. Dedicated [evidence](sidebar-phase-a1/) is separate from Phase A0, Phase A and accepted roadmap/UI/CLI evidence.

## Cause and correction

Previously Navigation owned the logo inside the width-changing sidebar and passed `compact={collapsed}`. Logo therefore deliberately switched its image classes from `h-9 max-w-40` to `h-8 w-10`; the branding container also followed sidebar width. This was layout ownership coupling, not missing CSS.

Shell now renders one stable full-width topbar, including one normal Logo, above the sidebar/content row. The provider arranges those regions vertically; only the desktop sidebar changes width (256/80px). The sidebar is sticky below the 64px topbar. The desktop icon toggle is in the Workspace heading row, right aligned when expanded and centered when collapsed. Its semantic button, aria-controls, localized aria-label/tooltip and aria-expanded survive collapse. No persistence added.

The separate existing mobile trigger remains in the topbar and opens the existing Sheet. Mobile Navigation omits the desktop toggle. At narrow widths the Tenant context text is hidden and topbar button spacing reduced, preserving accessible dropdown names and avoiding clipping without shrinking branding. No route, authorization, page key, NavLink concrete-class fix or application command changed.

Changed existing files: Shell.tsx, components/ui/sidebar.tsx, tests/sidebar-layout.spec.ts, tests/search-stability.spec.ts. The latter changes only the existing desktop-toggle locator to its new accessible name. No locale/styles/backend/schema/repair-plan changes. [A.1-only patch](sidebar-phase-a1/phase-a1-only.patch) compares the pre-A.1 worktree, preserving all previously approved uncommitted changes.

## Commands and results

From frontend, Node 24.15.0:

```sh
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm test
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm run build
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm run dev -- --port 12080
PATH=/opt/homebrew/opt/node@24/bin:$PATH KELIOS_BASE_URL=http://127.0.0.1:12080 PLAYWRIGHT_BROWSERS_PATH="$PWD/../.cache/playwright" npm run test:smoke -- tests/sidebar-layout.spec.ts tests/search-stability.spec.ts
```

- Unit: **51 passed, 0 failed**, four files (Vitest 5.0.3).
- TypeScript/Vite 8.3.4 production build: **passed**.
- Final Chromium browser run: **26 passed, 0 failed, 0 skipped (36.4s)**: 10 sidebar cases and 16 rendering-stability cases.
- Two earlier runs: 22 passed/4 failed each, at the new tooltip assertion immediately after keyboard activation. Radix closes the tooltip on activation; hover while still focused did not reopen it. The test now asserts focus retention first, then deliberately moves focus away and back to verify the changed tooltip. No production behavior/test expectation was weakened. A subsequent run passed 26 cases before the small mobile spacing adjustment.
- One final rerun attempt was sandbox-blocked at Chromium launch: 26 infrastructure failures, no application assertions executed. Approved outside-sandbox rerun produced the final green result above.
- Existing development NO_I18NEXT_INSTANCE warnings remain outside this presentation scope. Production inspection recorded no page errors; this is not a claim that all console warnings/browser engines were audited.

From root:

```sh
docker compose --env-file .cache/sidebar-a/compose.env -p kelios-local-6a18b7783357 -f deploy/compose.yaml -f deploy/compose.postgres.yaml -f deploy/compose.auth.yaml build frontend
docker compose --env-file .cache/sidebar-a/compose.env -p kelios-local-6a18b7783357 -f deploy/compose.yaml -f deploy/compose.postgres.yaml -f deploy/compose.auth.yaml up -d --no-deps --no-build --wait frontend
PATH=/opt/homebrew/opt/node@24/bin:$PATH PLAYWRIGHT_BROWSERS_PATH="$PWD/.cache/playwright" node .cache/sidebar-a1/inspect.mjs
docker ps --filter label=com.docker.compose.project=kelios-local-6a18b7783357 --format '{{.Names}} {{.ID}} {{.Status}}'
docker image inspect kelios-frontend:phase0 --format '{{.Id}}'
docker exec kelios-local-6a18b7783357-frontend-1 sh -c 'sha256sum /usr/share/nginx/html/assets/index-B_inrATc.css /usr/share/nginx/html/assets/Shell-*.js'
git diff --check
```

Build, frontend-only redeployment, normal HTTPS browser authentication/inspection, health and diff checks passed. Read-only Docker access required sandbox approval. Backend/PostgreSQL container IDs remained unchanged; no database recreation or business data creation occurred. The temporary dev server was stopped; disposable production environment remains running with existing green branding.

## Runtime and visual evidence

[Assets](sidebar-phase-a1/assets.json) identify the production frontend image and served CSS/container Shell hashes. [Runtime measurements](sidebar-phase-a1/runtime.json) record English/German at 1440,1024,768,390px, expanded/collapsed desktop and mobile Sheet screenshots.

At both desktop widths and both languages:

- Logo image: **160×36px**, x=24,y=13.5, unchanged expanded/collapsed. Logo link: **160×40px**. Topbar: viewport width×64px, unchanged position/dimensions.
- Sidebar: **256px expanded / 80px collapsed** below topbar.
- Navigation anchors retain real `nav-item nav-active` tokens for the active route, `display:flex`, `flex-direction:row`; expanded icons and labels align horizontally. Collapsed labels absent; accessible names/tooltips preserved.
- Workspace heading and compact control share a row. The same control becomes the localized expand control when collapsed.
- Stored DOM equality: main/page/topbar/logo/image/control/link all true after collapse/expand. Route unchanged, control focused, collapse-triggered API requests **zero**.

At 768/390px: one visible navigation tree in the Sheet; no desktop collapse control in the Sheet. Mobile close returns focus to the original trigger in automated coverage. Logo remains full size. At 390px all visible topbar buttons fit; German account button x=343,width=35,right=378 within the 390px viewport. Bilingual screenshots visually inspected for sidebar alignment, branding, active state, toggle placement and mobile layout. Intentional sidebar resizing may reflow content; stable DOM identity does not prohibit that.

Regression coverage includes logo/topbar/control identity and dimensions, heading/control geometry, NavLink class composition, tooltip/accessibility, locale-preserved semantic nodes, unrelated input/form drafts and scroll, mobile focus return and the existing async search/context isolation suite. Browser fixtures intercept reads only and create no persistent records.

## Boundaries and limits

Pre-A.1 hash comparison confirms only the four listed existing files changed. Dedicated evidence files added. Prior accepted evidence, repair plan and other pre-existing work preserved. No staging/commit/push/PR. No Phase B, new dependencies, persistence, backend or authentication change. Real IServ interoperability remains MANUAL VERIFICATION PENDING. Safari/Firefox and physical assistive technology were not tested.
