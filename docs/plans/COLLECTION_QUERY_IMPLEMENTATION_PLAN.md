# Consolidated Domain and collection implementation gates

**Status: Q0, M1, Steps 2–9, I1 GLOBAL Email identity, Step 7 Tenant authorization correction and Step 8 IServ/OIDC authorization/physical DELETE correction are HUMAN ACCEPTED / CLOSED. The backend cross-module consistency audit and Catalog/Product/Merge lock-order correction are HUMAN ACCEPTED / CLOSED. Latest full backend verification (accepted U1 Tenant-read correction): 74 unit + 701 PostgreSQL integration = 775 passed, zero failures/errors/skips.** Public OIDC routing lookup search/default-batch alignment remains separately pending. Frontend integration follows its separately authorized execution plan; later gates have not started; documentation does not authorize them. Phase A/B remain accepted/frozen history.


Current authority: [ARCHITECTURE](../../ARCHITECTURE.md) §§4–5, 12–15 and [DOMAIN](../../DOMAIN.md) §§1–2, 8–9, 12–14. Historical ADRs, phase plans and verification describe the executable baseline; they are not alternate target requirements. The [backend audit](../analysis/BACKEND_ARCHITECTURE_AND_DOMAIN_AUDIT.md) remains evidence of current implementation and supported-write boundaries, not a mandate to retain its older query/account recommendations.

## 1. Current approved contract

Controller parses/validates/defaults/maps typed resource criteria; application service establishes CurrentIdentity, authorization and effective Tenant scope; persistence realizes combined predicates, full-result server order and SQL pagination/count. No query DSL, arbitrary field map, universal repository, frontend page sort/filter or fake paging.

For TENANT, service scope comes exclusively from signed identity/context. A shared DTO tenantId is not a TENANT criterion or proof to compare. For GLOBAL it is optional validated narrowing where aggregate reads are supported. Tenant-owned writes resolve one concrete authorized Tenant and independently validate persisted ownership/references.

Business Domains carry createdBy/createdAt/updatedBy/updatedAt. Ordinary business default order is createdAt DESC; Audit/history use occurredAt DESC. Equal primary values need no mandatory secondary UUID order. No new selectable timestamp fields arise merely from default ordering. Search lookup may order by readable names.

Normal pageable defaults are 0/20/max 100, with frontend 10/20/30/40/50/100. Requested beyond-end pages return empty items/current metadata. Query state is not persisted across departure. Applying changed criteria/order/size resets page 0. Search trims and uses literal case-insensitive substring with escaped wildcards and consistent practical 128-code-unit query validation. Soft-deleted rows never appear in normal collections or lookup; remove ordinary deleted selectors, preserve internal repair/history/reference semantics.

Lists keep pending versus applied criteria; entity lookup remains immediate, bounded incremental loading in batches of 20 with continuation append and no page-size selector. UUID is identity, labels are presentation. No backend name-to-ID guess. Preserve latest-response/context guards, stable nodes/focus, write-session exit/reset and accepted Merge semantics.

## 2. Historical consolidation baseline and resource progress

**Historical pre-refactor inspection, not current completion status or new runtime evidence.** Steps 2–9 and I1 below record the separately authorized backend changes; the resource progress matrix distinguishes completed portions from remaining gates. Backend paths are relative to `backend/src/main/java/com/github/ksewen/kelios`. The prior audit's 74 unit/241 PostgreSQL results remain historical; no tests were rerun in this consolidation.

| Boundary | Current inspected implementation | Required target change |
|---|---|---|
| HTTP/typed criteria | security controllers pass raw query maps; platform/ListInput:5–14 parses/defaults downstream | Controller validation/defaults and resource-owned typed contracts; preserve service auth |
| Paging | ListInput default 25/max 100; Page items/page/size/totalElements | Default 20 coordinated with consumers; current response metadata remains suitable |
| Persistence metadata | Asset contains all four fields; other business entities/V1–V6 do not uniformly provide them | Explicit per-resource metadata mapping/schema/writer audit before createdAt default rollout |
| Catalog/Places | CatalogService.list:160–169, PlaceService.list:63–69 filter/count/page full scoped collection, fixed Name/UUID; accept deleted selectors | Add approved sorts/default createdAt; exclude deleted always for browser; Product criteria extensions |
| Asset | InventoryReads.list:25–42 combines scope, q identifiers, enums/references/loaned and four sorts; extra UUID order | Preserve foundation; add loanable and dedicated identifier predicates; typed/default 20. Existing extra tie is not mandatory target |
| Users | IdentityAdminController:21 -> AuthenticationService.list:60–63 -> IdentityStore.list:25–28; unbounded array, forced admin scope | Real Page/count, displayName/enums/scope criteria and approved sorts/default createdAt |
| Audit/history | AuditReads:12–23 fixed time/UUID and current exact criteria; InventoryReads.events:51–59 bounded owning-Asset history | Audit actor/range/order; both independently optional inclusive UTC bounds; history stays simple/default 20 |
| TENANT scope | InventoryOperations validates caller route/context against signed Tenant; adapters vary | Reconcile shared transport handling to service-derived scope; never use supplied Tenant as authority/proof. Persisted target/reference checks remain |
| Identity | I1 replaces local_global_credential identifier with Email, explicit verification timestamp and12–128 policy; exact issuer/subject mapping remains | Replace GLOBAL credential identifier with Email and validity conditions;12–128 policy, no dual mode; preserve TENANT OIDC |
| Model boundaries | Mixed JPA entity models, SQL map projections and HTTP maps | Inventory endpoint-specific DTO/application/entity boundaries and explicit mapping; staged migration, no copy-all reflection |
| Frontend | inventory.ts:11–14/Pager assume25; Management/Audit/history omit size; Pick requests25 with Previous/Next | Response-aware20 pager/selectors; combined applied state; incremental lookup append20; controlled reset/identity unchanged |
| API/tests | OpenAPI defaults25/Users array; inventory and collection tests assert25/ties | Coordinated contract/test changes at approved gates; no API/test edit in Q0 |

### Resource gap matrix

Accepted backend portions are CLOSED; only the explicitly remaining integration/lookup/future gates are OPEN. Frontend integration is separately pending. Required schema impact is distinguished from speculative performance indexing.

