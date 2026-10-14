# Frontend UX and functional defects — investigation and repair plan

Status: **Phase A (A0 through A.4) completed and human accepted**, 2026-10-11. Issue A’s final accepted contract is in §5. Phase B (B through B.4) is **HUMAN ACCEPTED / CLOSED as of 2026-10-11**; Phases C–E remain independently gated planning work; **NO REMAINING IMPLEMENTATION PHASE MAY START WITHOUT SEPARATE HUMAN APPROVAL.** These are repair gates, not a new roadmap phase. There is no Phase 5.

## 1. Executive summary

Four user-reported defects have separate repair gates. Sidebar Issue A is resolved and human accepted; §5 describes the final product contract, root cause, invariants and evidence. Phase B frontend repairs through B.4 are HUMAN ACCEPTED / CLOSED as of 2026-10-11. Create-workflow Issue C and Audit actor Issue D remain separately gated and have not started. The mandatory collection-query backlog below is a separate backend approval gate.

Evidence labels remain: **CONFIRMED** is directly supported by inspected code/documentation or recorded verification; **INFERRED** needs further verification; **UNKNOWN** cannot yet be established. The cleanup and baseline below are historical records of the initial planning investigation. Later authorized Phase A runtime verification is summarized separately in §5; it does not establish runtime causes for Issues B–D.

## 2. Initial disposable environment cleanup — historical record

CONFIRMED: before application investigation, inspected the saved installation manifest and Docker project/volume labels for `kelios-local-15d2379dc18a`. Managed database ownership was exactly `com.docker.compose.project=kelios-local-15d2379dc18a`, `com.docker.compose.volume=postgres-data`.

Used the supported `./kelios uninstall` through its interactive terminal mechanism, answered runtime removal yes, permanent managed deletion yes, and typed exactly `DELETE kelios-local-15d2379dc18a`. The CLI owns container/network removal and checks database-volume ownership/consumers before deletion.

Removed:

- `kelios-local-15d2379dc18a-frontend-1`;
- `kelios-local-15d2379dc18a-backend-1`;
- `kelios-local-15d2379dc18a-postgres-1`;
- `kelios-local-15d2379dc18a_default` network;
- `kelios-local-15d2379dc18a_postgres-data` volume and all its database data;
- installation-specific ignored `.kelios/`, after confirming the CLI recorded `PHASE=data-deleted`;
- ignored `.cache/manual-ui/` startup scripts, worktree-hash capture and private temporary administrator password.

Post-cleanup project-filtered container/network queries returned no resources; named-volume inspection confirmed absence. No ownership ambiguity was found for these resources. Shared image/build/developer caches, prior verification artifacts, committed fixtures and unrelated PostgreSQL/MongoDB containers were not removed. Existing reusable TLS material in `.cache/deployment-cli/` was not owned exclusively by this installation and was preserved. No replacement environment or application data was created.

## 3. Initial investigation repository baseline — historical record

Branch `main`; HEAD `7872f5e192d6cf9c6e19c98cb99df07166699b9f` (`feat: add guided local deployment CLI`). Accepted Phase 0–4 and UI redesign remain the baseline. Deployment CLI acceptance is separate from this repair plan.

Existing changes, preserved without staging or editing:

```text
 M ARCHITECTURE.md
 M frontend/src/app/Dashboard.tsx
 M frontend/src/app/OrganizationPicker.tsx
 M frontend/src/app/Shell.tsx
 M frontend/src/auth.test.ts
 M frontend/src/auth.ts
 M frontend/src/features/inventory/pages.tsx
 M frontend/src/locales/de.json
 M frontend/src/locales/en.json
?? docs/analysis/UI_IDENTITY_AND_RENDERING_STABILITY.md
?? frontend/tests/search-stability.spec.ts
```

At the initial investigation, only this plan was added; hashes checked preservation of existing work. No application tests, builds or deployments ran in that analysis phase. Subsequent independently authorized Phase A implementation and verification are recorded in §5 and the linked reports. This consolidation changes documentation only and preserves all uncommitted implementation work.

## 4. Architecture constraints and inspected inputs

Read ARCHITECTURE.md completely, particularly §§4, 5, 10 and 12; DOMAIN.md identity/authorization and history rules; UI_REDESIGN_PLAN.md, the UI identity/stability analysis, deployment/authentication documentation, current frontend primitives/pages, authentication/authorization/audit/create code, migrations and relevant OpenAPI schemas/test source.

Preserve React/Vite, Tailwind, existing Radix/shadcn primitives, TanStack Table and i18next. Preserve independent images and runtime branding. Keep request state separate from displayed data, UUID entity keys stable, latest-response guards intact, and pending reference options non-actionable. Preserve tables/input focus/drafts across same-context refetch. Genuine session/authorization/resource/Tenant changes must clear stale state. Sidebar collapse is layout state, not a route/session/authorization boundary, and must not remount page content. Do not add blanket memoization or a fetching framework.

