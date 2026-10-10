> Implementation authorization update: the user explicitly approved Phase 3 implementation after this finalized specification. The original planning review below is retained as the implementation contract. No P1–P8 decision changed. Actual implementation results belong in [Phase 3 evidence](../verification/PHASE_3.md), and user acceptance remains pending review. No Phase 4, commit or push is authorized.

# Phase 3 plan — Inventory workflows and UI

Status: **P1–P8 and the DEREGISTERED event convention approved; Phase 3 implementation explicitly approved. User acceptance pending review.** Phases 0, 1 and 2 are accepted. The design below was finalized during planning and is now the approved implementation contract; actual results are recorded separately in Phase 3 verification. Real IServ interoperability remains separately pending manual evidence and does not block this plan.

Authority: [Domain](../../DOMAIN.md) and [Architecture](../../ARCHITECTURE.md); the original **Phase 3** section of [Implementation Task](../../CODEX_IMPLEMENTATION_TASK.md) defines the upper-level scope. [AGENTS.md](../../AGENTS.md) was re-read; its original Phase 0 starting instruction does not reset the user's subsequently accepted phases. All documentation remains English; repository-only work, no commits/pushes.

Reviewed context: [ADR 0001](../adr/0001-phase-0-foundation.md), [ADR 0002](../adr/0002-phase-1-domain-persistence.md), [ADR 0003](../adr/0003-phase-2-authentication-and-authorization.md); [Phase 0](PHASE_0_PLAN.md), [Phase 1](PHASE_1_PLAN.md), [Phase 2](PHASE_2_PLAN.md) plans; [Phase 0](../verification/PHASE_0.md), [Phase 1](../verification/PHASE_1.md), [Phase 2](../verification/PHASE_2.md) evidence and [manual IServ procedure](../verification/PHASE_2_ISERV_MANUAL.md); backend services/security, frontend/authentication/branding, [OpenAPI](../api/openapi.yaml), and Flyway V1–V4. Relative source paths below are from the repository root.

**Labels:** **Settled** identifies preserved Phase 0–2 rules. **Approved Pn** identifies user-approved Phase 3 decisions, reflected in Domain/Architecture where appropriate. **Proposed** identifies implementation/API/UI details within those approved rules, submitted with this finalized plan for explicit implementation approval. No P1–P8 question remains unresolved. Historical ADRs/evidence remain unchanged.

**Audit traceability:** [Audit attribution analysis](../analysis/PHASE_3_AUDIT_ATTRIBUTION.md) establishes writer gaps and legal Merge combinations. Its conditional legacy recommendations and pending-FK conclusions are superseded by approved P7: **KELIOS has not been deployed; no production historical AuditLog requires preservation or reconstruction.** Disposable development/test data does not justify a third attribution state, compatibility model or repair workflow. The analysis remains unchanged as technical evidence.

## 1. Scope and non-scope

Expose usable inventory through existing application services plus missing workflow operations, authenticated `/api/v1` endpoints and a minimal frontend. Implement browse/search/detail/registration/edit; approved Lifecycle/Condition transitions; Location/Owner changes; embedded loan/return; immutable AssetEvent timeline; authorized administrative AuditLog display; minimal Catalog and Tenant administration UI. Publication and Category/Manufacturer Merge remain separate, with confirmation and an impact preview, as required by original Task Phase 3 items 3–4.

The minimum management UI covers **all Category, Manufacturer, Product, Location and Owner operations listed in the current Phase 3 request**, plus screens for existing Tenant/user/OIDC administration APIs deferred from Phase 2 plan §§1, 12. This exposes accepted behavior, not new account administration rules.

Excluded: Phase 4 work, bulk import, procurement, Repair domain/tickets, maintenance workflows, PhysicalDevice, serial uniqueness, Asset name, duplicate detection/automatic Merge, Product/Owner/Location Merge, hierarchies/types/PIM, Loan entity, reservations/overdue scheduling/notifications/fines/borrower accounts, automatic return/movement, QR/labels/printers, Redis, WebSockets, offline/mobile features, analytics/dashboards/charts/saved views/report builders/query languages, generic admin/RBAC/workflow/provider frameworks, multi-Tenant membership, continuous IServ synchronization, privacy deletion/anonymization, and new account recovery/reactivation/deletion policies.

No user-facing Asset deletion, deleted-Asset list/filter UI, deleted-Asset mutation workflow, undelete or hard deletion. Preserve internal Asset soft deletion and retained-reference semantics.

No infrastructure or schema solely for UI convenience. No token/session/revocation redesign. A production ingress change or real-provider test is not a prerequisite for planning.

## 2. Accepted Phase 0–2 foundation reused

| Foundation | Preserve and reuse |
|---|---|
| Phase 0 / ADR 0001 | Java 25 LTS, Boot 4.1.1, Maven 3.9.16 Wrapper; Node 24; React/TypeScript/Vite, shadcn/Tailwind, TanStack Table; separate non-root multi-stage images, nginx `/api` proxy, Flyway/PostgreSQL, UUID/UTC/JSON/RFC 9457, checked-in OpenAPI. Runtime-mounted branding JSON/logo and `KELIOS_CONFIG_FILE` work without image rebuild. |
| Phase 1 / ADR 0002 | Minimal Tenant/User; accepted Catalog/Location/Owner/Asset mappings, tags, composite/deferred FKs, Asset `@Version`, immutable history, protected UNKNOWN, guarded mutation/publication/Merge. READ COMMITTED and concrete parent-lock handshakes, no derived Product usage flag or generic triggers/locking framework. |
| Phase 2 / ADR 0003 | Explicit scope/role/status principal and `Authorization`, `CurrentIdentity`, `SecuredDomainOperations`; dynamic database IServ OIDC, JIT PENDING, login-only group eligibility, current-User refresh; existing identity administration endpoints. Memory Access Bearer, Secure HttpOnly Refresh cookie, same-origin HTTPS, default-off CORS, stateless CSRF/Origin on unsafe ambient-cookie auth endpoints, RS256/absolute refresh lifetime. |

No per-business-request database account revocation check: a previously issued ACTIVE Access JWT remains valid until its approved expiry after disable/demotion. PENDING signed principals have no business access; DISABLED cannot obtain new tokens/refresh, and a token explicitly claiming DISABLED is rejected. UI and tests must preserve this distinction.

Tenant is immutable per Asset and explicit per User scope. Historical actors use UUID/display snapshots, with no mandatory live User FK. Generic `engine.statemachine` stays independent of Asset, JPA, persistence, OIDC and authorization.

## 3. Current implementation inventory and gap analysis

| Current implementation | Phase 3 gap / planned extension |
|---|---|
| `inventory/AssetService.java`: `register`, `get`, `tenantOf`, `changeLocation`, `changeOwner`, `changeCatalog`, `editText`, `delete` | Reuse these transactional operations and registration shapes. Add scoped list/detail projections, workflow operations and explicit secured HTTP adapters. Do not recreate persistence or tag allocation. Expose existing text/Catalog commands and add separate guarded loanability command under P1; keep delete internal/unexposed under P2. |
| `inventory/Asset.java` | Already contains all approved fields including loan data and version; no new Asset identity/name/entity. |
| `AssetService.View` and `snapshot` | Current View and snapshots omit `loanedTo`, `loanedAt`, `expectedReturnAt`, `loanNote` and some audit fields. Extend read projections and new snapshots for complete loan/current-state/history presentation; retain existing JSON Product-ID paths and old immutable payloads. |
| `InventoryGraphs.java`, `engine/statemachine/StateMachine.java` | Approved graphs and full pure-engine transition tests already exist. Add inventory workflow orchestration, not graph redesign or engine side effects. |
| `AssetHistory.java` / `AssetEvent.java` | Append-only write path with structured before/after snapshots exists; no list/history HTTP service. Add scoped ordered read DTOs and reason/loan payload handling for new events, preserving old events. |
| `catalog/CatalogService.java`, `inventory/PlaceService.java`, `CatalogMergeService.java` | Existing create/read/edit/delete/publication/Merge and locks/guards are implemented. Add scoped lists, UI/API and advisory Merge impact read; commit still performs fresh validation/discovery. |
| `platform/security/SecuredDomainOperations.java` | Existing secured mutation wrappers supply actors server-side. Extend explicit wrappers for new workflows and reads; do not expose raw internal services. |
| `SecurityConfiguration.java`, `AuthProblems.java` | Current security explicitly permits only authentication and identity administration routes; unknown business routes are denied. Add only reviewed inventory/Catalog/place/audit routes, ACTIVE gates and suitable business errors/body bounds; preserve unknown-route 404 and auth CSRF behavior. Current advice is scoped to identity controllers and maps generic IllegalArgumentException to 400; it is not an adequate business guard/not-found classifier. |
| `tenancy/TenantService`, `IdentityAdminController`, identity authentication services | Tenant list/create, user list/create/approve/role/disable/credential and versioned OIDC configuration already exist. Build their minimal administration screens; no new Tenant rename/delete or account reactivation/privacy policy. |
| `audit/AuditService`, `AuditLog` | Administrative write path exists; no browsing API. The current eight-column schema has no Tenant attribution. P7 requires V5, explicit target-owned attribution at every writer, and scoped GLOBAL ADMIN/all and TENANT ADMIN/own-Tenant audit reads; no scope inference from live state or JSON. |
| `frontend/src/App.tsx`, `Authentication.tsx`, `auth.ts` | Branding/technical shell and login/pending/refresh/logout only. Add authenticated navigation, shared existing session state, safe Bearer requests, inventory/management screens. TanStack Table dependency exists; no inventory table yet. |
| `docs/api/openapi.yaml` | Phase 2 contract only, 17 paths / 19 operations including status. Extend after approval, preserving existing endpoints/security schemas. No contract change in this planning task. |
| V1–V4, existing backend/frontend/browser tests | Asset schema supports workflows; AuditLog requires additive V5 (§21). Preserve V1–V4 and existing tests/invariants; add meaningful application/HTTP/browser tests only after approval. |

