# Phase B.1 — Form lifecycle and searchable-picker consistency

Status: implemented and verified; awaiting human review. Phase B is not marked human-accepted. Phase C/D/E were not started.

## Scope and baseline

This correction pass addresses the four human-reported problems: stale create values, an empty Merge candidate row, repeated preview confirmation, and Product reference search/select pairs. The four referenced human screenshots were not available as attachments in this session; diagnosis therefore used inspected source and actual production DOM rather than claiming screenshot measurements.

The pre-B.1 working tree was hashed before edits, with local snapshots retained under `.cache/phase-b1/`. [Baseline comparison](phase-b1/baseline-check.json) confirms only the seven existing files listed below changed. Phase A source, context wording, Sidebar/topbar, repair plan, and all prior verification documents/evidence remain unchanged. Existing uncommitted work is preserved; no Git staging/commit/push/PR occurred.

## Confirmed root causes and corrections

1. **Stale create draft:** `Management` reused edit/create state and successful creation only refreshed data/closed the shared `Command`; controlled values survived. Separate create state now resets explicitly on success. Tenant/GLOBAL-user creation had equivalent missing resets and now clears submitted state. Product clears name, both UUIDs, their labels/queries/paging, and manufacturer number. A local form reset context signals controlled picker cleanup; it is not a React key. Registration explicitly clears its draft before normal navigation. Errors preserve input. Cancel/X preserve existing in-progress drafts. Success clears errors/action-local state.
2. **Blank candidate row:** production DOM contained an empty `role=status` paragraph with `min-height:20px` even after loading. It reserved a 20px row before options. The paragraph now exists only while pending; no negative margins or list-padding workaround. Ready production lists have zero status rows and a measured 5px border/padding-to-option gap. Paging remains conditional on additional pages.
3. **Repeated preview modal:** a `Command` inside the Merge modal opened its own command modal before executing. Preview and execution commands are now embedded forms inside the primary Merge modal. Preview clicks call the existing endpoint directly; impact is shown in that same mounted workflow. Execution alone retains the existing AlertDialog confirmation and authoritative backend revalidation. Generation checks reject obsolete preview responses and changed pairs during execution confirmation. Participant changes invalidate preview. Successful execution closes/reset the action.
4. **Product legacy references:** the shared `Pick` still implemented separate search/select controls. It now adapts the existing searchable combobox, also serving confirmed equivalent entity-reference fields. No parallel widget or dependency was added. Search remains server-backed, bounded to 25 entries per page; explicit Previous/Next preserve access beyond page one. UUID, query, and captured label remain separate. Typing clears the old UUID; normal paging/refetch retains it. Pending/error options are non-actionable. Optional references have a localized explicit clear action. Required references validate selection rather than accepting arbitrary typed text.

## Audit A — form lifecycle

All locations below are in `frontend/src/features/inventory/pages.tsx` unless stated otherwise. Line locations are approximate identifiers for this worktree; function names are authoritative.

