# Sidebar sizing/control and operating-context label — Phase A.4

> Historical implementation-time verification snapshot. Phase A through A.4 is now **human accepted**. The [final accepted Sidebar contract](../plans/FRONTEND_UX_AND_FUNCTIONAL_DEFECTS.md#5-issue-a--final-accepted-sidebar-contract) governs current behavior; intermediate layouts/widths and pending-review wording below retain their original historical meaning. Evidence and recorded results are unchanged.

2026-10-11. Phase A.4 implementation and verification complete; human closure pending. Phase B not started. Dedicated [evidence](sidebar-phase-a4/) preserves all prior accepted and A0/A/A.1/A.2/A.3 evidence.

## Context-label investigation

Pre-A.4 Shell.tsx selected ui.allTenantsRead whenever GLOBAL scope had no route Tenant context. That translation was fixed English “All Tenants (read)” / German “Alle Mandanten (Lesen)”; it did not inspect role or a calculated read-only capability. This was presentation wording for an aggregate workspace, not evidence that the administrator was USER.

Inspected paths:

- Shell derives admin from user.role, global from user.scope, and GLOBAL workspace Tenant from the URL; TENANT workspace comes from authenticated user. Label selection was independent of admin. Those calculations and routes remain unchanged.
- AuthSession validates server-provided identity scope/role/status; tokens remain memory-only. OrganizationPicker handles pre-auth provider routing and uses identity only to protect async presentation context. It neither sets workspace authority nor supplies the suffix. Neither file changed.
- Assets uses aggregate /api/v1/assets when no Tenant is selected; registration control requires admin && tenant. Scoped registration route likewise requires Tenant/admin. Asset detail mutations use the loaded Asset and backend authorization. These guards were not changed.
- Management canCreate/writable distinguish role, GLOBAL Catalog authority and Tenant ownership; GLOBAL ADMIN can manage global Catalog in Global context. Thus the old “read” wording was misleading as a blanket permission description. Known separate create-workflow defects remain Phase C scope, not new authorization changes.
- Location/Owner management and OIDC configuration require concrete Tenant context under existing Shell branches. Audit/admin visibility and administration guards remain unchanged. Tenant list returns own Tenant for TENANT principals through existing scoped backend behavior.

Conclusion: **presentation-only suffix, no permission-calculation defect established in the traced path**. New ui.globalContext displays **Global** for GLOBAL aggregate operating context and its menu item, in both languages. Selected context displays its Tenant name. Existing ui.allTenantsRead remains a separate Audit filter concept (“All Tenants” / “Alle Mandanten”), now without the misleading permission suffix; it is not used as an operating-context label. No audit query/filter behavior changed.

Global is not authorization and does not make the aggregate Asset context writable. Existing Tenant-required mutations and role distinctions remain authoritative. No backend/auth/authorization change.

## Sidebar presentation

Collapse button is now a plain semantic button: localized aria-label, aria-expanded, aria-controls, type=button, directional icon and keyboard operation retained; visual Tooltip removed only from this control. Collapsed navigation Tooltip roots remain unchanged.

Fixed CSS responsive geometry: **208px expanded at lg desktop (1024px), 224px at xl (1440px), 64px collapsed**. Previous widths **256px expanded / 80px collapsed**. No JavaScript viewport sizing or label-dependent widths. At 768/390px existing desktop-hidden/mobile Sheet architecture remains unchanged.

Control uses a 40×40px hit target. Collapsed links center their icons inside the same sidebar content column, removing horizontal padding in collapsed presentation. Control and every nav icon center measure **x=31.5px**. No per-icon offsets. Expanded links explicitly retain single-line text, concrete nav-item/nav-active classes and horizontal alignment. Sidebar/topbar independent; logo/Home route unchanged; no Dashboard sidebar entry.

## Delta and preservation

Exactly five pre-existing files changed relative to pre-A.4: Shell.tsx, components/ui/sidebar.tsx, locales/en.json, locales/de.json, tests/sidebar-layout.spec.ts. [Phase-only patch](sidebar-phase-a4/phase-a4-only.patch) and hashes record the delta. styles.css, OrganizationPicker/auth, Dashboard, inventory pages, search-stability tests, backend/schema/repair plan and all other existing work untouched. No new dependency, staging, commit, push or PR. New dedicated evidence only.

## Commands/results

From frontend, Node 24.15.0:

```sh
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm test
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm run build
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm run dev -- --port 12080
PATH=/opt/homebrew/opt/node@24/bin:$PATH KELIOS_BASE_URL=http://127.0.0.1:12080 PLAYWRIGHT_BROWSERS_PATH="$PWD/../.cache/playwright" npm run test:smoke -- tests/sidebar-layout.spec.ts tests/search-stability.spec.ts
```

- **51 unit tests passed, 0 failed**, four files, Vitest 5.0.3.
- TypeScript/Vite 8.3.4 production build **passed**.
- **43 Chromium browser tests passed, 0 failed, 0 skipped (55.5s)**: 27 sidebar/context cases and 16 unchanged rendering-stability cases.
- git diff --check **passed**.

Strengthened rendered assertions: responsive widths, collapsed icon axes, one-line labels/content bounds, active-link focus-ring geometry within nav bounds, no Radix state/Tooltip for the control on hover/focus, preserved navigation tooltip, identity/dimensions/focus/draft/scroll, final structure and Home route. Eight new bilingual context cases cover all four principal combinations: GLOBAL ADMIN/USER show Global then selected KBS; TENANT ADMIN/USER show KBS; no permission suffix. Registration absent in Global context for both GLOBAL roles; available with selected Tenant only for ADMIN. Existing four-role admin-navigation assertions retained. Intercepted read fixtures are ephemeral browser responses, not seeded application data. Full auth unit suite ran unchanged; no new claim of backend authorization regression tests.

From root:

```sh
docker compose --env-file .cache/sidebar-a/compose.env -p kelios-local-6a18b7783357 -f deploy/compose.yaml -f deploy/compose.postgres.yaml -f deploy/compose.auth.yaml build frontend
docker compose --env-file .cache/sidebar-a/compose.env -p kelios-local-6a18b7783357 -f deploy/compose.yaml -f deploy/compose.postgres.yaml -f deploy/compose.auth.yaml up -d --no-deps --no-build --wait frontend
PATH=/opt/homebrew/opt/node@24/bin:$PATH PLAYWRIGHT_BROWSERS_PATH="$PWD/.cache/playwright" node .cache/sidebar-a4/inspect.mjs
docker ps --filter label=com.docker.compose.project=kelios-local-6a18b7783357 --format '{{.Names}} {{.ID}} {{.Status}}'
docker image inspect kelios-frontend:phase0 --format '{{.Id}}'
git diff --check
```

Frontend-only rebuild/redeploy passed; three services healthy. Backend/PostgreSQL IDs unchanged; no DB reset or business records created. Production normal HTTPS authentication used existing private-file administrator credential; no secrets/tokens in evidence. Temporary dev server stopped; production remains running with existing green brand.

## Actual production observations

[Runtime JSON](sidebar-phase-a4/runtime.json), [asset/image identity](sidebar-phase-a4/assets.json), bilingual screenshots at 1440/1024/768/390px. Visually reviewed expanded/collapsed desktop and mobile Sheet, headings/long labels, focus presentation and Global wording. Navigation labels remain horizontal/single-line; no clipped active backgrounds or focus rings observed in this matrix.

Measured width: 224/64 at 1440; 208/64 at 1024. Mobile Sheet retains its own width/expanded navigation, one visible tree and no desktop control. Logo remains 160×36px, topbar 64px. Global label visible at desktop/tablet; existing narrow-mobile icon-only context trigger behavior is preserved, with Global in its menu. No read/admin/write suffix in operating-context presentation.

All stored node identities true through collapse/expand: main/page/topbar/brand/image/control/all nav anchors; route unchanged, control focused, collapse-triggered API requests zero. Control hover produced zero tooltip nodes. Navigation tooltip remains covered by browser tests. Production Home mouse navigation preserves document/shell, zero falsely active sidebar links on Dashboard. Page-error list empty.

## Limits / stop boundary

No new Phase A blocker found. Existing development-only NO_I18NEXT_INSTANCE warnings are outside scope; Safari/Firefox/physical assistive technology and real IServ provider not tested. Real IServ interoperability remains MANUAL VERIFICATION PENDING. Known other repair issues remain separately gated. Phase A.4 ready for human review; no Phase B implementation.