The current core graphs match Domain §6. No contradictory active Product Merge, permanent usage latch, universal SERIALIZABLE policy or generic trigger strategy was found. Missing workflow services/UI are expected phased gaps, not failures of accepted Phase 1/2.

## 4. Asset registration

**Settled:** Domain §§4, 11 and existing `Registration`/sealed `CatalogSelection` are the contract. Proposed endpoint: `POST /tenants/{tenantId}/assets`. The path Tenant ID expresses requested operating context and is authorized/loaded server-side (§14); it is not a caller assertion of ownership. No Tenant field in the JSON command.

Use mutually exclusive JSON input shapes:

- Product-backed: `productId`, required `locationId`, optional `ownerId`, required explicit boolean `loanable`, optional `serialNumber`, `externalInventoryNumber`, `note`. Reject redundant Category/Manufacturer fields rather than choosing which copy to trust.
- Product-less: required `categoryId` and `manufacturerId`, no selected Product, and the same other inputs. No generic UNKNOWN Product; Category/Manufacturer UNKNOWN are valid entries under existing rules.

Load Product and derive dependencies, or load direct Category/Manufacturer. Load Location/Owner and enforce same Tenant, non-deleted selection and Catalog visibility with accepted locks. Generate UUID and UTC `yyyyMMdd-NNN` tag; sequence minimum three digits, expands past 999, global atomic daily counter and independent uniqueness. No serial uniqueness or gapless promise. Set REGISTERED/UNKNOWN, no loan. Reuse blank-optional-to-null and existing service normalization; required text remains non-blank.

Reject unknown/server-owned JSON fields: UUID/tag, Tenant facts, visibility/dependencies/snapshots, lifecycle/condition, current loan, version on registration, actor/audit timestamps. Server principal supplies actor snapshot and Clock supplies time. Commit Asset and one REGISTERED event atomically; 201 with Location header and current detail/version is proposed.

Form: explicit Product versus no-Product mode; Product dependencies displayed read-only, never sent redundantly. Require Location and explicit loanable choice; Owner removable/optional; optional text clearly labeled. Fetch paged non-deleted visible options from backend. Required manual `Unbekannt` Location remains a Tenant-created label, not an auto-generated seed. Show tag only after successful registration. Handle reference deletion/Merge/visibility changes since selection as backend validation/conflict, preserving user input for review. Never resubmit after uncertain network success automatically.

## 5. Asset browsing, detail and history

**Approved P6:** bounded offset pagination, fixed filters/sorts and literal substring search; the concrete supported fields below are implementation choices within that approval, not a query language. Tenant list route returns `{items,page,size,totalElements}`; zero-based page, default size 25, maximum 100, non-negative page, reject unsupported sort/filter values with 400. Default `createdAt DESC, id DESC`; supported primary sorts: tag, createdAt, lifecycle, condition, each with UUID tie-breaker. Offset pagination is deliberately simple and not snapshot-consistent under concurrent writes; a refresh may change page membership.

Filters: lifecycle, condition, Location ID, Owner ID, Category ID, Manufacturer ID, Product ID and `loaned=true|false`; literal case-insensitive substring `q` (maximum 128 characters) on tag, serial number and external inventory number, with SQL parameters and escaped wildcard metacharacters. Do not infer Asset identity from search matches. No full-text/search infrastructure or speculative index additions. Public Asset lists/detail/workflow lookups exclude soft-deleted rows in the database; no deleted-Asset filter/UI or public mutation workflow. Internal reference guards/Merge scans still include all retained rows. DEREGISTERED remains in normal lists because it is not deleted. Selection lists always exclude deleted entries.

Approved P5 includes `GET /assets`, an additional **GLOBAL-only** paged read across Tenants with the same search/sort/filter semantics and optional Tenant filter. It supports Domain §4 cross-Tenant serial search and both GLOBAL read roles; TENANT principals must use their scoped route. Default frontend view uses an explicitly selected Tenant; GLOBAL users may choose an all-Tenant read view, with Tenant column and no registration until one Tenant is selected. Any supplied reference filter is validated within the applicable read scope; no cross-Tenant existence disclosures.

List columns: tag/link, Product or Category/Manufacturer, serial/external inventory number as available, Location, Owner, lifecycle, condition and current loan indicator; Tenant in all-Tenant view. TanStack Table drives server pagination/sort/filter controls, not frontend-only filtering of unrestricted records.

Detail loads authoritative current display labels alongside IDs, version, nullable fields, all current loan data, identity and created/updated timestamps/actor UUIDs. No deleted-Asset browsing view is exposed. Existing retained references remain displayable after metadata rename/deletion. No fabricated live actor display name for audit UUID fields lacking a snapshot; event actor display comes from its stored snapshot. Backend may return permitted transitions from the existing graphs for usability, but revalidates every command. No event-query N+1 or eager history dump in every list item.

History: `GET /assets/{id}/events`, page size 25/max 100, descending `(occurredAt,id)` with deterministic ties, read-only. Show event type/time, actor display snapshot and meaningful before/after/reference/loan/reason data; render stored labels, not live joins as substitutes for history. Old payloads omit newly added loan keys: show unavailable historical detail without rewriting/backfilling or claiming absent data means no loan. A stable ordered presentation does not claim a total commit order for concurrent unrelated transactions. Fresh history is fetched after successful mutation.

## 6. Ordinary Asset metadata editing

**Settled:** UUID, Tenant and tag are immutable (Domain §4, ADR 0002). Lifecycle, condition, Location, Owner and loans have dedicated commands, never arbitrary PATCH fields. Existing `editText` permits serial number, external inventory number and note; existing `changeCatalog` explicitly supports Product selection or direct Category/Manufacturer with derived dependencies and UPDATED history.

**Approved P1:** expose text edits exactly as `PUT /assets/{id}/metadata` with `serialNumber`, `externalInventoryNumber`, `note`, `expectedVersion`; this is replacement of this small field set, including nullable clearing, not general entity replacement. Expose existing reference reassignment as separate `PUT /assets/{id}/catalog` with the registration Catalog-selection union plus expectedVersion. Selection continues to reject deleted/unavailable entries; Product edit freeze is unrelated to an explicit Asset reassignment, and before/after Product IDs preserve permanent usage evidence.

`PUT /assets/{id}/loanability` is a separate explicit business command taking required loanable and expectedVersion. Load the Asset and reject with 409 if any outstanding loan exists (`loanedTo != null`); otherwise change loanability with optimistic version and one atomic UPDATED event. Text and Catalog edits likewise create UPDATED atomically. Loanability is not registration-only and never belongs in arbitrary metadata PATCH. Never place loanedTo/time/state or identity fields in ordinary metadata JSON. Reject overposted fields with 400.

All mutable Asset requests require a non-negative expectedVersion, return updated version/detail, and supply actor/time server-side. Stale request returns RFC 9457 409 with a stable conflict code; UI keeps the draft, loads latest state and requires explicit reviewed resubmission. No silent overwrite, stale replay or whole-command retry.

## 7. Lifecycle workflow

**Settled graph:** Domain §6 and `InventoryGraphs.LIFECYCLE` agree:

| From | Allowed targets |
|---|---|
| REGISTERED | IN_STOCK, DEPLOYED, DEREGISTERED |
| IN_STOCK | REGISTERED, DEPLOYED, DEREGISTERED |
| DEPLOYED | REGISTERED, IN_STOCK, DEREGISTERED |
| DEREGISTERED | None |

Nine allowed edges; all other pairs including all self-edges reject. Dedicated `POST /assets/{id}/lifecycle-transitions` takes target lifecycle, expectedVersion and deregistration reason when applicable; never takes claimed current lifecycle. Inventory application service loads/authorizes/validates version, calls the existing pure engine, updates timestamps/version and appends history inside one transaction.

DEREGISTERED reason is separately recorded as **TRANSFER, DISPOSAL, LOST or ERROR** (Domain §6); the approved structured reason is required for DEREGISTERED; a deregistration reason on other targets is invalid command input. Store reason in immutable event data, with before/after state/reference snapshots. No column is needed to preserve it. Approved event convention: one **DEREGISTERED** event for transition to that terminal state; other lifecycle transitions use **LIFECYCLE_CHANGED**. The terminal event includes the lifecycle change; do not duplicate it with a second event. Optional event note is genuine text, not a replacement for the required reason.

Deregistration neither soft-deletes nor clears Location/Owner/Catalog/loan references. It does not create a cross-Tenant transfer operation: old registration is deregistered; receiving Tenant registers anew with new identity/tag. Other concepts remain independent; no engine knowledge of loans/auth/persistence. Approved P4 leaves every otherwise-valid graph transition available while a loan is outstanding, including LOST deregistration, with no extra loan guard or automatic clearing.

UI displays only graph-permitted lifecycle actions, backend remains authoritative. Deregistration confirmation shows terminal outcome, Asset tag and required reason; no new approval workflow. IN_STOCK does not promise a storage-room Location. No movement on lifecycle change.

## 8. Condition workflow

**Settled graph:** Domain §6 and `InventoryGraphs.CONDITION` agree:

| From | Allowed targets |
|---|---|
| UNKNOWN | WORKING, DEFECTIVE, UNUSABLE |
| WORKING | DEFECTIVE, UNUSABLE |
| DEFECTIVE | WORKING, UNUSABLE |
| UNUSABLE | None |

Seven allowed edges; all other pairs reject, including self-transitions and returns to UNKNOWN. Dedicated `POST /assets/{id}/condition-transitions` takes target condition, expectedVersion and optional event note; current state/actor are loaded/derived. Apply graph, update Asset/version and append CONDITION_CHANGED before/after snapshot atomically. Confirm irreversible UNUSABLE; no repair ticket, automatic lifecycle change or automatic return. Condition remains independent after deregistration; terminal lifecycle does not make Condition terminal. Approved P4 preserves every permitted edge while a loan exists, including UNUSABLE, without clearing loan fields.

## 9. Explicit Location and Owner changes

Reuse `changeLocation` and `changeOwner` through secured wrappers:

| Operation | Inputs | Required behavior |
|---|---|---|
| `PUT /assets/{id}/location` | locationId, expectedVersion | Required same-Tenant non-deleted target loaded server-side; before/after IDs/display snapshots; version/timestamps/actor; LOCATION_CHANGED, atomic. Same Location selection rejects as unchanged, matching existing service. |
| `PUT /assets/{id}/owner` | ownerId explicitly UUID or null, expectedVersion | Same-Tenant non-deleted Owner if supplied; null removes Owner. OWNER_CHANGED snapshots include null and old/new labels. Same value rejects as unchanged, matching existing service. |

Location editing by stable ID creates its existing administrative audit only, never an Asset movement event or version change. Owner is metadata, not authenticated User or Tenant ownership. DEREGISTERED reference changes remain valid. Existing internal retained/deleted-row capabilities remain intact, but P2 exposes no deleted-Asset mutation workflow. Every retained ID still blocks relevant guards until resolved; no automatic clear on deregistration/deletion/loan/return.

## 10. Loan and return

**Settled issuance rules; approved P3/P4; Domain §§6–7:** no Loan entity. Dedicated `POST /assets/{id}/loan` accepts non-blank borrower text, optional expectedReturnAt UTC instant, optional loanNote and expectedVersion. Set loanedAt from server Clock, never caller time. Revalidate loanable=true, lifecycle DEPLOYED, condition WORKING and loanedTo null; any failed guard returns 409. Normalize blank optional note to null; borrower remains required non-blank. Domain imposes no future-only expected-return rule; add none.

Atomically update fields/timestamps/version and append LOANED with complete loan snapshots. UI explains issuance guards; backend decides. No Location/Lifecycle/Condition change, borrower account, reservation, scheduler or hidden effects.

`POST /assets/{id}/return` takes expectedVersion only. Its business guard is **exactly a current outstanding loan**; no loan returns 409. Authorization, valid input and optimistic version still apply, but do not reapply loanable/DEPLOYED/WORKING issuance guards. Load current loan, snapshot before-state, clear loanedTo/loanedAt/expectedReturnAt/loanNote, advance version and append one RETURNED atomically. Return remains valid after DEREGISTERED or UNUSABLE, and must not reject a current loan merely because loanable is false. No caller old-loan/time/location facts or new return-note feature.

Outstanding loans preserve every approved Lifecycle/Condition edge. Explicit transitions retain loan fields; DEREGISTERED-with-loan and UNUSABLE-with-loan are valid results. Return remains available while that loan exists. Loanability changes themselves reject an outstanding loan (P1); this separate guard is not a graph restriction or Return issuance check.

## 11. Deregistration versus Asset soft deletion

**Approved P2; Domain §§6, 9:** no Phase 3 Asset soft-delete HTTP endpoint, frontend action, deleted-Asset list/filter UI, deleted-Asset mutation workflow, undelete or hard deletion. Preserve existing internal `AssetService.delete`/secured internal capability and persistence semantics unchanged; do not remove it, add a new loan guard to it or expose a controller adapter.

DEREGISTERED is the terminal business Lifecycle action, requiring structured reason and exactly one DEREGISTERED event. It is not soft deletion and remains in normal inventory browsing. It clears none of Category, Manufacturer, Product, Location, Owner or current loan fields. A loan does not prevent deregistration; Return remains possible afterward. Reference changes and independent Condition commands follow existing rules.

Public inventory lookups exclude internally soft-deleted Assets without exposing a deleted-row workflow. Internal reference guards/Merge scans still include retained Assets/Products regardless of deletion/lifecycle; redirected retained Assets still receive version advances/UPDATED history. This UI/API boundary changes neither internal capability nor retained-reference meaning.

## 12. Catalog, Location and Owner management

This scope is settled by the current request and original Task Phase 3 item 4; it is not limited to read-only pickers. Use separate small management tables/forms, with paged lists, active/deleted filter and explicit command actions; new-selection lookups return only non-deleted permitted entries. Required names are non-blank, optional Product manufacturerNumber follows existing normalization. No name/manufacturer-number uniqueness or duplicate suggestions.

| Entity | Expose existing operations / guards | Permissions |
|---|---|---|
| Category / Manufacturer | Create; edit/delete only with no retained Product/Asset references; UNKNOWN immutable/undeletable/not Merge source; separate publication and explicit Merge. | GLOBAL ADMIN global or any Tenant; TENANT ADMIN own Tenant edits/create/delete only; all ACTIVE roles read permitted entries. |
| Product | Create with Category/Manufacturer IDs, name and optional manufacturerNumber; server validates dependencies. Edit permanently frozen after retained or historical Asset use; delete only with no retained Asset refs. Publication separately validated, same ID. No Merge. | Same scoped Catalog writes; publish GLOBAL ADMIN only. |
| Location | Create/edit non-blank label; edit while referenced allowed; delete blocked by every retained Asset reference. No movement event for metadata edit. | GLOBAL ADMIN any Tenant; TENANT ADMIN own; readers scoped. |
| Owner | Create label; edit/delete only when no retained Asset reference. Optional Asset relationship, no Merge. | Same Tenant-write/read matrix. |

Backend guard failures identify a safe actionable reason (referenced/frozen/protected/deleted/dependency unavailable); frontend does not calculate authoritative counts or promise deletion/edit success. Explain reference resolution and explicit Merge alternatives where applicable, never auto-run them. Catalog/place entities lack Asset-style optimistic versions; preserve row locks and fresh guards without adding version columns solely for forms. Ordinary concurrent label edits therefore retain existing serialized last-writer behavior, not an invented version guarantee.

Tenant admin screens reuse Phase 2 APIs: manageable user list with approve/disable; GLOBAL ADMIN role grant/change, GLOBAL user creation/reset, Tenant creation and per-Tenant OIDC configuration. Own GLOBAL password change uses the current-password endpoint. No user deletion/reactivation, self disable/demotion, Tenant rename/delete, local Tenant credentials, automatic ADMIN/group mapping or last-admin invariant. OIDC client secret remains write-only; preserve current configuration expected-version semantics and group UUIDs, no raw provider payload viewer. Stored tokens/secrets must never enter URL/persistent browser storage, public branding or client logs.

## 13. Publication and Category/Manufacturer Merge

Publication is a distinct GLOBAL ADMIN action with confirmation of same-ID promotion. Product can become GLOBAL only with GLOBAL Category/Manufacturer. Never copy, merge, cascade publication or mutate ordinary Product facts as a side effect. UI surfaces missing dependency publication requirements; administrator invokes each explicit action. For Category, Manufacturer and Product, capture the locked resource's pre-publication Tenant UUID for the publication AuditLog before changing ownership. Earlier audit attribution remains unchanged; permitted later operations on the now-GLOBAL resource capture null.

Merge UI selects explicit same-type source and target by IDs, shows name/visibility/Tenant and target preservation/source deletion, and requests an **advisory impact preview**. Approved P8 advisory preview may return retained Product and Asset counts (including deleted/deregistered), relevant affected Tenant summaries and known visibility blockers; it is GLOBAL ADMIN only. Source UNKNOWN forbidden; UNKNOWN target permitted by the existing implementation. Same source/target, deleted source/target and invalid final visibility fail. Different names are valid; no fuzzy matching, duplicate detection or implicit rename/Merge.

Preview is a separate bounded read transaction. Counts are observations, not reserved facts, caller authority, or a promise that execution affects exactly that set. UI states that execution recomputes impact and asks explicit confirmation naming source -> target. Approved P8 creates no reservation, preview token/session or database lock spanning interaction. Results may be stale before confirmation. Execution accepts only source/target IDs and performs fresh authoritative validation, locking and reference discovery.

Commit accepts only source ID + target ID and reruns existing `CatalogMergeService`/Catalog validation under locks: source/target FOR UPDATE in local UUID order, fresh retained Product/Asset discovery, affected row locks, dependency validation, explicit Product and Asset redirects, Asset version advance and UPDATED snapshots, source soft-delete and Catalog AuditLog, one transaction. Deferred Product-equality FK remains the commit backstop. No caller reference counts/snapshots/visibility/Tenant facts, whole-command retry, merge-specific event, Product ordinary-edit bypass endpoint or rewrite of immutable history. All failure paths roll back the entire merge. Persist one Merge AuditLog with target ID = source ID and attribution = source's pre-operation Tenant UUID or known GLOBAL/null. Never fan out audit rows to indirectly affected Tenants; their business effects remain UPDATED AssetEvents.