| Resource | Target query/default | Current gap | Backend/API work | Frontend/test work | Schema impact |
|---|---|---|---|---|---|
| Categories | Name q/visibility; Name/Visibility sorts; createdAt DESC | Backend completed in Step 2; HUMAN ACCEPTED / CLOSED | typed criteria/default 20, scoped count/server sorts, deleted excluded, safe list/detail/command DTOs; M1 metadata preserved | frontend headers/filters/selector and incremental lookup remain separately gated; PostgreSQL backend tests passed | M1 V7 accepted; no Step 2 migration/index |
| Manufacturers | same; no manufacturerNumber field | Backend completed in Step 2; HUMAN ACCEPTED / CLOSED | same closed label criteria/DTO boundaries; existing publication/Merge guards preserved | frontend remains separately gated; PostgreSQL backend tests passed | M1 V7 accepted; no Step 2 migration/index |
| Products | q Name+number; visibility/ref IDs/number substring; three sorts | Backend completed in Step 3; HUMAN ACCEPTED / CLOSED | typed criteria/DTO/model/SQL projection; 0/20/100, createdAt DESC; number NULLS LAST for either selectable direction; existing lifecycle guards/writers | backend PostgreSQL tests passed; readable-reference frontend integration remains separately gated | M1 V7 preserved; no Step 3 schema/index change |
| Locations | Name q/sort; createdAt DESC; concrete Tenant | Backend completed in Step 4; HUMAN ACCEPTED / CLOSED | typed criteria/DTO/model/scoped SQL detail/query; 0/20/100; M1 note/metadata preserved; referenced edits allowed, deletion guarded | backend PostgreSQL tests passed; detail/query/note frontend integration separately gated | M1 V7 preserved; no Step 4 schema/index change |
| Owners | Name q/sort; createdAt DESC; concrete Tenant | Backend completed in Step 4; HUMAN ACCEPTED / CLOSED | same scoped boundaries; every referenced edit including note-only rejected; deletion guarded; no note query or Merge | backend PostgreSQL tests passed; frontend integration separately gated | M1 V7 preserved; no Step 4 schema/index change |
| Assets | existing four sorts; expanded approved predicates | Backend completed in Step 5; HUMAN ACCEPTED / CLOSED | typed criteria/read/HTTP projections; loanable and dedicated literal identifier filters; default 20; service-owned Tenant scope | backend PostgreSQL evidence; frontend integration separately gated; no manual Asset dataset claim | no Step 5 schema/index change |
| Users | bounded Page; displayName/scope/role/status/Tenant; four sorts | HUMAN ACCEPTED / CLOSED Step 6 | typed query/model/DTO/scoped SQL list/detail; 0/20/100; metadata; existing commands/credentials preserved | frontend integration separately gated; no Tenant.name sort | no Step 6 migration/index; I1 Email separate |
| Audit | exact current+actor; inclusive optional dates; time direction | HUMAN ACCEPTED / CLOSED Step 9 | typed combined criteria/explicit projection; captured scope; 0/20/100; occurredAt ASC/DESC | frontend UTC/local inputs/header/pager separately gated; no username work | no schema/index; immutable writer/history preserved |
| AssetEvent | bounded owning-Asset timeline | Backend completed in Step 5; HUMAN ACCEPTED / CLOSED | typed owning-Asset history/default 20; fixed time order, no new filters | frontend integration separately gated; immutable snapshots retained | no Step 5 schema/index change |
| Tenants | unpaged createdAt DESC | HUMAN ACCEPTED / CLOSED Step 7 correction | explicit application authority/model/SQL projection/DTO; scoped UUID detail and strict GA create; no paging/q/edit/delete | frontend detail integration separately gated | M1 metadata preserved; no Step 7 migration/index |
| IServ config | single Tenant resource | HUMAN ACCEPTED / CLOSED Step 8 correction | strict configure DTO; safe application/SQL/HTTP detail projection with Tenant ID/name and metadata; GA or signed-own-Tenant TA; versioned physical DELETE completed; existing PUT writer mechanics retained | frontend integration separately gated; no paging | no migration/index; existing transaction/audit preserved |
| Public routing lookup | Name lookup, batch size 20/readable order |25 and page replacement | typed bounded20; enabled minimal projection | incremental append; no membership inference | no query columns assumed |

API effects: paging defaults25->20, Catalog/Place sorts, Product/Asset criteria, removal of ordinary deleted query inputs, Users array->Page, Audit actor/inclusive time parameters, safe association DTO projections, Email credential/bootstrap/reset transport. No legacy API shim is required. Historical source constraints and executable behavior are not relabeled as already compliant.

## 3. Separately approved implementation gates

### Q0 — this documentation consolidation

Files: normative architecture/Domain, this plan, repair-plan active backlog/actor target and necessary documentation hierarchy notes. No implementation, test rerun, deployment or data change. Acceptance: one coherent target with resolved Product search, inclusive optional Audit range, batch size 20 dropdowns, no mandated UUID ties and no username-first audit requirement. Human review ends Q0; it does not authorize the next gate.

### Backend-only execution order — current refactoring authorization

The accepted backend dependency order remains unchanged. M1 and Steps 2–9, I1 and the Step 7/8 corrections are HUMAN ACCEPTED / CLOSED. Frontend remains a separately approved integration gate, never a reason to preserve unauthorized Tenant list access.


1. M1 shared business metadata, authoritative attribution, Location/Owner note and narrow schema/writer foundation.
2. Category + Manufacturer resource/query/detail/commands.
3. Product, depending on Category/Manufacturer.
4. Location + Owner resource completion under M1 note and reference guards.
5. Asset, depending on Catalog/Places; approved commands/query/detail only, no user-facing deletion.
6. User/identity administration: bounded collection/detail and approved commands; Step 6 and I1 Email are HUMAN ACCEPTED / CLOSED; Steps 7/8 corrections and Step 9 are also closed.
7. Tenant: ACTIVE GLOBAL ADMIN/USER list/detail; GA-only create; TA own detail only, no list; TU denied; no invented Tenant edit/delete.
8. Tenant IServ singleton configuration; required physical singleton DELETE; no reset/history model.
9. Audit queries/reads after the preceding administrative writer changes are stable.

No technical dependency found requires reordering. Q/R/M/I identifiers retain their existing scope; this order does not grant permission for the next resource or collapse all work into a universal framework. Existing frontend integration recommendations elsewhere describe later work and do not authorize frontend changes or compatibility work in this backend-only sequence.

**M1 attribution decision — human approved, 2026-10-11:** the reserved actor UUID `00000000-0000-0000-0000-000000000003`, displayed as `KELIOS system/seed`, is attribution only. It is never an app_user, credential, membership, login principal, Tenant or authorization grant. The database rejects ordinary User IDs colliding with it. Safe metadata projections recognize its stable label without a User lookup. Seed creation uses the actual INSERT timestamp, with identical initial creation/update attribution and time. Administrator bootstrap and OIDC JIT continue using their real created-user actor; administrator-created users use the acting administrator.

