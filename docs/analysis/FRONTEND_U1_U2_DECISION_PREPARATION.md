**Current decision: U1 HUMAN RESOLVED / CLOSED, 2026-10-12. Prior pending-projection approval discussion below is historical audit evidence. Complete existing Tenant collection/detail representation is approved for ACTIVE GLOBAL USER.**

# U1/U2 — context and detail-route decision preparation

Date: 2026-10-12. **U1 HUMAN RESOLVED / CLOSED, 2026-10-12; Tenant backend correction HUMAN ACCEPTED / CLOSED, 2026-10-12. U2 HUMAN RESOLVED / CLOSED, 2026-10-12. Original decision preparation was documentation-only; subsequent F1b/D05–D07 is HUMAN ACCEPTED / CLOSED, 2026-10-12.** Backend baseline `939c7373552d676822d0f9d0fe92798fa8618100`; F0/D24 and F1a/D01 human accepted/closed. F1a live-backend limitation is unchanged.

Purpose: provide the bounded facts/choices needed to decide U1 and U2 without another architecture audit. [Execution plan](../plans/FRONTEND_BACKEND_INTEGRATION_PLAN.md) remains authoritative; this document changes no API, authority or resource lifecycle.

## 1. Inspected facts and evidence

Frontend paths below are current dirty-worktree files, not an assumed newer implementation:

| Source | Verified behavior relevant to these decisions |
|---|---|
| `frontend/src/App.tsx:5–6` | React Router has one catch-all route mounting Authentication/ConfirmationProvider; resource routing is dispatched manually in Shell, not separate declared detail routes |
| `frontend/src/Authentication.tsx:16–24` | Signed ACTIVE identity enters Shell; pending remains restricted. Unknown OIDC routing requires explicit organization selection; known `/login/tenant/{routingId}` is authentication routing, not resource context |
| `frontend/src/app/Shell.tsx:19–23` | Business navigation builds root or `/tenants/{T}/{resource}` links. GLOBAL operating context comes from route prefix; TENANT uses signed `user.tenantId`. Shell unconditionally reads `/api/v1/tenants`, an obsolete dependency for USER/TA |
| `Shell.tsx:27–40` | Asset detail alone matches explicit `/inventory/assets/{id}`. Other screens use final path segment. `/`, `/login`, `/auth/complete` normalize to context Dashboard. TENANT foreign prefix redirects to signed Dashboard |
| `Shell.tsx:43` | Logo/Home navigates context Dashboard; Global dropdown for all GLOBAL roles currently lists names from /tenants and changes route, not identity. Missing concrete name falls back to “Workspace.” TENANT name also depends on forbidden collection. Resource/form container key includes identity, Tenant and full pathname |
| `frontend/src/app/Dashboard.tsx:10–11` | Aggregate/concrete Asset metrics use server counts; recent Assets use size5. Tenant name/count unconditionally reads administrative list. User admin metrics read Users only for ADMIN. No ordinary reader needs these admin reads |
| `frontend/src/features/inventory/pages.tsx:102–109` | Root Assets GET `/api/v1/assets`; contextual Assets GET `/api/v1/tenants/{T}/assets`; rows contain IDs and currently render old flat names (D09). Asset row/detail link uses `/inventory/assets/{id}` |
| `pages.tsx:112–115` | Asset detail/history read independently by UUID. This provides a reference for reconstruction, not a requirement to copy all Asset UI behavior |
| `pages.tsx:133–154` | Shared Management selects collection Row in local state; modal opens without URL change. Form uses row fields, not independent detail; note absent. Catalog root and contextual list paths are distinct; Places need concrete Tenant |
| `pages.tsx:167–175` | Tenant list Open goes to `/tenants/{T}/dashboard`, not Tenant resource detail. Users select local collection Row and nested command modals; no User UUID detail request. User Page consumer is still a pending integration |
| `frontend/docker/default.conf:35–37` | Existing frontend-serving fallback is SPA index; direct navigation support is not equivalent to a implemented React detail route. Configuration was read only; no deployment/runtime verification here |

