# Phase 3 — AuditLog organizational attribution analysis

Status: **Analysis only, 2026-10-10.** The user accepted target/resource-based audit visibility as the product direction, but has not authorized the Domain update or implementation. This report validates that direction against current production writers. It does not resolve P7 in the Phase 3 plan, change authoritative specifications, or authorize a migration.

## 1. Result and analysis boundary

A nullable immutable target-ownership UUID is sufficient for **all currently implemented operations going forward**; a separate AuditScope enum is unnecessary. Tenant creation can use the newly created Tenant UUID. Publication can consistently use the loaded pre-operation Tenant; Merge can consistently use the source's pre-operation Tenant. Neither requires per-affected-Tenant AuditLog duplication.

**It is not sufficient by itself to represent all legacy records truthfully if null means exclusively known global ownership.** Some existing writers omit the Tenant from historical data. Assigning null to those rows would conflate unknown historical attribution with global ownership. An explicit legacy treatment requires approval (§10).

This is repository/source analysis, including existing tests and accepted documentation. No database connection, Docker operation, build, test execution or live-row census was performed. The report establishes that ambiguous rows **can be produced by supported operations**, and existing tests exercise those operations. It does not claim a number of ambiguous records in any running/production database. No current actor/resource lookup was used as historical ownership evidence.

## 2. Current persisted model and write contract

The actual schema is created in [V2, lines 60–65](../../backend/src/main/resources/db/migration/V2__domain_foundation.sql). V1 does not create it; V3 protects AssetEvent and UNKNOWN records only; V4 makes no AuditLog change.

| Persisted column | Current type / guarantee | Historical attribution relevance |
|---|---|---|
| id | UUID primary key | Record identity only |
| actor_id | UUID NOT NULL, no User FK | Actor identity; not organizational attribution |
| actor_display_name | Text NOT NULL/non-blank | Actor display snapshot; not ownership |
| action | Text NOT NULL/non-blank | Operation name; sometimes establishes global ownership by definition |
| target_type | Text NOT NULL/non-blank | Target kind; TENANT targets directly identify an organization |
| target_id | UUID NOT NULL; no polymorphic target FK | Target identity; User/Location/Catalog UUID alone does not identify a Tenant |
| occurred_at | TIMESTAMPTZ NOT NULL | Server Clock timestamp; no ownership meaning |
| data | JSONB NOT NULL/object CHECK | Writer-specific historical context; ownership is present in some payloads, absent in others |

There is **no tenant_id, scope, created_at, version, attribution status or audit-specific query index**. [AuditLog.java](../../backend/src/main/java/com/github/ksewen/kelios/audit/AuditLog.java) maps precisely those eight fields and uses Hibernate `@Immutable`. [AuditService.record](../../backend/src/main/java/com/github/ksewen/kelios/audit/AuditService.java), lines 19–23, inserts all eight in an existing mandatory transaction, generates UUID/time server-side, and serializes supplied internal context.

Audit history is immutable through supported application use: there is no AuditLog update/delete API or service. Unlike AssetEvent, AuditLog has **no database append-only trigger**. `@Immutable` is not a database guarantee against direct SQL. Backfill feasibility below assumes genuine records written by the inspected supported paths, not arbitrary manually forged/modified SQL data. This report does not propose expanding triggers or claim stronger existing protection.

Action and target type are free non-blank strings, not an AuditAction/AuditTarget enum. CatalogService.Kind and PlaceService.Kind generate names; CatalogService.MergeKind is restricted to Category/Manufacturer. Searching production sources found one SQL insert site (AuditService) and all callers listed below. No hidden AuditLog entity persistence writer was found. Phase 1 `UserService.create` creates foundation Users without an AuditLog; Asset registration/mutations write AssetEvent, not AuditLog; failed operations, logins, refresh/logout and Merge preview do not generate administrative audit actions.

### Source index for the matrices

Paths are production sources under `backend/src/main/java/com/github/ksewen/kelios/`:

- **T:** [tenancy/TenantService.java](../../backend/src/main/java/com/github/ksewen/kelios/tenancy/TenantService.java), create lines 13–17.
- **C:** [catalog/CatalogService.java](../../backend/src/main/java/com/github/ksewen/kelios/catalog/CatalogService.java): snapshot 27–35; creates 79–91; edits 93–105; delete 107–111; publish 113–121; Merge lock/redirect/finish 125–149.
- **P:** [inventory/PlaceService.java](../../backend/src/main/java/com/github/ksewen/kelios/inventory/PlaceService.java): snapshot 19–20; create 41–43; edit 48–51; delete 53–56.
- **U:** [identity/auth/AuthenticationService.java](../../backend/src/main/java/com/github/ksewen/kelios/identity/auth/AuthenticationService.java): bootstrap 20–28; GLOBAL create 53–57; approve 64–75; role 78–85; disable 88–94; reset 97–107; own password 110–118.
- **J:** [identity/auth/OidcProvisioning.java](../../backend/src/main/java/com/github/ksewen/kelios/identity/auth/OidcProvisioning.java), locked provider/JIT/create/audit 15–27.
- **O:** [identity/auth/OidcConfigurationService.java](../../backend/src/main/java/com/github/ksewen/kelios/identity/auth/OidcConfigurationService.java), put/target locks/audit 29–42.
- **M:** [inventory/CatalogMergeService.java](../../backend/src/main/java/com/github/ksewen/kelios/inventory/CatalogMergeService.java), coordinator 27–43.
- **S:** [platform/security/SecuredDomainOperations.java](../../backend/src/main/java/com/github/ksewen/kelios/platform/security/SecuredDomainOperations.java); **I:** [platform/security/IdentityAdminController.java](../../backend/src/main/java/com/github/ksewen/kelios/platform/security/IdentityAdminController.java); **A:** [identity/auth/Authorization.java](../../backend/src/main/java/com/github/ksewen/kelios/identity/auth/Authorization.java).