**M1 target-schema operational precondition:** additive V7 requires a clean non-seed business state. It does not backfill historical metadata or use transitional nulls/default attribution. It recreates only the two protected infrastructure UNKNOWN rows in a clean state with actual creation timestamps. A populated pre-M1 development database cannot upgrade; its reset/cutover requires separate human authorization. No manual-environment reset is authorized or performed. Existing migration source history remains unchanged.

M1 implementation and isolated PostgreSQL verification are recorded in [M1 business metadata verification](../verification/M1_BUSINESS_METADATA.md). M1 is HUMAN ACCEPTED / CLOSED. Step 2 Category/Manufacturer Q1/M2/R1b backend work is human accepted/closed. Step 3 Product Q1/M2/R1c backend work is human accepted/closed. Step 4 Location/Owner Q1/M2/R1d backend work is HUMAN ACCEPTED / CLOSED; Step 5 Asset Q1/M2/R1a is HUMAN ACCEPTED / CLOSED; Step 6 User Q2/M2/R1e is HUMAN ACCEPTED / CLOSED; I1 Email is HUMAN ACCEPTED / CLOSED; Steps 7/8 corrections and Step 9 are HUMAN ACCEPTED / CLOSED. Frontend integration, Email delivery and other separately gated work have not started.

### Step 2 — Category + Manufacturer backend vertical refactor

HUMAN ACCEPTED / CLOSED. Implemented relevant Q1/M2/R1b portions for these two flat Catalog resources only. [Step 2 verification](../verification/STEP_2_CATEGORY_MANUFACTURER.md) records the executed PostgreSQL/Maven checks and exact delta.

- Typed controller commands/list/detail DTOs map explicitly to a serialization-independent label application model and explicit SQL persistence read projection. Tenant associations include ID/name; no entity response, Product-only field or writable attribution.
- Controller supplies page 0/size 20/max 100; closed name/visibility sorts and asc/desc direction, normalized literal name search and exact visibility combine with authorized scope, SQL ordering/count/pagination. Normal lists reject deleted/unknown/unpaged parameters. Default createdAt DESC is not a selectable sort; retained internal SQL tie ordering creates no mandatory Domain UUID rule.
- Service derives TENANT scope solely from authenticated identity, including shared/root routes; supplied Tenant path IDs are ignored for TENANT scope, not compared as authority. GLOBAL supplied context is validated. Detail IDs are scoped independently and retain the existing non-disclosure contract.
- Existing create/edit/soft-delete/publication/Merge writers, retained-reference/UNKNOWN/visibility guards, real actor/time attribution and audit/AssetEvent transactions remain authoritative. Preview is advisory; execution locks/revalidates. Authorized retained/deleted detail stays readable without enabling undelete or repair commands.
- Product routes/query defaults, shared default 25 for not-yet-migrated resources, frontend and all later resources remain unchanged. No schema/index/data reset or compatibility layer is introduced. Backend API response/query changes may leave frontend integration pending, as explicitly authorized for this sequence.

Product/Step 3 was subsequently separately authorized; no later resource follows automatically. Step 2 does not close all Q1/M2/R1 work or any frontend gate.

### Step 3 — Product backend vertical refactor

HUMAN ACCEPTED / CLOSED. Implemented applicable Q1/M2/R1c backend work for Product only. [Step 3 verification](../verification/STEP_3_PRODUCT.md) records the exact delta, API consequences, PostgreSQL evidence and limitations.

Product-specific typed criteria and explicit transport/application/SQL projection boundaries support name OR manufacturerNumber literal q, visibility, exact categoryId/manufacturerId and dedicated manufacturerNumber substring together. Controller defaults are page 0/size 20/max 100; SQL performs scope/filter/count/order/paging. Selectable sorts are name/manufacturerNumber/visibility; default createdAt DESC is not selectable. Nullable numbers sort NULLS LAST in either direction; no Category.name/Manufacturer.name sorting or mandatory Domain UUID tie rule is added.

Independent UUID list/detail projections include safe Category/Manufacturer ID/name and authorized Tenant ID/name, without N+1 or entity exposure. Root/Tenant routes retain their established paths. TENANT service scope ignores transport Tenant IDs and derives signed own-Tenant authority; GLOBAL supplied context is validated. Foreign/absent detail and mutation targets retain non-disclosure. Reference filters only narrow authorized Products and are not assignment validation/authority or readable-name resolution.

Create/edit/delete/publication continue through the accepted trusted transactional writers. Permanent ordinary-edit freeze after historical Asset use, all retained current-reference deletion guards, visibility/new-assignment rules, M1 actor/time ownership and administrative AuditLog remain unchanged. Publication is separately validated and not blocked merely by historical use; no implicit Asset rewrite or duplicate AssetEvent is introduced. Product Merge remains absent.

No schema/index/deployment/data reset or frontend work occurred. Existing M1 operational prerequisites remain unchanged. The shared default 25 and not-yet-migrated resource contracts remain separately gated. Location/Owner Step 4 was subsequently separately authorized; no later resource follows automatically; Product frontend integration and the 390px Product-table limitation remain open.

### Step 4 — Location + Owner backend vertical refactor

HUMAN ACCEPTED / CLOSED. Implemented applicable Q1/M2/R1d backend work for these two Tenant-owned flat resources only. [Step 4 verification](../verification/STEP_4_LOCATION_OWNER.md) records the exact delta, APIs, PostgreSQL evidence and limitations.

Explicit transport commands/list/detail DTOs map to serialization-independent Place models/closed name-only criteria and an explicit scoped SQL read projection. Controller defaults page 0/size 20/max 100; name ASC/DESC is selectable, default createdAt DESC is not. Literal q searches name only; note/deleted/visibility/unpaged/unsupported criteria are rejected. Scope/search/count/full-sort/paging combine server-side. Lists exclude soft-deleted rows; independently scoped UUID detail preserves authorized retained/deleted reads with safe Tenant ID/name, note and metadata.

Service derives TENANT scope solely from signed identity, ignoring supplied Tenant path IDs rather than comparing them as authority. GLOBAL collection/create requires a validated concrete Tenant; no aggregate Place collection or Global-context write is introduced. Loaded persisted ownership controls mutations; foreign/absent detail and write UUIDs share existing non-disclosure behavior.

Accepted M1 Place writers and transaction/lock/reference guards are unchanged. Location name/note edits remain allowed while referenced by any retained Asset; deletion remains prohibited. Every referenced Owner edit, including unchanged-name/note-only input, and deletion remains prohibited. Resolving all retained current references allows normal Owner edits/deletion. Nullable note uses existing normalization/replacement semantics and does not participate in query criteria. No Location/Owner Merge or duplicate AssetEvent is introduced. Stable IDs, M1 metadata ownership and required AuditLog atomicity are preserved.