Identity remains exact external `(issuer, subject)` -> KELIOS User -> explicit scope/Tenant/role/status. Local credentials exist only for GLOBAL users. Provider selection routes authentication and grants no authority. Authenticated GLOBAL workspace selection is operating context only. Backend guards remain authoritative. ACTIVE ADMIN is required for creation in an authorized Tenant; USER is read-only; PENDING has no business access; DISABLED Refresh rejection and already-issued Access expiry semantics remain unchanged.

Audit target Tenant attribution is immutable and independent of actor identity. No AuditScope or live actor/Tenant FK. Administrative AuditLog and AssetEvent business history remain separate. Do not introduce account deletion, privacy or transfer-linkage. Future GLOBAL Email/validity changes follow the separately approved design contract and gated implementation plan; this repair does not implement them.

Historical application data is disposable for this initiative. No backfills, historical reconstruction, compatibility states or live-user joins to render old actor history. Repository migration history remains source: preserve V1–V6; any necessary future schema change is separately reviewed. No actor-account snapshot migration is assumed.

## 5. Issue A — final accepted Sidebar contract

**COMPLETED / HUMAN ACCEPTED through A.4.** This section is the current product contract. Phase-specific reports retain implementation-time measurements and intermediate decisions; they are evidence, not competing current specifications.

### Final navigation and Home

The sidebar has two ordered groups, with no intermediate headings or decorative separators:

| Group | German navigation, in order | English navigation, in order |
| --- | --- | --- |
| ARBEITSBEREICH / WORKSPACE | Assets, Kategorien, Hersteller, Produkte, Standorte, Eigentümer | Assets, Categories, Manufacturers, Products, Locations, Owners |
| ADMINISTRATION | Benutzer, Mandanten, IServ, Protokoll | Users, Tenants, IServ, Log |

There is **no Übersicht/Dashboard or replacement Home sidebar entry**, on desktop or mobile. Dashboard’s existing page/route remains available through the whole KELIOS brand/logo area: a semantic React Router Link with localized accessible name, keyboard activation and visible focus indication. It navigates within the application without document reload or session/shell replacement. The destination is `/dashboard` in GLOBAL aggregate context and `/tenants/{tenantId}/dashboard` in selected GLOBAL or authenticated TENANT context. On Dashboard, **zero active sidebar rows is intentional**; do not falsely activate Assets.

Concise IServ/Protokoll/Log labels apply to navigation only; descriptive page titles, Domain/API terminology and route semantics are unchanged.

### Layout ownership, sizing and collapse

One stable topbar sits above the sidebar/content row. Topbar and sidebar are separate layout regions. Collapse owns sidebar width only: it must not change logo dimensions, topbar height, brand layout/identity or account controls. The same brand/Home link remains mounted.

| Responsive desktop geometry | Accepted width |
| --- | ---: |
| xl / large desktop, approximately 1440px | 224px expanded |
| lg / desktop, approximately 1024px | 208px expanded |
| Collapsed desktop | 64px |

Widths are deliberate CSS-responsive geometry, independent of locale, route, active styling or permission-filtered content. Labels remain horizontal, single-line and readable, with comfortable hit targets and unclipped active backgrounds/focus rings. Mobile/tablet at approximately 768/390px uses the existing Sheet, not desktop compact-width rules.

Expanded: the compact icon collapse control sits at the right of the ARBEITSBEREICH/Workspace heading row; links show icon + label. Collapsed: heading text may disappear; the **same semantic control** remains aligned on the same horizontal axis as navigation icons, with the expand-direction icon; links are icon-only.

The collapse control has **no visual floating Tooltip**. It retains localized aria-label (German “Seitenleiste einklappen” / “Seitenleiste ausklappen”), aria-expanded, aria-controls, type=button, keyboard operation, focus and DOM identity. **Collapsed navigation items retain tooltips and accessible names.** Collapse is in-memory presentation state; browser persistence was not added.

### Operating context and authorization

The ordinary operating-context selector shows **Global** in both languages for GLOBAL aggregate context, or **the Tenant’s name** for concrete context. It does not append Lesen/Read/Admin/Write/Schreiben or another permission badge. Existing narrow-mobile icon-only trigger presentation remains; its menu identifies the context.

CONFIRMED investigation: the old “Alle Mandanten (Lesen)” / “All Tenants (read)” was a fixed translation chosen for aggregate context, independently of role/capability. It was misleading presentation, **not evidence of a discovered permission-calculation defect**. Organization/provider selection remains pre-authentication routing; it grants no authority. Authenticated context comes from the existing User scope and operating-context rules.

**Global does not mean aggregate context is writable.** Asset registration still requires ADMIN and a concrete Tenant; Location/Owner and Tenant OIDC context requirements remain. GLOBAL Catalog authority, ADMIN/USER distinctions, PENDING/DISABLED behavior and backend authorization remain unchanged. Audit filtering is a separate resource-filter concept, not operating-context authority.

Grouping is presentation only. Existing visibility is preserved: GLOBAL ADMIN sees all four Administration entries; GLOBAL USER sees Tenants; TENANT ADMIN sees Users and Log; TENANT USER sees none. Omit empty group headings. Frontend visibility never replaces server authorization, Tenant isolation or authoritative target validation.

### Root cause and architectural lessons

