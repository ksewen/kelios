# KELIOS — Domain Specification (v1)

**Status:** Approved domain baseline. **Language:** English only.  
This document defines business invariants. `ARCHITECTURE.md` defines deployment and technical boundaries, including the mandatory **Apache Maven 3** backend build and Maven Wrapper. Build-tool selection is not a domain rule; do not duplicate its technical configuration here. If a business rule is missing, flag it rather than inventing it.

**Approved Phase 1 revision (2026-10-10):** The user's reviewed mutation rules below supersede the former requirement to merge Products. KELIOS v1 supports explicit Category/Manufacturer Merge only. This revision also approves reference-ID commands, registration inputs and tag allocation, minimal Tenant/User fields, historical actor snapshots, and rejection of state-machine self-transitions. The revised Phase 1 plan was subsequently explicitly approved by the user on 2026-10-10; later phases remain gated.

**Approved reference-membership clarification:** Every retained business row counts as a reference while its stored reference ID points to the target, regardless of lifecycle or soft deletion. Immutable AssetEvent/AuditLog snapshots are historical data, not relational references. This resolves the earlier open membership question; it introduces no new active/current-reference lifecycle concept.

**Approved Phase 2 clarification (2026-10-10):** The user approved D1–D5 and D7–D9 in the reviewed [Phase 2 plan](docs/plans/PHASE_2_PLAN.md), including revised deployment-side bootstrap/recovery semantics, GLOBAL credential/account rules, administrator self-protection, and the IServ/KELIOS lifecycle boundary. These replace the earlier one-time bootstrap and optional last-admin proposals; Phase 2 implementation was subsequently explicitly approved. D6 permits a documentation-supported adapter; the latest approved clarification distinguishes completed implementation/deterministic contract checks from pending real-IServ interoperability evidence when no provider environment is available. It changes no account or group-validation behavior. Phase 0/1 business decisions remain unchanged.

**Approved Phase 3 clarification (2026-10-10):** P1–P8 and the DEREGISTERED event convention are approved in the revised [Phase 3 plan](docs/plans/PHASE_3_PLAN.md). They clarify post-registration commands, independent loan/state behavior, Phase 3 deletion exposure and target-owned administrative audit. Phases 0–2 are accepted; their decisions remain unchanged. This specification/plan approval does not authorize Phase 3 implementation.

**Approved Phase 4 clarification:** Phases 0–3 are accepted. The user approved L1–L19, N1, M1 and S1 in the [Phase 4 plan](docs/plans/PHASE_4_PLAN.md), adding optional organization-assigned `localIdentifier` metadata while preserving all other identity/workflow/audit rules. Phase 4 also includes stabilization and frontend English/German presentation; localization changes no Domain/API machine values or user-entered data. Phase 4 implementation is **explicitly approved**; user acceptance remains pending implementation/evidence review.

## 1. Tenancy and authorization

One Kreis (global administrative scope), **not** a Tenant. A Tenant is an isolated asset-management scope, initially generally one IServ domain (KS/KBS share one Tenant; GS another). No multi-Kreise, Tenant hierarchy, or multi-Tenant membership.

```java
enum UserScope { GLOBAL, TENANT }
enum Role { ADMIN, USER }
enum AccountStatus { PENDING, ACTIVE, DISABLED }
```

A User belongs either to GLOBAL (`tenantId == null`) or to exactly one TENANT (`tenantId != null`); scope is explicit. Database CHECK:

```sql
(scope = 'GLOBAL' AND tenant_id IS NULL)
OR (scope = 'TENANT' AND tenant_id IS NOT NULL)
```

- **GLOBAL ADMIN:** manage all Tenants/assets, Tenant OIDC configuration, GLOBAL users, global Catalog, publication/merges; approve users in any Tenant; grant ADMIN.
- **GLOBAL USER:** read-only across all Tenants.
- **TENANT ADMIN:** manage assets, locations, owners and ordinary users in their Tenant; create Tenant Catalog entries; approve Tenant USERs; **cannot grant ADMIN or administer other admins/Tenants**.
- **TENANT USER:** read-only within own Tenant.

Backend must enforce scope, role, status and Tenant isolation. `PENDING` and `DISABLED` may not access business data. New IServ users are always PENDING; there is **no approval bypass configuration**.

Through normal authenticated administration, a GLOBAL ADMIN cannot perform destructive account-state/role/removal operations on itself: reject self deletion/removal, disable, and demotion from ADMIN. This is self-protection, not an invariant that at least one ACTIVE GLOBAL ADMIN must permanently remain. Another administrator may perform otherwise authorized operations; deployment-side recovery handles a zero-usable-admin installation. User deletion/anonymization policy remains deferred; this rule does not introduce a deletion workflow.

