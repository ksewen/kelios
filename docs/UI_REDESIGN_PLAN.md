# KELIOS frontend UI/UX redesign plan

**Current documentary boundary (2026-10-15):** The redesign implementation was committed, and dependent Phase A/B plus F0–F6 integration were later HUMAN ACCEPTED / CLOSED. This original Plan and its [verification report](verification/UI_REDESIGN.md) still record a separate visual/user acceptance review as pending; no explicit decision satisfying or waiving it was found. [VR1](plans/remaining-work.md) owns that decision. Pre-redesign code, loginName/size25 and earlier routing/status statements below are dated design evidence, not current contracts. Current authority is [Domain](../DOMAIN.md), [architecture](../ARCHITECTURE.md) and [OpenAPI](api/openapi.yaml).

## 1. Status, authority and boundaries

Redesign implementation is approved. The latest organization-selection decision supersedes the earlier default-provider proposal. Phases 0–4 are accepted; this document does not supersede their business or security invariants. The focused review below precedes further implementation. Historical ADRs, accepted verification evidence and business invariants remain unchanged. The isolated manual-test deployment is left running unchanged.

Authority: `AGENTS.md`, `ARCHITECTURE.md`, `DOMAIN.md`, `CODEX_IMPLEMENTATION_TASK.md`, accepted Phase 0–4 plans, especially the Phase 3 command/API model and Phase 4 localization/localIdentifier contract, and ADR 0001–0005. Current implementation and `docs/api/openapi.yaml` determine what can be presented today. Real IServ interoperability remains MANUAL VERIFICATION PENDING.