CONFIRMED in A0: `Tooltip.Trigger asChild` wrapped a React Router NavLink with a **functional className**. Radix Slot’s prop/class composition did not preserve that callback behavior; the rendered anchor lost actual `nav-item` / `nav-active` tokens. The anchor computed to inline while the SVG was block, producing icon-over-label stacking even though the source `.nav-item` rule correctly requested flex.

The accepted repair supplies **concrete navigation class strings across the Radix composition boundary**, using React Router matching while preserving existing route semantics, including Asset-detail active navigation. Do not restore a functional className through this Slot boundary. Relevant implementation: `frontend/src/app/Shell.tsx` Navigation; `frontend/src/components/ui/sidebar.tsx`; `.nav-item` in `frontend/src/styles.css`.

The earlier logo-size coupling came from placing branding inside the width-changing sidebar and passing `compact={collapsed}`. Structural topbar/sidebar separation corrected ownership; duplicating topbars/logos or hiding coupling with arbitrary icon offsets is not the contract.

### Identity and mobile invariants

Collapse/expand must not unnecessarily recreate main page, topbar, brand/logo, control or navigation anchors; change route; trigger unrelated API requests; discard form drafts; or reset unrelated scroll. Intentional width changes may reflow content without replacing semantic nodes. Navigation uses stable semantic IDs/routes, never translated labels, collapse state or generated/revision/random keys. Locale and ordinary same-context updates preserve semantic navigation nodes. React rerendering is acceptable; avoidable remount/state loss is not. These apply the existing ARCHITECTURE.md §12 invariant; blanket memoization is not a substitute.

Mobile retains the existing trigger and Sheet, one visible navigation tree, close focus return, and no competing desktop collapse control. It uses the same simplified groups and has no Home/Dashboard sidebar entry; Home remains in the topbar brand link.

### Verification and documentation hierarchy

Final A.4 recorded results: **51 unit tests passed; 43 Chromium browser tests passed; production build passed; git diff --check passed**. English/German production rendering was inspected at **1440/1024/768/390px**. Recorded node identities remained stable; collapse generated **zero API requests**; mobile Sheet behavior was preserved. Automated coverage includes intermediate rendering stability, focus/drafts/scroll, role-filtered navigation and context/registration guards. Intercepted frontend fixtures are not proof of backend authorization.

Evidence and history:

- [Phase A functional repair](../verification/SIDEBAR_PHASE_A.md): accepted A0 root-cause account, concrete NavLink/Radix repair and original browser evidence.
- [A.1 ownership/control evidence](../verification/SIDEBAR_PHASE_A_1.md): topbar/logo separation and DOM/geometry verification; intermediate control/width details are historical.
- [A.2 information-architecture evidence](../verification/SIDEBAR_PHASE_A_2.md): grouping/labels/Home accessibility and role/identity tests; its standalone Overview entry was superseded.
- [A.3 logo-only Home evidence](../verification/SIDEBAR_PHASE_A_3.md): sidebar Dashboard removal and intentional zero active rows on Dashboard.
- [A.4 final sizing/context evidence](../verification/SIDEBAR_PHASE_A_4.md): final widths, aligned control, control-tooltip removal, Global terminology and complete final results. Linked evidence directories retain actual commands, counts, asset identities, screenshots and runtime measurements.

Human acceptance now closes Phase A; implementation-time “pending review” text in the reports is historical. This consolidation ran no new application verification. Chromium is the verified matrix; Safari/Firefox and physical assistive technology were not tested. Existing development-only NO_I18NEXT_INSTANCE warnings remain outside this repair. Real IServ interoperability remains **MANUAL VERIFICATION PENDING**, separately from sidebar acceptance. Phase B requires its own approval.

## 6. Issue B — Merge selectors — human accepted / closed

**Final status: HUMAN ACCEPTED / CLOSED, 2026-10-11.** The original investigation/proposal below is historical; the final implemented B–B.4 behavior and acceptance qualifications are summarized in the Phase B closeout section. It is not an outstanding implementation instruction.

### Evidence and diagnosis — original investigation

- CONFIRMED: `frontend/src/features/inventory/pages.tsx:66–70` defines Pick as Field search plus native select, with separate labels and optional paging.
- CONFIRMED: Management `:115–130` uses the same Pick twice in one explicit Merge Modal for Categories and Manufacturers only. Product/Owner/Location Merge does not exist. An advanced-ID section also permits explicit source/target identifiers.
- CONFIRMED: Pick performs immediate per-keystroke requests, page size 25, resets page on search and handles subsequent pages. It uses the current useRead `:30–57` retention/latest-response/context guards; pending/error disables selection. There is no picker debounce. OrganizationPicker's distinct 150 ms debounce is not involved.
- CONFIRMED: `catalog/CatalogService.java:160–169` provides bounded paged literal case-insensitive name search and stable name/UUID ordering. Tenant Catalog reads include visible GLOBAL entries as defined by existing boundaries; the list does not pre-authorize Merge.
- CONFIRMED: `CatalogService.java:133–157,171–184`, CatalogMergeService and SecuredDomainOperations own fresh source/target/visibility/UNKNOWN validation, locks, advisory preview and atomic execution. Merge is GLOBAL ADMIN only. Execution inputs remain sourceId/targetId; preview is advisory and may become stale.