Spec context: [Domain](../../DOMAIN.md) §§1–3, 5, 8–11; [Architecture](../../ARCHITECTURE.md) §§4–5, 8–9; original [Task](../../CODEX_IMPLEMENTATION_TASK.md) Phases 1–3; [Phase 1 plan](../plans/PHASE_1_PLAN.md) historical actors/transactions; [Phase 2 plan](../plans/PHASE_2_PLAN.md) §§11–14; [ADR 0002](../adr/0002-phase-1-domain-persistence.md) and [ADR 0003](../adr/0003-phase-2-authentication-and-authorization.md). These establish actor snapshots, administrative audit and atomicity, but not an already implemented Tenant audit read model. Phase 2 plan §13 mentions necessary target/type/Tenant context; actual User writers do not store that Tenant UUID. That planned wording cannot be used to manufacture missing historical evidence.

## 3. Complete current action attribution matrix

There are **28 distinct implemented action strings**. The matrix has 30 rows because USER_CREATED and LOCAL_CREDENTIAL_CHANGED each have two production paths with different historical payloads. Forward attribution is unambiguous in every row when captured from the authoritative operation target inside its transaction. **G/T** means the Catalog resource may be GLOBAL or TENANT-owned and can later be published; it does not mean scope follows the actor.

Backfill classifications: **1** deterministically recoverable from that stored historical record; **2** known global by operation definition or explicit stored GLOBAL creation context; **3** insufficient historical ownership evidence. Exact extraction rules and counterexamples are in §9.