Backend sources under `backend/src/main/java/com/github/ksewen/kelios/`:

- `platform/security/InventoryOperations.java:22–27`: ACTIVE GLOBAL aggregate/concrete Asset reads; signed Tenant isolation for TENANT. Asset detail UUID independently scoped. `inventory/InventoryReads` obtains authoritative associations.
- `catalog/CatalogLabelOperations.java:27–44`, `ProductOperations.java:19–26`, label/Product Reads.list: root context constrains Catalog collection to GLOBAL entries; concrete context includes GLOBAL plus that Tenant. GLOBAL UUID detail is independently authorized across business ownership; TENANT detail limited to visible GLOBAL/signed Tenant.
- `inventory/PlaceOperations.java:21–31`: collection needs concrete effective Tenant; UUID detail independently scoped. Place/Asset/Product/Catalog DTOs contain safe Tenant/reference ID/name projections where applicable.
- `tenancy/TenantOperations.java:14–18`: ACTIVE GA collection/create/detail; TA own detail only, USER denied. `identity/auth/AuthenticationService.list/detail` and `IdentityStore` force TA own ordinary USER population.
- `platform/security/{CatalogController,PlaceController,IdentityAdminController}.java`: concrete independent ID detail endpoints in §4. `*Dtos` define detail richness/metadata/note/account projections without secrets.

A route/path ID is a locator, never authority. Knowing it grants no additional scope. No administrative or anonymous routing source is needed to authorize business reads; the backend independently decides them.

## 2. U1 — current context facts

1. Normal GLOBAL login from `/login` goes to `/dashboard` with empty Tenant context, displayed as **Global**. A login entered from an existing business URL stays there; refresh reconstructs identity and context from signed session/URL, not a saved membership selection.
2. `/tenants/{T}/assets|categories|manufacturers|products|locations|owners|dashboard` embeds a concrete business context. For GLOBAL, the supplied UUID narrows approved resource reads; it does not grant write/Admin authority. For TENANT, signed Tenant is effective; no switching option is needed.
3. Aggregate ordinary reads differ by resource: Assets/Dashboard Asset metrics can span authorized Tenants; root Catalog shows GLOBAL Catalog entries only; Locations/Owners have no unscoped aggregate collection. Do not imply Global Catalog is an all-Tenant Catalog inventory.
4. Current dropdown enumeration, context names, Dashboard Tenant count/name and TenantAdministration all depend on administrative `/api/v1/tenants`. Current backend allows the list for GA only; human now requires safe GLOBAL USER discovery without administration authority. Current implementation denies USER Tenant detail; that restriction is not the newly clarified product intent. Anonymous `/auth/oidc/organizations` remains routing only.
5. Existing safe **business response** fields provide context labels: Asset Item/Detail has `tenant:{id,name}`; contextual Catalog Item/Detail may have tenant reference or null for a GLOBAL row; Place Item/Detail has owning Tenant reference. These are server-projected authorized associations, not invented safe-Tenant endpoint results. Root GLOBAL Catalog rows do not discover concrete Tenants.
6. A concrete list can return zero rows. Successful empty business read can validate the supplied locator's permitted query, but supplies no Tenant name projection. There is no existing ordinary-reader standalone Tenant-name lookup. Honest fallback must show that name is unavailable (for example “Tenant” plus technical UUID); do not guess a name or fetch administrative detail. A name received from a resource is presentation only; do not persist an old name as current authority.
7. Canonical Asset UUID detail currently has no Tenant prefix, so GLOBAL Shell still shows Global while the resource has an owning Tenant. Operating context and resource ownership must be presented separately. An explicit action may navigate to owning Tenant context using its authorized association ID/name; do not silently infer membership or auto-switch identity.
8. The frontend currently parses broad UUID-looking strings rather than exact UUID shape. Any future UUID-entry/context action needs format validation and must let backend resolve authorized resource facts. This is a future F1b detail, not a patch in this task.

## 3. U1 — clarified intended behavior; pending read correction

