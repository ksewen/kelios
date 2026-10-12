# Phase B.2 — Form exits, compact actions and list toolbars

Status: implemented and verified; awaiting human review. Phase B remains open. Phase C/D/E were not started.

## Final state rule

**READ STATE MAY BE RETAINED. UNSUBMITTED WRITE STATE IS EPHEMERAL.**

Backend-read resources, lists and search results keep the existing same-authorized-context rendering-stability rules. Locally edited values belong only to an active form session. Rejecting discard continues that session; it is **not persistent drafts**. Confirmed abandonment or successful submission ends it and clears controlled write state. Reopen creates empty/default create state or current authoritative edit state. A failed submission continues the session and retains input for correction.

This supersedes B.1's cancelled-draft retention. B.1 evidence is historical and unchanged, not rewritten to claim these later semantics.

## Reproduced root cause

Before edits, read-only production diagnosis opened Product create, typed an unsubmitted name, and clicked Cancel. Measurements: zero form dialogs, zero discard dialogs, **one hidden dirty marker**. React Router navigation then produced the discard confirmation. The shared `Command` closed through `setOpen(false)` without the route guard, while `data-unsaved` remained on a persistent wrapper outside the unmounted dialog.

[Before measurement](phase-b2/before-runtime.json) documents this actual reproduction. No POST/create operation was sent.

## Exit architecture

- `Command` marks its active form, not a hidden command launcher. It owns explicit parent-state reset callbacks, transient error/dirty cleanup, and the existing controlled picker reset signal. No random/revision/translated remount keys.
- Form Cancel uses the existing localized `confirmAction`. Pristine close is immediate; dirty close asks once. Reject retains the same mounted form and values. Confirm resets values before completing close.
- Shared `Modal` applies the same policy to X, Escape and permitted outside click, scoped to the active content's dirty sessions. Its in-flight exit guard prevents duplicate requests. While an existing submission is busy, X/Escape/outside dismissal remains blocked before any reset; a delayed-failure test confirms input remains retryable. Nested resource forms retain their legitimate focus boundaries.
- Shared confirmation handling performs registered DOM-event resets on confirmed discard before resolving existing router/logout guards. The existing translated discard request is classified when opened, so later locale changes do not alter its meaning. Explicit local close callbacks restrict reset to that form/content. Destructive/security confirmation requests are not interpreted as discard requests.
- Existing Shell `useBlocker` remains unchanged. Sidebar, brand/Home, context and supported history navigation go through that router guard; no second prompt after reset. Native document unload retains the existing browser `beforeunload` behavior; document destruction cannot restore memory-only form state.
- Existing Radix focus management remains. Rejecting an outside-click discard returns focus into the still-mounted form rather than to the page body; button/input identity is preserved. Confirmed close returns to the normal dialog trigger or completes navigation.
- Merge has explicit session reset for participant IDs, query/picker state and advisory preview. Discard invalidates outstanding preview generations. Execution confirmation and fresh backend validation remain unchanged.

No new state-management/fetching framework, cache, memoization scheme, backend contract or persistence mechanism was introduced. Read hooks/request ordering and authorization/context boundaries remain unchanged.

## Audit A — dirty form/session exits

Locations below are in `frontend/src/features/inventory/pages.tsx` unless specified. Function names identify the relevant blocks more reliably than shifting line numbers.

| Location | Disposition | Reason / final behavior |
|---|---|---|
| `Management`: Category/Manufacturer/Product/Location/Owner create | Changed | Shared guarded Cancel/X/Escape/outside; confirmed discard and success clear independent create draft and reference/query state. Errors retain current session. |
| `Management`: selected-resource edit | Changed | Confirmed discard clears edits via authoritative initialization. Reopen uses current selected backend resource, never cancelled writes. Loaded table/resource is not cleared merely for form exit. |
| `Registration`: Asset registration | Changed | Explicit reset of metadata, references, optional Owner and loanability defaults on success/confirmed navigation discard. Inline form uses existing navigation guard. |
| `TenantAdministration`: create | Changed | Independent name reset on confirmed discard or success. |
| `UserAdministration`: GLOBAL create | Changed | Login/display name/password reset on confirmed discard or success. |
| `AssetDetail`: metadata/catalog/loanability/Location/Owner | Changed | Authoritative initialization on pristine reopen; confirmed discard resets edited state. Same-session rejection/failure retains edits. |
| `AssetDetail`: lifecycle/condition/Loan | Changed | Explicit target/reason/note or borrower/return/note cleanup on confirmed discard/success. |
| `AssetDetail`: Return | Intentionally retained | No meaningful editable state; no additional dirty confirmation. Actual action semantics unchanged. |
| Category/Manufacturer Merge | Changed | Participant IDs, query state and preview discarded when the workflow is abandoned. Same-session rejected exit keeps selections. Preview/execution safety unchanged. |
| User role / credential reset | Changed | Role resets from selected User; credential login/password cleared on confirmed discard/success. Security confirmations remain separate. |
| `ProviderAdministration`: inline OIDC configuration | Changed | On confirmed navigation discard, initialize from current backend configuration and clear write-only secret. Successful save retains saved configuration/read presentation and clears secret. |
| `PasswordChange`: inline account form | Changed | Clear password fields on confirmed navigation discard; existing success clearing retained. |
| Approval/disable/publish/delete commands | Intentionally retained | No meaningful editable draft; existing consequential-operation confirmations remain. |
| Authentication/public organization routing | Intentionally retained | Not record-write dialogs; no auth/Phase A changes. |
| Search/filter/paging/Audit filters | Intentionally retained | Read/presentation state, not unsaved writes. |