| Action | Producing operation; audited target type | Ownership at operation / authoritative forward source | Proposed persisted tenantId | Ownership changed by this operation? | Legacy class; location/spec |
|---|---|---|---|---|---|
| TENANT_CREATED | Tenant create; TENANT | Newly persisted/flushed Tenant itself | new Tenant.id | New target created, not an existing ownership transfer | 1; T 13–17; Domain §1 |
| OIDC_CONFIG_CHANGED | Settings/secret/allowlist put; TENANT | Validated/locked configured Tenant, config PK | configured Tenant.id | No; issuer change does not change configured Tenant | 1; O 29–42; Domain §2 |
| USER_CREATED (GLOBAL) | Explicit GLOBAL user creation; USER | Server-created GLOBAL/null-Tenant User | null | New GLOBAL target | 2; U 53–57; Domain §2 |
| USER_CREATED (JIT) | Allowed OIDC first-login provision; USER | Verified locked provider Tenant, constructed TENANT User | created User.tenantId | New TENANT target | 3; J 15–27; Domain §2 |
| USER_APPROVED | PENDING activation; USER | Loaded/locked target User scope/Tenant, GLOBAL or TENANT | target User.tenantId | No; status only | 3; U 64–75; Domain §§1–2 |
| USER_ROLE_CHANGED | Role grant/change; USER | Loaded/locked target User scope/Tenant, GLOBAL or TENANT | target User.tenantId | No; role only | 3; U 78–85; Domain §§1–2 |
| USER_DISABLED | Disable managed User; USER | Loaded/locked target User scope/Tenant, GLOBAL or TENANT | target User.tenantId | No; status only | 3; U 88–94; Domain §§1–2 |
| LOCAL_CREDENTIAL_CHANGED (reset) | Administrator credential create/reset; USER | Loaded/locked GLOBAL target User; no Tenant credential path | null | No | 2; U 97–107; Domain §2 |
| LOCAL_CREDENTIAL_CHANGED (own) | Current-password-verified own change; USER | Loaded GLOBAL target User (actor is also target here) | null | No | 2; U 110–118; Domain §2 |
| GLOBAL_ADMIN_BOOTSTRAPPED | Deployment bootstrap/recovery; USER | Server-created GLOBAL ADMIN User | null | New GLOBAL target | 2; U 20–28; Domain §2 |
| CATEGORY_CREATED | Create label; CATEGORY | G/T row constructed after validated scope/Tenant selection and insertion | created Category.tenantId | New G/T target | 1; C 79–84; Domain §3 |
| CATEGORY_EDITED | Guarded name edit; CATEGORY | Loaded FOR UPDATE pre-state, G/T | before Category.tenantId | No | 1; C 93–97; Domain §3 |
| CATEGORY_DELETED | Guarded soft delete; CATEGORY | Loaded FOR UPDATE pre-state, G/T | before Category.tenantId | No; ownership retained on deleted row | 1; C 107–111; Domain §§3,9 |
| CATEGORY_PUBLISHED | Same-ID promotion; CATEGORY | Loaded FOR UPDATE **TENANT pre-state** | before Category.tenantId | **Yes: TENANT -> GLOBAL** | 1; C 113–121; Domain §3 |
| CATEGORY_MERGED | Explicit source -> target; CATEGORY | Locked pre-operation **source** G/T | source.tenantId | No source/target ownership rewrite; references redirected | 1; C 125–149/M 27–43; Domain §§3,8 |
| MANUFACTURER_CREATED | Create label; MANUFACTURER | Created validated G/T row | created Manufacturer.tenantId | New G/T target | 1; C 80–84; Domain §3 |
| MANUFACTURER_EDITED | Guarded name edit; MANUFACTURER | Loaded FOR UPDATE pre-state, G/T | before Manufacturer.tenantId | No | 1; C 93–97; Domain §3 |
| MANUFACTURER_DELETED | Guarded soft delete; MANUFACTURER | Loaded FOR UPDATE pre-state, G/T | before Manufacturer.tenantId | No | 1; C 107–111; Domain §§3,9 |
| MANUFACTURER_PUBLISHED | Same-ID promotion; MANUFACTURER | Loaded FOR UPDATE **TENANT pre-state** | before Manufacturer.tenantId | **Yes: TENANT -> GLOBAL** | 1; C 113–121; Domain §3 |
| MANUFACTURER_MERGED | Explicit source -> target; MANUFACTURER | Locked pre-operation **source** G/T | source.tenantId | No ownership rewrite; references redirected | 1; C 125–149/M 27–43; Domain §§3,8 |
| PRODUCT_CREATED | Product create; PRODUCT | Created G/T row, validated Tenant/dependencies | created Product.tenantId | New G/T target | 1; C 86–91; Domain §3 |
| PRODUCT_EDITED | Permitted ordinary edit; PRODUCT | Loaded FOR UPDATE pre-state, G/T; historical freeze separately checked | before Product.tenantId | No; dependency edits are not organizational transfer | 1; C 99–105; Domain §3 |
| PRODUCT_DELETED | Guarded soft delete; PRODUCT | Loaded FOR UPDATE pre-state, G/T | before Product.tenantId | No | 1; C 107–111; Domain §§3,9 |
| PRODUCT_PUBLISHED | Same-ID promotion; PRODUCT | Loaded FOR UPDATE **TENANT pre-state**; GLOBAL dependencies validated | before Product.tenantId | **Yes: TENANT -> GLOBAL** | 1; C 113–121; Domain §3 |
| LOCATION_CREATED | Scoped label create; LOCATION | Validated Tenant, inserted Location.tenantId | owning Tenant.id | New TENANT target | 3; P 41–43; Domain §5 |
| LOCATION_EDITED | Metadata edit; LOCATION | Loaded target `(id,tenant_id)` pre-state | before Location.tenantId | No | 1; P 48–51; Domain §5 |
| LOCATION_DELETED | Guarded soft delete; LOCATION | Loaded target pre-state | before Location.tenantId | No | 1; P 53–56; Domain §§5,9 |
| OWNER_CREATED | Scoped label create; OWNER | Validated Tenant, inserted Owner.tenantId | owning Tenant.id | New TENANT target | 3; P 41–43; Domain §5 |
| OWNER_EDITED | Unreferenced metadata edit; OWNER | Loaded target pre-state | before Owner.tenantId | No | 1; P 48–51; Domain §5 |
| OWNER_DELETED | Guarded soft delete; OWNER | Loaded target pre-state | before Owner.tenantId | No | 1; P 53–56; Domain §§5,9 |

Forward attribution is **not ambiguous** merely because legacy extraction is: U already loads target User and J already validates provider Tenant, but those values were not written historically. Source UUID, target UUID and actor UUID are independent identities. No PRODUCT_MERGED, Asset administrative action or login-audit action exists. UNKNOWN seeds are migration inserts and do not produce *_CREATED AuditLog rows.

## 4. Tenant creation and OIDC boundaries

### TENANT_CREATED

T creates UUID/name, persists Tenant, **flushes before audit**, then calls AuditService in the same transaction. The audited type is TENANT and target_id is the new Tenant UUID; `data` contains only name. The preferred attribution to the new Tenant is clean and deterministic. No installation/global attribution is needed just because a GLOBAL administrator creates it.

A normal non-deferrable FK would see the already flushed Tenant in the same transaction; no separate commit is needed, no orphan window and no FK cycle. Failure of the audit insert rolls back Tenant creation. A future Tenant Admin can read this creation row under `tenant_id = itsTenant`. Historical rows backfill directly from target_id with target_type/action validation, not from present-day Tenant name or existence.

### OIDC_CONFIG_CHANGED

O authorizes GLOBAL ADMIN, requires and locks the selected Tenant, creates/locks configuration under that Tenant PK, validates version, then updates settings/groups and appends audit. target_type is TENANT, target_id is the configured Tenant; current `data={version,enabled,allowedGroupsChanged}` need not contain a duplicate Tenant UUID for safe backfill.