No schema/index, frontend, deployment or data reset occurred. Existing M1 operational preconditions remain unchanged; the shared default 25 for not-yet-migrated resources remains separately gated. Asset Step 5 was subsequently separately authorized; no later resource follows automatically. Backend completion does not authorize frontend integration or close all Q1/M2/R1 work.

### Step 5 — Asset backend vertical refactor

HUMAN ACCEPTED / CLOSED. Implemented applicable Q1/M2/R1a work for Asset only. [Step 5 verification](../verification/STEP_5_ASSET.md) records the exact delta, PostgreSQL verification, API projections and limitations.

- Explicit AssetQuery and AssetHistoryQuery application criteria; controller defaults 0/20/100. SQL combines authorized scope, all predicates, full ordering and paging with scoped counts. Beyond-end pages are empty with current metadata.
- Added loanable and dedicated localIdentifier/serialNumber/externalInventoryNumber literal substring criteria. q retains its four identifier fields. Trim/blank/128 UTF-16-unit query rules and literal wildcard escaping apply; note and joined labels remain outside search.
- Preserved the four accepted sort fields and the established external `tag` sort alias, createdAt DESC default and existing internal SQL tie behavior. No extra selectable sorts, fake pagination or client sort.
- Explicit Asset DTOs, application read projections and SQL row mappings; concise list items, richer independent UUID detail with safe Tenant/Catalog/Place ID+name associations, and immutable owning-Asset event DTOs. Strict command DTOs retain existing input shapes and reject overposting; no persistence entities are exposed.
- TENANT path context resolves exclusively from signed identity; GLOBAL concrete context is validated. The established GLOBAL-only aggregate endpoint remains GLOBAL-only. Detail/history/command target lookup constrains Tenant before returning authoritative ownership; foreign and unknown targets remain non-disclosing.
- AssetService/AssetHistory/entities, tag allocator, graph/version/loan/reference rules, metadata writers, immutable snapshots and all required event/audit transactions are unchanged. No user-facing deletion or general history query capability.

No migration/index, frontend, deployment or data reset occurred. Existing M1 operational prerequisites remain unchanged. Frontend DTO/paging integration remains separately pending; deployment is not authorized. Step 6 User administration (excluding I1) was subsequently separately authorized; no later resource follows automatically. This resource completion does not close all Q1/M2/R1 gates.

### Step 6 — User administration backend vertical refactor (excluding I1)

HUMAN ACCEPTED / CLOSED. Implemented applicable Q2/M2/R1e backend work only. [Step 6 verification](../verification/STEP_6_USER_ADMINISTRATION.md) records the exact delta, query/detail/command scope, PostgreSQL evidence and limitations.

- Bounded User Page API, controller defaults 0/20/max 100, displayName-only trimmed/literal q, exact scope/role/status and authorized GLOBAL Tenant narrowing, selectable displayName/scope/role/status ordering, default createdAt DESC. Full SQL predicates/order/count/page; no client slicing or unpaged escape hatch.
- Application Service forces TENANT ADMIN to signed Tenant/TENANT/ordinary USER. Supplied tenantId is removed from effective TENANT criteria; valid scope/role/status inputs only narrow that fixed set. GLOBAL ADMIN optional Tenant context is validated. Readers/PENDING/DISABLED remain denied.
- Independent GET /users/{id}; safe User/Tenant ID+name and metadata projection; foreign, elevated, GLOBAL and absent targets outside TENANT ADMIN scope remain non-disclosing. No credential, issuer/subject, token or provider secret projection.
- Explicit UserQuery/UserModel/private SQL row/HTTP DTO mappings. Existing creation, approval, role, disable and credential-reset writers remain unchanged; command DTOs reject overposting and preserve established response/error/status shapes and secret-safe logging.
- Existing login-name credentials, password policy/Argon2id, bootstrap/recovery, OIDC JIT, issuer/subject, JWT/status, self-protection, authoritative metadata and transaction-local AuditLog are preserved. No Email migration, dual login, generic profile/status PATCH, reactivation or User deletion.

No frontend, migration/index, deployment or data reset occurred. Array-to-Page and detail integration require later frontend approval; no compatibility code was added or deployment attempted. Step 6 is HUMAN ACCEPTED / CLOSED. I1 was subsequently separately authorized backend-only; Steps 7/8 correction work requires separate approval; Step 9 is accepted. STOP before any implementation gate until explicit approval. Backend completion does not close unrelated query/identity/detail gates.

### M1 — universal business metadata and Location/Owner note

HUMAN ACCEPTED / CLOSED backend-only prerequisite for createdAt-default collection rollout. Applicable inventory: Tenant, User, Category, Manufacturer, Product, Location, Owner and Tenant OIDC configuration; Asset retains its existing metadata. Distinguish immutable Audit/history creation attribution and technical guard/counter rows. Derive actors/times authoritatively, maintain create/update + audit/event transaction atomicity, and define nonhuman seed attribution without fabricating a User. Add nullable note to Location/Owner with unchanged Owner reference edit prohibition. No searchable note or note-only exception.

M1 backend files: explicit resource models/services and supported writers, additive V7, directly affected backend read/command DTOs, OpenAPI and PostgreSQL tests. Frontend forms/tests/integration remain out of scope. Preserve source history; use a separately approved disposable-data reset/cutover rather than artificial backfills. PostgreSQL tests prove field ownership, creation preservation/update attribution, rollback and Owner guards. Human checkpoint: M1 accepted and Step 2 separately authorized; any operational reset and later resources still require separate authorization. This does not diagnose Phase C creation failures.

### Q1 — resource collection backend contracts

After M1 prerequisites and separate approval, implement controller defaults/closed criteria and direct SQL predicates/order/count/page. Resource subgates: Catalog categories/manufacturers; Products; Locations/Owners; minimal Asset additions; history/routing default sizing. Default createdAt is independent of selectable sort allowlists. No ordinary deleted criterion; unsupported fields fail normal validation. Product number participates in q and dedicated literal substring; Asset loanable/dedicated identifiers combine with existing criteria. Nullable-number SQL ordering is documented consistently at API review, not an unresolved Product capability or forced UUID rule.

Expected files: ListInput/small paging values; CatalogController/PlaceController/AssetController/InventoryOperations; CatalogService/PlaceService/InventoryReads; AuthController/OidcConfigurationService; typed resource criteria; OpenAPI and PostgreSQL query tests. Schema only from approved M1 or demonstrated narrow need. Ship defaults together with minimum response-aware frontend consumers, or hold deployment until Q4; never deploy 20 against Pager 25. No old-data/API compatibility machinery.

Acceptance: authorization first; full-result filtering/order before SQL page; scoped count; one combined request; accepted selectable fields only; reference visibility; literal search/default/bounds/beyond-end. Equal values do not require UUID tie assertions. Separate resource/API/test review before deployment.

### Q2 — Users bounded collection