Phase 1 Tenant foundation: UUID `id` and required, non-blank `name`. Phase 1 User foundation: UUID `id`, explicit `scope`, `tenantId` consistent with scope, `role`, `status`, and required, non-blank `displayName`. Do not add email, username, passwords/local credentials, OIDC identity fields, tokens/sessions, or other authentication implementation in Phase 1.

## 2. Authentication domain

### GLOBAL local authentication and account creation

Local credentials exist only for GLOBAL users. Login identifiers are separate from email, display name and OIDC identity: 3–64 ASCII characters, letters/digits/`.`/`_`/`-`, canonicalized to lowercase and unique. GLOBAL local passwords contain 15–128 Unicode code points, are not trimmed, and require at least one uppercase letter, one lowercase letter, one digit and one special/non-alphanumeric character. There is no periodic mandatory rotation policy.

Apply this same password policy to bootstrap/recovery, GLOBAL-user creation, administrator credential creation/reset and own-password changes. GLOBAL users may change their password with current-password verification; GLOBAL ADMIN may create/reset credentials for GLOBAL users. No TENANT local credentials, generated temporary-password semantics, `passwordChangeRequired` state or forced first-login password-change workflow.

A GLOBAL ADMIN's explicit creation of a GLOBAL user constitutes approval and creates exactly `GLOBAL / USER / ACTIVE`, `tenantId=null`. Granting ADMIN is a separate privileged operation. An administrator/operator-provided initial password is the actual credential.

### Deployment-side GLOBAL ADMIN bootstrap/recovery

One deployment-side command serves both first installation and disaster recovery; it is not an HTTP/browser endpoint and never runs automatically at normal application startup. It may create an administrator only when a fresh authoritative count of **usable GLOBAL administrators is exactly zero**.

For this guard, a usable GLOBAL administrator has `scope=GLOBAL`, `role=ADMIN`, `status=ACTIVE`, and usable GLOBAL local credentials. Existing inactive administrators or administrators without usable credentials do not satisfy the guard. The zero-admin check and creation are atomic; concurrent recovery commands cannot both create administrators from the same zero-admin condition.

On success create exactly one `GLOBAL / ADMIN / ACTIVE`, `tenantId=null`, plus its GLOBAL credential and administrative AuditLog. If any usable GLOBAL administrator exists, refuse to create another. There is no permanent bootstrap-completed rule, permanent recovery credential/token, email recovery or separate recovery domain. The command can serve later recovery whenever the guard is satisfied; possession of the deployment/runtime administration boundary is required.

The operator supplies login identifier, required display name and password through a private mounted/input file. No hard-coded/default/generated reusable credential, plaintext/reversible password storage, password command-line argument or secret in logs/source/images/migrations/frontend configuration/evidence. Store only the normal password hash; remove/unmount the input secret operationally. No forced first-login password change is required.

### Tenant OIDC, groups and KELIOS lifecycle

TENANT users authenticate with per-Tenant IServ OIDC configured by GLOBAL ADMIN in KELIOS's database. Identify external users by verified `(issuer, subject)`, never email, username or display name. Per-Tenant allowed-group eligibility requires a non-empty intersection with securely determined IServ groups; missing/ambiguous/unavailable group information fails closed. The exact integration contract must be supported by authoritative provider evidence, not guessed. Real-provider interoperability verification remains required for full IServ compatibility. IServ groups/roles never automatically grant KELIOS ADMIN.

On first allowed OIDC login create exactly `scope=TENANT`, the verified provider's Tenant, `role=USER`, `status=PENDING`. Approval is mandatory and cannot be configured away. No primary IServ user import. Tenant admins may approve their own Tenant's ordinary USERs; only GLOBAL ADMIN grants ADMIN.

Group eligibility is checked during OIDC authentication/login, including later OIDC logins. After KELIOS provisioning there is no continuous synchronization of IServ account/group/role state. Later IServ group removal, role changes or Tenant allowlist changes do not automatically disable the KELIOS User or invalidate KELIOS JWTs. Removing KELIOS access requires an authorized KELIOS administrator to set the User to DISABLED. Refresh never queries IServ and requires no stored provider tokens or provider session.

### KELIOS JWT behavior