The route Tenant UUID expresses selection, not trusted audit metadata. Existence/authorization and the locked operation establish the target; arbitrary actor scope or request JSON cannot relabel it. Changing issuer/client/groups leaves configuration ownership at that same Tenant. No provider secret/claims are needed for attribution. GLOBAL-only write authority and Tenant audit read authority are distinct: the accepted sanitized audit can be Tenant-visible without granting OIDC configuration-management API access.

## 5. Publication boundary

All three Catalog kinds use the same C.publish method. It loads the entry FOR UPDATE into an immutable Entry record, rejects already-global/deleted entries, and for Product verifies GLOBAL dependencies. SQL then sets visibility GLOBAL and tenant_id null on the same row. Audit is written **after** this SQL, but the local `entry` still holds the **pre-operation Tenant** and `data.before=entry.snapshot()` stores it. `data.after` reloads the now-GLOBAL row. No code destroys the retained before-state.

| Action | Attribution source | Existing historical extraction | Additional validation |
|---|---|---|---|
| CATEGORY_PUBLISHED | locked entry.tenantId before update | data.before.tenantId | before.visibility TENANT; after.visibility GLOBAL/tenantId null |
| MANUFACTURER_PUBLISHED | same | same | same |
| PRODUCT_PUBLISHED | same | same | Category and Manufacturer must be GLOBAL; publication separate from Product ordinary edit/freeze |

Creation/edits before publication stay attributed to their stored original Tenant forever. Publication itself belongs to that pre-operation Tenant. Later global edits/deletes/Merge-source actions receive null from newly loaded global pre-state. Do not infer historical visibility by joining the current resource or choosing data.after for publication. The user's Product example is semantically correct; a post-publication PRODUCT_EDITED is possible only if the separately approved permanent usage freeze does not block it.

This is a specific **pre-operation ownership** convention, not “look at ownership whenever audit happens to be inserted.” Writing audit after mutation is safe only when the operation explicitly supplies its captured pre-state. Existing row locks/atomic transaction already prevent a concurrent publication/edit from corrupting that snapshot. No new concurrency framework is needed.

## 6. Category/Manufacturer Merge boundary and every legal scope case

The administrative target is already **source**, not target: C.finishMerge writes target_type=Category/Manufacturer and target_id=pair.source.id. Its payload stores both original `source` and `target` snapshots including Tenant/visibility. It soft-deletes the source but does **not** move source ownership or change target ownership. Product/Asset reference redirects also do not change their Tenant ownership. A reference moving to a globally owned label is not an Asset organizational transfer.

Legality is based on **every affected retained Product/Asset's final visibility/dependency validity**, not a blanket ban on differing source/target scopes. C.lockMerge only rejects protected UNKNOWN source, same IDs or deleted endpoints; C.redirectProducts and M validate target against each referenced Product/Asset Tenant. There are no lifecycle/deletedAt filters. For both Merge kinds:

| Source pre-ownership -> target ownership | Actually legal when | Audited source attribution | Audit / indirectly affected Tenant visibility |
|---|---|---|---|
| Tenant A -> Tenant A | Existing source/target and resulting dependencies valid | Tenant A | Tenant A ADMIN sees single Merge audit plus authorized affected AssetEvents. |
| Tenant A -> GLOBAL | Existing endpoints/dependencies valid; GLOBAL target visible to all source dependents | Tenant A | Tenant A ADMIN sees publication-independent Merge audit; affected Assets keep Tenant A and get UPDATED. |
| GLOBAL -> GLOBAL | Existing endpoints/dependencies valid | null | GLOBAL ADMIN sees audit; each affected Tenant sees its own AssetEvent, not a duplicated global administrative record. |
| GLOBAL -> Tenant A | **All** retained referencing Products and Assets belong to A; no GLOBAL Product or other-Tenant dependent can remain. Empty affected set also passes. | null | Tenant A affected Asset history shows UPDATED; Merge audit remains global, even though target is Tenant A. No actor/affected-Tenant attribution. |
| Tenant A -> Tenant B, A != B | **No retained Product or Asset references to source**. In supported valid data, any existing dependent belongs to A and would reject B's target. Empty set passes current implementation. | Tenant A | A ADMIN sees source Merge audit; B ownership of existing target creates no B Merge audit and no affected Asset history when set is empty. |

For GLOBAL -> Tenant A, a retained GLOBAL Product alone prevents commit even if no Asset uses it. A retained soft-deleted Product or other-Tenant/deregistered/deleted Asset also blocks an incompatible target. Current tests explicitly demonstrate invalid GLOBAL -> Tenant redirection with GLOBAL Product and another-Tenant Asset; they do not exercise every permitted empty/single-Tenant case. The conditional legal cases above are established by actual validation paths, **not newly executed integration tests** or invented new permissions.

There is no sixth structural combination under GLOBAL/TENANT with A/B equal or different. All cases require same Catalog kind, distinct non-deleted IDs, non-UNKNOWN source and full resulting Product/Asset validity. UNKNOWN may be the GLOBAL target. Only GLOBAL ADMIN can invoke secured Merge.