After needed metadata/primitives, separate approval. Controller criteria -> AuthenticationService forced scope -> IdentityStore full predicate/order/count/page. Filters remain displayName/scope/role/status/authorized GLOBAL Tenant; sorts remain displayName/scope/role/status, default createdAt. TENANT ADMIN stays own-Tenant ordinary USER-only regardless of shared tenantId/role/scope inputs. No local TENANT Email or universal username query.

Expected files: IdentityAdminController, AuthenticationService, IdentityStore, User query/DTO, OpenAPI, UserAdministration and query/security/browser tests. Page API and frontend consumer ship together; selected authoritative UUID remains independent of loaded page. Acceptance: bounded metadata, forged-scope tests, no secret projections, no client slicing. Email identity may proceed separately; it is not a reason to block correct displayName collection criteria. Human checkpoint: API/admin scope and query evidence.

### I1 — GLOBAL Email credentials, validity and password policy

HUMAN ACCEPTED / CLOSED after backend-only implementation and verification. Replace login_name design with editable case-insensitively unique Email; no second lifecycle or dual lookup. Model verified/approved/active distinctly and enforce required validity. Pre-delivery manually supplied Emails are verified; admin creation approved and ACTIVE/USER, ADMIN grant separate. Bootstrap/recovery uses manually entered admin Email through the existing private-input, zero-usable-admin guarded transaction. Evolve reset/current-password change and private CLI input; no delivery/reset token/self-registration now. Preserve Argon2id, signed scope, OIDC issuer/subject, CSRF/HTTPS and Access snapshot/Refresh architecture. Frontend visibly validates 12–128 plus uppercase/lowercase/digit/special; backend independently enforces.

Expected files: identity/credential services and persistence constraints, auth/admin DTOs/OpenAPI, bootstrap command and CLI prompts only as required by approved identity handoff, frontend authentication/user/password forms, localized policy text and security/integration tests. Necessary Email uniqueness is an integrity constraint, distinct from speculative query performance indexes. Development data reset requires explicit operational approval; do not rewrite V1–V6 or introduce login-name compatibility.

Acceptance tests: case-insensitive duplicate Email, edit collision, validity combinations, GLOBAL-only login/credentials, all password boundaries/composition, bootstrap refusal/concurrency/audit atomicity, admin defaults and untouched TENANT mapping. Email syntax/canonical storage details are reviewed within this gate; no provider-username guesses or credential leakage. Human checkpoint: schema/validity/API design before implementation.

I1 backend completion/evidence: [GLOBAL Email identity](../verification/I1_GLOBAL_EMAIL_IDENTITY.md). The existing GLOBAL credential now uses canonical ASCII Email, case-insensitive uniqueness and nullable emailVerifiedAt (null is unverified). ACTIVE remains the existing approved/active lifecycle state; GLOBAL login/Refresh require ACTIVE and verified usable credentials. Manual creation/bootstrap/reset/Email edit establishes actual verification time. GLOBAL ADMIN may edit Email without changing password; safe account detail exposes only Email/verification time. Exact Tenant issuer/subject and UUID/displayName audit identity remain unchanged. No frontend or CLI integration, delivery, compatibility mode or later resource is authorized.

V8 is additive and refuses populated legacy local credentials. It never invents Email/provenance or resets data. Such development state requires separately approved clearing/reset followed by fresh Email bootstrap before rollout. Existing migration sources remain immutable; the manual database/environment is untouched.

### Step 7 — Tenant authorization correction (HUMAN ACCEPTED / CLOSED)

Historical prior-policy correction and verification: [Step 7 authorization correction](../verification/STEP_7_TENANT_AUTHORIZATION_CORRECTION.md). That prior production delta was only TenantOperations: ADMIN detail reader and GLOBAL ADMIN collection guard. The subsequent U1 correction below broadens GLOBAL read only. Existing scoped reads, DTO/entity boundaries, creation/metadata/audit and internal business Tenant helpers are unchanged. Focused and full PostgreSQL suites verify the revised matrix; no frontend or Step 8 implementation. Prior evidence remains historical, never rewritten.

[Prior Step 7 verification](../verification/STEP_7_TENANT.md) remains unchanged historical evidence of the previous USER reads/TA own-list implementation. The resource UUID/detail DTO, unpaged projection, GA creation, metadata and atomic TENANT_CREATED foundation remain valid. Current target: ACTIVE GLOBAL ADMIN/USER list/detail; GA-only create; ACTIVE TA signed-own detail only, no list; TU/PENDING/DISABLED denied. Tenant rename/edit/delete remain undefined. TA sidebar Tenant entry points directly to own detail.

Correction is primarily TenantOperations administrative service policy and route/DTO tests; do not globally change business readTenant helpers or trusted Tenant existence/lock functions. Keep persisted UUID scope/non-disclosure, M1 ownership and AuditLog transactions. Test GA list/create/detail, TA list rejection and own-detail/foreign404, GLOBAL USER reads/create denial, TENANT USER denial and status guards. No schema change is required for this policy correction.

TENANT ADMIN/USER never switch Tenants. Remove obsolete frontend/backend selector dependence on /api/v1/tenants; do not add a switching/discovery alias. GA uses the collection for administration/selection; GLOBAL USER uses the same projection for ordinary context selection only. Safe authorized business association names and anonymous authentication routing do not confer administrative access. Frontend route/navigation/selector corrections require separate approval.

### Step 8 — IServ/OIDC authorization and physical DELETE (HUMAN ACCEPTED / CLOSED)

Implementation/verification: [Step 8 correction evidence](../verification/STEP_8_OIDC_AUTHORIZATION_DELETE_CORRECTION.md). Own-Tenant ADMIN scope is resolved in the service; DELETE requires current version query parameter and physically removes owned groups/configuration/secret plus one atomic deletion audit. Final provisioning acquires Tenant then configuration locks, rechecks current client ID in addition to existing enabled/version/issuer/group/identity checks. Valid recreated configuration may accept an earlier flow; no generation state or schema change.

[Prior Step 8 verification](../verification/STEP_8_ISERV_OIDC_CONFIGURATION.md) remains unchanged historical evidence of GA-only GET/PUT without DELETE. Existing safe DTO/application/SQL projection, strict input, metadata, PUT version/secret/group/enabled mechanics and AuditLog foundation are preserved. Current target: ACTIVE GA manages an authorized concrete Tenant singleton; ACTIVE TA manages only signed own Tenant; USER/PENDING/DISABLED denied. Required create/read/update/delete, no collection/paging/reset/restore or Tenant username.