Pure JWT: short-lived access token and long-lived refresh token with an absolute expiry that refresh does not extend. PENDING users receive restricted JWTs and minimal authentication/status interactions plus a contact-administrator message, with no business data. Logout discards tokens; no server revocation state. Refresh reloads current KELIOS User/scope/role/status/Tenant, refuses DISABLED, and uses current permissions for newly issued access tokens. Already-issued access tokens expire naturally; the interval after disable/demotion is accepted.

A copied refresh token can remain usable until absolute expiry unless the current User is DISABLED or its signing key is invalid. Password changes do not independently revoke issued JWTs. No Redis, blacklist, server-side token/session table, `authVersion`, `tokensValidAfter`, per-device sessions, Refresh-time group revalidation or background IServ synchronization.

## 3. Catalog

Distinct entities:

- **Category:** flat equipment type, e.g. Notebook, Beamer, Switch, Dongle.
- **Manufacturer:** flat manufacturer name, e.g. Lenovo, Epson.
- **Product:** specific identifiable product, with `categoryId`, `manufacturerId`, `name` and optional `manufacturerNumber` (Hersteller-Nr., e.g. `21SK00EEGE`). Do not confuse product with an individual Asset.

Each Category, Manufacturer and Product has `CatalogVisibility { GLOBAL, TENANT }`, `tenantId` (null for GLOBAL; non-null for TENANT), ID, and soft-delete metadata. All Tenants can use GLOBAL entries; Tenant entries are usable only within their Tenant. Tenant admins can create/edit their Tenant entries within the mutation guards below; GLOBAL ADMIN manages global entries within those same guards.

**Publish/Freigeben:** GLOBAL ADMIN changes an existing Tenant entry to GLOBAL, retaining its ID (no copy). A GLOBAL Product must reference GLOBAL Category and GLOBAL Manufacturer. A Tenant Product may reference GLOBAL or same-Tenant Category/Manufacturer. Validate reference visibility on creation, update, publication, and merge.

### Mutation guards and independent operations

Edit, soft deletion, and Merge are independent operations; none implicitly invokes another. Publication remains a separate operation. Equal/similar names do not impose uniqueness, trigger duplicate detection, or cause automatic Merge. Administrators explicitly decide whether entries should be merged. Renaming `Lenvo` to `Lenovo` does not merge Manufacturers with equal names.

- **Category:** ordinary edit and soft deletion require no current business references. Reject either operation if referenced; the user must explicitly resolve references first. Explicit human-controlled Merge is supported.
- **Manufacturer:** the same edit/deletion/reference policy as Category. Explicit human-controlled Merge is supported.
- **Product:** once ever referenced by an Asset registration, ordinary editing is permanently prohibited, even after relevant relationships have been resolved. Product edits must never implicitly mutate Asset facts. Soft deletion is prohibited while any current Asset references the Product; once there are no such references, normal soft deletion is allowed. **Product Merge is not part of v1.** Do not automatically rewrite Assets to resolve duplicate Products. The permanent edit guard does not turn approved Category/Manufacturer Merge reference redirection into an ordinary Product edit, and does not prohibit separately validated publication.

**Manual Merge/Zusammenführen:** GLOBAL ADMIN explicitly selects Category or Manufacturer source and target IDs. Redirect all current references transactionally, including Product and Asset references where applicable; preserve target, soft-delete source, and create AuditLog. Validate visibility and all dependent Product/Asset invariants; reject invalid merges. An affected Asset gets an immutable `UPDATED` event with before/after reference IDs and display snapshots in the same transaction. All reference redirection, AssetEvents, source soft deletion, and AuditLog are atomic. No Product, Owner, or Location Merge exists in v1.

### Retained-reference membership

For every edit/deletion guard and Category/Manufacturer Merge scan, a reference exists whenever a retained business row stores the target's reference ID. REGISTERED, IN_STOCK, DEPLOYED, and DEREGISTERED Assets all count. Soft-deleted retained Assets and Products also count. Do not filter these scans by lifecycle or `deletedAt`, and do not create a separate active/current-reference status.

References must be explicitly resolved when a guard requires no references. Deregistration and soft deletion do not implicitly clear or deactivate stored Location, Owner, Category, Manufacturer, or Product IDs. Merge includes all retained Product/Asset rows storing the source ID; redirected retained Assets receive the same required UPDATED history as other Assets. Immutable AssetEvent and AuditLog snapshots do not count as relational references and are never rewritten by Merge.

System-managed, undeletable, unrenameable GLOBAL `UNKNOWN` entries exist for **Category** and **Manufacturer**; they cannot be merge sources. No generic UNKNOWN Product. `Product` may be absent from Asset.

Product v1:

```java
class Product {
  UUID id;
  UUID categoryId;           // required
  UUID manufacturerId;       // required; UNKNOWN is an entry
  String name;               // required
  String manufacturerNumber; // nullable
  CatalogVisibility visibility;
  UUID tenantId;             // null iff GLOBAL
  Instant deletedAt;
  UUID deletedBy;
}
```

Do not add a Category hierarchy, PIM specifications, supplier/price fields, or automatic uniqueness assumptions based on names/manufacturer numbers.

## 4. Asset identity and registration

Each Asset is a **registration** in exactly one Tenant, not a universal PhysicalDevice. It receives a system-generated UUID and immutable, KELIOS-generated human-readable `assetTag` in approved format `yyyyMMdd-NNN`: UTC registration date plus application-wide atomic daily sequence, with a minimum of three sequence digits that expands beyond 999. Protect uniqueness in PostgreSQL. Sequence gaps are acceptable; do not promise gapless allocation. Manufacturer `serialNumber` is nullable and **not unique**. Never generate manufacturer SN or treat matching SNs as proof of identity.

Cross-Tenant transfer: deregister old Asset and register a **new** Asset in receiving Tenant with new UUID/tag; optionally same SN. Destination `localIdentifier` defaults to null when omitted; the destination operator may explicitly enter it through normal registration. Never automatically copy it or introduce a transfer/copy linkage. No PhysicalDevice or cross-registration linkage. GLOBAL ADMIN may search SN across Tenants; Tenant users see only their Tenant's matches.

```java
class Asset {
  UUID id;
  UUID tenantId;                 // required, immutable
  String assetTag;               // required, immutable, generated
  String localIdentifier;        // nullable, organization-assigned, non-unique
  UUID categoryId;               // required
  UUID manufacturerId;           // required
  UUID productId;                // nullable
  String serialNumber;           // nullable, non-unique
  String externalInventoryNumber;// nullable; distinct from assetTag
  UUID locationId;               // required, same Tenant
  UUID ownerId;                  // nullable, same Tenant if present
  LifecycleState lifecycle;      // required
  ConditionState condition;      // required
  boolean loanable;
  String loanedTo;               // nullable
  Instant loanedAt;              // nullable
  Instant expectedReturnAt;      // nullable
  String loanNote;               // nullable
  String note;                   // nullable
  long version;                  // optimistic locking
  Instant createdAt;
  UUID createdBy;
  Instant updatedAt;
  UUID updatedBy;
  Instant deletedAt;
  UUID deletedBy;
}
```

When `productId` is present, `Asset.categoryId == Product.categoryId` and `Asset.manufacturerId == Product.manufacturerId`. Product, Category and Manufacturer must be visible to the Asset's Tenant. On registration the backend loads the Product and derives Category/Manufacturer; the caller does not supply redundant Category/Manufacturer IDs alongside it. Without a Product, Category ID and Manufacturer ID are explicit required reference inputs. `loanable` is explicit input; registration cannot start with an active loan. Normalize blank optional text to null; required text remains non-blank. The UI may display derived facts, but the backend is authoritative. Do not add an Asset `name` field without a new decision.

### Asset identifier distinctions and localIdentifier

| Field | Meaning |
|---|---|
| UUID `id` | Internal persistent registration identity. |
| `assetTag` | Immutable KELIOS-generated human-readable identity, with the existing UTC `yyyyMMdd-NNN` rules unchanged. |
| `localIdentifier` | Optional Tenant/organization-assigned human operational identifier or label according to the organization’s own convention, such as `R421-PC7` or `Lehrer-NV`. A specific operational label, not a generic Asset descriptive name. |
| `serialNumber` | Nullable, non-unique external/device/manufacturer serial, never generated by KELIOS. |
| `externalInventoryNumber` | Existing nullable external inventory field, independent of every other identifier; not redefined by localIdentifier. |

`localIdentifier` is optional during either registration variant, may be edited/cleared afterward, and is **not unique**. Duplicates are intentionally allowed within/across Tenants, in every Lifecycle and retained/internal soft-deletion state. UUID and Asset Tag distinguish registrations. No uniqueness constraint, duplicate precheck, allocation/reservation, naming service or normalized uniqueness key.

Use existing Java optional-text normalization: null/blank becomes null; strip outer whitespace, preserve stored letter case and internal whitespace; no collapsing, lowercasing, uppercasing or additional Unicode normalization. After stripping, at most **128 Unicode code points**, not UTF-16 code units. Allow Unicode human text, including letters, numbers, punctuation and spaces; reject Unicode General Category **Cc**, U+2028 LINE SEPARATOR and U+2029 PARAGRAPH SEPARATOR in the normalized value. Backend validation is authoritative; the value is data, never HTML. Registration omission means null.