**Source pre-operation tenantId, otherwise null covers every legal case without counterexample.** Broad/global operations can indirectly affect many Tenants while retaining one administrative target and one audit record. Those Tenants see their Asset UPDATED history according to Asset authorization. They see AuditLog only when its captured source Tenant equals theirs. Do not fan out audit rows or union affected Tenants into administrative visibility. Publication and Merge remain separate operations; Merge does not publish source.

## 7. User administration and scope stability

U approves/changes role/disables under target User locks and current target authorization. Those operations change only status or role; the target User remains the ownership source. Therefore GLOBAL ADMIN disabling or promoting Tenant A User attributes A; Tenant A ADMIN approving its ordinary User attributes A; a GLOBAL User -> ADMIN promotion is null. LOCAL_CREDENTIAL_CHANGED is always GLOBAL because both credential paths explicitly reject TENANT targets; own change uses actor.id **as the target ID**, then loads that User. Its global attribution is not a general actor-attribution rule.

JIT creation derives Tenant from locked, version-verified provider configuration and creates TENANT/USER/PENDING. Its audit actor is the newly created User's snapshot, but ownership must still come from the constructed User/provider Tenant, not an actor join. GLOBAL creation and bootstrap construct GLOBAL/null-Tenant targets server-side.

There is **no supported UserScope or tenantId mutation, membership transfer, automatic OIDC relinking or scope inference operation**. Domain §§1–2 enforce explicit exclusive scope and exact provider/Tenant mapping. Current role/status/credential APIs do not move ownership. This is supported-operation stability, not an explicit database immutability guarantee: `User.java` maps scope/tenant as ordinary columns and CHECK/composite FKs are structural, not universal update rejection. Do not invent a new permanent database identity immutability rule or use current User ownership as a substitute for missing historical snapshots. Future ownership-transfer policy would require an explicit review.

## 8. Actor/target trust-boundary findings

Current AuditService does not accept a Tenant parameter and Actor contains only UUID/displayName; **no current audit attribution code derives from actor Tenant/scope because attribution is not persisted at all**. Existing `data` is assembled internally, not accepted as an HTTP AuditLog payload. That does not make it uniformly complete historical evidence.

| Potential wrong source | Actual finding / correct future boundary |
|---|---|
| Actor scope/Tenant | Actor has no Tenant field. Secured services derive actor from verified principal. GLOBAL administrator on Tenant target must not be treated as global audit. Preserve actor columns separately. |
| Caller-selected Tenant | Catalog/place creation and OIDC configuration accept selected Tenant through trusted application operations. Secured wrappers authorize that scope, TenantService validates existence, and creation writes actual row Tenant. Capture attribution from validated created target/context, not an unchecked request field. TENANT ADMIN cannot select another Tenant and bypass S/A. Internal Phase 1 APIs intentionally lack HTTP auth; only secured entry points are supported externally. |
| Caller JSON | No AuditLog write endpoint; audit snapshots are internal maps. A future auditTenantId/scope/actor body property must not be accepted as authority. |
| Generic JSON inference | `before/after/source` are useful migration evidence but are not a universal runtime attribution API. Place creation and User operations do not store ownership uniformly. Each operation should explicitly provide its authoritative target snapshot/context to AuditService. |
| Target reload after mutation | C creates safely reload new target; ordinary edit/delete retain unchanged ownership. **Publication after reload is GLOBAL and would misattribute audit**; use captured before entry. Merge's target snapshot is also the wrong owner for its source-targeted audit. |
| Authorization's actor Tenant | IdentityStore uses authenticated Tenant to scope manageable User queries, but target loaded User remains attribution source. Actor Tenant alone cannot prove historical User target ownership. |

Locations/Owners are always Tenant-owned; creates validate/insert the selected Tenant, edits/deletes load `(id,tenant_id)` and retain ownership. Category/Manufacturer/Product are GLOBAL or Tenant-owned; creation attribution may use server-loaded after snapshot, edit/delete the loaded pre-snapshot, publication pre-snapshot, Merge source pre-snapshot. Existing administrative context/actor remains sanitized. No raw OIDC data/credential secret or mutable name is needed to decide audit ownership.

## 9. Historical migration matrix — exact rules, not present-day reconstruction

Classifications apply to correctly formed legacy records from the inspected writers. If expected snapshot fields are missing/malformed/contradictory, treat that particular record as insufficient, not implicitly global. SQL null extraction alone must not equate a missing JSON key with an explicit stored GLOBAL/null snapshot. Match exact action and target type, validate IDs/scope consistency, and verify snapshot identity matches target where applicable before deterministic backfill. No migration was created or run.