### Proposed approach / alternatives

Introduce a small controlled searchable combobox for the two Merge participants, using existing React and Radix Dialog infrastructure plus native input/listbox ARIA; no new framework required. One visually unified editable selection control per participant, with popover/listbox, keyboard arrow/Enter/Escape, mouse options, accessible labels, focus return and empty/error/updating states. Do not nest another modal just to show options. If a proven primitive is required, justify its narrowly scoped dependency before adding it.

Keep selected UUID and display label separate from query; preserve selected value when its row is absent from a page. Display sufficient visibility/Tenant and UUID suffix information to disambiguate equal names. Options use UUID keys. Retain pending options mounted but disabled; Enter/click cannot commit stale options. Latest response wins. Changing picker target/resource/context invalidates options immediately. Support bounded page navigation/load-next within the listbox without claiming all candidates fit page one. Query changes alone must not reset focus or selection.

Prevent equal source/target selections in the UI and disable preview/execution for incomplete/equal IDs; keep backend checks. Selection changes invalidate preview. Execution still freshly validates and discovers impact. Do not add preview tokens or locks across interaction. Remove the ordinary duplicate search/select presentation; whether the existing advanced-ID disclosure remains is a presentation review item, not an authorization mechanism. Keep other reference pickers unchanged initially to limit scope; the component may be reused later only with approval.

## 7. Issue C — creation paths

### Per-domain classification

| Domain | Confirmed complete path | Confirmed gate / validation | Classification and unknown |
| --- | --- | --- | --- |
| Assets | Shell :32–35 resolves `assets` and scoped `assets/new`; pages.tsx :82–94 has Registration and list CTA; authSession.request POSTs `/api/v1/tenants/{tenant}/assets`; AssetController.java:16 -> InventoryOperations.java:28 -> AssetService.register | CTA requires ADMIN and selected Tenant. GLOBAL all-Tenant view intentionally has no register CTA. Product derives Category/Manufacturer; direct mode requires both; Location/explicit loanability required, Owner/text optional. Backend writeTenant and referenced-target checks/transaction enforce ownership | Not missing UI/endpoint/persistence. Missing selected operating context is a possible explanation, not confirmed root cause. UNKNOWN whether CTA, route, form validation, POST, response or navigation failed in the reported case |
| Locations | Shell places branch requires Tenant; Management pages.tsx :115–128 creates name-only form; POST `/api/v1/tenants/{tenant}/locations`; PlaceController.java:13 -> InventoryOperations.createPlace -> PlaceService.java:43–45 | ADMIN and Tenant required; name required/stripped; Tenant existence checked; INSERT and LOCATION_CREATED audit atomic | Not missing implementation. UNKNOWN exact observed failure; inspect context, action visibility, dialog opening, form submit and HTTP error separately |
| Owners | Same Management flow; POST `/api/v1/tenants/{tenant}/owners`; PlaceController.java:18 -> same secured service/PlaceService Kind.OWNER | Same create permission/name/Tenant guard; OWNER_CREATED audit atomic. Retained-reference edit/delete guards do not prohibit create | Not missing implementation. UNKNOWN exact observed failure; no evidence justifies weakening guards |

Shared CONFIRMED observations: Command at pages.tsx :71–76 owns modal/inline form, required HTML validation, submission state and error code display; successful submission clears dirty state with flushSync before navigation. Management creates only when no row is selected and canCreate is true. Selected entries open their own modal; new-entry action clears selection/name. Assets and places are unavailable for creation without Tenant operating context; TENANT users' context comes from their signed identity. Route context is never authorization proof.

Existing frontend/tests/inventory.spec.ts :4–13 exercises registration with API-created prerequisites; :26–30 includes UI management creation. This does not prove fresh-install discoverability, every role/context, or the current operator failure. Backend DomainFoundationIT, Phase3/Phase4 integration/security tests cover persistence, references and authorization; they were inspected, not rerun.

### Proposed approach / alternatives

Before a repair, approve a deterministic component/browser reproduction matrix for each domain: active GLOBAL ADMIN with no Tenant, selected Tenant, ACTIVE TENANT ADMIN own Tenant, both USER scopes, PENDING, invalid inputs, backend failures and wrong-Tenant attempts. Use intercepted responses for presentation diagnosis; later isolated PostgreSQL fixtures for real API behavior. The no-context view should explain/select operating context rather than impersonate Tenant membership. Ensure clear create CTA and form/error visibility for authorized context. Change only the proven broken step; do not rebuild existing create services or add endpoints merely because the operator could not find/use them.

Later manual procedure: operator creates Tenant first, selects it in the header, creates Category/Manufacturer/Location (Owner optional; Product optional), then registers Asset. Capture safe status/problem code and failing stage without tokens/passwords. No automated setup should seed the operator's manual environment.

## 8. Issue D — accepted actor presentation contract; implementation separately gated

The current target is **historical displayName as primary human-facing actor**, with stable actor UUID for authoritative identity/correlation/filtering. Email/login identifier snapshots are not required merely for human display. No TENANT username acquisition, guessed OIDC claim or live User join is needed. Target Tenant attribution remains immutable and independent of actor/current operating context.