DEREGISTERED and internal soft deletion retain the value and history; no automatic clearing, new terminal metadata-edit freeze or user-facing deletion workflow. `localIdentifier` and `externalInventoryNumber` are completely independent: no copying, fallback, mutual exclusion, coupled uniqueness or inference from each other.

Asset `q` search includes Asset Tag, localIdentifier, serial number and external inventory number as bounded literal case-insensitive substring matches; stored case is unchanged. No dedicated localIdentifier filter, sort or index in Phase 4. In the UI, Local identifier is a separate secondary human label; KELIOS Asset Tag remains the primary KELIOS identity. Localization never translates identifier values or changes Domain/API property names. KELIOS still has no generic Asset `name`.

### Post-registration Asset commands

Ordinary text editing is a replacement command for `serialNumber`, `externalInventoryNumber`, `note` and `localIdentifier`, including optional clearing under the existing normalization rules. Omitted nullable fields and explicit null both clear their values; this is not PATCH semantics. The frontend submits the complete current four-field state. LocalIdentifier edits use expectedVersion / Asset optimistic locking and one atomic `UPDATED` event with authoritative before/after snapshots. There is no new event type or administrative AuditLog action for ordinary identifier editing. All newly produced authoritative Asset state snapshots, including `REGISTERED` and `UPDATED`, contain localIdentifier consistently. Older immutable events are not rewritten: a missing key means not captured historically and must not be inferred from current Asset state. Catalog reassignment is a separate explicit operation: select a Product by ID and derive Category/Manufacturer server-side, or select required Category/Manufacturer IDs without a Product. Preserve visibility, dependency and new-assignment guards. UUID, Tenant and generated Asset Tag remain immutable.

Loanability may be changed after registration through a separate explicit business command **only when no current outstanding loan exists**. If a loan exists, reject the loanability change. Do not include loanability in arbitrary Asset patching. Text editing, Catalog reassignment and loanability changes use optimistic locking and create `UPDATED` AssetEvent with before/after snapshots atomically. Lifecycle, Condition, Location, Owner and loan/return retain their dedicated operations.

## 5. Location and Owner

**Location:** Tenant-managed flat controlled list, generally rooms but may contain `IT-Lager`, `Ausgeliehen`, `Unbekannt`. `Asset.locationId` is required; if unknown, the Tenant explicitly creates/selects an `Unbekannt` Location. No special location enum, hierarchy, or automatic Lifecycle behavior. Validate same Tenant and active reference on new assignment.

Location is a mutable label identified by stable ID: editing is allowed while Assets reference it. Existing Assets continue to reference that ID and reflect its current display data. Editing Location metadata is not an Asset location change and creates no `LOCATION_CHANGED` AssetEvent when `locationId` stays unchanged. Soft deletion is prohibited while any current Asset references the Location. No Location Merge.

For example, a DEREGISTERED Asset with `locationId` pointing to `Raum 211` still blocks deletion of that Location. Its reference must first be explicitly changed to another valid Location. Lifecycle and Location remain independent.

**Owner:** Tenant-managed flat controlled list; metadata only, **not** authorization or Tenant ownership. `Asset.ownerId` nullable; some devices have no owner. Validate same Tenant if present.

Owner edit and soft deletion require no current Asset references. Reject a referenced Owner edit/deletion; the user must explicitly resolve references first. No Owner Merge.

## 6. Lifecycle and Condition

```java
enum LifecycleState { REGISTERED, IN_STOCK, DEPLOYED, DEREGISTERED }
enum ConditionState { UNKNOWN, WORKING, DEFECTIVE, UNUSABLE }
```

Lifecycle initial `REGISTERED`. `IN_STOCK` means not actively deployed, **not necessarily physically stored**. `DEPLOYED` means in service. `DEREGISTERED` is terminal; deregistration requires a separately recorded structured reason: TRANSFER, DISPOSAL, LOST or ERROR.

Allowed Lifecycle transitions:

- `REGISTERED -> IN_STOCK | DEPLOYED | DEREGISTERED`
- `IN_STOCK -> REGISTERED | DEPLOYED | DEREGISTERED`
- `DEPLOYED -> REGISTERED | IN_STOCK | DEREGISTERED`
- No outgoing transition from `DEREGISTERED`.

Condition initial `UNKNOWN`. `DEFECTIVE` means potentially repairable; `UNUSABLE` means irreparable/economically unjustifiable and is **terminal**.