| Action/path (every implemented action covered) | Stored historical ownership evidence | Class / exact safe rule |
|---|---|---|
| TENANT_CREATED | target_type TENANT, target_id is newly created Tenant UUID; data.name only | **1:** tenantId = target_id. No target lookup. |
| OIDC_CONFIG_CHANGED | target_type TENANT, target_id is configured Tenant UUID; version/enabled/groups-changed context | **1:** tenantId = target_id. No provider/actor lookup. |
| USER_CREATED / GLOBAL create | data.scope GLOBAL, role USER, status ACTIVE; target User ID | **2:** null; the supported path inherently constructs a GLOBAL target and records scope. |
| USER_CREATED / Tenant JIT | data.scope TENANT, role USER, status PENDING, origin OIDC; **no tenantId/issuer/provider ID** | **3:** known Tenant-owned, but which Tenant is not recoverable from this row. Never null-as-global. |
| USER_APPROVED | data.before PENDING, data.after ACTIVE; target User UUID only | **3:** can be Tenant A/B or a foundation GLOBAL PENDING User. Neither target scope nor Tenant is stored. |
| USER_ROLE_CHANGED | data.before and data.after role strings; target User UUID only | **3:** USER -> ADMIN could be GLOBAL or any Tenant; role is not scope. |
| USER_DISABLED | data.before old status and data.after DISABLED; target User UUID only | **3:** ACTIVE -> DISABLED may target GLOBAL or any Tenant. |
| LOCAL_CREDENTIAL_CHANGED / admin reset | data.operation administrator-reset, target User UUID | **2:** null; supported credential setting is GLOBAL-only, checked on loaded target. |
| LOCAL_CREDENTIAL_CHANGED / own password | data.operation own-password-change, target User UUID | **2:** null; supported own change is GLOBAL-only. |
| GLOBAL_ADMIN_BOOTSTRAPPED | data.scope GLOBAL; target created administrator UUID | **2:** null; both first installation/recovery inherently GLOBAL. |
| CATEGORY_CREATED | data.after includes id, tenantId explicit UUID/null, visibility TENANT/GLOBAL | **1:** after.tenantId, with matching explicit visibility/nullability and target ID. |
| MANUFACTURER_CREATED | same after snapshot contract | **1:** same rule. |
| PRODUCT_CREATED | same after ownership snapshot, plus dependency IDs/number | **1:** same rule; dependency ownership is not Product ownership. |
| CATEGORY_EDITED | data.before and data.after complete ownership snapshots | **1:** before.tenantId (explicit GLOBAL/null or TENANT/UUID); ownership unchanged. |
| MANUFACTURER_EDITED | same | **1:** same rule. |
| PRODUCT_EDITED | same | **1:** same rule; manufacturer/category changes do not determine audit Tenant. |
| CATEGORY_DELETED | before/after snapshots retain Tenant/scope | **1:** before.tenantId; soft deletion does not clear ownership. |
| MANUFACTURER_DELETED | same | **1:** same rule. |
| PRODUCT_DELETED | same | **1:** same rule. |
| CATEGORY_PUBLISHED | before TENANT UUID; after GLOBAL/null | **1:** before.tenantId, never after.tenantId. |
| MANUFACTURER_PUBLISHED | same | **1:** same rule. |
| PRODUCT_PUBLISHED | same | **1:** same rule. |
| CATEGORY_MERGED | data.source and data.target have ID/Tenant/visibility snapshots; target_id is source ID | **1:** source.tenantId with snapshot-source ID matching target_id. Ignore target/affected ownership for attribution. |
| MANUFACTURER_MERGED | same | **1:** same rule. |
| LOCATION_CREATED | data.name only; target_id is Location UUID, not Tenant UUID | **3:** Tenant A/B cannot be determined. |
| OWNER_CREATED | data.name only; target_id is Owner UUID, not Tenant UUID | **3:** Tenant A/B cannot be determined. |
| LOCATION_EDITED | data.before = {id, tenantId, name}; new name separately | **1:** before.tenantId, validated UUID and target ID. |
| OWNER_EDITED | same | **1:** same rule. |
| LOCATION_DELETED | data.before = {id, tenantId, name} | **1:** before.tenantId. |
| OWNER_DELETED | same | **1:** same rule. |

**All historical rows are not guaranteed deterministically backfillable.** Six writer variants can produce insufficient records: Tenant USER_CREATED, USER_APPROVED, USER_ROLE_CHANGED, USER_DISABLED, LOCATION_CREATED, OWNER_CREATED. Four variants are known global; twenty variants have direct deterministic evidence. These are counts of writer cases, not live database rows. Because USER_CREATED has both GLOBAL and TENANT paths, the action name alone cannot be assigned one global classification.

Concrete counterexamples:

- A GLOBAL ADMIN creates `Raum 211` in Tenant A, or in Tenant B. LOCATION_CREATED stores the Location UUID and name, but no Tenant; actor remains GLOBAL in both. The UUID has no encoded Tenant meaning. A Location may never be edited/deleted afterward, so later ownership-bearing audit need not exist.
- Same missing evidence for OWNER_CREATED; same names are deliberately non-unique and cannot establish ownership.
- First allowed OIDC login under A or B produces USER_CREATED with `scope=TENANT` but no organization ID. Neither actor_id (new User) nor `origin=OIDC` identifies which configured provider/Tenant.
- USER_ROLE_CHANGED `USER -> ADMIN` and USER_DISABLED `ACTIVE -> DISABLED` use identical context shapes for GLOBAL and TENANT targets. Approvals similarly may include a legacy GLOBAL PENDING User created by internal foundation services without a creation audit.