No abandoned B.1 draft-persistence path intentionally remains. Browser coverage is representative rather than an exhaustive execution of every security/Asset mutation; source audit confirms explicit reset ownership for the listed forms.

## Audit B — action labels

| Location | Disposition | Labels / rationale |
|---|---|---|
| Five management-list toolbar create triggers | Changed | `Erstellen` / `Create`; descriptive resource-specific dialog titles retained. |
| Management create dialog primary action | Changed | `Erstellen` / `Create`. |
| Tenant/GLOBAL-user create triggers and primary actions | Changed | `Erstellen` / `Create`; descriptive dialog/form names remain. |
| Assets toolbar registration trigger | Changed | `Erstellen` / `Create`; existing registration page/command unchanged. |
| Category/Manufacturer toolbar Merge | Changed | `Merge` in both languages; dialog title remains explicit/descriptive. |
| Merge preview / execution action | Changed | `Vorschau` / `Preview`, `Bestätigen` / `Confirm`. |
| Asset registration, metadata/catalog/Location/Owner/lifecycle/condition/Loan/Return commands | Intentionally retained | Specific command names distinguish operations; no blanket one-word relabeling of operational actions. |
| Publish/delete/disable/credential/password/OIDC/security operations | Intentionally retained | Consequential/security actions remain unambiguous. |
| Pager, search labels, enum selects and explanatory safety text | Intentionally retained | Already appropriate functional labels, not repeated long resource action titles. |

Navigation labels, Global terminology, authorization labels and domain/API values were not changed.

## Audit C — toolbar layout

| Page | Disposition | Geometry |
|---|---|---|
| Categories / Manufacturers | Changed | Search → Create → Merge in one wrapping flex toolbar. Search consumes remaining space. |
| Products / Locations / Owners | Changed | Search → Create, with authorized actions only. |
| Assets | Changed | Search and existing sort/direction/filter controls plus authorized concrete-Tenant Create in one responsive toolbar. No new filter/action. |
| Tenant/User administration | Intentionally different | No existing server search/action pair to combine. Compact Create retained; no speculative search added. |
| OIDC/account/Audit pages | Intentionally different | Configuration/action forms or specialized filters, not the same management-list toolbar. No redesign. |

CSS flex/wrap handles responsive widths; no JavaScript viewport calculations or translated-label-based sizing. No placeholder space for absent unauthorized actions. DOM/tab order follows search then available actions. Mobile wrapping retains usable controls and avoids horizontal document overflow.

## Automated verification

From `frontend/`, Node 24 on PATH:

1. `npm test`: **51 passed, 0 failed**, four files. [Log](phase-b2/unit.txt).
2. `npm run build`: TypeScript/Vite production build **passed**. [Log](phase-b2/build.txt).
3. `KELIOS_BASE_URL=http://127.0.0.1:12080 npm run test:smoke -- tests/dialog-toolbar.spec.ts tests/form-lifecycle.spec.ts tests/merge-combobox.spec.ts tests/search-stability.spec.ts tests/inventory.spec.ts`: **67 Chromium tests passed, 0 failed, 0 skipped**. [Log](phase-b2/browser.txt).
4. Additional outside-click regression repeated five times: **5 passed**. [Log](phase-b2/outside.txt). These repetitions are reported separately, not inflated into the 67-test suite count.
5. `git diff --check`: passed.

`inventory.spec.ts` remains excluded by the existing non-TLS configuration; its real-backend tests were **not executed or counted as skipped/passed**. Helpers/selectors were updated for short action labels and confirmed Escape discard, not weakening backend assertions.