The accepted backend correction extends OidcConfigurationService service-authoritative scope and IdentityAdminController/DTO/API/test contracts; caller-selected/path/body Tenant cannot expand TA authority. It implements physical DELETE of owned groups, encrypted secret/configuration and exactly one OIDC_CONFIG_DELETED audit, atomically. Capture target Tenant before removal independently from actor; audit is the historical record, never raw secret/ciphertext/claims. Repeated missing delete produces no successful audit. Disabled state remains a stored/readable/editable singleton, distinct from deletion. Recreation is initial configure with fresh supplied secret. Do not remove Tenant, Users or external mappings; preserve JWT/session behavior.

Current tenant_id primary key already enforces the singleton; allowed groups require child-first delete (existing FK has no cascade). Physical DELETE requires no schema change or deletion metadata/tombstones. Existing Tenant parent locking and final configuration FOR SHARE must serialize mutation/provisioning with consistent lock order. Callback and final provisioning must validate the current existing, enabled, authorized configuration and all existing security checks, never solely pre-delete flow/cache state. Missing/disabled configuration prevents completion; missing config is removed from routing. Existing token semantics remain unchanged; no provider-network-long database transaction.

Recreated/current configuration may legitimately validate an already-started flow. DELETE between start and callback is not itself an error or a rejection condition. There is no configuration-generation identity: do not add a generation UUID, freshness discriminator, retained deleted state or schema solely for delete/recreate flow identity. Preserve all existing protocol/version/issuer/client/group/identity checks; do not add old-flow rejection based only on the fact of deletion. No freshness-fence implementation or backlog remains.

Required isolated PostgreSQL/security tests: GA/TA/USER/status matrix; forged/foreign Tenant read and create/update/delete; singleton initial/update/version/secret/group behavior; physical removal and missing GET/update/repeated DELETE; exactly one captured-Tenant audit and rollback restoring config/secret/groups; disabled versus deleted; fresh recreation; retained Users/external mappings/JWT semantics; delete versus final provisioning in both lock orderings; update/delete/recreate races; callback/final rejection when current config is missing/disabled or existing checks fail; completion of a pre-delete flow when recreated/current configuration legitimately satisfies all existing checks; cached pre-delete state alone cannot authorize completion; no secret projection/overposting. Preserve exact issuer/subject JIT and egress/group checks. DELETE expected-version transport is the required nonnegative int64 version query parameter; stale state conflicts, missing/repeated deletion remains 404; no generation/freshness design gate is needed. OpenAPI/tests change only under separate authorization.

### Q3 / Step 9 — Audit collection queries

HUMAN ACCEPTED / CLOSED; authorization unchanged. [Step 9 verification](../verification/STEP_9_AUDIT_QUERY.md) records the exact delta and tests. Closed AuditQuery and explicit persisted row/application/HTTP projections replace arbitrary query-map/full-row transport. Existing service authority remains captured target Tenant attribution: ACTIVE GA reads all/authorized narrowing; ACTIVE TA is forced to its signed Tenant and cannot supply GA scope filters. Reads never join live Users/targets or rewrite historical actor snapshots.

Combined exact action/targetType, targetId/actorId UUID, optional inclusive from/until and GA tenantId/globalOnly criteria constrain both SQL count and full-result ordering before paging. Date bounds require explicit UTC/offset and PostgreSQL microsecond precision; finer boundaries and reversed ranges return validation errors rather than rounding. No arbitrary maximum duration. Only occurredAt sorting is accepted; direction asc/desc can be supplied independently and defaults desc. No mandatory UUID tie. Page0/default20/max100; beyond-end pages stay empty with requested page/current count metadata.

Frontend local timestamps/date-only day boundaries must be converted to UTC in a separately approved frontend gate; no frontend implementation here. No writer, history/actor presentation, schema/index, username, Phase D, deployment, export or delivery change. User-facing API defaults and criteria are intentionally updated without compatibility shims. The collection backlog's Audit backend criteria are accepted backend-only, not a blanket acceptance of frontend or unrelated future gates.

### Accepted cross-module verification and lock-order correction

The [backend cross-module audit](../analysis/BACKEND_CROSS_MODULE_CONSISTENCY_AUDIT.md) is HUMAN ACCEPTED. Its source-inferred Catalog/Product/Merge lock-order risk was subsequently reproduced as PostgreSQL40P01 in Product edit/publish versus Category/Manufacturer Merge, then corrected and HUMAN ACCEPTED / CLOSED. Product mutation prelocks required labels before Product and revalidates persisted references under the Product lock; obsolete prelocked inputs fail with optimistic conflict without reverse-order label acquisition. Metadata, lifecycle guards, signed-Tenant authority and audit atomicity remain intact. [Correction evidence](../verification/CATALOG_PRODUCT_MERGE_LOCK_ORDER_CORRECTION.md): focused281 passed; latest full74 unit +699 PostgreSQL integration =773 passed, zero failures/errors/skips. Historical investigation/audit conclusions are preserved as evidence at their dates, not current unresolved implementation work.

Public OIDC routing lookup remains a separate pending alignment: current default batch25 and untrimmed q must be reconciled with approved20/literal normalized search. This is authentication routing, not Tenant administration or a provider collection. No frontend integration, Email delivery/export, Phase C/D or other later gate has started.

### Q4 — frontend collection and incremental picker integration

After real server capabilities and separate approval. Standard pager uses returned page/size/count and exact six choices; no persisted query state across departure. Apply resets page, sorts/pages use applied criteria only. Supported table headers request backend ordering and preserve returned rows; no arbitrary-page sort/filter. Query changes keep nodes mounted; pending options remain safe.

Reference lookup uses batches of 20 and continuation append without the six-choice selector or Filter button. Keep query/UUID/label distinct; query change resets continuation generation, stale pages never append, overlap is UUID-deduplicated and valid selection survives same-context refresh. Backpressure prevents repeated continuation dispatch. Optional refs clear deliberately; duplicate names show meaningful disambiguation.

Expected files: inventory.ts, pages.tsx, existing searchable-combobox, auth/organization lookup query handling, small pager primitives/locales and focused tests. Sidebar/topbar/Global presentation is frozen. Preserve Merge same-dialog preview/no-op confirmation, form exit semantics and search focus. Verify German/English at 1440/1024/768/390. The existing 390px Product-table limitation remains separately OPEN unless directly approved/touched/proven fixed. Human checkpoint: request/identity/accessibility/runtime evidence; no fabricated manual Asset dataset.

### M2 — DTO/application/persistence separation

Independent resource-sized approval after mapping inventory; do not make one universal object or mapping framework. Define request/response DTOs, in-memory models and persistence responsibilities; explicit mapping/copying, no serialization dependency in Domain models or reflective copy-all. IDs/lightweight association projections avoid full graph loading. Each exposed reference carries ID and useful readable attributes; frontend shows names/submits IDs. No secret associations or name-based authority.