Allowed Condition transitions:

- `UNKNOWN -> WORKING | DEFECTIVE | UNUSABLE`
- `WORKING -> DEFECTIVE | UNUSABLE`
- `DEFECTIVE -> WORKING | UNUSABLE`
- No outgoing transition from `UNUSABLE`.

Lifecycle, Condition, Location, and Owner are independent concepts. Repair in v1 is only a Condition change; **no RepairOrder/workflow**.

Reject same-state transitions for both graphs. There are no self-edges and no implicit no-op transitions.

An outstanding loan does not restrict or modify either graph. Every otherwise-valid Lifecycle or Condition transition remains valid while a loan exists. Transitions never automatically return the Asset, clear loan fields, or change Location. An Asset may validly become DEREGISTERED (for example with reason LOST) or UNUSABLE while still on loan. Deregistration retains Category, Manufacturer, Product, Location and Owner references; terminal lifecycle does not make Condition, reference changes or Return implicitly terminal. Return remains possible whenever the loan exists (§7).

## 7. Loan (embedded in Asset)

No separate Loan entity in v1. `loanedTo` is free text (teacher, student, group, external person), not a User FK. Loan issuance requires `loanable=true`, `lifecycle=DEPLOYED`, `condition=WORKING`, `loanedTo=null`. A current outstanding loan exists when `loanedTo` is non-null; it sets borrower/time and optional expected return/note. When `loanedTo=null`, all other current loan fields are null.

Return requires **exactly a current outstanding loan**. Reject Return without a loan as a conflict. Do not reapply issuance guards: Return does not require loanability, DEPLOYED lifecycle or WORKING condition, including after deregistration or UNUSABLE. Return explicitly clears `loanedTo`, `loanedAt`, `expectedReturnAt` and `loanNote`, and creates `RETURNED` atomically using optimistic locking. Loan creates `LOANED` atomically using optimistic locking.

Loan/return never automatically changes Location, Lifecycle or Condition. Conversely, approved Lifecycle/Condition transitions neither automatically return nor clear a loan (§6). The separate loanability-change guard in §4 does not become a Return guard.

## 8. AssetEvent and AuditLog

Asset is current state; **immutable AssetEvent** is business history. Examples: REGISTERED, LIFECYCLE_CHANGED, CONDITION_CHANGED, LOCATION_CHANGED, OWNER_CHANGED, LOANED, RETURNED, DEREGISTERED, UPDATED. Suggested event fields: `id`, `assetId`, `tenantId`, `type`, `occurredAt`, `createdAt`, `actorId`, `actorDisplayName` snapshot, `data` JSONB (before/after and human-readable snapshots), optional note. Preserve historical meaning after user/location/owner rename or soft deletion. Asset mutation and event are atomic.

A normal non-terminal Lifecycle transition creates `LIFECYCLE_CHANGED`. A transition into DEREGISTERED creates **exactly one `DEREGISTERED` event**, containing lifecycle before/after, required structured reason and approved context/snapshots. Do not also create LIFECYCLE_CHANGED for that action. One explicit business action produces one corresponding business event.

**AuditLog** separately records administrative/system actions, including successful TENANT_CREATED, OIDC_CONFIG_CHANGED, USER_CREATED, USER_APPROVED, USER_ROLE_CHANGED, USER_DISABLED, LOCAL_CREDENTIAL_CHANGED and GLOBAL_ADMIN_BOOTSTRAPPED. The latter covers both first installation and later zero-usable-admin recovery. Existing Catalog examples include CATEGORY_CREATED/PUBLISHED/MERGED, MANUFACTURER_MERGED and PRODUCT_PUBLISHED. Include actor, action, target type/id, timestamp, necessary context. Do not substitute AuditLog for AssetEvent. There is no PRODUCT_MERGED action or Product Merge requirement in v1.

Phase 2 AuditLog contains sanitized administrative context only: no plaintext passwords, password hashes, JWTs, OIDC authorization codes/client secrets, encryption keys, cookies or raw provider claims/group payloads. Login successes/failures do not create permanent per-login AuditLog entries; bounded redacted operational logging is distinct and does not introduce a permanent security-log domain or retention policy.

AssetEvent and AuditLog retain actor UUID plus required actor display-name snapshot sufficient for historical interpretation. Immutable history must not depend on a mandatory live User record. User deletion/anonymization/DSGVO policy remains deferred; do not invent it or prematurely constrain it with mandatory historical actor-to-User foreign keys.