The previous A/B/C alternatives and restrictive read inference are **rejected**, not fallback implementation choices. GLOBAL USER initially enters Global and has a Tenant dropdown, asynchronously/lazily loaded if appropriate, displaying authoritative ID/name. Selecting UUID enters ordinary business context without changing identity or granting administration. TENANT principals stay signed-own; anonymous Provider lookup is never context discovery.

[Bounded read-authority audit](GLOBAL_USER_READ_AUTHORITY_AUDIT.md) verifies current Tenant GET guard and exact projection. Existing collection/detail expose identical ID/name/metadata, no secrets. Reusing existing collection is the smallest safe-read candidate. Remaining human approval concerns full metadata exposure versus a reduced role-specific projection, and whether detail widening accompanies required collection widening. No replacement endpoint or backend correction is implemented. Existing lack of ordinary standalone Tenant labels is a current implementation fact, not a product prohibition.

## 4. U2 — current and proposed resource route matrix

**Current stable ID detail route: none for these seven resources.** `App` catch-all serving accepts the URL but `Shell` does not reconstruct these UUID details: `/categories/{id}` etc ends in UUID and normally renders pageUnavailable. `/tenants/{id}` is likewise not a resource detail view; `/tenants/{id}/dashboard` is a business context Dashboard, not Tenant administration detail. Empty UUID/list membership cannot be repaired by q filtering.

`T` means business context UUID, `R` resource UUID. Canonical paths and preferred presentation below are human approved; implementation remains pending and APIs remain unchanged. “Modal + direct page” means one shared independent detail content: route-backed overlay only when opened from a still-active appropriate list, standalone page when direct entry/refresh has no background list. It never requires a retained collection Row as detail data.

| Resource | Current list / selection | Actual independent detail API | Direct entry/refresh now | Authority boundary | Approved preferred composition | Approved canonical route |
|---|---|---|---|---|---|---|
| Category | `/categories` or `/tenants/{T}/categories`; Management Row selection → local modal, nested edit Command | GET `/api/v1/catalog/categories/{R}` (`CatalogController:23–24`) | No ID view; selection lost on refresh | ACTIVE ordinary business read; TENANT visible GLOBAL/own only, foreign/absent404. Writes use approved ADMIN/visibility/reference/UNKNOWN guards | Route-backed modal from list; standalone detail fallback. Small name/visibility/metadata read and existing commands fit current modal structure | `/categories/{R}` |
| Manufacturer | `/manufacturers` or contextual prefix; same local modal | GET `/api/v1/catalog/manufacturers/{R}` (`CatalogController:61–62`) | Same absence | Same Catalog scope/guards; manufacturerNumber is not a Manufacturer field | Same modal/fallback as Category for consistency | `/manufacturers/{R}` |
| Product | `/products` or contextual prefix; Row-based modal, Category/Manufacturer edit selectors | GET `/api/v1/catalog/products/{R}` (`CatalogController:93`) | No ID detail; collection selected refs cannot reconstruct after refresh | ACTIVE business read; TENANT GLOBAL/own; permanent ordinary edit freeze after historical Asset use, retained-reference delete guard; no Merge | Standalone detail page for richer readable associations/number/visibility/metadata and explicit guarded commands | `/products/{R}` |
| Location | `/tenants/{T}/locations`; Row-modal name edit, no note/detail fetch; root `/locations` prompts context | GET `/api/v1/locations/{R}` (`PlaceController:15`) | No ID detail, even though API read is independent | ACTIVE scoped read; signed TENANT only; GLOBAL permitted detail. Referenced Location editable, delete blocked; note in detail | Route-backed modal + standalone fallback fits small name/note resource; ID GET required to avoid note erasure | `/locations/{R}` |
| Owner | `/tenants/{T}/owners`; Row-modal name edit, no note/detail fetch | GET `/api/v1/owners/{R}` (`PlaceController:20`) | No ID detail | Same read scope; retained refs prohibit every edit/delete including note. Current DTO has no generic canEdit/usage-count flag: do not invent one | Modal/fallback like Location, with authoritative guard feedback and readonly detail available; note does not bypass guard | `/owners/{R}` |
| User | `/users`; old array consumer and Row-based modal with approve/role/disable/credential commands | GET `/api/v1/users/{R}` (`IdentityAdminController:18`) | No ID view; list selection required | ACTIVE GA authorized admin; TA own signed ordinary USER population only; USER denied. TA cannot read an ADMIN/self profile by reinterpreting this admin endpoint. No secrets or User deletion | Detail page preferred for status/role/safe account/metadata and consequential commands; avoid layering a new identity model into list modal | `/users/{R}` |
| Tenant | `/tenants` list for current GLOBAL route, must become GA-only; Open navigates context Dashboard. TA currently has no own-detail entry | GET `/api/v1/tenants/{R}` (`IdentityAdminController:15`) | Neither `/tenants/{R}` nor its Dashboard is administrative detail | ACTIVE GA list/detail/create; TA own detail only, no list; USER denied; edit/delete undefined | Detail page for GA and TA. A required list-backed modal would wrongly depend on a collection TA cannot read. Name/metadata safe readonly; no invented lifecycle | `/tenants/{R}` |