CONFIRMED current flow: Actor.java carries UUID/displayName; Identity creates Actor from resolved User; AuditService inserts actor_id/actor_display_name and captured target tenant_id in the mutation transaction; AuditReads returns stored snapshots; the frontend displays actorDisplayName and details include actorId. “Manual Review Administrator” was a display snapshot, not a role or permission label. Those fields already support the consolidated actor target; do not assume a schema defect or schedule an Email/username snapshot migration.

The historical backend has canonical GLOBAL login_name and exact external issuer/subject mapping without a TENANT username. The new GLOBAL target is Email, separately gated; neither representation changes primary audit display. Future account/display changes must not rewrite already-recorded snapshots. Existing writers use human actors including bootstrap/JIT; future actual system writers require explicit service attribution, not fabricated human identity.

A separately approved D review should identify only a remaining concrete presentation/correlation/accessibility gap, if one exists. If the existing snapshot UI meets the target, record that finding rather than manufacture implementation. No historical backfill, AssetEvent username extension or universal username policy is required.

## 9. Risks and alternatives

Primary risks: regressing the accepted concrete-class/layout ownership repair or verifying runtime against different assets; masking legitimate context/permission gates as creation defects; inaccessible hand-built combobox keyboard/focus behavior; assuming first page contains all references; stale pending selection; widening global/Tenant access; invented actor/account identity; audit capture failing inside a transaction. Minimize by reproducing the failing stage first, stable components/keys, server guards, existing immutable snapshots, and narrow controlled tests.

Reject generalized select/query/UI frameworks, speculative caching/memoization, authentication redesign, persistent test setup, duplicate history, and historical repair. No compatibility requirements for disposable application data; source migration history and accepted evidence remain intact.

## 10. Scope and non-goals

Scope: the four reported defects, their approved target UX, actor presentation review under the approved displayName/UUID contract, focused regression/acceptance. No new feature/domain, dependencies without justification, account/tenant deletion, privacy policy, fixture setup in manual environments, deployment CLI redesign, Phase 5, commits/pushes/PRs. Phase A is completed and human accepted; remaining repairs require their own gates. This documentation consolidation changes no application implementation or tests.

## 11. Independently approved implementation gates

### A. Sidebar layout and collapse — completed / human accepted

- Preconditions and human checkpoints: satisfied through separately authorized A0/A/A.1–A.4 diagnosis, implementation, automated/production verification and human review.
- Final acceptance contract: §5; dedicated historical evidence is linked there. No outstanding Sidebar product decision.
- Regression commands: frontend `npm test`, `npm run build`, `npm run test:smoke -- tests/sidebar-layout.spec.ts tests/search-stability.spec.ts`; root `git diff --check`. Latest executed results are A.4 evidence, not a new run by this consolidation.
- Preserve the accepted implementation and existing uncommitted stability work. Future Sidebar changes require separate authorization; do not restart Phase A or add collapse persistence implicitly.
- Checkpoint before B: Phase A human acceptance is complete. **Phase B is not authorized by that acceptance** and cannot start automatically.

### B. Searchable Merge combobox — completed / human accepted

The original implementation gate below is retained for traceability. It was separately authorized and completed through B.4; human acceptance closes it on 2026-10-11. It does not authorize C, D or the mandatory backend query backlog.

- Preconditions: human approval B; confirm option paging and duplicate-name display; A is independently reviewable and need not supply infrastructure.
- Exact expected files: frontend/src/features/inventory/pages.tsx; new frontend/src/components/ui/searchable-combobox.tsx; locales/en.json and de.json; frontend/tests/inventory.spec.ts; search-stability.spec.ts; new frontend/tests/merge-combobox.spec.ts. Backend/API unchanged unless separately approved evidence requires it.
- Steps: implement controlled two-combobox presentation; preserve server search/paging, selected UUID/name and async guards; prevent same IDs; clear obsolete preview; retain fresh execution.
- Commands: frontend `npm test`, `npm run build`, `npm run test:smoke -- tests/merge-combobox.spec.ts tests/search-stability.spec.ts tests/inventory.spec.ts`; root `git diff --check`.
- Acceptance: one searchable control each; mouse/keyboard/focus return; equal IDs rejected; options beyond 25 accessible; equal labels unambiguous; stale options non-actionable, late response ignored; no input/option remount solely on refetch; preview still advisory and Merge authoritative.
- Risks: popup/modal focus interaction and stale selected label. Checkpoint: human review of both Category/Manufacturer flows before wider picker reuse.

### C. Asset/Location/Owner create repairs