The accepted audit analysis verifies the following **existing** legal scope combinations for both kinds. These are not new Merge permissions. All require distinct same-kind non-deleted endpoints, non-UNKNOWN source and every resulting dependency/visibility invariant. Only GLOBAL ADMIN executes; UNKNOWN remains a permitted GLOBAL target.

| Source -> target | Existing legality condition | Single audit tenantId |
|---|---|---|
| Tenant A -> Tenant A | All resulting dependencies/references valid | A |
| Tenant A -> GLOBAL | All resulting dependencies/references valid | A |
| GLOBAL -> GLOBAL | All resulting dependencies/references valid | null |
| GLOBAL -> Tenant A | All retained referencing Products/Assets belong to A; no GLOBAL Product/other-Tenant dependent; empty set also passes | null |
| Tenant A -> Tenant B, A != B | No retained Product/Asset references; valid dependents of A cannot use B's target | A |

Source ownership stays on the soft-deleted row; target ownership is unchanged. Reference redirection is not Asset Tenant transfer. Indirectly affected Tenant Admins read their own AssetEvents, and read the single Merge audit only if its captured source Tenant is theirs.

## 14. Authorization and Tenant context

Use existing explicit authorization methods and secured application boundaries, not configurable RBAC. ACTIVE required for every business read and administrative operation. Backend query scoping and freshly loaded target scope/visibility/Tenant are authoritative. UI only improves usability.

| Phase 3 operation | GLOBAL ADMIN | GLOBAL USER | TENANT ADMIN | TENANT USER |
|---|---|---|---|---|
| Asset list/detail/history; permitted Catalog/place reads | All Tenants | All Tenants, read-only | Own Tenant + GLOBAL Catalog | Own Tenant + GLOBAL Catalog |
| Register/text/Catalog/loanability/reference/transition/loan/return | Any Tenant within Domain guards | Denied | Own Tenant within guards | Denied |
| Catalog create/edit/delete; place create/edit/delete | Global Catalog and any Tenant | Denied | Own Tenant only | Denied |
| Publish, Category/Manufacturer Merge/preview | Allowed | Denied | Denied | Denied |
| Tenant create/OIDC; GLOBAL users/credential reset; ADMIN grant | Existing Phase 2 permissions | Denied | Denied | Denied |
| Ordinary Tenant-user approval/disable | Existing Phase 2 permissions | Denied | Own USER targets only; cannot administer ADMIN | Denied |
| Administrative AuditLog list/detail | All global/Tenant records | Denied | Captured own-Tenant records only | Denied |

**Approved P5:** GLOBAL principals select an authorized Tenant from existing `GET /tenants`; selected Tenant is represented in frontend URL (`/tenants/{id}/assets`) and scoped collection API paths. It is a routing choice, never a changed JWT Tenant, membership, server session, body-owned fact or inferred null scope. Existing-object commands use Asset/entry/place ID and backend-derived Tenant; optional frontend route context is checked rather than trusted. TENANT principals are fixed to authenticated tenantId; a different route Tenant is denied, and backend queries must include own Tenant predicates. GLOBAL all-Tenant read view (§5) creates no write context until a Tenant is explicitly chosen.

Creation paths indicate requested scope: Tenant Catalog creation under `/tenants/{id}/catalog/...`; GLOBAL Catalog under `/catalog/...`. No nullable caller Tenant/visibility facts in creation bodies. Backend authorizes the route and constructs explicit visibility/Tenant. Catalog entry reads/mutations by ID load current visibility, with existing FOR UPDATE authorization on writes to cover concurrent publication.

**Approved P7 audit boundary:** TENANT ADMIN uses its authenticated Tenant UUID as mandatory SQL equality for selection/count/detail before pagination. GLOBAL ADMIN reads all with optional narrowing filters. Both USER roles/PENDING are denied before queries. Out-of-scope audit detail equals absent detail; no global-row fallback, Java post-filtering, cross-Tenant filter/existence/count leakage or actor/live-target joins. Preserve accepted Access snapshots; no per-business-request User reload/revocation.

Proposed security/error mapping follows Phase 2 plan §12: missing/invalid Bearer 401; forbidden operation/role/status 403 before target disclosure; missing/out-of-scope object 404 without confirming existence. Existing non-HTTP wrappers sometimes return 403 for cross-Tenant objects; Phase 3 scoped HTTP reads/adapters must implement the documented 404 convention, not forward arbitrary IllegalArgumentException as 400. Cross-Tenant collection-route attempts may be 403 because the requested operating scope is explicitly denied; object lookup in permitted scope is 404. Tests must distinguish these cases.

## 15. Proposed API

All paths below have `/api/v1` prefix. This is a review proposal, not an OpenAPI modification. Authenticated Bearer only for business endpoints; a Refresh cookie alone grants nothing. No GET performs mutations. JSON UUIDs, UTC instants; bounded JSON and page sizes. Unknown/server-owned fields rejected. Proposed success conventions: 201 creation + Location; 200 read/updated Asset; 204 existing administrative mutation commands with no representation. Keep Phase 2's already implemented statuses intact.

Mutation **A** means ACTIVE GLOBAL ADMIN or same-Tenant TENANT ADMIN, using target loaded scope; **C** means GLOBAL ADMIN for GLOBAL Catalog, otherwise A; **G** means ACTIVE GLOBAL ADMIN only. **R** means ACTIVE scoped reader. Every Asset mutation except registration requires expectedVersion and advances version on success; Catalog/place mutations have existing target locks, not invented versions. Common error contract follows the table below.