Conditional corroboration from **other historical audit records** may recover some individual cases: e.g. a later Location edit contains that same target's Tenant, or a GLOBAL USER_CREATED record proves the scope of subsequent User role events under the current no-transfer operations. It is not guaranteed, is not evidence in every record, and cannot reconstruct the Tenant of JIT Users with the current User payloads. Any cross-record recovery needs a separately reviewed immutable chain/no-ownership-transfer proof; this report does not silently apply it or assert universal safety. Current tables, actor joins, external_identity/provider tables, current Catalog visibility or mutable labels/status are not permitted historical backfill sources.

### Existing tests and evidence: what they actually establish

[DomainFoundationIT](../../backend/src/test/java/com/github/ksewen/kelios/inventory/DomainFoundationIT.java) has `minimalIdentityAndHistoryNeedsNoLiveActor` (line 74, Tenant audit count), `catalogVisibilityAndPublicationLoadAuthoritativeDependencies` (159), parameterized `mergeIncludesRetainedRowsAndPreservesIdentityAndHistory` (175), rollback/failure-injection checks (191/199), and `administrativeAuditCapturesProductMutableInputBeforeAndAfter` (453). They cover counts, target identity, reference/history/audit atomicity, before/after Product context and actor display; they do **not** assert an AuditLog Tenant column or Tenant-scoped audit reads.

[IdentityAuthenticationIT](../../backend/src/test/java/com/github/ksewen/kelios/identity/auth/IdentityAuthenticationIT.java) covers GLOBAL creation, credential audit counts, bootstrap/recovery audit (118–157), sanitized OIDC settings/audits (159–173), JIT/approval/disable and role boundaries. It exercises writers whose Tenant snapshots are missing. Those tests passing does not supply missing historical data or prove audit-attribution enforcement.

[Phase 1 evidence](../verification/PHASE_1.md) records ten mappings, actor snapshots, administrative snapshots and rollback tests. [Phase 2 evidence](../verification/PHASE_2.md) records sanitized administrative audits and identity atomicity, with 186 backend tests at its accepted run. Neither claims a implemented Tenant AuditLog read model. ADR 0002/0003 retain their accepted scope; no historical ADR/evidence is rewritten to imply new fields. No tests were run during this analysis.

## 10. Legacy ambiguity options — decision required

Known records can be assigned their deterministic UUID/null without altering original actor/target/data. For insufficient records, preserve original context and give GLOBAL ADMIN access, but do not fabricate Tenant attribution or grant Tenant Admin visibility. The strict preferred meaning of null requires a distinction between known global and unknown legacy ownership.

| Option | Schema consequence | Tenant Admin visibility | GLOBAL ADMIN visibility / interpretation | Migration complexity / historical correctness | Permanent extra state? |
|---|---|---|---|---|---|
| **L1: explicit legacy provenance boolean (recommended)** | Nullable tenant_id plus `legacy_unattributed` boolean; true only for insufficient legacy rows, tenant_id null; CHECK forbids true with non-null Tenant. New supported writes always false. | SQL tenant_id equality exposes only known own-Tenant records; ambiguous rows excluded. | All records, with unknown legacy rows labeled **unattributed**, never global. | Small additive backfill/classification; preserve original payload. No guessed ownership. Validated global and Tenant rows marked false. | Yes: permanent historical attribution-completeness marker, not a new organizational scope, permission or business-resource lifecycle. Requires explicit approval. |
| **L2: separate legacy-marker table** | Add nullable tenant_id; `audit_legacy_unattributed(audit_id)` marks only insufficient historical rows. No AuditScope enum/boolean on core entity; marker persists. | Same simple tenant_id equality; ambiguous null rows excluded. | All rows; a marker lookup identifies unknown versus known-global null. | Extra table/reader projection/migration work; preserves originals and truthful interpretation. No actor/current-state inference. | Yes: persistent legacy provenance records, no new ownership scope; not literally the nullable-field-only model at the complete read-model level. |
| **L3: overloaded legacy null without a marker** | Only nullable tenant_id; insufficient old rows kept null and GLOBAL-ADMIN-only; explicitly relax null's meaning to global **or unknown legacy**. | Known own-Tenant records only; no cross-Tenant leakage from ambiguous null. | All records; cannot uniformly assert null means global or reliably distinguish legacy unknown from known global using this field. | Smallest schema, but loses the preferred semantic guarantee unless a separately reliable provenance source is approved. | No explicit extra column/state, but a permanent ambiguous historical meaning. **Not recommended** under the stated invariant. |

Recommend **L1 with deterministic backfill wherever the row has evidence**, and unknown legacy rows visible to GLOBAL ADMIN only. This is a provenance exception for existing omissions; it does not add AuditScope, infer attribution, duplicate events or change resource authorization. It is **not yet approved**. If the user rejects any legacy provenance representation, the strict two-value Tenant/global meaning cannot safely cover insufficient historical rows; report that conflict rather than labeling them global. Do not delete/archive away existing audit history or guess ownership to avoid the decision.

Before a future migration, inventory actual action/payload variants in the target database and report sanitized classification counts. Unexpected/malformed/unsupported action records must not default to known global; AuditService's open string contract permits them structurally even though no other supported production writer was found. This is not a claim that such records currently exist.