- Preconditions: human approval C and evidence of exact failing step for each separately; no speculative backend repair.
- Exact candidate files: frontend/src/app/Shell.tsx, features/inventory/pages.tsx, locales/en.json/de.json, frontend/tests/inventory.spec.ts, new frontend/tests/create-workflows.spec.ts. Backend AssetController.java, PlaceController.java, InventoryOperations.java (current HTTP adapter), SecuredDomainOperations.java (older supported adapter), inventory/AssetService.java and PlaceService.java are investigation targets only; edit only if a reproduced backend defect and narrower approved scope warrants it.
- Steps: trace CTA -> authorized context -> form -> request -> persistence -> success navigation; fix smallest confirmed issue; retain required fields and safe error/draft behavior.
- Commands: frontend `npm test`, `npm run build`, `npm run test:smoke -- tests/create-workflows.spec.ts tests/inventory.spec.ts tests/search-stability.spec.ts`; backend `./mvnw -B verify` if backend surface changes, or targeted real PostgreSQL acceptance tests when validating API authorization; root `git diff --check`.
- Acceptance: each authorized create workflow discoverable and completes; all-Tenant GLOBAL view explains context; readers/PENDING cannot create, hidden action invoked directly remains denied; forged route/body Tenant does not grant permission; Product derivation, place same-Tenant guards, initial Asset states/event and rollback preserved; error keeps draft and avoids ambiguous replay.
- Risks: changing a correct permission gate, form-validation vs API failures. Checkpoint: domain-by-domain human approval of reproduced cause/patch; operator provides later manual data.

### D. Audit actor presentation review

- Preconditions: separate human approval; use displayName snapshot + stable actor UUID, with no Email/username acquisition requirement.
- Candidate files only if a confirmed gap exists: frontend Audit rendering/locales and focused tests; backend read DTO/projection only if needed and separately approved. Audit writers/schema/identity need no assumed change.
- Steps: compare existing display/details/filtering with target; preserve immutable snapshots and captured target scope; do not implement a needless actor schema migration.
- Acceptance: recognizable stored displayName and UUID, no live-name dependency, history stable after rename, no cross-Tenant count/detail leakage or guessed system identity.
- Tests after approval: focused browser presentation; PostgreSQL only for a changed backend surface. Human checkpoint: confirm actual gap before any patch. No Phase D implementation is authorized now.

### E. Regression and acceptance

- Preconditions: separate human approval E; approved preceding patches and resolved D contract. No automatic phase advancement.
- Exact expected files: independent docs/verification/FRONTEND_UX_AND_FUNCTIONAL_DEFECTS.md and its dedicated evidence directory; focused test files named above as needed. Do not overwrite accepted Phase 0–4/UI/CLI evidence.
- Steps: run focused/complete frontend suites/build; required backend PostgreSQL gates for D; browser intermediate-state, permission-bypass and responsive/bilingual checks; inspect console and loaded CSS/image provenance; human manual acceptance with user-created data.
- Commands: frontend `npm test`, `npm run build`, targeted `npm run test:smoke -- ...` for approved repair suites and existing affected workflows; backend `./mvnw -B verify` for schema/audit changes; root `git diff --check`.
- Acceptance: A–D criteria demonstrated, no domain/auth/Tenant/history regression, exact commands/counts and safe screenshots recorded. Backend authorization not inferred from rendering tests. Real IServ remains MANUAL VERIFICATION PENDING.
- Risks: fixture tests mistaken for manual/provider acceptance. Checkpoint: user review, no commit unless explicitly requested.

## 12. Testing data boundaries

For remaining phases after their separate approval, component/browser tests may use deterministic intercepted responses and delayed promises to observe mounted nodes, pending states, focus, paging, errors and out-of-order responses. These are isolated automated fixtures, not production bypasses. Backend migration/query/atomicity/concurrency tests use fresh ephemeral PostgreSQL/Testcontainers, not H2. Only after the relevant implementation approval may those fixtures/databases be created/run. The initial investigation created no persistent application data; subsequent approved Sidebar verification used the deployment administrator and intercepted fixtures without business-data seeding.

Manual acceptance is separate: the human operator creates all Tenant/Catalog/place/Asset data. Do not reuse old smoke databases or seed a fresh manual instance. Any later environment startup needs authorization; this consolidation does not alter or recreate the existing approved disposable environment.

## 13. Rollback

Keep each repair phase logically separable from existing uncommitted stability work and review diffs before application. If a repair fails, revert only its approved patch by a reviewed procedure; never reset/clean/stash the whole worktree or discard pre-existing work. No Git mutation is authorized now. Preserve snapshots/tests of stability before changing shared code.

For an approved actor migration on disposable databases, code and schema rollback means rebuild a fresh disposable database at the prior approved version, not editing migration checksums or reconstructing discarded history. Preserve source V1–V6. No production rollback/repair subsystem is required. Do not deploy schema-dependent UI against an incompatible backend.

## 14. Remaining separately gated work

C: exact failing step/context for the original Asset/Location/Owner creation reports remains UNKNOWN; audited services are complete and concrete-Tenant requirements must not be weakened. Approve reproduction before repairs.

D: displayName snapshot + UUID is the accepted contract. No unresolved TENANT username decision blocks it; review an actual presentation gap before code. System actors are designed only for an actual supported writer, never fabricated to satisfy hypothetical history.

A is accepted/frozen; B is closed. C, D and regression E require separate approval. Collection, metadata, Email/model and export gates are independent of the original creation repair.

## Domain and collection implementation status — backend CLOSED; frontend separately pending