| Method/path | Required caller input | Server-derived validation / effect | Authorization / version |
|---|---|---|---|
| POST `/tenants/{tenantId}/assets` | Registration union (§4), locationId, explicit loanable; optional owner/text | Load references; derive Product facts; generate identity/tag/states/actor/time; REGISTERED atomically | A; no expectedVersion |
| GET `/tenants/{tenantId}/assets` | Reviewed page/filter/sort query | Scoped current projection, no authoritative facts from client | R |
| GET `/assets` | Reviewed page/filter/sort; optional Tenant | All-Tenant read projection | GLOBAL ACTIVE read roles only |
| GET `/assets/{id}` | Asset ID | Load within authorized scope, complete current detail/version | R |
| GET `/assets/{id}/events` | Asset ID, page/size | Same-scope immutable snapshot history | R |
| PUT `/assets/{id}/metadata` | serialNumber, externalInventoryNumber, note, expectedVersion | Only text fields, normalize; UPDATED | A; version |
| PUT `/assets/{id}/catalog` | Product or direct Category/Manufacturer union, expectedVersion | Load/derive visible active targets; preserve old/new Product evidence; UPDATED | A; version |
| PUT `/assets/{id}/loanability` | loanable, expectedVersion | Outstanding-loan rejection; UPDATED | A; version |
| POST `/assets/{id}/lifecycle-transitions` | target, expectedVersion; reason for DEREGISTERED; optional event note | Loaded graph validation; LIFECYCLE_CHANGED or DEREGISTERED with reason | A; version |
| POST `/assets/{id}/condition-transitions` | target, expectedVersion; optional event note | Loaded graph validation; CONDITION_CHANGED | A; version |
| PUT `/assets/{id}/location` | locationId, expectedVersion | Loaded same-Tenant target, LOCATION_CHANGED | A; version |
| PUT `/assets/{id}/owner` | ownerId UUID or explicit null, expectedVersion | Loaded same-Tenant target or removal, OWNER_CHANGED | A; version |
| POST `/assets/{id}/loan` | borrower, optional expectedReturnAt/loanNote, expectedVersion | Four current-state guards; server loanedAt; LOANED | A; version |
| POST `/assets/{id}/return` | expectedVersion | Existing current loan; clear fields, RETURNED | A; version |
| GET `/tenants/{tenantId}/catalog/categories`, `/manufacturers`, `/products` (each under `catalog/`) | page/size/q, deleted filter; optional visibility GLOBAL/TENANT | Own/global visible entries; query mode narrows, never widens scope | R |
| POST those Tenant Catalog collections | Label name; Product name/categoryId/manufacturerId/optional manufacturerNumber | Tenant loaded; server constructs visibility; dependencies validated; existing *_CREATED AuditLog | C; no version |
| GET/POST `/catalog/categories`, `/catalog/manufacturers`, `/catalog/products` | GET GLOBAL list filters; POST same entity inputs | GLOBAL collection; construct GLOBAL/null Tenant and dependencies; existing creation audit | GET ACTIVE readers; POST G |
| GET/PUT/DELETE `/catalog/categories/{id}`, `/catalog/manufacturers/{id}`, `/catalog/products/{id}` | GET/DELETE ID; PUT permitted name, or full Product editable fields | Load current scope; read retained labels; independent edit/delete guards/UNKNOWN/usage; existing *_EDITED / *_DELETED AuditLog | GET R; writes C; existing row locks, no Asset version |
| POST `/catalog/categories/{id}/publish`, `/catalog/manufacturers/{id}/publish`, `/catalog/products/{id}/publish` | Target ID only | Load scope/dependencies; same-ID promotion; *_PUBLISHED AuditLog | G; existing row locks |
| POST `/catalog/categories/merge-preview`, `/catalog/manufacturers/merge-preview` | sourceId, targetId | Advisory retained impact/validity read, no mutation/event/audit | G; advisory read |
| POST `/catalog/categories/merge`, `/catalog/manufacturers/merge` | sourceId, targetId only | Fresh validated redirects, affected Asset versions/UPDATED, source delete, *_MERGED audit, atomic | G; no caller per-Asset versions for discovered set |
| GET/POST `/tenants/{tenantId}/locations`, `/tenants/{tenantId}/owners` | Paged label search/deleted filter; POST name | Scoped list or create, existing place creation audit | GET R; POST A |
| GET/PUT/DELETE `/locations/{id}`, `/owners/{id}` | ID; PUT name only | Load Tenant; existing entity guards and *_EDITED/*_DELETED audit | GET R; writes A; existing row locks |
| GET `/audit-log` | page/size; fixed action/targetType/targetId filters; GLOBAL ADMIN optional tenantId or globalOnly | Captured attribution in scoped SQL; identical selection/count predicate before pagination | ACTIVE GLOBAL ADMIN all; ACTIVE TENANT ADMIN own authenticated Tenant |
| GET `/audit-log/{id}` | Audit ID | Database lookup scoped by authenticated Tenant for TENANT ADMIN; identical 404 absent/out-of-scope | Same audit read roles |

This table uses compact notation for enumerated concrete Category, Manufacturer, Product, Location and Owner routes. Implement distinct DTOs/controllers/explicit operations, not a caller-provided table name, generic RPC dispatcher or arbitrary entity mutation endpoint. No Product/Owner/Location Merge or Asset deletion routes. Existing `/tenants`, `/users`, `/users/global`, user approval/role/disable/credential, `/tenants/{id}/oidc`, and `/auth/local/password` serve minimal administration screens unchanged; no new business routes solely to prove auth.

Audit pages use page 0/default size 25/max 100 and descending `(occurredAt,id)`. tenantId/globalOnly narrowing filters are GLOBAL ADMIN-only and mutually exclusive; TENANT ADMIN cannot widen authenticated-Tenant SQL scope via parameters. Fixed filters never trigger unauthorized target/existence lookups. DTOs expose captured nullable tenantId, independent actor snapshot, action/target/time and sanitized stored context. No audit write endpoint or caller attribution field.

Proposed business problems add stable non-sensitive `code` and optional field-validation details to RFC 9457, not SQL messages or reference inventories. Relevant failures for **every** mutation:

| Status / code | Use and response boundary |
|---|---|
| 400 / INVALID_INPUT | Invalid UUID/enum/instant, missing/null required boolean/version, negative version/page, conflicting registration union, blank required text, unknown/server-owned fields or unsupported filters. No mutation/event. |
| 401 / AUTHENTICATION_REQUIRED | Missing, expired, malformed or forged Access JWT, Bearer challenge; Refresh is not a business token. No writes. |
| 403 / FORBIDDEN | PENDING/disallowed role or requested scope; evaluate before disclosure. Existing ACTIVE Access retains approved expiry window after database disable. |
| 404 / NOT_FOUND | Target absent or outside permitted object scope; identical sanitized detail. Reference outside allowed scope also concealed. |
| 409 / STALE_ASSET_VERSION | Expected-version mismatch or JPA optimistic failure; rollback event/mutation together; no blind retry. |
| 409 / INVALID_TRANSITION, LOAN_GUARD, NO_CURRENT_LOAN, OUTSTANDING_LOAN, REFERENCED, PRODUCT_EDIT_FROZEN, PROTECTED_UNKNOWN, DELETED_TARGET, INVALID_DEPENDENCY | Operation-specific loaded business guards; correct blocked field/action explanation without leaking another Tenant's rows. Self-state transitions rejected, no events. |
| 409 / CONCURRENT_OPERATION | PostgreSQL lock timeout/deadlock or stale Product-dependency acquisition; rollback entire command, including events/audit. Explicit fresh command only, no deadlock-freedom claim. |
| 409 / CONSTRAINT_CONFLICT | Declarative integrity violation translated safely; no SQL/constraint internals, rollback. |
| 503 / SERVICE_UNAVAILABLE | Unavailable database/infrastructure, sanitized; no success claim or automatic command replay after ambiguous outcome. |

Guard and failure classification belongs in supported application operations/adapters; do not parse exception-message substrings to guess Domain errors. Expand current advice narrowly and preserve Phase 2 identity problem behavior, no-store and throttling/CSRF headers. API contract tests check responses and rollback, not only exception names.

## 16. Frontend information architecture and workflows

Keep runtime branding header/colors/logo and safe defaults. Replace the ACTIVE inventory placeholder with navigation, not an unrelated design system. Reuse shadcn/Tailwind controls and TanStack Table. Proposed routes are a small explicit route map using browser history/links and React state; no new router/query/admin framework is required for the initial screens. If implementation shows a routing dependency is necessary, record its concrete reason before adding it rather than silently expanding architecture.

| Screen | Minimum behavior |
|---|---|
| Authentication / pending | Existing GLOBAL/Tenant login, OIDC completion/failure, pending message, refresh/logout; no business prefetch for PENDING. ACTIVE navigation after successful restoration. |
| Tenant context | GLOBAL selector from accessible Tenant list; fixed Tenant for TENANT principals; optional GLOBAL all-Tenant read view. Clear Tenant label on every scoped form. |
| Assets | Server-driven table, search/fixed filters, paging and links; register action for authorized admins. No dashboard or generalized view builder. |
| Asset registration | Product/direct mode, paged visible choices, required Location/loanable, optional text/Owner; generated tag after success. |
| Asset detail | Current facts, loan state, notes, allowed actions and immutable paged history. Separate metadata/Catalog/loanability/Location/Owner dialogs and lifecycle/condition/loan/return commands; no Asset delete control. Return remains available for a current loan after terminal state changes. |
| Catalog | Categories/Manufacturers/Products; Tenant/global distinction; permitted CRUD, publication and explicit Merge with advisory preview/confirmation. Guard failures surfaced, no client-authoritative reference counts. |
| Locations / Owners | Small scoped CRUD tables/forms preserving different edit rules. Required existing labels remain visible even if not selectable anew. |
| Tenant / account administration | Reuse accepted Phase 2 API functions only: Tenant create/list, OIDC config/group UUIDs, manageable users/approval/disable/role, GLOBAL credentials and own password. Protect write-only inputs and self-protection. |
| Administrative audit | GLOBAL ADMIN all-records and TENANT ADMIN own-Tenant read-only paged list/detail; captured attribution independent of actor plus sanitized context. Only GLOBAL ADMIN has Tenant/global narrowing filters; USER/PENDING have no audit viewer. No edit/export/security-log domain. |

Use one shared existing AuthSession instance/context; add a narrow relative-URL Bearer request helper with coordinated refresh. Refresh proactively before mutation when token expired; retry a GET after a 401 if refresh succeeds. Do not automatically replay a mutation after 409, timeout, ambiguous network failure or lost success response. Preserve drafts and require user review/resubmission. Clearing auth/Tenant context also clears scoped data; prevent an old in-flight read from repopulating a newly selected Tenant or logged-out view. No token in URL/localStorage/sessionStorage and no public-runtime-config API destination override.

Destructive confirmations: terminal deregistration with reason, UNUSABLE, Catalog/place deletion, publication, Merge impact/source/target, account disable/role demotion. No invented multi-stage approval. Forms label null/clear semantics, disable unavailable actions for usability, show backend problems and optimistic conflict recovery. Preserve accessible labels/focus/error status; do not render note/history text as HTML.

## 17. Transaction and concurrency behavior

PostgreSQL READ COMMITTED, existing 10-second lock timeout and Asset optimistic locking remain. ExpectedVersion required on text/Catalog/loanability/reference/state/loan/return; missing version is input error, never inferred from a later load. Force persistence before returning final version and ensure event failure rolls back both update and version. One event per successful ordinary Asset action; no retry of stale intent.

| Concrete race | Smallest required protection | Result and verification |
|---|---|---|
| Two state/loan/return/text/reference commands on one Asset | Load expected version; JPA @Version UPDATE predicate and one transaction for history. No new universal Asset FOR UPDATE protocol. | At most one same-version command commits; loser 409, no lost fields/duplicate event. New workflow concurrency tests use independent real PostgreSQL connections. |
| Loan versus return/state/loanability | Same Asset version predicate; validate current fields from loaded version. Apply approved P1–P4: loanability rejects loan, graph transitions retain it, Return only requires it. No new isolation infrastructure. | Loser rolls back completely; fresh explicit command uses committed current state. No stale loan can overwrite return or vice versa. |
| New reference or registration versus edit/delete/Merge/publication | Reuse existing target FOR SHARE versus guarded FOR UPDATE, fresh subsequent reference/history checks and dependency re-read. | Assignment/deletion cannot both commit an invalid new relation; retained references still counted. Preserve existing proven races, add HTTP adapter coverage and new reassignment path only where needed. |
| Workflow/text mutation versus Merge | Existing Merge affected-Asset FOR UPDATE and version advance; normal workflow @Version plus existing snapshot parent locks. | Stale command conflicts; completed earlier mutation is in fresh Merge before-state. Parent snapshot/Asset lock acquisition can deadlock; translated rollback is correct, not a reason for a global lock framework. |
| Preview versus new reference/Merge | Advisory read transaction only; no lock across user confirmation. Fresh existing execution locks/discovery on commit. | Preview may become stale; it never authorizes/defines affected IDs. Test intervening assignment and source changes, all actual commit guards still hold. |
| Detail/list/history versus metadata changes | Scoped ordinary read transactions, no locking every row for presentation. | Current detail uses loaded facts and stored historical snapshots. Page total/items may change at READ COMMITTED; no snapshot promise. |
| Catalog/place edits versus publication/guarded mutation | Existing per-target locks and fresh scope authorization. No invented Catalog/place optimistic schema. | Preserve Phase 2 publication authorization race tests and Phase 1 guard tests. |

Retain Product dependency/category/manufacturer shared locks and old/new reference snapshot locks in the concrete existing operations; do not duplicate them in controllers. New state/loan history reuses the accepted snapshot boundary rather than copying stale frontend facts. No SERIALIZABLE, KELIOS-wide lock-order abstraction, silent whole-transaction retry, long lock spanning UI or deadlock-freedom promise. Retryability does not imply an automatic retry: UI explicitly reloads and presents a new command.

Audit capture reuses each operation's existing protected authoritative target/context, with no universal lock. Creation uses inserted ownership; publication keeps its locked pre-state before ownership-changing SQL; Merge uses the locked source. User administration uses its locked target User; OIDC its validated/locked configured Tenant; JIT its verified locked provider and created User. Audit insertion remains in the same mutation transaction, and its failure rolls back all effects. No post-mutation attribution lookup, new lock across UI or audit fan-out.

## 18. AssetEvent and AuditLog behavior

| Asset action | Business history |
|---|---|
| Registration | REGISTERED, after snapshot with explicit Product ID/null |
| Ordinary text/Catalog/loanability | UPDATED with before/after; Product IDs retained on every event |
| Non-terminal Lifecycle transition | Exactly one LIFECYCLE_CHANGED |
| Transition into DEREGISTERED | Exactly one DEREGISTERED with lifecycle before/after and required structured reason; never a second LIFECYCLE_CHANGED |
| Condition | CONDITION_CHANGED |
| Location / Owner reference change | LOCATION_CHANGED / OWNER_CHANGED with IDs/display snapshots, including null Owner |
| Loan / return | LOANED / RETURNED, complete loan before/after and server timestamp |
| Category/Manufacturer Merge | UPDATED for every redirected retained Asset, including deleted/deregistered, with version advance |

Extend new JSON before/after snapshots with complete loan values. Keep `data.before.productId` and `data.after.productId` as canonical UUID strings or JSON null for permanent Product usage queries. Reason fits existing JSONB; event note fits the nullable note. Never rewrite old AssetEvents or introduce event types. Actor UUID/display/time derive server-side; append-only triggers remain. No event edit/delete API. Internal Asset soft deletion retains existing UPDATED behavior, without a Phase 3 HTTP/UI adapter.

### Every supported AuditLog writer

Current AuditLog has eight fields and no Tenant column. Extend the immutable mapping/DTO with historical nullable Tenant UUID, **without a live Tenant FK**. Change AuditService's mandatory-transaction write contract to require explicit authoritative attribution from every operation; remove the old attribution-less overload instead of defaulting null. Null is permitted only for established GLOBAL ownership. No AuditScope enum, JSON resolver, legacy marker, generic trigger or permission framework.

The accepted analysis enumerates **28 action strings / 30 writer variants**, all covered here:

| Actions / writer | Authoritative capture inside transaction | Persisted tenantId |
|---|---|---|
| TENANT_CREATED | Persisted/flushed new Tenant; audit failure rolls back creation | new Tenant.id |
| OIDC_CONFIG_CHANGED | Validated/locked configured Tenant and configuration target | configured Tenant.id |
| USER_CREATED / GLOBAL administration | Constructed GLOBAL/USER/ACTIVE target | null |
| USER_CREATED / OIDC JIT | Verified locked provider Tenant and created TENANT/USER/PENDING User | created User.tenantId |
| USER_APPROVED, USER_ROLE_CHANGED, USER_DISABLED | Loaded/locked target User pre-operation explicit scope/Tenant; no supported ownership transfer | target Tenant UUID for TENANT; null for GLOBAL |
| LOCAL_CREDENTIAL_CHANGED / admin reset and own change | Loaded GLOBAL credential target User, independently of actor | null |
| GLOBAL_ADMIN_BOOTSTRAPPED / installation and recovery | Constructed GLOBAL/ADMIN/ACTIVE target | null |
| CATEGORY_CREATED, MANUFACTURER_CREATED, PRODUCT_CREATED | Created Catalog row after validated scope/dependencies | owning Tenant UUID or known GLOBAL/null |
| CATEGORY_EDITED, MANUFACTURER_EDITED, PRODUCT_EDITED | Loaded/locked pre-operation Catalog target | pre-operation Tenant UUID or known GLOBAL/null |
| CATEGORY_DELETED, MANUFACTURER_DELETED, PRODUCT_DELETED | Loaded/locked pre-operation target; deletion retains ownership | pre-operation Tenant UUID or known GLOBAL/null |
| CATEGORY_PUBLISHED, MANUFACTURER_PUBLISHED, PRODUCT_PUBLISHED | Captured pre-publication entry before same-ID promotion SQL | original Tenant UUID |
| CATEGORY_MERGED, MANUFACTURER_MERGED | Locked pre-operation source; audited target ID remains source | source Tenant UUID or known GLOBAL/null; exactly one audit |
| LOCATION_CREATED, OWNER_CREATED | Inserted target in validated/authorized Tenant | owning Tenant UUID |
| LOCATION_EDITED, OWNER_EDITED, LOCATION_DELETED, OWNER_DELETED | Loaded/locked target pre-operation Tenant | owning Tenant UUID |

Attribution is write-once through supported boundaries and immutable mapping; no audit update/delete service or endpoint. Never derive ownership from actor, arbitrary JSON or a current-target join. A writer unable to establish known GLOBAL or specific Tenant ownership is a design error, not a reason to insert ambiguous null. Current place/JIT/User payload omissions are writer gaps to fix at write time, not history to reconstruct.

Creation captures new ownership; existing-target mutation captures pre-operation ownership. Publication history keeps original Tenant forever and later GLOBAL operations use null. Merge has source-attributed single audit, independent of target/affected Tenants; Assets retain separate UPDATED history. Location metadata edit has audit only, no movement/event/version change.

Audit reads use immutable attribution: ACTIVE GLOBAL ADMIN all; ACTIVE TENANT ADMIN `tenant_id = authenticatedTenantId` in selection/count/detail SQL before pagination; USER/PENDING denied. Absent/out-of-scope detail matches; never expose global records to Tenant Admin. Optional GLOBAL ADMIN filters narrow only. Preserve Phase 2 Access expiry.

Keep action names and sanitized administrative context without passwords/hashes/JWTs/codes/secrets/keys/cookies/raw provider payloads. No ordinary Asset-event duplication or permanent login audit. KELIOS is pre-deployment; V5 (§21) leaves only known Tenant or known global/null records with no third state or compatibility domain.

## 19. Testing and verification strategy

All tests below are **planned after approval**, not executed planning evidence. Real PostgreSQL/Testcontainers/JUnit for persistence, transactions, locks and HTTP service integration; no H2 or mock substitution for those properties. Retain current pure-engine tests and add workflow-effect tests rather than duplicating the graph implementation.

| Group | Concrete required verification |
|---|---|
| Registration | Product derivation; reject redundant facts/overposting; direct Category/Manufacturer required; required Location/explicit loanable; same-Tenant Owner/Location, visibility/deleted selection; duplicate serial permitted; UTC/generated tag/expansion; initial REGISTERED/UNKNOWN/no loan; REGISTERED/actor snapshots; rollback leaves no partial Asset/event. |
| Browsing | SQL-scoped paging/list/detail, GLOBAL aggregate serial matches versus own-Tenant visibility; deterministic sort ties, literal search escaping/limits, internally deleted Assets excluded/no deleted-Asset filter UI, DEREGISTERED included; labels and nullable fields; no data leak/count from other Tenant. |
| Lifecycle | All 16 from/to pairs at application/HTTP boundary: nine allowed, seven forbidden including self/terminal; required reason enum, exactly one DEREGISTERED versus non-terminal LIFECYCLE_CHANGED; repeat every allowed edge with outstanding loan including LOST; terminal retains references/loan and remains distinct from soft deletion. |
| Condition | All 16 pairs: seven allowed, nine forbidden including self/terminal; repeat every allowed edge with an outstanding loan, including UNUSABLE; atomic CONDITION_CHANGED retains loan and has no lifecycle/Location coupling; no repair entity. |
| Location / Owner | Valid changes/removal, same-value rejection, cross-Tenant concealed denial, deleted target denial, before/after labels/null and versions; Location rename produces zero movement events and no Asset version change. |
| Metadata / exposure | Text and explicit Product/direct reassignment; separate loanability succeeds without loan/rejects outstanding loan; UPDATED/version/rollback; immutable UUID/Tenant/tag; Product historical freeze after reassignment; optional clearing. No arbitrary setter, Asset delete route/control, deleted-Asset workflow, undelete/hard-delete. Preserve internal deletion/reference tests. |
| Loan / return | Each loan guard independently fails, valid loan/server time/optional fields/complete event, double loan and no-loan return, valid clearing/null consistency, Return absence conflicts; return succeeds regardless of loanable/lifecycle/condition, including DEREGISTERED/UNUSABLE. Issuance guards are not Return guards; no automatic clearing/movement, rollback on event failure. |
| History / Audit | Paginated stored snapshots after rename/delete/Merge, actor interpretation without live User, no edit routes; old event shape renders safely; Product usage paths retained; administrative operations keep atomic sanitized audit, no duplicate business auditing. Target-owned attribution and SQL-scoped audit reads verified by dedicated matrix below. |
| Catalog / places | Reuse full edit/delete/publication visibility/retained-reference/UNKNOWN/history-freeze suites; new REST/UI wrappers preserve errors/locks; no Product/Owner/Location Merge; names remain non-unique; no auto duplicate/Merge. |
| Merge | Both types, advisory preview includes soft-deleted/deregistered rows; intervention between preview and commit; redirects/versions/UPDATED snapshots/source delete/audit atomic; all five legal scope cases in §13 and invalid dependents, source-attributed single audit/no fan-out, invalid visibility/failure rollback, UNKNOWN restrictions. |
| Optimistic / concurrency | Same expectedVersion simultaneous edits/loans, stale commands, loan/return race, new workflow versus Merge, event-insert rollback; actual independent connections and barriers/observed lock waits; timeout/deadlock 409 complete rollback, no silent retry. Reuse old reference/first-use/assignment/Merge races rather than duplicate every test. |
| Authorization | Four ACTIVE scope/role combinations for every route family, PENDING denial/no prefetch, explicit DISABLED token rejection and current refresh denial while prior ACTIVE access expires naturally; fixed Tenant, GLOBAL/TENANT read-only, Catalog visibility, publication/Merge only GLOBAL ADMIN, escalation/forged actor/Tenant/state and cross-Tenant attempts. |
| API contract | All proposed routes/auth/status/version/input contracts; UUID/instant/enum/page/body bounds; RFC 9457 and 400/401/403/404/409/503 mapping, no SQL/secrets; unknown paths 404; registration union; unsupported Merge 404; no cookie-only business authentication. |
| Frontend units/components | Registration mode/body shape, required/nullable inputs, role-sensitive controls, table query mapping, history formatting/old payloads, single session/refresh, stale/guard UX and draft preservation; stale Tenant read cannot restore old data. |
| Real HTTP/browser | Registration -> list/detail/history -> transitions -> movement/Owner -> valid loan/return; Catalog/place create/edit/guards/publication/Merge preview/confirm; Tenant/user/OIDC admin screens under accepted limits; PENDING/read-only/cross-Tenant UI plus malicious direct requests. Tenant/global audit list/detail viewers, role denial and no Asset delete controls; real backend/PG for core workflows, label any narrow UI fixtures explicitly; no fixtures offered as database/security evidence. |
| Branding/authentication regression | Both logos/palettes same image, all fallback/error cases, safe text; local HTTPS login/reload/refresh/logout/cookies/CSRF/Origin/no browser token storage and OIDC fixture tests remain passing. |

### AuditLog acceptance coverage

Use real PostgreSQL/Testcontainers for migration, query isolation, atomicity and relevant concurrency; deterministic application/HTTP tests also prove input/role behavior. These remain planned tests, not executed evidence.

| Group | Required assertions |
|---|---|
| Tenant / OIDC | TENANT_CREATED attributed to new Tenant; OIDC_CONFIG_CHANGED to configured Tenant even for GLOBAL actor; audit/mutation rollback atomic. |
| Users / credentials / recovery | Tenant JIT/approval/role/disable follow target User Tenant; GLOBAL user/credential/bootstrap/recovery operations known global/null. GLOBAL administrator actor on Tenant target does not change ownership; secrets excluded. |
| Catalog / places / completeness | Every Category/Manufacturer/Product create/edit/delete captures owning Tenant or known global/null; Location/Owner create/edit/delete captures owning Tenant. Exercise all 28 actions and both USER_CREATED/LOCAL_CREDENTIAL_CHANGED paths; no writer can omit attribution or silently default unknown to null. |
| Publication / immutability | Each Catalog kind uses pre-publication Tenant; earlier audits retain Tenant forever, permitted subsequent GLOBAL operations use null; later target rename/ownership/status changes never rewrite attribution. |
| Merge | Both kinds and all five legal scope combinations; pre-operation source attribution, unchanged target ownership, exactly one administrative audit/no fan-out, distinct affected UPDATED AssetEvents and full rollback. |
| Read roles / status | ACTIVE GLOBAL ADMIN all Tenant/global records; ACTIVE TENANT ADMIN only own; GLOBAL USER/TENANT USER/PENDING denied before queries. Preserve Phase 2 explicit DISABLED-token rejection and naturally expiring previously ACTIVE Access. |
| Database query security | Same authenticated-Tenant predicate for rows/count before pagination and detail lookup; no other-Tenant/global rows/counts for Tenant Admin. Cross-Tenant detail indistinguishable from nonexistent. Forged Tenant/filter/target IDs cannot widen scope/probe existence; no Java post-fetch filtering. |
| Trust / historical retention | Caller Tenant/scope/actor cannot control attribution; unrelated actor ownership irrelevant. Captured UUID unchanged after later target changes; no live Tenant FK, AuditScope or third state; new writers record known ownership. |
| Migration | Fresh V1-to-V5 final schema/JPA mapping; populated V4 structural upgrade preserves V1–V4 checksums and other business/history/identity data, clears only disposable pre-cutover AuditLog, then verifies correctly attributed new writes. No production backfill or legacy reconstruction tests. |
| Frontend | Both authorized audit viewers and paging/count/detail/filter UX; Tenant viewer has no global switch; USER/PENDING make no audit requests; attribution displayed independently of actor/current target. |

Test failure injection is test-only, cleaned up and not a production trigger. Ensure rollback assertions verify rows, version, events and AuditLog rather than status alone. Existing architecture tests currently assert no inventory controllers; after approval narrow those assertions to the Phase 3 route allowlist without weakening the pure-engine or unsupported Merge checks.

## 20. Phase 0–2 regression and evidence strategy

Run full Maven verification, all existing/new frontend tests/build and HTTPS Chromium suites; Testcontainers must run rather than silently skip Docker absence. Historical Phase 2 baseline is **186 backend tests (68 unit + 118 PostgreSQL)**, **19 frontend unit tests**, and **17 browser cases** from accepted evidence. These counts are historical, not results from this planning step or an assumed final total.

Preserve V1–V4 checksums, business schema/data/constraints/UNKNOWN/AssetEvent history, identity roles/status, Product freeze, retained references, tag allocation and targeted concurrency. Fresh V1-to-V5 and populated V4 structural upgrade/startup validate the audit addition. Only disposable pre-deployment AuditLog rows are cleared (§21); other data and accepted evidence artifacts remain intact. No production historical backfill/reconstruction. Keep documented IServ HTTPS fixture positive/negative/protocol cases; pending real-provider manual gate stays separately tracked and need not be fabricated or rerun for Phase 3.

Independent Docker builders still run verification; backend isolated builder runs explicit `-DskipITs verify` and is **not** PostgreSQL evidence. Runtime images remain toolchain-free/non-root. Verify status/proxy/public-vs-management isolation, same-origin TLS/auth/cookies and healthy existing Compose; two branding mounts reuse identical frontend image. Existing missing-business-route assertions require a narrow Phase 3 adjustment after approval; arbitrary missing APIs still 404 and SPA fallback must not swallow them.

**Planned runnable commands, not executed now:**

```sh
# From backend/, with all caches/output inside KELIOS:
MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw -B verify
# From frontend/, using Node 24 on PATH:
npm test
npm run build
# From repository root, new reviewed script to be created only after approval:
./scripts/smoke-phase3.sh
# That script will run both runtime-brand variants and HTTPS browser workflows:
# (cd frontend && KELIOS_EXPECTED_BRAND=blue npm run test:smoke)
# (cd frontend && KELIOS_EXPECTED_BRAND=green npm run test:smoke)
```

Do not execute existing Phase 1/2 smoke scripts unmodified because they write accepted evidence. The new Phase 3 script reuses/adapts their checks with Phase 3 destinations, existing accepted HTTPS configuration and private repository-local test secrets, no production shortcuts. Use narrow approval only when actual Docker/browser/network access needs it; no sudo or sandbox disabling/outside-repository writes. Regression checking is Phase 3 acceptance, not starting Phase 4 stabilization scope.

After implementation create `docs/verification/PHASE_3.md` and supporting `docs/verification/phase3/` artifacts: exact executed commands, versions, unit/PostgreSQL counts/failures/skips, frontend/build/browser outcomes, actual Docker/image IDs/deployment/branding results, V5 migration/structural-upgrade outcomes, controlled concurrency evidence, failures/fixes, Phase 0–2 preservation hashes, and limits/unverified environments. No implementation evidence is created now. Preserve every accepted Phase 0–2 evidence file and manual IServ procedure.

## 21. Migrations and schema changes

**Approved P7 requires an additive migration**, superseding the earlier no-migration assumption. Planned file after implementation approval: `V5__audit_tenant_attribution.sql`. Never edit V1–V4.

Use the simplest clean pre-deployment cutover:

1. Clear **only disposable pre-Phase-3 development/test AuditLog rows** in the one-time Flyway transaction. Do not retain ownership-ambiguous rows as global/null, reconstruct from current actor/resource state or add permanent compatibility state. This is a pre-deployment migration step, not runtime audit purge/repair or a future retention policy.
2. Add nullable `audit_log.tenant_id uuid`, **without a live Tenant FK**, enum, attribution status, legacy marker/table or guessing default. Null means known installation/global ownership; UUID means captured known Tenant ownership.
3. Add `(tenant_id, occurred_at DESC, id DESC)` scoped read index for the required own-Tenant audit query/pagination. Other reads start with existing indexes; extra indexes require measured need.
4. Extend immutable AuditLog mapping and mandatory write contract; fix all supported writers using authoritative capture under existing operation locks. Supported services never update attribution. No new generic trigger or concurrency framework.

KELIOS has not been deployed: **no production historical backfill, preservation or reconstruction is required**. Adding the nullable column alone while retaining ambiguous development rows would falsely label them global, so clear those disposable audit rows. Do not clear AssetEvents, Assets, Catalog, Location/Owner, Tenant/User, credentials/OIDC configuration/identities or tag counters. Accepted evidence files remain unchanged.

Fresh V1 -> V5 migration produces final schema; clearing an empty audit table is a no-op. A populated V4 upgrade test verifies structural correctness, preserved unrelated data/constraints, V1–V4 checksums, no Tenant FK and known-attribution subsequent writes. Test the disposable-data cutover, not historical reconstruction. Extend current migration-count/checksum/JPA validation checks narrowly after approval.

No other business schema change is needed: V2 supplies embedded loan/states/version/timestamps/event JSONB/note, V3 narrow history/UNKNOWN protection, V4 identity. Reasons and complete new loan snapshots fit existing events. No Asset name, Loan table, Product usage latch, cache, audit duplication or extra trigger. No migration is created/executed during this revision.

## 22. Resolved decisions, consistency review and implementation gate

### Final approved decisions

| ID | Approved result | Trace |
|---|---|---|
| P1 | Existing text editing/Catalog reassignment; separate loanability command only without outstanding loan, optimistic UPDATED | Domain §4; plan §§6, 15, 18–19 |
| P2 | No Asset deletion API/UI or deleted-row workflow; preserve internal capability; no undelete/hard-delete | Domain §9; plan §§1, 5, 11, 15–16 |
| P3 | Return requires exactly outstanding loan; absence conflicts; no issuance guards | Domain §7; plan §10 |
| P4 | Loan preserves all Lifecycle/Condition edges; no automatic return/clearing/movement | Domain §§6–7; plan §§7–10 |
| P5 | Explicit `/tenants/{tenantId}/...` operating context; TENANT identity fixed; GLOBAL-only all-Tenant read | Plan §§5, 14–16; no membership/JWT/session redesign |
| P6 | Offset page 0/default 25/max 100; fixed filters/sorts/UUID ties; literal case-insensitive identifier substring search | Plan §5; no snapshot promise |
| P7 | Immutable target-owned Tenant UUID/global-null; own-Tenant Admin/all GLOBAL Admin audit; no AuditScope, Tenant FK, third state or production backfill; additive V5 | Domain §8; Architecture §10; plan §§13–15, 18–21 |
| P8 | Advisory preview with no token/session/reservation/interaction lock; fresh Merge execution | Plan §§13, 17, 19 |
| DEREGISTERED event | Exactly one DEREGISTERED with before/after/reason; other transitions LIFECYCLE_CHANGED | Domain §8; plan §§7, 18–19 |

All previously identified product questions are resolved: exact graphs, reasons, editable fields, Return, deletion exposure, context, list behavior and management UI coverage. Minimal management includes all five Catalog/place entities and the already accepted Phase 2 administration screens. No genuine unresolved business decision or technical choice blocks this design. Concrete DTO/endpoint/error names, narrow UI routes, test organization and V5 details are conservative implementation work under this finalized plan, covered by subsequent **explicit implementation approval**, not new product gates.

### Superseded proposals and audit-analysis context

The Asset soft-delete endpoint/UI/deleted-row proposal is removed. GLOBAL-ADMIN-only audit is rejected and replaced by mandatory Tenant administrative audit. No-migration assumption is replaced by V5. The accepted analysis identifies insufficient historical payloads accurately, but its conditional legacy recommendations assumed history to preserve. The user confirmed KELIOS has never been deployed: omissions are writer gaps, development data is disposable, and no legacy marker, repair/backfill service or third state is approved. P7 also approves no live Tenant FK. Preserve the analysis as technical evidence, not an unresolved active policy.

### Final consistency checklist

| Required review checks | Reconciled result |
|---|---|
| 1 | P1–P8 all resolved; implementation subsequently explicitly approved. |
| 2–3 | No Phase 3 Asset soft-delete API/UI; DEREGISTERED distinct/terminal with retained references and loan. |
| 4–5 | Return needs only outstanding loan; loan changes neither approved graph nor valid transitions. |
| 6 | GLOBAL Tenant URL context is authorized operating intent, not membership/JWT mutation/session. |
| 7–9 | Target-owned immutable attribution; no AuditScope or unattributed/legacy third state. |
| 10–12 | TENANT ADMIN own audit; GLOBAL ADMIN all audit; database predicates before selection/count/pagination/detail. |
| 13–15 | Publication pre-publication Tenant; Merge pre-operation source; single audit/no fan-out. |
| 16–18 | AuditLog distinct from AssetEvent; no live Tenant FK; required additive V5 without production backfill. |
| 19–20 | No Phase 4 scope or silent Phase 0–2 changes; auth/JWT/OIDC, graphs, identity, targeted locks/constraints and narrow triggers preserved. |

Domain/Architecture now record approved business/technical changes. Original Task Phase 3 is broad and compatible and stays unchanged. ADR 0001–0003 and accepted plans/evidence remain historical. The eventual Phase 3 ADR is scoped in §23 and created only after implementation approval, without rewriting old ADRs. Current missing workflow/read APIs and audit Tenant/writer contract are expected phased gaps, not failures of accepted implementation.

HTTP scope concealment still needs the adapters in §§14–15 rather than forwarding current internal generic exceptions. New loan projections/snapshots preserve old AssetEvents and Product usage evidence. No Product Merge, usage flag, universal SERIALIZABLE, generic trigger, last-admin rule or continuous IServ sync is restored.

**Readiness:** finalized plan explicitly approved for Phase 3 implementation. No business/technical decision remains unresolved. Real IServ manual verification is separately pending evidence, not a Phase 3 blocker. This revision performs no production implementation/build/test/migration/OpenAPI/deployment change.

## 23. Implementation sequence after approval

1. Explicit Phase 3 implementation approval obtained. Create ADR 0004 for Asset command/API boundaries, Tenant URLs, bounded pagination/search, persisted audit attribution/no AuditScope/no live Tenant FK, pre-publication/Merge-source ownership, own-Tenant/all-admin audit reads and no Asset soft-delete exposure. Preserve ADR 0001–0003/evidence.
2. Add V5, immutable audit Tenant mapping and every writer change under existing locks. Test fresh/structural-upgrade schema and known-attribution writes with PostgreSQL; no historical repair/compatibility layer.
3. Extend explicit secured read/application boundaries and projections: scoped list/detail/history/audit queries, complete loan snapshots and typed safe errors. Preserve dependency direction and identity/security behavior.
4. Implement lifecycle/condition/loan/return/separate loanability with accepted graphs/version/history/guards. Keep internal Asset soft deletion unchanged and unexposed; verify transaction/rollback behavior with PostgreSQL.
5. Expose inventory/Catalog/place/publication/Merge preview/commit and audit REST; extend narrow security matchers/OpenAPI only now. Test every role/status/Tenant boundary and hostile inputs.
6. Connect shared auth to shell/Tenant context, registration/list/detail/history/workflow forms and conflict UX. Add minimal Catalog/place/Tenant/user/OIDC administration and both authorized audit viewers; keep secrets write-only/branding intact.
7. Run new workflow/HTTP/browser/concurrency verification and full Phase 0–2 regression. Any further query index needs measured justification and additive migration; no extra global locks/weakened constraints.
8. Record actual evidence/limits in Phase 3 verification, API/operations docs and ADR 0004. Stop after Phase 3; no Phase 4, commits, pushes or fabricated real-IServ claims.

## 24. Acceptance criteria

- P1–P8 and event convention are traceable; no missing policy is invented.
- Authorized Asset registration/text/Catalog/loanability/state/reference/loan commands preserve server-authoritative facts, generated identity, optimistic conflicts and one atomic business event. Loanability rejects outstanding loan; Return requires only a loan; loans preserve graphs. Deregistration requires reason and one DEREGISTERED event, retaining references/loan.
- Readers stay within authorized scope; PENDING has no business/audit data and approved JWT lifetime behavior is unchanged. Backend security is proven independently of hidden controls.
- Catalog/place/accepted identity/Tenant administration is usable with publication, explicit advisory-preview Category/Manufacturer Merge, UNKNOWN protection, Product historical freeze and retained-reference guards. Unsupported merges/deferred domains are absent.
- No user-facing Asset delete/deleted-row/restore/hard-delete workflow; internal soft deletion preserved. Deregistration/deletion and Location metadata/movement remain distinct.
- Every supported AuditLog writer captures known created/pre-operation target Tenant or known global/null, separately from actor. Publication uses pre-publication Tenant; Merge uses pre-operation source with exactly one audit. No AuditScope, third state, live Tenant FK, production backfill or duplicate Asset business audit.
- GLOBAL ADMIN reads all audit; TENANT ADMIN own captured Tenant with database-scoped selection/count/detail before pagination; USER/PENDING denied and cross-Tenant existence/count leakage prevented.
- V5 fresh/structural upgrade passes with PostgreSQL; V1–V4/unrelated business/AssetEvent/identity data preserved. Disposable development audit cutover is not runtime purge/retention/repair functionality.
- Accepted constraints/locks/versions/Product history/narrow triggers preserved; no universal retry/lock framework or speculative infrastructure.
- Actual Maven/PostgreSQL/frontend/build/HTTPS browser/independent-image/runtime-branding regression evidence records commands/results/failures/limits without overwriting Phase 0–2 artifacts. IServ fixtures remain passing; real interoperability separately pending manual evidence.
- Stop after approved Phase 3 implementation/verification/documentation. This planning revision changes no production code, migration, OpenAPI, test, deployment or script, and performs no implementation build/test/commit/push.