| Location / form | Classification | Disposition / rationale |
|---|---|---|
| `Management`, ~123–145: Category, Manufacturer, Product, Location, Owner create | Create | Fixed: independent create draft, explicit success reset, no edit leakage. Cancel/error retain draft. All five receive browser regression coverage. |
| `Registration`, ~91 | Create | Fixed: clears all submitted metadata/reference/loanability state and picker query state on success, then retains existing navigation. Mounted harness proves deliberate value reset without node recreation. |
| `TenantAdministration`, ~154 | Create | Fixed: clears Tenant name on success; regression covered. |
| `UserAdministration`, ~158: GLOBAL user create | Create | Fixed: separate creation login/display/password state, all cleared after success; regression covered. |
| `Management`: Save entry | Edit | Pristine reopen initializes from current selected resource; cancelled dirty draft remains. Create/edit state separated. |
| `AssetDetail`, ~102: metadata, catalog, loanability, Location, Owner | Edit | Pristine reopen initializes from current Asset; preserves dirty cancelled drafts. Current resource remains authoritative. No permission or command payload change. |
| `AssetDetail`: lifecycle, condition, loan | Action | Fixed success cleanup of target/reason/note or borrower/return/note. No automatic clearing on failure/cancel. |
| `AssetDetail`: Return | Action | Retained: no transient fields. |
| `Management`: Merge | Action | Successful execution clears participant UUIDs/preview and closes workflow. Cancel retains chosen participants; selection changes invalidate preview. |
| `UserAdministration`: credential reset | Security action | Fixed: clears login as well as password after success. Selection of a different user already initializes action fields. |
| User approve/disable/role | Action/edit | No transient create draft; role pristine initialization follows current selected user. Consequential confirmation retained. |
| `ProviderConfiguration`, ~164 | Intentionally persistent edit | Retained issuer/client/group/enabled configuration, because it represents saved provider state. Write-only secret already clears after success. Not a new blank create operation. |
| `PasswordChange`, ~170 | Security action | Retained existing correct success cleanup of both password fields. |
| Login/public organization entry (`Authentication.tsx`, `OrganizationPicker.tsx`) | Authentication / routing | Inspected, intentionally not changed. Outside create-record lifecycle and Phase A frozen surface. |
| Table search/filter/paging, Audit filters | Persistent presentation state | Retained. Not record drafts. |

Shared `Command` no longer leaves a stale “Saved” status beside a later create trigger. On reopen it clears transient problem state; failed submission itself retains its problem/input. Dirty tracking includes option mouse/keyboard selection and optional clearing, so selection-only cancelled edits are not mistaken for pristine state. Ordinary field/input updates remain mounted.

## Audit B — nested dialogs

| Location | Classification / disposition |
|---|---|
| Explicit Category/Manufacturer Merge Preview | Redundant non-destructive confirmation: removed; direct embedded preview. |
| Merge execution | Consequential mutation: embedded execution button retains meaningful AlertDialog confirmation. |
| Selected resource → Save entry form | Intentionally retained: nested form collects actual edited values, not “confirm inspect/preview”. |
| Catalog delete/publish, user disable | Destructive/security or scope-changing operation: confirmations retained. |
| User role/credential action dialogs | Intentionally retained: collect/validate operation input; not repeated non-destructive preview. |
| Audit detail / Asset history / resource inspection | Actual content inspection: retained; no second repeated preview action found. |
| Asset edit/action dialogs | Actual input forms: retained. No additional non-destructive repeated-action confirmation found. |

No new modal, focus trap, preview token, lock or backend state was introduced.

## Audit C — entity-reference pickers

| Location | Classification / disposition |
|---|---|
| Category/Manufacturer Merge source/target | Shared combobox retained/generalized. |
| Product create and edit Category/Manufacturer | Migrated: one entity control each. |
| Asset registration direct Category/Manufacturer, Product mode, Location, optional Owner | Migrated equivalent reference fields via shared `Pick`. |
| Asset catalog reassignment Category/Manufacturer/Product | Migrated equivalent reference fields. |
| Asset Location/optional Owner editing | Migrated equivalent reference fields. |
| Enum/status/role/lifecycle/condition/loanability/catalog-mode choices | Intentionally different fixed-value choices; retained native selects. |
| Tenant operating context and public organization/provider selector | Separate authorized context/auth routing interaction; frozen, not migrated. |
| Audit Tenant filter | Deliberate small context filter, not server entity search/select pair; retained. |
| Explicit UUID filters / Merge advanced-ID section | Retained advanced/manual identifiers. Allows known off-page UUIDs; not duplicate ordinary search interaction and not authorization. |

No other legacy shared entity search/native-select pair remains. Reuse is limited to existing reference choices; no new Merge domains/features were introduced.

## Verification

Commands ran from `frontend/` using Node 24 on PATH:

- `npm test`: **51 passed, 0 failed**, four test files. [Log](phase-b1/unit.txt).
- `npm run build`: TypeScript and Vite production build **passed**. [Log](phase-b1/build.txt).
- `KELIOS_BASE_URL=http://127.0.0.1:12080 npm run test:smoke -- tests/merge-combobox.spec.ts tests/form-lifecycle.spec.ts tests/search-stability.spec.ts tests/inventory.spec.ts`: **40 Chromium tests passed, 0 failed, 0 skipped**. [Log](phase-b1/browser.txt). `inventory.spec.ts` is excluded by the existing non-TLS configuration, not counted as executed or skipped; no real-backend mutation suite was run against operator data.
- `git diff --check`: passed.

New lifecycle tests cover all comparable create workflows, pristine Asset metadata reopen from refreshed data, successful Loan/Lifecycle action cleanup, error/cancel preservation, Product UUID payload/complete reset, Product delayed/out-of-order search and paging, and an inline registration harness proving the same input survives explicit reset. Updated Merge tests cover direct one-dialog preview, keyboard/mouse, paging, duplicate names, zero empty status rows, stale preview/option rejection, equal-ID exclusion, execution confirmation, locale/input/dialog identity. Existing search-stability tests cover context/session/resource isolation, retained table identity/focus, selection/scroll and locale stability.

An initial build exposed a JSX fragment typo, corrected before verification. One new test initially treated the fixed `Direct` catalog-mode select as an entity input; the assertion now separately verifies that intended default and empty reference inputs. Its rerun passed. A production inspection script initially waited for a nonexistent `pre` instead of the actual structured impact content; corrected locator, final inspection completed. No security behavior or assertions were weakened.

## Production inspection and evidence

Only frontend was rebuilt/redeployed with existing Compose files in `kelios-local-6a18b7783357`; no backend/database reset. Frontend image: `sha256:a88a35468ac75dbef9ea353cb96b1d716c82e27a04eaa56895c4fe92b78d7d79`. Existing database/backend containers retained.

German and English inspected at the existing HTTPS installation. Ready lists measured zero status rows, zero list padding and 5px first-option gap. Actual operator-owned Category candidates were read and advisory preview requested: **one dialog, same DOM node**, no execution. Product create displays exactly two reference comboboxes. [Runtime measurements and loaded asset identities](phase-b1/runtime.json) record **zero business mutation requests and zero page errors**. Screenshots in [phase-b1/](phase-b1/) include both languages: Category create, Merge candidates, same-dialog preview and Product create. Fixture screenshots are explicitly named `*-fixture.png`.

Successful Category reopen and all creation/action mutations were not manually executed against operator data; success/reset verification is automated using intercepted responses. Existing operator data was not seeded, modified, deleted or merged. Authentication and read-only preview are the only non-GET application paths used in production inspection. Environment remains running.

## Changed files and limitations

B.1 existing-file delta: `frontend/src/features/inventory/pages.tsx`, `frontend/src/components/ui/searchable-combobox.tsx`, both locale JSON files, `frontend/tests/merge-combobox.spec.ts`, `frontend/tests/search-stability.spec.ts`, `frontend/tests/inventory.spec.ts`. New: `frontend/tests/form-lifecycle.spec.ts`, this report and `docs/verification/phase-b1/` evidence. No dependency/backend/API/schema/auth/authorization/deployment changes.

The Vite test-server console emitted `react-i18next NO_I18NEXT_INSTANCE` warnings during browser runs; assertions passed and production German/English rendering was inspected. This warning was not investigated or suppressed in this focused pass. Chromium/intercepted fixtures prove interaction and async safety; Safari/Firefox and physical assistive technology were not tested. Complete success-reopen coverage of every edited Asset action and credential form is not yet browser-proven individually; lifecycle changes were source-audited and shared mechanism/registration coverage passed. Real-backend inventory suite remains unexecuted; manual successful creates and Merge execution await human acceptance using operator-controlled data. Real IServ interoperability remains separate/manual pending. No Phase C investigation or Audit actor work occurred.