Category/Manufacturer Merge reference changes append `UPDATED` AssetEvents with sufficient before/after IDs and display snapshots. Do not introduce a merge-specific AssetEvent type. Merge AuditLog and all resulting events and reference changes commit together. Location metadata edits do not change Asset reference IDs and do not produce `LOCATION_CHANGED` events.

### Administrative audit organizational attribution and visibility

AuditLog organizational attribution is an immutable snapshot of the audited **target/resource's authoritative ownership for the operation**. Persist nullable `tenantId`: a UUID means known ownership by that Tenant; null means known installation/global ownership. These are the only two meanings; there is no AuditScope enum or unattributed state. Record actor UUID/display snapshot independently. Never infer attribution from actor scope/Tenant/role/status, current target ownership or Catalog visibility, current User state, mutable labels, arbitrary JSON or request-supplied audit facts. Never recompute or change captured attribution after a later target change.

Creation captures the authoritative newly created target's ownership. Existing-target mutation captures **pre-operation ownership**:

| Audited operation | Captured ownership |
|---|---|
| TENANT_CREATED | Newly created Tenant UUID; its future Tenant Admin can see its creation history. |
| OIDC_CONFIG_CHANGED | Configured Tenant UUID, even when the actor is GLOBAL ADMIN. |
| User creation/approval/role change/disable | Created or loaded target User's explicit scope/Tenant: TENANT target's UUID, GLOBAL target null. JIT remains TENANT/USER/PENDING; no ownership transfer is introduced. |
| GLOBAL credential change / GLOBAL administrator bootstrap or recovery | Known GLOBAL target: null. |
| Catalog creation/edit/deletion | Created target or loaded pre-operation resource: GLOBAL null, TENANT its Tenant UUID. |
| Location/Owner creation/edit/deletion | Owning Tenant UUID. |
| Category/Manufacturer/Product publication | Resource's **pre-publication Tenant UUID**; earlier Tenant audits retain that UUID, later GLOBAL operations record null. Same-ID promotion does not rewrite history. |
| Category/Manufacturer Merge | **Source's pre-operation ownership**, because source is the administrative audited target: source Tenant UUID or GLOBAL/null. |

Merge creates one administrative AuditLog, not one per indirectly affected Tenant. Affected Assets retain their own UPDATED AssetEvents. Tenant Admin sees the Merge AuditLog only if the source was owned by its Tenant; indirect Asset effects remain visible through authorized Asset history. Do not change legal Merge combinations for audit visibility or duplicate ordinary Asset business events into AuditLog. All administrative mutations and their required audit remain atomic. A supported writer unable to establish known GLOBAL or a specific Tenant ownership is a design error; it must not silently record ambiguous null attribution.

**Audit reads:** ACTIVE GLOBAL ADMIN may read all global and Tenant AuditLog records. ACTIVE TENANT ADMIN may read only records with captured `tenantId` equal to its authenticated Tenant, including detail and paginated counts. GLOBAL USER, TENANT USER and PENDING have no audit access. Out-of-scope detail must not reveal another Tenant's record existence. Audit visibility uses captured ownership, not live actor/target joins. Preserve §2 JWT snapshots and natural Access expiry; no new per-business-request revocation semantics.

## 9. Deletion and deferred features

Business entities (Asset, Location, Owner, Category, Manufacturer, Product) use soft deletion (`deletedAt`, `deletedBy`), preserving historical references. Soft-deleted entries are not selectable for new assignments. User/Identity deletion follows separate privacy requirements.

Soft deletion is subject to the entity-specific reference guards in §§3 and 5. It does not bypass edit restrictions, imply Merge, or automatically reassign references.

Retained soft-deleted rows continue to participate in reference guards and Merge discovery whenever their stored IDs point to the target (§3); soft deletion is not reference removal.

Phase 3 exposes **no user-facing Asset soft-deletion operation**. Keep the existing internal Asset soft-deletion persistence capability and retained-reference semantics; do not add deleted-Asset mutation, undelete or hard-delete workflows. DEREGISTERED is the terminal business Lifecycle operation, distinct from soft deletion. It requires the structured reason and retains all references and any outstanding loan; it never implicitly deletes or returns the Asset.

- **Bulk Import:** deferred application feature, not a new domain; later CSV/XLSX preview/validation/import must call normal registration rules and generate UUID/tag server-side.
- **Labeling:** deferred. **No QR code required**; printer integration unspecified. Do not build a label/printer subsystem yet.
- **Repair:** condition-state change only, no workflow.
- **Procurement:** out of scope (no Supplier, Offer, Price Comparison, Purchase Order, Invoice, Budget).