The authoritative current resource/field contract is [DOMAIN §§12–14](../../DOMAIN.md#12-collection-query-contract). The [dedicated implementation plan](COLLECTION_QUERY_IMPLEMENTATION_PLAN.md) owns staged backend-first implementation and records executable gaps. Backend portions below are HUMAN ACCEPTED / CLOSED at commit `939c7373552d676822d0f9d0fe92798fa8618100`; frontend integration, public routing lookup alignment and future export/delivery remain separately pending. This inventory is not a competing obsolete query matrix.

| Resource/work | Accepted backend capability or separately pending integration |
|---|---|
| Categories/Manufacturers | Bounded 0/20/max 100; Name search/visibility; selectable Name/Visibility; createdAt default; normal deleted exclusion |
| Products | Name+number search; visibility/ref UUID/number filters; approved three sorts, createdAt default; no joined-name sorts |
| Locations/Owners | Bounded Name query/order and metadata; nullable note under unchanged Owner edit freeze |
| Assets | Four useful server sorts and approved loanable/dedicated identifier predicates completed; no arbitrary reference/identifier sorts |
| Users | Real bounded Page/count, displayName/enums/authorized narrowing, approved sorts; forced administrative scope |
| Audit | Actor/action/target/context + optional inclusive dates, selectable time direction; captured target attribution |
| History | Bounded owning-Asset timeline; fixed occurredAt DESC; no extra lookup/sort/filter model |
| Tenants/IServ | Intentionally unpaged small Tenant list / one provider resource; no artificial paging |
| Shared Domain foundations | Universal business audit metadata, explicit DTO/model/entity mapping, GLOBAL Email validity/password target |
| Frontend | Metadata-aware pager/six sizes; applied/pending criteria; no persisted query on departure; incremental lookup batches of 20 |
| Export/Email delivery | Separately designed and approved future gates, not part of this repair implementation |

Queries authorize scope, filter the full authorized set, order server-side, paginate and count; no frontend arbitrary-page filtering/sorting or fake paging. No mandatory UUID secondary sort. TENANT scope is service-derived, never caller tenantId proof. Ordinary browser queries exclude soft-deleted data. Existing backend 25/Name+UUID/deleted-selector facts remain historical implementation evidence only, not target requirements. No username/Email-first audit schema work or old decision marker remains required.

The backend collection/identity/metadata/resource work was subsequently completed and accepted; frontend query controls and public routing lookup alignment remain separate approval gates. The 390px Product-name/table limitation remains deferred UX work. No Phase C/D advancement or schema/reset operation is automatic.

## Phase B closeout — human acceptance, 2026-10-11

**HUMAN ACCEPTED / CLOSED.** Human review accepts the implemented Merge combobox, form-session lifecycle, compact actions/toolbars, explicit list filtering, quiet refresh, direct-route repair, confirmation/no-op behavior and supported Assets server-backed header sorting. See [Phase B](../verification/MERGE_COMBOBOX_PHASE_B.md), [B.1](../verification/PHASE_B_1_UI_CONSISTENCY.md), [B.2](../verification/PHASE_B_2_DIALOG_TOOLBAR_UX.md), [B.3](../verification/PHASE_B_3_LIST_FILTERING_AND_ROUTING.md), [B.4](../verification/PHASE_B_4_TABLE_UX_AND_CONFIRMATION.md) for historical results and limitations. Their implementation-time acceptance statuses are historical, not a competing current gate.

No operator-created Asset dataset exists. Real manual Asset sorting against business data is not a closure prerequisite; automated request/order/paging tests and production empty-list/header inspection remain the actual evidence. Do not claim a real Asset dataset was sorted manually. Preserve the recorded 51 unit / 112 Chromium tests, supplementary 26-test rerun, 34 routing checks and production/build evidence without expanding their claims.

At Phase B closure the mandatory backend backlog remained open; it was subsequently completed and HUMAN ACCEPTED in the committed backend sequence. Users pagination, Catalog/Place/Audit sorting, Product criteria and Audit combined querying now exist server-side. Frontend integration is separately pending and must follow the current executable contract, not the historical frontend mocks. Broad column filters and the pre-existing 390px Product-name/narrow-table limitation remain deferred UI work. Phase A/B remain accepted/frozen; Phase C/D require separate approval.


## Backend audit gate — 2026-10-11

The [backend architecture/domain audit](../analysis/BACKEND_ARCHITECTURE_AND_DOMAIN_AUDIT.md) reconstructs executable boundaries, creation flows, all V1–V6 constraints, collection SQL, transaction behavior, API errors and test coverage. CONFIRMED: the full isolated Maven gate passes 74 unit and 241 PostgreSQL integration tests, with zero failures/errors/skips. This evidence does not reproduce the operator's original creation failure. No implementation was performed by the audit.

**Phase C preparation:** the live inventory HTTP adapter is `InventoryOperations`, not the older `SecuredDomainOperations`. Location/Owner creation and Asset registration are implemented and transactional. They independently require an ACTIVE ADMIN authorized for a concrete Tenant; Asset registration additionally requires visible Catalog references, a same-Tenant Location and explicit loanability. The no-context explanation is plausible, not a confirmed reproduction. No backend creation/security/data-integrity defect was established. Separately approve a bounded frontend/context reproduction pass before choosing any C patch; do not weaken concrete-Tenant requirements.

**Phase D preparation:** use the approved displayName snapshot + stable UUID target. Current columns already support that representation; do not add Email/login snapshots or guess TENANT usernames. Review any actual remaining UI gap separately before implementation.

### Current future-gate references — OPEN

[COLLECTION_QUERY_IMPLEMENTATION_PLAN.md](COLLECTION_QUERY_IMPLEMENTATION_PLAN.md) is the single active staged plan for controller/service/persistence queries, metadata, Users/Email, Audit, frontend lookup/paging, model separation and later Excel/Email delivery. DOMAIN owns capabilities/defaults; ARCHITECTURE owns boundaries. Historical audit/verification findings remain accurate observations and do not override the consolidated target.

**Separate error-contract debt:** auth/admin ProblemDetail lacks the stable business error code and the shared AuthFailure mapping labels non401 errors FORBIDDEN. No authorization bypass was established; a narrow sanitized error mapping review requires separate approval.

**Deferred table UX remains OPEN:** 390px Product-name/table wrapping and broad supported column filtering. Query completion does not silently close these.

NO IMPLEMENTATION PHASE MAY START WITHOUT SEPARATE HUMAN APPROVAL. Phase A remains frozen, Phase B closed; no collection, identity, model, export, Email delivery or Phase C/D implementation is authorized by documentation.


## Frontend integration — accepted audit and separately approved execution gates

[Frontend/backend integration delta](../analysis/FRONTEND_BACKEND_INTEGRATION_DELTA.md) traces 77 method-route pairs and 22 frontend consumers against backend commit `939c7373552d676822d0f9d0fe92798fa8618100`. The backend remains CLOSED (latest accepted verification 773 passed); this static audit does not claim live compatibility or rerun historical evidence. Confirmed blockers include login/credential Email payloads, User Page decoding/counts, forbidden Tenant collection coupling, changed Asset name projections and response-size pagination. Tenant-own detail/configuration and physical OIDC DELETE, notes/detail/account projections, supported query controls and distinct error feedback remain frontend integration work.

The report separates 27 frontend deltas from the Product optimistic-error mapping inconsistency, pending public routing lookup normalization/batch alignment and documentation status. Those two backend dependencies require independent approval, not a frontend workaround or reopening of accepted Domain behavior. U1/U2 were subsequently human resolved; their authoritative decisions and accepted Tenant read correction supersede the audit’s earlier restrictive Tenant-read inference. Use the current execution plan, not those historical assumptions.

[FRONTEND_BACKEND_INTEGRATION_PLAN.md](FRONTEND_BACKEND_INTEGRATION_PLAN.md) is the single authoritative execution plan for frontend/backend contract integration: gate ownership, dependency order, decisions, verification and completion protocol are defined there. The audit is accepted evidence/rationale; this document remains the historical UX/functional repair plan. F0/D24, F1a/D01 and F1b/D05–D07 are HUMAN ACCEPTED / CLOSED,2026-10-12. U1/U2 are HUMAN RESOLVED / CLOSED; the Tenant GLOBAL USER read correction is HUMAN ACCEPTED / CLOSED. Original stage evidence and live-backend limitations are preserved; F2–F6/B1/B2 remain separately gated and unimplemented. Preserve Phase A/B geometry, Global terminology, immediate reference search, explicit list Apply, same-dialog Merge/no-op Cancel, ephemeral write sessions and the separate 390px Product-table issue. No Phase C/D, export or Email delivery starts.

## O2 diagnosis — 2026-10-14

Diagnosis only was separately authorized after the accepted post-O1 backlog audit. **IMPLEMENTED / PENDING HUMAN ACCEPTANCE:** [O2 Product table diagnosis](../verification/O2_PRODUCT_TABLE_DIAGNOSIS.md) reproduces the390px Name readability problem in DE/EN with isolated fixtures. Automatic shared table layout plus wrap-anywhere cells and no-wrap headers compress Product Name to94.14px; extra Product number column amplifies the shared mechanism. No page-level overflow reproduced. No production patch; proposed Product-only minimum Name width requires separate human implementation approval. Server queries, DOM identity/focus and pending typing behavior were preserved in the bounded observations. O6 and all unrelated work remain outside scope.

## O2 bounded correction — 2026-10-14

Diagnosis **HUMAN ACCEPTED / CLOSED**. Separately authorized correction **HUMAN ACCEPTED / CLOSED**: Product-only section class and scoped14rem Name minimum; shared table wrapping/overflow and DOM structure remain unchanged. [Correction evidence](../verification/O2_PRODUCT_TABLE_CORRECTION.md) records222 units,27 combined intercepted Chromium tests (including10 O2 cases), successful production build, eight DE/EN viewport inspections and preservation checks. Historical diagnosis evidence remains unchanged apart from its acceptance cross-reference. No backend/query/O6 work, operator writes or subsequent gate. The human explicitly accepted the bounded correction; O2 is HUMAN ACCEPTED / CLOSED. Historical diagnosis/execution records remain preserved.