Coverage includes pristine/dirty Cancel, X, Escape and outside click; rejection preserves form/input DOM identity and values; confirmed discard clears Product name/number/reference selections; failed/successful submission lifecycle; current-authoritative Asset edit reopen; separate Tenant/User create; inline Asset Home/context exits; supported history and sidebar routing with no double confirmation; table identity preserved; English/German responsive toolbar order/geometry at 1440/1024/768/390; unauthorized action absence; existing B/B.1 paging, keyboard, latest-response, pending safety, preview and read-state/context-isolation regressions.

Failures encountered: an initial navigation test used accessible-role lookup for a link intentionally aria-hidden by the modal; its explicit router-dispatch locator now includes hidden nodes. A repeated outside click exposed focus returning to body after rejected discard; fixed in shared confirmation and verified repeatedly. Earlier suite results (59/60 and 65/66) were superseded by the final complete green run. Shell build commands initially used an incorrect working directory; corrected before implementation/build completion.

Fixture screenshot paths initially still pointed to B.1. B.2 paths were corrected, and the four historical images were restored to **exact pre-B.2 SHA-256 hashes** using isolated browser interception of the pre-B.2 source snapshots. No production/source rollback occurred. [Baseline comparison](phase-b2/baseline-check.json) confirms all prior B.1/Phase A evidence unchanged.

## Production verification

Only frontend was rebuilt/redeployed into `kelios-local-6a18b7783357` using the existing Compose configuration; backend/database remained healthy and unchanged. Final frontend image:

`sha256:ce7d4f609b157f7ab10ea74289cc64997993e2a023e532ebf59d0d770fe8bdf4`

[Runtime measurements](phase-b2/runtime.json) include loaded production asset identities, toolbar control coordinates/widths, dirty-session identity/value results, **zero business mutations and zero page errors**. German/English inspected at 1440/1024/768/390 for Categories, Manufacturers, Products and aggregate Assets. No horizontal overflow in 32 actual viewport/page/language combinations. Product screenshots, discard confirmations and same-dialog read-only Merge previews are stored separately in [phase-b2/](phase-b2/). Captured production visuals were inspected.

The operator environment contains no selectable Tenant. Locations/Owners concrete-Tenant toolbar rendering is **automated verified**, **manual pending**; no Tenant/data was created to fill that gap. Production Product dirty Cancel was rejected (same input/value), then confirmed (fresh empty reopen) in both languages. Successful creates and destructive/security mutations were tested with intercepted fixtures, not performed against operator records. Advisory preview used existing operator Category data; no actual Merge execution.

A separate existing runtime limitation was discovered: direct document GET `/assets` returns HTTP 301 to `https://localhost:8443/assets/`, causing connection refusal at the unexposed port. The application Assets link works through React Router and was used for inspection. This deployment/static-directory redirect issue is **not fixed in B.2**; no deployment/backend scope expansion was made.

## Files and boundaries

B.2 changes existing files:

- `frontend/src/features/inventory/pages.tsx`
- `frontend/src/components/ui/dialog.tsx`
- `frontend/src/components/ui/confirmation.tsx`
- `frontend/src/locales/en.json`, `frontend/src/locales/de.json`
- `frontend/tests/form-lifecycle.spec.ts`, `frontend/tests/merge-combobox.spec.ts`
- `frontend/tests/inventory.spec.ts`, `frontend/tests/ui-helpers.ts`

New: `frontend/tests/dialog-toolbar.spec.ts`, this report, and `docs/verification/phase-b2/` evidence. [Git status](phase-b2/git-status.txt) records the full preserved worktree. Pre-B.2 hashes/snapshots remain private under `.cache/phase-b2/`.

Phase A source/specification/evidence unchanged. No backend/API/schema/auth/authorization change, database reset, persistent business-data creation, commit, staging, push or PR. No Phase C investigation or Audit actor work. Environment remains running.

## Limitations and handoff

Chromium only; Firefox/Safari and physical assistive technology not verified. Native full-document unload remains the browser's existing warning behavior and was not manually exercised. Not every audited Asset/security form received a separate browser mutation test; shared exit/reset mechanics and representative independent forms are covered. Existing Vite test-server i18next warnings remain documented, not suppressed or treated as production runtime errors. Real IServ interoperability remains separately manual pending.

B.2 is ready for human review, not marked accepted. Human success/reopen checks and any real Merge execution remain operator-controlled. The direct `/assets` deployment redirect and Places manual verification limitation are separate from verified B.2 form/toolbar behavior.