Expected files: scoped controllers/DTOs, application models, entities/query projections/mappers, OpenAPI and boundary/projection tests. No blanket rename or unrelated transaction rewrite. Acceptance: no entity API responses/request persistence, endpoint-specific projections, overposting rejection, correct duplicate reference behavior and no unnecessary association/N+1 loading. Human checkpoint: one resource map and its API/transaction consequences before code.

### X1 — future Excel export

Design approval first, implementation approval later. Export complete applied-filter authorized result, never current frontend page. Backend reauthorizes at dispatch and reuses authoritative predicates. Readable association values are spreadsheet presentation; IDs are supplemental if useful. Approve safe row/time/memory/concurrency bounds and sync/stream/job choice; define truncation/refusal and error behavior honestly. Handle formula injection for every user-controlled spreadsheet value and exclude secrets. Do not broaden interactive Asset sorts merely to cover uncommon Excel analysis.

Expected future files: export application/API/projection implementation and spreadsheet writer, UI applied-filter action, OpenAPI, authorization/resource-bound/formula-injection tests. Dependencies and resource policy require review before implementation; no speculative framework or Excel generation now.

### I2 — future real Email delivery and verification

Separate product/security/design gate for real verification, delivery, Email-change validity, Email-based reset and self-registration. Define token lifetimes/use, approval/activation relationships, abuse protections, configuration/redaction and delivery failures before code. Do not add these flows to I1 or invent a parallel bootstrap/reset domain. TENANT remains external identity, not local Email login. This task authorizes no mail integration or sending.

Phase C creation repair and Phase D audit presentation remain independently gated. Audit target is now displayName snapshot plus stable UUID, not username/Email-first display; existing columns may suffice. No actor-schema migration is presumed. Error-contract debt and physical/provider acceptance remain separate.

### R1 — resource command and detail API completion

**Documentation-defined gate only; NOT AUTHORIZED.** Collection contracts remain under Q1–Q4. R1 compares each Domain §15 operation with executable routes/services, completes proven gaps, and provides ID-backed frontend detail capability. It is not a universal CRUD rewrite, Phase C diagnosis, identity delivery or schema authorization.

Dependencies are resource-specific: M1 for future notes/audit metadata exposed or written; M2 boundary approval for new DTO/application/entity mappings; I1 for Email/validity/credential changes. Existing safe detail reads can be reviewed without waiting for unrelated query work. Q2 provides the Users collection scope contract to reuse for detail; R1 must preserve its forced predicates even if the existing array has not yet been migrated. Q1–Q4 keep all approved search/filter/paging behavior and release sequencing; never implement fake collection behavior in R1.

#### Historical pre-resource-completion inventory — static evidence only

Paths below are relative to `backend/src/main/java/com/github/ksewen/kelios`. No runtime test, new exploit or backend repair is claimed.

| Resource | Confirmed implemented command/detail surface | Required completion or separately gated target |
|---|---|---|
| Asset | security/AssetController:12–26 has list/detail/events, scoped registration and explicit commands; InventoryOperations:26–39 authorizes; InventoryReads detail/events scopes selection; AssetDetail uses detail API | Preserve routes/version/transactions; check safe association completeness, direct detail refresh and reference presentation; no arbitrary PATCH/deletion |
| Category/Manufacturer | CatalogController detail/create/edit/delete/publish/preview/merge methods; InventoryOperations.entry/writable and CatalogService/CatalogMergeService guard persisted targets | Detail exists; safe DTO/association review, ID-route frontend detail; no guard relaxation |
| Product | CatalogController productsDetail/create/edit/delete/publish; service checks historical use/current refs/visibility | Rich Category/Manufacturer ID+name detail projection and independent frontend detail read; no Merge |
| Location/Owner | PlaceController:12–21 detail/create/edit/delete; PlaceService.readScoped and edit/delete guards; InventoryOperations.place | Future note under M1; ID-backed detail frontend/projections; Owner note cannot bypass references |
| User | IdentityAdminController:21–26 lists/creates/approves/role/disables/resets; AuthenticationService and IdentityStore.managed:18–22 force administrative scope; own password is AuthController | GET /users/{id} absent; add service-scoped safe detail, associated Tenant projection and direct frontend route; Email only I1; no reactivation/deletion assumption |
| Tenant | Step 7 TenantOperations/Reads/Model/DTO: authorized create, unpaged list and independent UUID detail; TenantService retains transactional writer/internal existence validation | Accepted administrative correction; frontend detail separately gated. Edit/delete remain undefined; require is not a detail endpoint |
| Provider | IdentityAdminController:27–28 GET/PUT /tenants/{id}/oidc; OidcConfigurationService.get/put enforce GA, version/secret/groups | Completed: signed-own-TA authority and physical DELETE; retain initial/update/enable and safe projections; no reset/history |

Current frontend pages.tsx AssetDetail:111–116 reads Asset/detail/history independently. Management and UserAdministration use selected collection rows for resource modals; TenantAdministration navigates to Tenant dashboard. These are **CONFIRMED target-completion gaps**, not proof of a mutation/security defect. ProviderAdministration reads its singleton API. Existing raw/map/record detail projections require M2 review, not a blanket claim that every detail response is already a proper target DTO.

This historical surface review preceded the accepted backend sequence. Backend detail/projection and service-authority work described here is now completed; the current resource progress matrix and accepted verification govern completion status. Frontend detail integration remains separately gated. The subsequently confirmed Catalog lock-order defect is corrected/closed as recorded above. Retained/deleted detail remains distinct from normal collection exclusion; no repair interface or retroactive history disappearance is approved.

#### Resource-sized subgates

| Subgate | Review/implementation boundary after separate approval | Preconditions and acceptance |
|---|---|---|
| R1a Assets | Existing commands/detail/events + safe readable associations + existing direct browser route | Preserve version, reference guards, all graph/loan rules; prove one required event and atomic rollback; no new command merely for symmetry |
| R1b Categories/Manufacturers | Detail projections/ID routes, current guarded commands, publish and preview/Merge | No retained-reference/UNKNOWN/visibility regression; advisory preview, fresh locked execution, one audit plus redirected Asset events |
| R1c Products | Safe referenced-ID/name detail and guarded create/edit/delete/publish frontend/API | Preserve permanent ordinary-edit freeze and current-reference delete guard; no joined-name authority or Product Merge |
| R1d Locations/Owners | Scoped detail/projection/page and approved name/note commands | M1 before note implementation; referenced Location editable, referenced Owner entirely non-editable; no Merge, note search or Owner exception |
| R1e Users | Administrative UUID detail and route-backed frontend; explicit approved identity commands | Reuse forced GA/TA scope; M2 safe projection; I1 before Email changes; preserve self protection/JIT/status; no generic profile/status/delete |
| R1f Tenants | ACTIVE GLOBAL ADMIN/USER list/detail; GA-only create; TA own detail only; TU denied | GA/GU list unpaged; no TA list/switching; no Tenant rename/delete; frontend separately gated |
| R1g Tenant provider | Accepted GA/own-TA singleton GET/PUT/physical DELETE; safe projections | Preserve PUT rules; one atomic deletion audit; current-configuration validation/lock proof; no history/reset/collection; no secrets |

