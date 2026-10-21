# Default brand, breadcrumbs and Asset status palette

**Baseline:** HEAD `76d46bf` plus the separately accepted, uncommitted Asset transition-loanability Gate 2 worktree. Its eight paths and evidence remain owned by [that Plan](asset-transition-loanability.md); overlapping documentation is hunk-isolated here.
**Plan:** APPROVED through the HUMAN request to implement the reviewed Plan and its explicit branding clarifications, 2026-10-21.
**Gate 1:** HUMAN ACCEPTED / CLOSED 2026-10-21, with the recorded verification and live/operator-data limits; separate commit permission granted. **Gate 2:** IN PROGRESS, explicitly authorized 2026-10-21. **Gate 3:** not authorized.

## Boundaries and decisions

The existing shell, sidebar, account control, resource layouts, operating context, authorization and API remain. New installations and invalid/missing runtime branding use a restrained orange/charcoal light palette. Existing stored palettes and legacy `logoUrl` files are neither migrated nor reinterpreted. Optional `organizationLogoUrl` is a distinct same-origin public asset: when valid, its image sits beside a live-text KELIOS wordmark whose O inherits `primary`. Without either image, a neutral mark sits beside that wordmark. A legacy `logoUrl` without the new key retains the old full-image treatment. No official district asset is bundled.

The three gates are separately reviewable. Each stops with exact verification and HUMAN review. HUMAN acceptance is distinct from separate commit permission; no gate automatically authorizes the next. The existing 8443 runtime and operator data are outside these gates.

## Gate 1 — theme and branding

Use `#f7f7f5` background, `#18181b` foreground, `#b45309` primary, white primary foreground, `#fff1e6` accent and `#78350f` accent foreground. Preserve semantic success/error tones independently. Make orange the fresh CLI/Compose default; keep blue/green selectable and old saved CLI manifests loadable. The new organization logo is an optional mounted SVG/PNG/JPG/WebP chosen through the supported CLI, with fallback and size constraints in the existing Logo component. Preserve legacy-logo rendering. Update only branding/runtime operator documentation and the narrow architecture contract.

Verify frontend unit/build, CLI control tests including legacy saved state and optional organization logo, intercepted Chromium branding/sidebar at desktop/mobile, document links and `git diff --check`. Stop for HUMAN branding/compatibility review. No 8443 deployment or commit.

## Gate 2 — breadcrumbs

Place compact shared semantic breadcrumbs immediately above content without changing shell positioning. Route/page definitions cover dashboard, Asset, Category, Manufacturer, Product, Location, Owner, User, Tenant, Audit, iServ, registration and account pages. Direct detail identity comes from its existing authoritative detail read, with Asset Tag then local identifier then UUID fallback. An unauthorized collection ancestor is omitted. Route-backed Product and other collection dialogs retain the underlying collection breadcrumb; direct detail URLs show collection plus readable resource. Preserve localized links, `aria-current`, focus, direct refresh, 390px truncation and DOM identity. Verify focused intercepted navigation and accessibility coverage in German/English and production build; stop for HUMAN navigation review.

## Gate 3 — Asset semantic colors and integration

Use one Asset-specific tone mapping for lifecycle, condition and loanability across Overview, Asset list and selected in-page transition controls: registered/unknown gray `#f3f4f6/#374151`, stock blue `#dbeafe/#1e40af`, deployed/working/Yes green `#dcfce7/#166534`, deregistered peach `#ffedd5/#9a3412`, defective amber `#fef3c7/#92400e`, unusable/No pink `#fee2e2/#991b1b`. Preserve text labels, interaction/authority, native option-menu behavior, focus and disabled states. No domain/API change.

Verify every state and interactive/read-only path, frontend unit/build, affected intercepted Chromium suites at 1440px/390px in German/English, CLI branding regression, documentation links and `git diff --check`. Synchronize durable architecture, operator instructions, this Plan, status and backlog; stop for HUMAN final review. Backend tests/OpenAPI edits require a demonstrated contract change and are not expected.

## Gate 1 evidence

Baseline: HEAD `76d46bf` plus the eight separately accepted, unstaged Asset Gate 2 paths. Gate 1 changes are limited to theme/logo frontend, CLI/Compose defaults, branding tests and the owning architecture/operations/checkpoint documentation. Shared `ARCHITECTURE.md`, `IMPLEMENTATION_STATUS.md`, `docs/plans/remaining-work.md` and locale files contain identifiable Gate 1 additions beside the pre-existing Gate 2 hunks; `docs/plans/asset-transition-loanability.md` records the separate HUMAN acceptance. `frontend/src/features/inventory/pages.tsx` and `frontend/tests/assets-status.spec.ts` remain solely predecessor work. No path was staged.

- `cd frontend && npm test` passed **239/239** in 11 files after the final Logo change; `npm run build` passed TypeScript and Vite with **2,143 modules** (exit 0).
- Initial sandboxed `./scripts/test-deployment-cli.sh` produced 16 failures because the terminal test's `stty` PTY call was denied. The permitted rerun reached the assertions and passed 35/36; the one new legacy-manifest fixture accidentally populated `ORG_LOGO` through a substring replacement. The fixture was corrected, its focused `python3 scripts/test-deployment-cli.py CliControlTests.test_legacy_logo_survives_start_and_new_organization_logo_can_be_added` rerun passed **1/1**, and final `./scripts/test-deployment-cli.sh` passed **37/37** (exit 0), including old saved manifests, optional organization-logo add/clear and missing-logo refusal. All CLI tests used their ignored fake-Docker fixture tree, not the 8443 installation.
- Initial sandboxed Chromium could not launch (`SIGTRAP`) and was interrupted before usable application assertions. The permitted intercepted `tests/runtime-branding.spec.ts tests/sidebar-layout.spec.ts` run passed **33**, with one configured live-HTTPS skip. After adding legacy/logo-fallback and 390px cases, final `PLAYWRIGHT_BROWSERS_PATH=../.cache/ms-playwright KELIOS_BASE_URL=http://127.0.0.1:13980 npx playwright test tests/runtime-branding.spec.ts tests/sidebar-layout.spec.ts --reporter=dot --retries=0 --workers=2` passed **35**, with **one configured live-HTTPS skip** (exit 0). The isolated Vite server was then stopped. These tests cover German/English sidebar behavior, keyboard Home navigation, desktop/mobile layout, runtime theme fallback/override, new organization logo, legacy full-image logo, image failure and 390px overflow.
- Final check: seven changed owner documents contain **79 local links, zero missing**; the new orange JSON parses, the two new files have no trailing whitespace, `git diff --check` passed and the index was empty.

No backend, domain, OpenAPI or database change is part of Gate 1. The existing 8443 installation retains its saved palette and has not been restarted for this gate. Intercepted Chromium and fake-Docker CLI controls do not establish authenticated live HTTPS branding or operator-data behavior. **Review: HUMAN ACCEPTED / CLOSED 2026-10-21** with those limitations. The HUMAN separately permitted the Gate 1-only commit and authorized Gate 2; Gate 3 remains outside that authorization.