## 11. Query-security validation

The accepted read matrix works directly with captured attribution:

- ACTIVE GLOBAL ADMIN: all rows, known global/null and all Tenant UUIDs, plus any explicitly identified legacy-unattributed rows.
- ACTIVE TENANT ADMIN: only `audit_log.tenant_id = authenticatedTenantId`.
- GLOBAL USER, TENANT USER and PENDING: reject before query/count. Explicit DISABLED principals are rejected; preserve Phase 2's approved already-issued ACTIVE Access expiry window rather than introduce immediate per-request account revocation.

Query scoping must be part of **both row selection and count SQL before pagination**, including detail-by-ID (`WHERE id = :id AND tenant_id = :authenticatedTenant`). No fetch-all/Java filtering, no default inclusion of global records for Tenant admins and no actor/target joins. Tenant queries use the authenticated Tenant, never a request parameter to widen it. Tenant/action/target filters only narrow the authorized predicate. Out-of-scope detail and absent detail should have the same not-found response; unavailable-filter values must not expose other-Tenant existence or counts.

GLOBAL ADMIN may optionally filter by Tenant UUID or known global records for usability, as an **API choice**. Such a filter never changes authority. With L1, a global-only filter must use tenant_id null **and legacy_unattributed=false**; legacy-unattributed is distinct in presentation/filtering. Rows/counts use identical predicates. A future `(tenant_id,occurred_at,id)` index can support scoped ordered reads; this is analysis, not a proposed migration applied now. No configured RBAC or organizational hierarchy is needed.

## 12. Tenant FK and historical retention

Current Tenant entity/table has only UUID/name, **no soft deletion, deletedAt, or deletion service/API**. Existing business/User/OIDC rows have Tenant FKs and normally retained data; Tenant deletion policy is not defined. It is therefore wrong to assert “Tenant is already soft-deleted” or silently introduce that policy.

A nullable AuditLog Tenant FK with NO ACTION/RESTRICT is technically safe for **current supported operations**; Tenant creation flushes first, and other targets already reference existing Tenant rows. It would prevent a future Tenant hard deletion while audit history refers to it, regardless of future privacy policy. CASCADE would erase audit; SET NULL would falsely turn Tenant history into global history. Neither preserves the attribution invariant. Deferral would only change transaction timing, not these retention consequences.

**Recommendation: store captured historical Tenant UUID as a value without a live Tenant FK**, consistent with KELIOS's separation of historical actor snapshots from mandatory live User records and its retained administrative history. New attribution is validated in supported service transactions from an existing loaded/created target/Tenant; structural current-resource FKs and trusted operation context already establish valid ownership. This preserves meaning if a later explicitly approved Tenant lifecycle removes the live row. Render UUID/stored historical context if the Tenant is unavailable; never rewrite attribution or infer names from actor.

An FK would remain a legitimate alternative if the user explicitly chooses the added deletion restriction. No-FK does not authorize Tenant deletion today, guarantee deliberate SQL correctness, or create a privacy policy. It is a technical retention recommendation for review, not an implemented architectural decision. Legacy Tenant UUIDs established by stored evidence remain historical even if a live Tenant lookup fails; a future FK backfill would otherwise require an additional missing-Tenant policy.

## 13. Recommended final invariant and remaining decisions

Proposed future Domain wording, **not applied to Domain**:

> Administrative audit attribution is a write-once snapshot of the audited target/resource's authoritative organizational ownership for the operation. The actor is recorded independently and never determines attribution. Existing-target operations capture pre-operation ownership; creation captures the new target's ownership. TENANT_CREATED belongs to the newly created Tenant; OIDC configuration belongs to its configured Tenant; publication belongs to the pre-publication Tenant; Category/Manufacturer Merge belongs to its pre-operation source. A known Tenant-owned operation records that Tenant UUID; known installation/global-owned operations record null. Attribution is never recomputed from current actor or target state. GLOBAL ADMIN can read all audit records; TENANT ADMIN reads only its own captured Tenant records; USER roles and PENDING have no audit access. Any approved legacy-unattributed representation remains separate from known global ownership and is GLOBAL-ADMIN-visible only.

Only **legacy handling requires a further product decision for this audit topic**: choose L1/L2 or explicitly relax null semantics via L3. Publication's pre-state, Merge's source, Tenant creation and the requested read roles fit the accepted direction without another ownership counterexample. The technical FK choice should be explicitly confirmed with the final design; no-FK is recommended above. Optional GLOBAL ADMIN filtering is an API choice, not an additional product authorization rule.

Forward nullable tenantId and no AuditScope are validated. Complete historical attribution with **only** nullable tenantId and strict null=global is not validated because of the identified missing evidence. After legacy/FK choices are approved, the eventual specification/plan update must identify a necessary additive attribution migration; this supersedes the Phase 3 plan's earlier no-schema/GLOBAL-ADMIN-only audit recommendation **only when separately authorized**, not through this analysis document.

No authoritative specification, Phase 3 plan, production code, migration, OpenAPI, frontend, tests, historical ADR, accepted evidence or implementation script was changed. No Phase 3 implementation, commit or push occurred.