Design references: [official dashboard blocks](https://ui.shadcn.com/blocks?category=dashboard), [Sidebar documentation](https://ui.shadcn.com/docs/components/sidebar), and [Vite integration](https://ui.shadcn.com/docs/installation/vite). Use their sidebar/header, compact cards, tables and simple login composition. Do not copy sample metrics, chart data, signup, teams, billing, or Next.js-specific architecture into KELIOS. The current reference offers different primitive families; preserve a coherent primitive family compatible with the existing Radix-based Button rather than mixing families blindly.

## 2. Pre-redesign implementation inventory

| Area | Actual implementation | Redesign implication |
| --- | --- | --- |
| Toolchain | React/React DOM 19.3.0, TypeScript 7.0.2, Vite 8.3.4, Node 24 | Retain the SPA/toolchain; no Next.js migration |
| Styling | Tailwind 4.3.3; `components.json` uses new-york, CSS variables and `@/` aliases | shadcn integration is already established |
| Components | Only `components/ui/button.tsx`; Radix Slot 1.4.0, CVA, clsx, tailwind-merge | Add appropriate shadcn primitives after approval; avoid handmade dialog/menu accessibility |
| Tables | TanStack React Table 9.2.8 | Preserve installed API; do not paste incompatible v8 examples |
| Localization | i18next 26.4.3/react-i18next 17.0.16; static en/de bundles | Extend semantic keys and completeness tests, retain language detection/preference |
| Branding | `branding.ts` loads `/runtime/config.json`, validates same-origin logo and six palette tokens | Retain mounted/no-rebuild brand contract |
| Root | `App.tsx` renders large marketing introduction, connection test, language select, then authentication | Replace with mutually exclusive login, pending, or authenticated application shell |
| Authentication | `Authentication.tsx` owns login, refresh, logout, pending and manual Tenant-ID OIDC form | Separate screen states; keep one `AuthSession` |
| Application | `InventoryApp.tsx` contains 129 dense lines with list/detail/actions, Catalog/place and identity administration | Extract feature pages and focused forms; avoid a parallel service layer |
| Navigation | `pushState`/`popstate`, path matching and navigation buttons; no router dependency | Propose a small established client router after approval, with route guards and nested shell |
| Deployment | nginx SPA fallback; same-origin `/api` proxy; separate images | Real route URLs/deep links are compatible with existing deployment |

The application already switches functional screens; the problem is not that every entity screen literally renders together. All screens remain nested below the oversized introduction/authentication wrapper, and Asset detail renders many unrelated command forms sequentially. This makes the delivered product resemble a technical demonstration rather than an inventory workspace.

Current functions to preserve: registration; Asset all-Tenant/scoped reads, fixed filters/search/sort/pagination; detail and paginated history; four-field metadata replacement; Catalog reassignment; loanability; lifecycle/deregistration; condition; Location/Owner assignment/removal; loan/Return; Catalog/place create/edit/guarded deletion; publication; Category/Manufacturer advisory Merge preview/execution; Tenant list/create; user list/create/approve/role/disable/credential reset; own GLOBAL password change; OIDC configuration; scoped AuditLog list/detail.

## 3. Authentication and Tenant resolution findings

The initial-gap descriptions below refer to the accepted baseline before redesign; the focused review and implemented contract identify the resulting changes.

### What is automatic today

- Local `POST /api/v1/auth/local/login` is GLOBAL-only. A local login identifier maps to a GLOBAL User with null Tenant, not an automatically selected inventory Tenant. “Username” can be the UI label, with no change to the existing `login` request field or identifier semantics.
- A verified TENANT identity belongs to exactly one Tenant. Authenticated Tenant operations use that Tenant from the identity; GLOBAL principals select an authorized operating context after login. No membership, JWT mutation or Tenant session is introduced.
- OIDC external identity is exact `(issuer, subject)`. `OidcProvisioning.complete` loads the provider, validates configuration version/issuer/groups, and rejects an existing mapping bound to another Tenant. New users remain TENANT/USER/PENDING. Email, display name and username cannot resolve external identity.

### Pre-redesign provider-routing gap

`AuthSession.startTenant`, `AuthController` and `OidcLogin.start(UUID tenant)` require `POST /api/v1/auth/oidc/{tenant}/start` **before** the user authenticates. The database provider supplies issuer/client/Discovery. The encrypted flow cookie binds Tenant, provider version, redirect, state, nonce and PKCE verifier. Callback `/api/v1/auth/oidc/{tenant}/callback` verifies that binding and UserInfo subject before provisioning.

`Authentication.tsx` already accepts `/login/tenant/{uuid}` as an entry link, but still displays the editable Tenant-ID field. There is no anonymous provider-directory/default-provider endpoint, trusted hostname-to-Tenant mapping, or identity-first OIDC discovery endpoint in the current OpenAPI. An authenticated identity cannot select its provider retrospectively, and a new JIT user has no existing mapping to query. A single central URL serving multiple Tenant providers cannot resolve a universally correct provider from an anonymous click with no additional routing information.

### Focused identity and authorization architecture review

Reviewed authoritative Domain §§1–2, §8; Architecture §§4, 10–12; original phased task; accepted Phase 0–4 plans and evidence; ADR 0001–0005; V2/V4/V5/V6; current OpenAPI; `Authorization`, `OidcConfigurationService`, `OidcLogin`, `OidcProvisioning`, `IdentityStore`, `AuthenticationService`, `JwtTokens`, `InventoryOperations`, inventory read queries, identity controllers, and frontend AuthSession/navigation.

**Intended model:** configured provider → verified external `(issuer, subject)` → KELIOS User → explicit scope / Tenant / role / status → backend authorization. Provider selection answers where to authenticate, not who the user is or what they may access.

**Current model — MATCHES REQUIREMENT:** V4 uses exact `(issuer,subject)` as external-identity primary key, unique User mapping, TENANT-only CHECK and deferred composite FK to the User's scope/Tenant. `OidcProvisioning.complete` reserves the exact identity transactionally, creates TENANT/USER/PENDING on first login, or reloads its existing User. It rejects another Tenant, non-TENANT scope and DISABLED. Names/email are never identity matching keys. Same display name in different issuers creates distinct users. Discovery, token and UserInfo verification bind issuer, subject, audience, state, nonce, PKCE, callback Tenant and configuration version before provisioning. Frontend selection cannot override these facts.

**Provider/Tenant relationship — MATCHES REQUIREMENT:** accepted Phase 2 plan §3 explicitly specifies one configuration per Tenant (`tenant_oidc_config.tenant_id` PK), one external identity per User, and one Tenant per TENANT User. Configuration is database-managed by GLOBAL ADMIN, encrypted and versioned. There is no generalized multi-provider registry. The same issuer is not unique across configurations: Phase 2 plan §3 expressly rejects claiming an existing identity from another Tenant. This is intentional, not permission to relink membership. A provider *configuration* belongs to one Tenant; an issuer endpoint may be configured more than once, but an exact identity still resolves to only one User/Tenant. No cardinality change is needed.

**Current authorization — MATCHES REQUIREMENT:** active JWT principal supplies explicit scope/Tenant/role. `Authorization` and application operations enforce the following matrix, with scoped database lookups for concealed objects and AuditLog counts/details. Navigation is presentation only. No per-request User reload/revocation is introduced; accepted Access snapshots expire naturally, while Refresh reloads current User and denies DISABLED.

| Operation | Intended GA | Intended GLOBAL USER | Intended TA | Intended TENANT USER | Implemented enforcement / result |
| --- | --- | --- | --- | --- | --- |
| Asset read / all-Tenant read | All | All, read-only | Own Tenant only | Own Tenant only | `InventoryOperations` + scoped reads; MATCHES REQUIREMENT |
| Asset commands | All authorized Tenants | Denied | Own Tenant | Denied | ADMIN plus target/context Tenant validation; MATCHES REQUIREMENT |
| Catalog read | Global / all Tenant contexts | Same, read-only | Global + own Tenant | Global + own Tenant | Visibility-scoped queries; MATCHES REQUIREMENT |
| Catalog edit/create/delete | Global / Tenant, domain guards | Denied | Own Tenant only, domain guards | Denied | `writeCatalog`; MATCHES REQUIREMENT |
| Catalog publication / Category/Manufacturer Merge | Allowed | Denied | Denied | Denied | `globalAdmin`; MATCHES REQUIREMENT |
| Location/Owner read | Any authorized Tenant | Any Tenant, read-only | Own Tenant | Own Tenant | `readTenant`; MATCHES REQUIREMENT |
| Location/Owner writes | Any Tenant, domain guards | Denied | Own Tenant, domain guards | Denied | `writeTenant`; MATCHES REQUIREMENT |
| Tenant list/create | List/create | List only | Own Tenant name only | Own Tenant name only | Identity controller; MATCHES REQUIREMENT |
| User list / administration | All | Denied | Own ordinary USERs only | Denied | Scoped `IdentityStore` queries and `manageUser`; MATCHES REQUIREMENT |
| Approve account | Any PENDING target | Denied | Own PENDING USER only | Denied | Loaded target + status guard; MATCHES REQUIREMENT |
| Grant/change role | Allowed; no self-demotion | Denied | Denied | Denied | GA-only, destructive self guard; MATCHES REQUIREMENT |
| Disable account | Allowed; no self-disable | Denied | Own ordinary USER only | Denied | Target scope/Tenant guard; MATCHES REQUIREMENT |
| Reset GLOBAL credentials | Allowed for GLOBAL target | Denied | Denied | Denied | GA + target GLOBAL check; MATCHES REQUIREMENT |
| Own GLOBAL password change | Allowed, current password required | Allowed, current password required | No local credentials | No local credentials | GLOBAL + current-hash verification; MATCHES REQUIREMENT |
| OIDC configuration | Allowed | Denied | Denied | Denied | GA-only sanitized reads/encrypted writes; MATCHES REQUIREMENT |
| AuditLog list/detail/count | All, optional narrowing | Denied | Captured own Tenant only | Denied | SQL attribution predicate before count/page/detail; MATCHES REQUIREMENT |
| PENDING business access | Denied | Denied | Denied | Denied | Authentication/status only; MATCHES REQUIREMENT |
| DISABLED fresh login / Refresh | Denied | Denied | Denied | Denied | Current User checked; previously issued Access lifetime unchanged; MATCHES REQUIREMENT |

GA/TA mean ACTIVE administrators. User scope/Tenant are not mutable through account administration. No delete/reactivation or last-admin invariant exists. The GLOBAL header context is an authorized workspace parameter, never membership, JWT mutation or session state.

**DOCUMENTATION AMBIGUITY:** none requiring an identity/authorization product decision. Accepted Phase 2 plan §3 explicitly resolves the current provider and identity cardinalities; no generalized provider or membership change is proposed.

**UI-ONLY ISSUE:** the former technical Tenant-UUID field, backend connection panel and developer presentation were routing/usability problems, not authoritative identity sources. Remove them. Role-aware navigation and dashboards must request only data authorized by the matrix above.

**IMPLEMENTATION MISMATCH (unaccepted redesign draft only):** the draft default-provider route/configuration contradicts the latest user instruction. Remove it and its draft tests. The accepted Phase 0–4 baseline contains no default-provider inference and needs no authentication-policy correction.

### Approved anonymous organization discovery and routing

Add a bounded anonymous `GET /api/v1/auth/oidc/organizations` directory of enabled configured organizations, returning only human-readable Tenant name and UUID routing ID, with bounded page/search. Reuse the existing Tenant UUID as a routing handle; users select names and never type or see technical input controls. A fresh routing-ID namespace/table/token would add complexity without granting security. IDs are untrusted routing inputs, not membership proofs. Do not expose issuer, client ID, secret, groups, version, credentials or internal policy.

Root iServ button opens a focused organization dialog with search/pagination. Organization entry links may retain `/login/tenant/{uuid}` and use the same secured start endpoint. No default provider, first-enabled inference, username/email/domain inference, arbitrary issuer input or iServ-initiated login. Existing CSRF/Origin checks and encrypted flow binding remain intact. Callback resolves the verified external identity and rejects an incompatible stored User relationship. Discovery visibility explicitly includes enabled configured organization names as approved; it is not a user directory.

**Required corrections:** minimal public discovery service/controller/OpenAPI contract, public selection UI and deterministic regression tests; remove draft default configuration/route. No migration, accepted role semantics, identity cardinality, JWT transport or security boundary change. Test disabled/unconfigured omission, minimal response fields, bounded search, wrong-origin start, identity mismatch and direct authorization bypasses. No major architectural contradiction or unresolved business decision was identified. Real IServ interoperability remains pending manual verification.

## 4. Navigation and authorization map

GA = ACTIVE GLOBAL ADMIN; GR = ACTIVE GLOBAL USER; TA = ACTIVE TENANT ADMIN; TR = ACTIVE TENANT USER. PENDING has its own access-pending screen and no business navigation. Refreshed DISABLED accounts return to authentication; issued Access behavior is unchanged.

| Sidebar / account destination | Proposed browser view | GA | GR | TA | TR |
| --- | --- | --- | --- | --- | --- |
| Dashboard | `/dashboard` or `/tenants/{id}/dashboard` | All/context | All/context | Own | Own |
| Assets | `/assets`, `/tenants/{id}/assets` | Read/write in selected Tenant | Read only | Own read/write | Own read |
| Catalog → Categories, Manufacturers, Products | `/catalog/{kind}`, `/tenants/{id}/{kind}` | Global/Tenant management | Read | Own visible, guarded Tenant writes | Own visible read |
| Places → Locations, Owners | `/tenants/{id}/locations`, `/tenants/{id}/owners` | Selected Tenant management | Selected Tenant read | Own management | Own read |
| Users | `/users` | Existing all-user administration | Hidden | Existing own-Tenant administration | Hidden |
| Tenants | `/tenants` | List/create | List/read | Hidden as separate management view | Hidden |
| Administration → OIDC configuration | `/tenants/{id}/oidc` | Configure selected Tenant | Hidden | Hidden | Hidden |
| Administration → Audit log | `/audit` | All; optional narrowing | Hidden | Own captured Tenant | Hidden |
| Account menu → Change password | `/account/password` | Own GLOBAL | Own GLOBAL | Hidden | Hidden |

Users appears once, not duplicated under Administration. Role changes are a privileged action inside User detail, not a “Roles” management product. Settings means existing OIDC configuration/account preferences only; no placeholder settings page, generic role editor or permissions builder. Readers may browse Catalog/places; administrative actions are omitted. TA cannot grant ADMIN/manage another ADMIN. Backend guards remain authoritative, including GLOBAL self-protection.

GLOBAL header context offers “All Tenants” for supported aggregate reads plus named Tenants. All-Tenant context does not make place writes, registration or OIDC editing valid; show a focused select-Tenant prompt before those screens. TENANT header shows the authorized Tenant's name read-only. Resolve display names from authorized `/tenants`, never from guessed membership. Preserve existing `/inventory/assets/{id}` detail links; the loaded Asset's authorized Tenant governs commands even when reached from an all-Tenant list.

## 5. Layout and wireframes

### Global shell, header and sidebar

```text
┌──────────────────────┬─────────────────────────────────────────────────┐
│ KELIOS / mounted logo│ ☰  Breadcrumb   [Tenant context]  [🇩🇪 ▾] [Account]│
│                      ├─────────────────────────────────────────────────┤
│ Dashboard            │ Page heading                  Primary action    │
│ Assets               │ Concise description / scope                     │
│ Catalog              │                                                 │
│   Categories         │ Page-specific content                           │
│   Manufacturers      │ Table, detail, form or overview                  │
│   Products           │                                                 │
│ Places               │                                                 │
│   Locations / Owners │                                                 │
│ Users *              │                                                 │
│ Tenants *            │                                                 │
│ Administration *     │                                                 │
│   OIDC / Audit       │                                                 │
└──────────────────────┴─────────────────────────────────────────────────┘
* Only authorized entries; no empty administrative groups.
```

Approximately 240–256px sidebar and 56px header; main content has 24px desktop gutters, 16px mobile, compact page headers, and readable form widths. Persistent desktop sidebar can collapse with labeled tooltips; mobile uses a Sheet with focus management. Header contains breadcrumbs/context, right-aligned language and account menus. Do not show backend-connect panels, UUID dumps, account token controls or automatic refresh buttons as normal navigation.

### Login

```text
                                        [🇬🇧 ▾]
                   [mounted logo / KELIOS]
                   Username  [____________]
                   Password  [____________]
                   [      Sign In         ]
                   [  Sign in with iServ   ]
                   concise error when needed
```

One compact form, approximately 360–400px, neutral background, no promotional hero or competing cards. Branding and the five requested normal elements form the login content; the public language control remains a small upper-right header utility, available before authentication. Username/password keep proper autocomplete and password masking. Inline labels/errors, busy submit state, keyboard operation and accessible password visibility control if approved as an implementation detail. No signup, forgotten-password flow, permanent Tenant selector on the main form, API URL or connection test. The separate iServ organization dialog selects only where authentication occurs. Preserve `/auth/complete` and safe `/auth/failed` handling. Pending screen shows the existing contact-administrator message, account status refresh and sign out; it makes no business requests.

### Dashboard

```text
Dashboard                              [Tenant context in header]
[Total Assets] [In stock] [Deployed] [Outstanding loans]
[Recent Assets: tag | local identifier | state | location       ]
[Recent administrative activity, admins only                   ]
[Tenants / Account summary, only where authorized              ]
```

Useful bounded overview using **existing authorized API data**, without a new analytics service:

- Asset totals: `GET /assets` for GLOBAL all-Tenant context or `/tenants/{id}/assets`, `size=1`, using `totalElements`. In-stock/deployed counts use existing lifecycle filters; outstanding loans uses `loaned=true`. Label scope, and make each card navigate to its filtered Asset view. Zero is real empty data, not a placeholder.
- Recent Assets: same list with existing createdAt descending sort, `size=5`. Say “Recently registered Assets”, not “recent activity” inferred from `updatedAt`.
- Recent administrative activity: `/audit-log?size=5` for admins only. GA selected-Tenant overview may narrow via `tenantId`; TA relies on backend authenticated SQL scope and sends no widening Tenant filter. Do not label this as an all-Asset activity feed.
- Tenant count/list: existing authorized `/tenants` response; GA/GR all or selected context as labeled. TA/TR may show their own workspace name without a separate Tenant-management widget.
- Account summaries: existing `/users` response for GA/TA only, filtered locally for presentation of the already authorized data; GA selected-Tenant summary narrows the returned list. This endpoint is currently an unpaginated list: do not fetch repeatedly or turn it into new scale infrastructure here. GR/TR receive no account summaries.
- Separate widget loading/error/empty states. Count requests are bounded and not a transactional snapshot. Never sum paginated items to invent totals, display sample trends, expose infrastructure health to ordinary users or claim fleet-wide business history absent an API.

### Assets list and registration

List: heading and authorized Register Asset action; compact search toolbar, fixed filters in a Popover/Sheet, existing sort/pagination; TanStack table with Asset Tag primary, Local identifier secondary, Product/category, Location, lifecycle/condition badges and loan status. Include Tenant column in all-Tenant view. No localIdentifier sort/filter, serial uniqueness, Asset delete or misleading generic “ID”. Detail opens through a real link; row actions use DropdownMenu with text labels.

Registration is a dedicated page, not permanently attached to the list. Sections: Catalog choice (Product derives dependencies; direct choice requires Category/Manufacturer), Location/optional Owner, identifiers, explicit loanability, note. Searchable paginated reference pickers display names; UUIDs are supporting disambiguation, not ordinary required typing. Save goes to detail; failures preserve draft. Cancel and dirty-navigation confirmation are frontend interactions, not new backend policy.

### Asset detail and history

```text
Asset Tag 20261010-001             [Actions ▾]
Local identifier R421-PC7          [Lifecycle] [Condition] [Loan badge]
[Overview] [History]
Overview: identifiers | Catalog | Location/Owner | Loan | Note
```

Use compact grouped facts rather than editable forms for every command. Metadata edit, Catalog reassignment, loanability, Location, Owner and state changes open focused Dialog/Sheet forms. Lifecycle and condition transitions remain separate commands; reason required for DEREGISTERED. Loan and Return are explicit actions: Return remains available whenever a current loan exists even for DEREGISTERED/UNUSABLE. No automatic clearing/movement or new state restrictions. History tab shows paginated immutable events, actor/date and readable before/after differences. Preserve raw user values as escaped text and old-snapshot absence semantics. Do not turn event JSON into a generic editor.

### Catalog and places

Dedicated list pages for Categories, Manufacturers, Products, Locations and Owners. Table, search, authorized Create and row actions; concise create/edit forms in Dialog/Sheet. Display visibility, guard errors and protected UNKNOWN status. Tenant-visible GLOBAL entries are read-only for TA. Location rename remains metadata editing with no movement event; Owner/reference and Product historical-edit guards remain backend decisions.

Publication is a distinct confirmation. Category/Manufacturer Merge gets a focused source/target selection and advisory preview view: named targets plus identity/visibility, retained Product/Asset counts, relevant affected-Tenant summary, blockers, stale-preview notice and one explicit confirmation. Use existing APIs; no preview token/session/reservation. Advanced source/target UUID entry is not necessarily a development control: retain an unobtrusive “select by identifier” fallback if needed to choose legal cross-context targets that one current collection cannot enumerate. Do not remove legal Merge scope combinations for UI simplicity.

### Users

Admin-only list with display name, scope/Tenant, localized role/status badges and focused detail panel. GA can create GLOBAL USER/ACTIVE with actual credential, grant/change ADMIN separately, reset GLOBAL credentials and disable permitted targets. TA can approve/disable ordinary own-Tenant USERs only. Pending filter is frontend presentation over the existing authorized list. Password fields are masked, write-only and cleared after success/unmount. No delete, reactivate, forced password change or invented invitation/reset email. Self-disable/demotion is unavailable and backend protection remains.

### Tenants

GA: table of Tenants, compact create dialog and “Open workspace” action; provider configuration action links to Administration. GR: read-only list/workspace links. TA/TR: own context displayed in shell, no misleading Tenant management. No rename/delete. Empty fresh-install state gives GA “Create Tenant” and links to Catalog/places after creation, not synthetic sample records.

### Administration and account

Administration is a navigation group with real pages, not a giant miscellaneous form:

- OIDC: GA-only selected-Tenant form; issuer/client ID, masked write-only secret with retain-existing explanation, allowed UUID groups, enabled state, callback URI and configuration version. Technical provider inputs belong here, not on login. Keep sanitized read contract and no real-provider compatibility claim.
- AuditLog: GA all records/narrowing; TA own captured Tenant only. Table of localized action, target, actor and timestamp; focused immutable detail and sanitized context. Replace GA's manual Tenant-ID filter with authorized Tenant selector while preserving global-only filtering. Target UUID filters remain optional advanced audit tools. Historical captured ownership must not be replaced by live target ownership joins.
- Account menu: display name, localized scope/role, sign out, GLOBAL own-password page. No permissions editor or generic settings without an existing behavior.

### Language selector

Upper-right DropdownMenu trigger shows current locale icon plus accessible language label and chevron. Items are `🇩🇪 Deutsch` and `🇬🇧 English` with selected indicator; flags are decorative, names remain visible. On login the same small public utility is available. Switching updates the current page without navigation, session changes or loss of drafts. Preserve `kelios.ui.language` validated en/de preference, existing browser detection, English fallback, static bundle completeness and localized machine/error display. No tokens/credentials in browser storage; user data is never translated.

## 6. Visual system, accessibility and component integration

Use shadcn Card, Table, Badge, DropdownMenu, Sidebar, Sheet, Dialog/AlertDialog, Input, Label/Field, Select, Tabs, Separator, Tooltip, Skeleton and Alert where each solves an actual interaction. No oversized containers, gradients, ornamental charts or custom component framework. Use restrained green primary accents for normal KELIOS presentation; mounted branding remains authoritative, including the accepted blue/green variants. Do not hard-code green over a deployment palette.

Extend frontend semantic CSS tokens for muted/surface/border/input/ring/popover/sidebar/destructive presentation without changing the public six-token branding schema. Light/dark-compatible component structure means no hard-coded white form surfaces, readable contrast and theme-aware tokens. A dark-mode switch and deployment-specific dark palette policy are not settled requirements; prepare compatible components without silently changing supplied palettes. Review contrast across both accepted branding variants, focus rings and badges; do not rely on color alone.

Adopt a cohesive supported shadcn primitive family and a small icon library if needed after compatibility review. Recommend a lightweight established React client router for nested routes, deep links, not-found handling and draft navigation protection. Verify React 19/toolchain peer compatibility before pinning; do not upgrade existing dependencies opportunistically. Do not add Next.js, a new table library, chart framework, global state framework, query-language layer, or form-schema framework by default. Only approved, compatible primitives, routing and icons may be added.

Accessibility: landmarks, skip link, `aria-current`, keyboard menus, labeled icon buttons, focus restoration/trapping, linked form errors, live mutation feedback, reduced-motion support, adequate touch targets, and responsive tables. Dialogs must fit narrow screens; registration remains a full page. No destructive action hidden behind an ambiguous icon. Successful/error notifications supplement rather than replace inline errors.

## 7. Controls to remove, relocate or preserve

| Current control | Finding | Proposal |
| --- | --- | --- |
| Check Connection / service status panel | Phase 0 technical demonstration exposed in `App.tsx` | Remove from normal UI; preserve technical status API/deployment health |
| Marketing introduction above every screen | Takes space from operational content | Replace with compact branding and shell |
| Manual Tenant UUID at login | OIDC routing workaround, not a backend authorization bypass | Remove visible field only with reviewed entry-routing solution |
| Backend/API URL input | Does not exist in current frontend | Do not introduce one; existing same-origin requests stay |
| GLOBAL Tenant selector | Legitimate authorized post-login operating context | Move to application header, preserve semantics |
| Reload Tenant choices | Technical interaction exposed as a button | Refresh choices after creation/on explicit page reload, without polling infrastructure |
| Refresh account status | Needed on PENDING screen/current permission refresh | Retain focused pending action; not a dashboard debug control |
| UUID/version/context dumps | Some are useful disambiguation/conflict information | Use names normally; preserve UUID support and expectedVersion internally, optional advanced detail |
| Browser-native confirm dialogs | Current destructive/publication confirmations | Replace with accessible AlertDialog retaining explicit confirmation |
| OIDC issuer/client/group UUID controls | Real GA configuration, not debugging | Keep on restricted provider page |
| Many Asset command forms | Valid commands presented simultaneously | Focused action dialogs, no generic PATCH |

## 8. Implementation boundaries and proposed structure

Frontend-only files may be organized as `app` (shell/routes/header/sidebar), `features/auth`, `features/dashboard`, `features/assets`, `features/catalog`, `features/places`, `features/identity`, `features/audit`, and existing `components/ui`. Reuse `auth.ts`, `branding.ts`, `i18n.ts`, inventory command helpers and stable backend contracts. Feature extraction should preserve behavior rather than create generic entity repositories/controllers.

Maintain one AuthSession with refresh/loading/pending/failure state and no login form shown during initial refresh. Protected route guards improve UX; backend is still authoritative. Context/session generation and effect cleanup prevent old Tenant/logout responses repopulating a new view. Abort stale reads where appropriate, preserve drafts on 409/timeout/uncertain success, and never silently replay a mutation. Each mutation builds its existing explicit payload and expectedVersion; metadata sends all four nullable fields. No concurrent background data refresh may overwrite an open edit draft.

Existing APIs can supply the dashboard and all proposed resource screens. No database migration or domain change is proposed. **The only backend/API addition is the approved bounded anonymous organization discovery contract**; existing secured start/callback endpoints remain unchanged. Document the chosen contract and its authorization/Origin/callback consequences before implementing it. No new metrics, health or business-history aggregation endpoint is required for the initial dashboard.

## 9. Review decisions and implementation sequence

Approved design decisions:

1. Approve the page/navigation/dashboard layout and moving commands into focused dialogs/pages.
2. iServ opens approved human-readable organization selection backed by minimal anonymous discovery; no default-provider routing.
3. Approve adding compatible shadcn primitives/icons and an established client router; exact versions/primitive family are technical compatibility choices to record before installation.
4. Dark-compatible structure is approved; no user-facing dark-mode switch. Runtime branding remains independent.

After approval: resolve/document provider entry routing first; add only required compatible primitives/routing; establish shell/login/pending/language; extract resource pages; implement bounded real-data dashboard; convert detail actions and administration; finish responsive/bilingual/accessibility/error states; run regression and visual review. Backend routing changes, if approved, get their own narrowly scoped contract/security tests rather than an authentication rewrite.

## 10. Verification and acceptance plan

- Preserve all accepted Phase 0–4 business/security/migration tests; update frontend presentation selectors intentionally, not assertions of backend guards. Implementation tests and rendered browser reviews use a separate isolated redesign environment.
- Login: ordinary fields only; exact supported endpoints; no Tenant/API input; refresh-on-reload, logout, safe failure and PENDING without business requests. Verify selected routing contract against normal state/nonce/PKCE/callback/group enforcement. Real IServ remains a separate manual gate.
- Routes: deep links, browser back/forward, unknown page, role guards, named Tenant context, fixed Tenant identities, all-Tenant read-only context and authorized resource-derived writes. No stale context/session data leaks.
- Dashboard: true scoped counts from totalElements, zero/loading/error states, no USER audit/user requests, no fabricated data or misleading activity aggregation, bounded network requests.
- Inventory: registration variants, identifiers/localIdentifier search/edit/clear, complete metadata replacement, all dedicated commands, retained history, loaned terminal transitions and Return, no Asset delete, stale-version and uncertain-response draft preservation.
- Catalog/places/users/Tenants/OIDC/audit: full existing guard/privilege matrix, legal Merge selections, advisory stale preview, publication, credential secrecy and target-owned SQL audit isolation. No generic Roles page.
- Localization: both languages, header switching without route/draft loss, first-use detection/persistence, key/interpolation/machine-code completeness, enum/error labels, user data unchanged and no token storage.
- Visual/browser acceptance: desktop/tablet/mobile at approximately 1440/1024/768/390px; keyboard dialogs/menus/sidebar, focus/error/empty states; representative screenshots in both languages and both mounted brands using one frontend image. Do not overwrite accepted evidence; create separate redesign evidence after implementation.
- Regression commands after implementation: frontend `npm test`, `npm run build`, real HTTPS Playwright workflows; backend `./mvnw -B verify` with PostgreSQL if approved backend routing changes occur, plus appropriate accepted security/deployment regression. Existing fresh manual installation is not a fixture database to mutate during analysis.

Acceptance requires product review of the actual rendered screens and real workflows, not merely passing builds. This plan is approved for implementation, subject to the reviewed narrow discovery contract. No Phase 5 scope, implementation, commit, push or change to the running manual-test environment is performed.

## 11. Implemented structure and review status

The reviewed identity/authorization matrix required no policy or persistence changes. The only backend addition is bounded anonymous organization discovery; the existing secured OIDC start/callback and exact identity resolution remain authoritative. No default-provider configuration is implemented.

The frontend uses React Router, a shared AuthSession, lazy authentication/shell boundaries, `app` shell/dashboard/organization components, and `features/inventory/pages.tsx` for explicit existing resource workflows. Related presentation helpers stay local rather than introducing generic entity infrastructure. Existing detail URLs remain supported. Dialogs preserve drafts on conflicts; successful registration clears dirty state before navigation. Session changes unmount stale authenticated views.

Pinned additions: react-router-dom 7.18.4, lucide-react 1.55.0, Radix Dialog 1.2.0, DropdownMenu 2.1.25, AlertDialog 1.1.24, Tabs 1.1.22 and Tooltip 1.3.0. These provide routing, icons and accessible shadcn-style primitives compatible with the existing React toolchain. No chart, form, query, state-management or authentication framework was added.

Implementation verification is recorded separately in `docs/verification/UI_REDESIGN.md`. Visual/user acceptance remains pending review. Historical Phase 0–4 acceptance and real-IServ manual-verification status are unchanged.

Structure note: Catalog browser collections use the existing simple `/{kind}` and `/tenants/{id}/{kind}` URLs rather than introducing a `/catalog/{kind}` prefix. Related explicit resource presentation remains in one feature file; pages render separately and no unrelated CRUD forms are stacked. Neither choice changes the API/domain boundaries.