## 10. Domain acceptance invariants

- [ ] Every Asset belongs to exactly one Tenant; no cross-Tenant read/write leakage.
- [ ] User scope/tenant consistency is enforced by DB and backend.
- [ ] New IServ users are PENDING and cannot access business data until approved.
- [ ] Only GLOBAL ADMIN can grant ADMIN.
- [ ] GLOBAL/TENANT Catalog visibility and Product dependency rules are enforced.
- [ ] Category/Manufacturer merges redirect all current references atomically with UPDATED AssetEvents, source soft deletion, and AuditLog; Product Merge is absent.
- [ ] Category/Manufacturer/Owner edits and deletions reject current references; Product ordinary edits reject any prior Asset registration reference and Product deletion rejects current Asset references.
- [ ] Location edits preserve stable IDs without LOCATION_CHANGED events; referenced Location deletion is rejected; no Owner/Location Merge.
- [ ] Category/Manufacturer UNKNOWN entries exist and cannot be removed/renamed/merged away.
- [ ] Asset location/category/manufacturer are mandatory; Product/Owner are optional as specified.
- [ ] Manufacturer SN is nullable and non-unique; Asset UUID/tag are generated per registration using UTC `yyyyMMdd-NNN`, atomic daily allocation, and database uniqueness; gaps are permitted.
- [ ] Lifecycle and Condition transitions obey their separate state machines.
- [ ] Asset mutation and AssetEvent creation are atomic.
- [ ] No physical deletion of ordinary business data.
- [ ] All retained business reference IDs count, including DEREGISTERED and soft-deleted rows; history snapshots do not count; no lifecycle/deletion filter silently removes references from guards or Merge scans.
- [ ] Commands load reference IDs and derive authoritative facts; Product registration derives Category/Manufacturer server-side; loanable is explicit and no initial active loan exists.
- [ ] State graphs reject self-transitions; minimal Tenant/User records contain no Phase 1 authentication fields; actor UUID/display snapshots remain interpretable without live User records.
- [ ] localIdentifier is optional, non-unique and independent of UUID/tag/serial/external inventory fields; approved normalization, 128-code-point and character rules apply. Registration, replacement edit/clear, immutable new snapshots, q search and retained/transfer semantics preserve identity and Tenant isolation.
- [ ] Post-registration loanability changes require no outstanding loan and atomic UPDATED history; ordinary text and explicit Catalog edits preserve identity/version/trust boundaries.
- [ ] Outstanding loans leave both state graphs unchanged; deregistration retains loan/references and creates exactly one reason-bearing DEREGISTERED event.
- [ ] Return requires only a current loan, rejects absence as conflict and atomically clears all loan fields with RETURNED; no issuance guards are reapplied.
- [ ] Every administrative audit captures immutable known target ownership, using created/pre-operation ownership, pre-publication Tenant or Merge source; actor ownership never determines attribution and Merge does not fan out audit.
- [ ] GLOBAL ADMIN reads all administrative audit; TENANT ADMIN reads only its captured Tenant; USER/PENDING are denied without cross-Tenant detail/count leakage.
- [ ] Phase 3 has no user-facing Asset soft deletion, restore or hard deletion; internal retained-reference behavior remains intact.

## 11. Reference-ID commands and trust boundary

Application commands and future APIs accept reference IDs and genuine user-entered mutable values. The backend loads referenced entities and derives/validates authoritative facts rather than trusting or requiring caller-supplied copies of them.

- Change Location: Asset ID + Location ID; load both and validate.
- Change Owner: Asset ID + Owner ID (nullable to clear an optional Owner); load referenced records and validate.
- Publish Catalog: entry ID; load current visibility/dependencies.
- Merge Category/Manufacturer: source ID + target ID; load both and determine affected references and validity.
- Delete: target ID; load it and determine whether reference guards permit deletion.
- Register Asset: derive Category/Manufacturer from loaded Product when selected; otherwise require their IDs. Accept explicit `loanable` and actual mutable values such as localIdentifier, serial number, external inventory number, and note, under §4 normalization rules.
- Change loanability: Asset ID + desired loanable value + expected version; load Asset and reject if an outstanding loan exists.

Generated identities, Tenant relationships, visibility, dependency facts, reference counts, actor identity/snapshots, and audit/event snapshots are server-owned facts. Authentication supplies trusted actor context in Phase 2; callers cannot fabricate it through business command fields. This principle does not remove authorization or optimistic concurrency checks.

**Rule for Codex:** Do not invent missing domain policy. Raise a focused question or record an explicit unresolved decision.