Asset reference remains `/inventory/assets/{R}`, API `/api/v1/assets/{R}` and owning history; do not rename it for visual symmetry. No new entity route serves as authority or maps a readable name to identity.

## 5. U2 — HUMAN RESOLVED / CLOSED, 2026-10-12

Approved canonical routes are the seven matrix routes; preserve `/inventory/assets/{id}`. No contextual detail aliases are needed for authority. Detail independently loads by persistent UUID; list membership/Row state is never authority. Direct entry/refresh reconstructs through independent detail GET. Backend scope/non-disclosure controls visibility.

Category/Manufacturer/Location/Owner use route-backed modal from a live list with standalone direct-entry/refresh fallback. Product/User/Tenant use standalone page. Modal/page is presentation, not authorization. Tenant administrative detail does not change operating context. No backend route renaming or new CRUD is implied.

Implementation must distinguish `/tenants/{id}` detail from contextual business routes; preserve semantic navigation/focus and dirty-session policy. Current full-pathname Shell key requires deliberate background-route composition to keep a live list mounted, not retained Row authority or random/revision keys. Closing an overlay restores focus where the origin remains; standalone refresh needs no collection state. These are future gate requirements, not implementation here.

## 6. Decision/verification boundary

| Item | Status | Remaining boundary |
|---|---|---|
| U1 | HUMAN RESOLVED / CLOSED, 2026-10-12 | Full existing collection/detail approved; backend correction and F1b/D05–D07 HUMAN ACCEPTED / CLOSED, 2026-10-12 |
| U2 | HUMAN RESOLVED / CLOSED, 2026-10-12 | Routes/composition approved; implementation remains separately gated |
| U3 | Unchanged | No new runtime/provider/operator evidence |

F1a/D01 acceptance/evidence/live limitation unchanged. F1b/D05–D07 HUMAN ACCEPTED / CLOSED, 2026-10-12; no later gate authorized. Static source facts in sections1/2/4 describe current implementation, not authority to retain obsolete read restrictions. No tests/runtime/deployment/data operations.

## Approved U1 resolution — 2026-10-12

Complete existing Tenant collection/detail ID/name/metadata projection and bounded actor UUID/timestamp disclosure approved for ACTIVE GLOBAL USER. No new endpoint/reduced DTO. GA-only create; TA own detail/no list; TU/status/anonymous denial unchanged. Other admin reads unchanged. U1 HUMAN RESOLVED / CLOSED. Global-first UUID/name dropdown optionally lazy loaded is approved future F1b behavior; identity/admin authority unchanged. [Backend correction](../verification/TENANT_GLOBAL_USER_READ_CORRECTION.md) HUMAN ACCEPTED / CLOSED, 2026-10-12. F1b/D05–D07 HUMAN ACCEPTED / CLOSED, 2026-10-12. Original F1b evidence/limitations preserved; no later gate authorized.