Backend portions of R1a–R1g are HUMAN ACCEPTED / CLOSED; the remaining frontend portions require separate authorization. Each future frontend integration subgate reviews endpoint shape, authoritative scope, DTO/model/entity mapping, command field whitelist, mutation guards, transaction ownership, exact audit/event, frontend direct detail reconstruction and tests. Existing endpoint names stay unless a separately approved API change is genuinely needed. A route-backed modal may be acceptable; list-only selected state is not detail authority. Do not force all resources into one batch or start R1 from documentation approval.

Expected future files: affected controllers/application/read services/DTO projections; frontend resource routes/detail views/locales; OpenAPI plus focused backend PostgreSQL/security and frontend browser tests. New User/Tenant detail wrappers must authorize inside application boundaries, not simply expose IdentityStore.load or TenantService.require. No generic controller/repository base class. Existing commands should remain intact when already compliant. Schema changes only under their separately approved prerequisite gates; no detail-by-ID schema change is presumed.

#### R1 verification after approval

Backend tests cover allowed/denied scope-role-status, foreign/absent UUID indistinguishability, authoritative TENANT identity despite shared DTO fields, GA allowed scope, same-scope references, no secret projections/overposting, every guard and required transaction rollback/audit/event count. PENDING self-status does not become User-resource access. Advisory reads produce no mutation/audit. Immutable snapshots stay readable after resource rename/deletion without a live-identity requirement.

Frontend tests cover direct entry/refresh for all eight entities and Tenant configuration, detail API requests when the resource is absent from the loaded page, concise-list/richer-detail projections, readable associations/duplicate disambiguation, loading/error/permission cases, form exit semantics and stable DOM/focus during same-context refresh. No need to retain list state across departure, no avoidable label-only N+1 requests and no unauthorized association display.

Run the existing Maven3 PostgreSQL gate and appropriate frontend unit/build/Chromium suites only after approval; retain A/B/routing regressions and exact executed counts. Writes use isolated fixtures, not operator data. Review each subgate's evidence separately; no reset/business-data creation or actual Merge in the manual environment. No legacy APIs, dual endpoint versions or development-data backfill. R1 does not close collection backlog, Phase C/D, M1/M2/I1/I2/X1 or the 390px Product-table limitation.

R1 documentation verification: approved ARCHITECTURE §13 and DOMAIN §12 collection sections remain byte-identical. PRE-task comparison found only ARCHITECTURE.md, DOMAIN.md and this plan changed; 864 other baseline files stayed byte-identical. Link and documentation-delta whitespace checks passed; index empty. No application tests or runtime verification were performed for this documentation gate.

## 4. Future verification and data/migration policy

Backend PostgreSQL/Testcontainers tests: omitted values 0/20, min1/max 100, six UI sizes, invalid negatives/noninteger/overflow/0/101; full-set filter/order before paging; ASC/DESC and duplicate primary values across three pages without imposing UUID tie order; matching scoped counts; empty/beyond-end; combined refs/enums/booleans/text; trim/blank/literal wildcard/length; foreign Tenant cannot expand authority; TENANT supplied tenantId is not scope proof; no secrets. Date tests include equality and optional bounds. Metadata, identity and model gates have their own atomicity/security tests.

Frontend intercepted tests prove requests, not SQL correctness: six sizes change server request, applied criteria survive sort/page/size, pending criteria never leak, Apply sends one logical query, return resets defaults, beyond-end renders metadata honestly, no page-only sort/filter, obsolete query/context/picker continuation cannot replace/append current data. Preserve A/B DOM/focus/form-session tests. Dropdown tests prove20-item append, continuation boundedness, no size selector/Filter and stable UUID nodes.

After separately approved implementation run Maven3 `./mvnw -B verify`, frontend `npm test`/`npm run build`, relevant Chromium suites and B.3 routing regression. Record exact executed counts/exclusions. Manual checks use operator data read-only; no real Merge/seeding/reset for evidence. No manual Asset-sort claim without data.

No production business data must be preserved. Approved future changes may reset development data with explicit authorization, but never add backfills, dual schemas/login support, compatibility shims or transitional code solely for that data. Preserve migration source history; normal additive schema changes remain separately reviewed. Query indexes need representative isolated PostgreSQL EXPLAIN evidence; do not add every possible filter index. Integrity constraints such as Email uniqueness are not optional performance speculation.

## 5. Remaining design details and stop

No old Product-search, Audit-range, picker-size or mandatory UUID decision marker remains open. Nullable sort SQL presentation, timestamp precision, practical Email validation/canonical storage, export/delivery safety policy are implementation/design-gate details, not permission to contradict the approved contract. Explicitly resolve them at their affected gate; no current code is claimed to implement them.

Q0 changes documentation only. Application/frontend/tests/OpenAPI/migrations/deployment/runtime/business data remain untouched; no commit/push/PR/reset/clean/stash. Documentation checks passed: file links, tracked/untracked documentation-delta whitespace and `git diff --check`; index empty. PRE-task SHA-256 comparison covered 869 files: 18 documentation files changed and 851 remained byte-identical. Accepted Phase A and Phase B closeout sections are unchanged; prior verification evidence is untouched. No implementation suite or runtime check was rerun. **STOP for human review.** None of the listed gates starts automatically.

### U1 Tenant read-authority correction — 2026-10-12

U1 HUMAN RESOLVED / CLOSED: ACTIVE GLOBAL USER may GET the existing complete Tenant collection/detail representation, including id/name and metadata createdBy/createdAt/updatedBy/updatedAt/createdByDisplayName/updatedByDisplayName. Bounded actor UUID/timestamp disclosure is approved; no new endpoint or reduced DTO. Read does not grant administration. GA-only create; TA own detail/no list; TU/status/anonymous denial unchanged. OIDC/User/Audit reads unchanged. GLOBAL USER initially Global with future authoritative UUID/name dropdown, optionally lazy loaded; context selection changes neither identity nor authority. TENANT principals never switch. New backend correction HUMAN ACCEPTED / CLOSED, 2026-10-12; previous Step7 reports remain historical. [Evidence](../verification/TENANT_GLOBAL_USER_READ_CORRECTION.md). F1b/D05–D07 HUMAN ACCEPTED / CLOSED, 2026-10-12; no later gate authorized.
