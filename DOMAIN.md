# KELIOS — Domain Specification (v1)

**Accepted implementation status:** Backend M1/Steps 2–9/I1 and later Tenant/OIDC/Tenant-read, lock-order, routing and conflict corrections are HUMAN ACCEPTED / CLOSED. Frontend A/B and F0–F6 with dependent subsets are closed. Current rules are in this Domain, [architecture](ARCHITECTURE.md) and [OpenAPI](docs/api/openapi.yaml); dated results and limits are in [backend](docs/history/backend-domain.md) and [frontend](docs/history/frontend-integration.md) history. [Remaining work](docs/plans/remaining-work.md) is the current backlog, not implementation authorization.

**Status:** Approved domain baseline. **Language:** English only.  
This document defines business invariants. `ARCHITECTURE.md` defines deployment and technical boundaries, including the mandatory **Apache Maven 3** backend build and Maven Wrapper. Build-tool selection is not a domain rule; do not duplicate its technical configuration here. If a business rule is missing, flag it rather than inventing it.

**Current normative contract:** consolidated human-approved Domain, identity and query decisions. Accepted Phase 0–4 and frontend A/B results are in compact [history](docs/history/) and ADRs; historical plans/results are not competing target contracts. Future [I2/X1 design](docs/plans/COLLECTION_QUERY_IMPLEMENTATION_PLAN.md) requires separate approval. No code/schema/runtime change is authorized by this specification.

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

- **GLOBAL ADMIN:** manage all Tenants/assets, Tenant OIDC configuration, GLOBAL users, GLOBAL and authorized Tenant-owned Catalog entries, publication/merges; approve users in any Tenant; grant ADMIN. Selecting an operating context does not itself grant Catalog write authority.
- **GLOBAL USER:** approved business reads across Tenants; no administration access.
- **TENANT ADMIN:** manage assets, locations, owners and permitted ordinary users in their signed Tenant; create Tenant Catalog entries; approve Tenant USERs; read own Tenant detail and manage own IServ/OIDC singleton including physical deletion. No Tenant collection/switching; **cannot grant ADMIN or administer other admins/Tenants**.
- **TENANT USER:** approved business reads within signed own Tenant; no administration access or Tenant switching.

Backend must enforce scope, role, status and Tenant isolation. `PENDING` and `DISABLED` may not access business data. New IServ users are always PENDING; there is **no approval bypass configuration**.

Through normal authenticated administration, a GLOBAL ADMIN cannot perform destructive account-state/role/removal operations on itself: reject self deletion/removal, disable, and demotion from ADMIN. This is self-protection, not an invariant that at least one ACTIVE GLOBAL ADMIN must permanently remain. Another administrator may perform otherwise authorized operations; deployment-side recovery handles a zero-usable-admin installation. GLOBAL ADMIN may atomically save changed GLOBAL Email and/or User role under the same target and recovery-guard rules as the existing individual commands; a combined Save records one sanitized audit event for each changed field and rolls back all changes on failure. TENANT ADMIN gains no Email, role-assignment or creation authority. User deletion/anonymization policy remains deferred; this rule does not introduce a deletion workflow.

Tenant has UUID `id` and required non-blank `name`. User has UUID `id`, explicit `scope`, scope-consistent `tenantId`, `role`, `status` and non-blank `displayName`. GLOBAL Email credentials and external identity mapping remain distinct concepts; no universal TENANT Email/username field is inferred. All persisted business Domains carry §13 audit metadata.

Application Service is authoritative for effective Tenant scope. For TENANT, it comes exclusively from authenticated identity/context; a shared DTO tenantId is not a Domain query criterion and must not be used or compared as proof of authority. For GLOBAL, absence permits only a supported authorized aggregate/global read; a supplied Tenant is validated narrowing. Tenant-owned writes require exactly one authorized concrete Tenant. Persisted target/reference ownership is validated independently of transport shape. No selected context changes identity, membership or role.

### Administration access

Administration capabilities require ACTIVE ADMIN; USER/PENDING/DISABLED have no User/Tenant/configuration/Audit administration access. GLOBAL ADMIN retains approved User administration and Tenant list/detail/create. TENANT ADMIN reads all Users in its signed Tenant and manages only permitted ordinary USERs, has no Tenant list/general administration, and may directly read only its own Tenant detail. Tenant rename/edit/delete remain undefined. TENANT ADMIN navigation opens its own detail, not a list. TENANT ADMIN/USER never select another Tenant; /api/v1/tenants coupling for such a selector is obsolete and must be removed. GLOBAL USER uses the unpaged Tenant collection for ordinary context discovery without administration authority; GLOBAL ADMIN retains administration. Minimal self-authentication and approved business association projections are not administrative resource access.

## 2. Authentication domain

### GLOBAL local authentication and account creation

Local credentials exist only for GLOBAL users. **Email replaces loginName** as the unique login identifier; uniqueness is case-insensitive, Email is editable and displayName remains a separate presentation value. There is no dual identifier mode, alias compatibility or development-data migration requirement. TENANT users receive no local Email/password login path.

A normal effective GLOBAL user requires Email verified, administrator approved and active status, plus the applicable usable credential. These validity conditions are distinct, even if the initial flow satisfies them together. At the current pre-delivery stage manually entered/bootstrap/admin-created Email is treated as verified; administrator-created users are approved by default and have GLOBAL scope, ACTIVE status and null Tenant. Creation role defaults to USER; a GLOBAL ADMIN may explicitly create ADMIN in the same guarded creation transaction. A manually edited Email follows the same pre-delivery verified treatment. Future real Email verification/delivery, Email reset and self-registration are separately gated; no delivery is required now. Refresh issuance respects current required validity; the accepted expiry of already-issued Access snapshots is preserved.

GLOBAL passwords have **minimum 12, maximum 128 characters**, at least one uppercase, lowercase, digit and special character. Preserve the existing Unicode code-point measure, exact untrimmed password content and Argon2id architecture. Apply policy independently on the backend to bootstrap/recovery, creation, admin credential reset and current-password-verified own change. Frontend creation/change forms visibly explain and validate policy. This composition policy is a product rule, not an EU statutory claim. No periodic mandatory rotation or forced-first-login password state.

### Deployment-side GLOBAL ADMIN bootstrap/recovery

Evolve the existing deployment-side command for initial installation and recovery around manually supplied administrator **Email**, displayName and private password input. Never add a second credential lifecycle, HTTP recovery endpoint or automatic startup bootstrap.

The singleton guard/check/create transaction permits creation only when zero usable GLOBAL administrators exist. Usability includes GLOBAL/ADMIN/ACTIVE, required verified/approved validity and a usable Email credential. Create exactly one verified, approved GLOBAL/ADMIN/ACTIVE User, credential and GLOBAL_ADMIN_BOOTSTRAPPED AuditLog atomically. Another usable administrator causes refusal. No permanent completion flag, reusable recovery secret or last-admin invariant; preserve normal destructive self-operation protection.

Operator inputs remain private mounted/input files; no passwords in arguments, ordinary environment variables, logs, source/images/migrations/public config or evidence. Store only the normal hash; remove/unmount transient password input operationally. Real Email recovery is future work, not a prerequisite for this non-web recovery mechanism.

### Tenant OIDC, groups and KELIOS lifecycle

TENANT users authenticate with per-Tenant IServ OIDC configured by ACTIVE GLOBAL ADMIN for an authorized concrete Tenant or ACTIVE TENANT ADMIN for its signed own Tenant in KELIOS's database. Identify external users by verified `(issuer, subject)`, never email, username or display name. Per-Tenant allowed-group eligibility requires a non-empty intersection with securely determined IServ groups; missing/ambiguous/unavailable group information fails closed. The exact integration contract must be supported by authoritative provider evidence, not guessed. Real-provider interoperability verification remains required for full IServ compatibility. IServ groups/roles never automatically grant KELIOS ADMIN.

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

Each Category, Manufacturer and Product has `CatalogVisibility { GLOBAL, TENANT }`, `tenantId` (null for GLOBAL; non-null for TENANT), ID, creation/update audit metadata and soft-delete metadata. All Tenants can use GLOBAL entries; Tenant entries are usable only within their Tenant. Tenant admins can create/edit their Tenant entries within the mutation guards below; GLOBAL ADMIN may manage GLOBAL and authorized concrete-Tenant entries within those same guards.

For Category, Manufacturer and Product collection reads, GLOBAL resource scope is distinct from Global operating context: an authorized GLOBAL principal's root collection contains active Global and Tenant-owned rows across authorized Tenants. A concrete-Tenant collection contains active Global and that Tenant's rows; a TENANT principal remains bounded to Global and signed-own rows. Optional visibility filtering narrows this already authorized set and never selects another Tenant. Normal collections still exclude soft-deleted rows. Their shared-catalog aggregate query is implemented through the existing root endpoints; no additional Catalog endpoint is introduced.

**Publish/Freigeben:** GLOBAL ADMIN changes an existing Tenant entry to GLOBAL, retaining its ID (no copy). A GLOBAL Product must reference GLOBAL Category and GLOBAL Manufacturer. A Tenant Product may reference GLOBAL or same-Tenant Category/Manufacturer. Validate reference visibility on creation, update, publication, and merge.

### Mutation guards and independent operations

Edit, soft deletion, and Merge are independent operations; none implicitly invokes another. Publication remains separately guarded and is never implicit. A GLOBAL ADMIN may select publication as pending intent in a Category, Manufacturer or Product edit; Save then applies any changed ordinary fields and publication in one transaction, or neither on failure. Unchanged ordinary fields in a publication-only Save do not invoke the ordinary edit guard or record an EDITED AuditLog. Existing explicit publish commands retain their semantics for API clients. Equal/similar names do not impose uniqueness, trigger duplicate detection, or cause automatic Merge. Administrators explicitly decide whether entries should be merged. Renaming `Lenvo` to `Lenovo` does not merge Manufacturers with equal names.

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
  UUID createdBy;
  Instant createdAt;
  UUID updatedBy;
  Instant updatedAt;
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

When `productId` is present, `Asset.categoryId == Product.categoryId` and `Asset.manufacturerId == Product.manufacturerId`. Product, Category and Manufacturer must be visible to the Asset's Tenant. On registration the backend loads the Product and derives Category/Manufacturer; the caller does not supply redundant Category/Manufacturer IDs alongside it. Without a Product, Category ID and Manufacturer ID are explicit required reference inputs. This direct-reference registration reuses existing visible Category and Manufacturer UUIDs; it creates neither label nor a Product, and persists the Asset with those references and a null Product reference. The Asset Create dialog uses existing Product selection, while the direct-reference backend contract remains available independently. `loanable` is explicit input; registration cannot start with an active loan. Normalize blank optional text to null; required text remains non-blank. The UI may display derived facts, but the backend is authoritative. Do not add an Asset `name` field without a new decision.

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

Asset `q` search includes Asset Tag, localIdentifier, serial number and external inventory number as bounded literal case-insensitive substring matches; stored case is unchanged. A dedicated literal localIdentifier filter is part of §12; no localIdentifier sort or speculative index is approved. In the UI, Local identifier is a separate secondary human label; KELIOS Asset Tag remains the primary KELIOS identity. Localization never translates identifier values or changes Domain/API property names. KELIOS still has no generic Asset `name`.

### Post-registration Asset commands

Ordinary text editing is a replacement command for `serialNumber`, `externalInventoryNumber`, `note` and `localIdentifier`, including optional clearing under the existing normalization rules. Omitted nullable fields and explicit null both clear their values; this is not PATCH semantics. The frontend submits the complete current four-field state. LocalIdentifier edits use expectedVersion / Asset optimistic locking and one atomic `UPDATED` event with authoritative before/after snapshots. There is no new event type or administrative AuditLog action for ordinary identifier editing. All newly produced authoritative Asset state snapshots, including `REGISTERED` and `UPDATED`, contain localIdentifier consistently. Older immutable events are not rewritten: a missing key means not captured historically and must not be inferred from current Asset state. Catalog reassignment is a separate explicit operation: select a Product by ID and derive Category/Manufacturer server-side, or select required Category/Manufacturer IDs without a Product. Preserve visibility, dependency and new-assignment guards. UUID, Tenant and generated Asset Tag remain immutable.

Loanability may be changed after registration through a separate explicit business command **only when no current outstanding loan exists**. If a loan exists, reject the loanability change. Do not include loanability in arbitrary Asset patching. Text editing, Catalog reassignment and loanability changes use optimistic locking and create `UPDATED` AssetEvent with before/after snapshots atomically. Lifecycle, Condition, Location, Owner and loan/return retain their dedicated operations.

On a valid Lifecycle transition into `DEREGISTERED`, or Condition transition into `DEFECTIVE` or `UNUSABLE`, the same versioned mutation also sets `loanable=false`. This prospective side effect is one-way: other transitions preserve loanability, including repair from `DEFECTIVE` to `WORKING`. It is part of the triggering transition's single AssetEvent before/after snapshot, not a separate manual loanability command. It applies even with an outstanding loan; borrower and current-loan fields remain unchanged and Return remains possible. The manual command's outstanding-loan guard stays intact. This does not prohibit an otherwise authorized explicit `loanable=true` command while a blocking state is present, normalize existing rows, or change registration's initial Condition/loanability choice.

## 5. Location and Owner

**Location:** Tenant-managed flat controlled list, generally rooms but may contain `IT-Lager`, `Ausgeliehen`, `Unbekannt`. `Asset.locationId` is required; if unknown, the Tenant explicitly creates/selects an `Unbekannt` Location. No special location enum, hierarchy, or automatic Lifecycle behavior. Validate same Tenant and active reference on new assignment.

Location is a mutable label identified by stable ID: editing is allowed while Assets reference it. Existing Assets continue to reference that ID and reflect its current display data. Editing Location metadata is not an Asset location change and creates no `LOCATION_CHANGED` AssetEvent when `locationId` stays unchanged. Soft deletion is prohibited while any current Asset references the Location. No Location Merge. Location also has nullable user-editable `note`; it is metadata, not search/filter/sort input. Its implementation is a separately approved Domain field gate.

For example, a DEREGISTERED Asset with `locationId` pointing to `Raum 211` still blocks deletion of that Location. Its reference must first be explicitly changed to another valid Location. Lifecycle and Location remain independent.

**Owner:** Tenant-managed flat controlled list; metadata only, **not** authorization or Tenant ownership. `Asset.ownerId` nullable; some devices have no owner. Validate same Tenant if present.

Owner edit and soft deletion require no current Asset references. Reject a referenced Owner edit/deletion; the user must explicitly resolve references first. No Owner Merge. Owner also has nullable user-editable `note`; it follows the same referenced-Owner edit prohibition, with no note-only exception. It is not search/filter/sort input; implementation is separately gated.

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

Registration may set any defined initial Condition. Omitted initial Condition defaults to `UNKNOWN` for existing clients; explicit null or an unknown value is invalid. The selected Condition is part of the initial `REGISTERED` AssetEvent snapshot, not a post-registration transition. `DEFECTIVE` means potentially repairable; `UNUSABLE` means irreparable/economically unjustifiable and is **terminal**.

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

AuditLog contains sanitized administrative context only: no plaintext passwords, password hashes, JWTs, OIDC authorization codes/client secrets, encryption keys, cookies or raw provider claims/group payloads. Login successes/failures do not create permanent per-login AuditLog entries; bounded redacted operational logging is distinct and does not introduce a permanent security-log domain or retention policy.

AssetEvent and AuditLog retain actor UUID plus required historical displayName snapshot as the primary human-facing actor representation. No Email/login-identifier snapshot is required for display; never guess a TENANT username. Actor UUID remains authoritative for identity, correlation and exact filtering. Immutable history must not depend on a mandatory live User record. User deletion/anonymization/DSGVO policy remains deferred; do not invent it or prematurely constrain it with mandatory historical actor-to-User foreign keys.

Category/Manufacturer Merge reference changes append `UPDATED` AssetEvents with sufficient before/after IDs and display snapshots. Do not introduce a merge-specific AssetEvent type. Merge AuditLog and all resulting events and reference changes commit together. Location metadata edits do not change Asset reference IDs and do not produce `LOCATION_CHANGED` events.

### Administrative audit organizational attribution and visibility

AuditLog organizational attribution is an immutable snapshot of the audited **target/resource's authoritative ownership for the operation**. Persist nullable `tenantId`: a UUID means known ownership by that Tenant; null means known installation/global ownership. These are the only two meanings; there is no AuditScope enum or unattributed state. Record actor UUID/display snapshot independently. Never infer attribution from actor scope/Tenant/role/status, current target ownership or Catalog visibility, current User state, mutable labels, arbitrary JSON or request-supplied audit facts. Never recompute or change captured attribution after a later target change.

Creation captures the authoritative newly created target's ownership. Existing-target mutation captures **pre-operation ownership**:

| Audited operation | Captured ownership |
|---|---|
| TENANT_CREATED | Newly created Tenant UUID; its future Tenant Admin can see its creation history. |
| OIDC_CONFIG_CHANGED / OIDC_CONFIG_DELETED | Authoritative configured Tenant UUID, independently of GLOBAL or TENANT actor; DELETE captures ownership before physical removal. Exactly one successful deletion audit; no configuration secret material. |
| User creation/approval/role change/disable | Created or loaded target User's explicit scope/Tenant: TENANT target's UUID, GLOBAL target null. JIT remains TENANT/USER/PENDING; no ownership transfer is introduced. |
| GLOBAL credential change / GLOBAL administrator bootstrap or recovery | Known GLOBAL target: null. |
| Catalog creation/edit/deletion | Created target or loaded pre-operation resource: GLOBAL null, TENANT its Tenant UUID. |
| Location/Owner creation/edit/deletion | Owning Tenant UUID. |
| Category/Manufacturer/Product publication | Resource's **pre-publication Tenant UUID**; earlier Tenant audits retain that UUID, later GLOBAL operations record null. Same-ID promotion does not rewrite history. |
| Category/Manufacturer Merge | **Source's pre-operation ownership**, because source is the administrative audited target: source Tenant UUID or GLOBAL/null. |

Merge creates one administrative AuditLog, not one per indirectly affected Tenant. Affected Assets retain their own UPDATED AssetEvents. Tenant Admin sees the Merge AuditLog only if the source was owned by its Tenant; indirect Asset effects remain visible through authorized Asset history. Do not change legal Merge combinations for audit visibility or duplicate ordinary Asset business events into AuditLog. All administrative mutations and their required audit remain atomic. A supported writer unable to establish known GLOBAL or a specific Tenant ownership is a design error; it must not silently record ambiguous null attribution.

**Audit reads:** ACTIVE GLOBAL ADMIN may read all global and Tenant AuditLog records. ACTIVE TENANT ADMIN may read only records with captured `tenantId` equal to its authenticated Tenant, including detail and paginated counts. GLOBAL USER, TENANT USER and PENDING have no audit access. Out-of-scope detail must not reveal another Tenant's record existence. Audit visibility uses captured ownership, not live actor/target joins. Preserve §2 JWT snapshots and natural Access expiry; no new per-business-request revocation semantics.

## 9. Deletion and deferred features

Business entities (Asset, Location, Owner, Category, Manufacturer, Product) use soft deletion (`deletedAt`, `deletedBy`), preserving historical references. Soft-deleted records are excluded from all normal browser collections and reference searches, with no ordinary deleted=true/deleted=all filter and no automatic GLOBAL ADMIN repair exception. They are not selectable for new assignments. Immutable historical snapshots and retained-reference validation still include relevant retained evidence; dedicated internal/repair semantics are not normal browser query features. User/Identity deletion follows separate privacy requirements.

Soft deletion is subject to the entity-specific reference guards in §§3 and 5. It does not bypass edit restrictions, imply Merge, or automatically reassign references.

Retained soft-deleted rows continue to participate in reference guards and Merge discovery whenever their stored IDs point to the target (§3); soft deletion is not reference removal.

The current product exposes **no user-facing Asset soft-deletion operation**. Keep the existing internal Asset soft-deletion persistence capability and retained-reference semantics; do not add deleted-Asset mutation, undelete or hard-delete workflows. DEREGISTERED is the terminal business Lifecycle operation, distinct from soft deletion. It requires the structured reason and retains all references and any outstanding loan; it never implicitly deletes or returns the Asset.

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
- [ ] State graphs reject self-transitions; GLOBAL Email credentials and TENANT external identity remain separate; actor UUID/display snapshots remain interpretable without live User records.
- [ ] localIdentifier is optional, non-unique and independent of UUID/tag/serial/external inventory fields; approved normalization, 128-code-point and character rules apply. Registration, replacement edit/clear, immutable new snapshots, q search and retained/transfer semantics preserve identity and Tenant isolation.
- [ ] Post-registration loanability changes require no outstanding loan and atomic UPDATED history; ordinary text and explicit Catalog edits preserve identity/version/trust boundaries.
- [ ] Outstanding loans leave both state graphs unchanged; deregistration retains loan/references and creates exactly one reason-bearing DEREGISTERED event.
- [ ] Return requires only a current loan, rejects absence as conflict and atomically clears all loan fields with RETURNED; no issuance guards are reapplied.
- [ ] Every administrative audit captures immutable known target ownership, using created/pre-operation ownership, pre-publication Tenant or Merge source; actor ownership never determines attribution and Merge does not fan out audit.
- [ ] GLOBAL ADMIN reads all administrative audit; TENANT ADMIN reads only its captured Tenant; USER/PENDING are denied without cross-Tenant detail/count leakage.
- [ ] No user-facing Asset soft deletion, restore or hard deletion; internal retained-reference behavior remains intact.

## 11. Reference-ID commands and trust boundary

Application commands and future APIs accept reference IDs and genuine user-entered mutable values. The backend loads referenced entities and derives/validates authoritative facts rather than trusting or requiring caller-supplied copies of them.

- Change Location: Asset ID + Location ID; load both and validate.
- Change Owner: Asset ID + Owner ID (nullable to clear an optional Owner); load referenced records and validate.
- Publish Catalog: entry ID; load current visibility/dependencies.
- Merge Category/Manufacturer: source ID + target ID; load both and determine affected references and validity.
- Delete: target ID; load it and determine whether reference guards permit deletion.
- Register Asset: derive Category/Manufacturer from loaded Product when selected; otherwise require their IDs. Accept explicit `loanable` and actual mutable values such as localIdentifier, serial number, external inventory number, and note, under §4 normalization rules.
- Change loanability: Asset ID + desired loanable value + expected version; load Asset and reject if an outstanding loan exists.

Generated identities, Tenant relationships, visibility, dependency facts, reference counts, actor identity/snapshots, and audit/event snapshots are server-owned facts. Authentication supplies trusted actor context; callers cannot fabricate it through business command fields. This principle does not remove authorization or optimistic concurrency checks.

**Rule for Codex:** Do not invent missing domain policy. Raise a focused question or record an explicit unresolved decision.

The resource-command/detail matrix in §15 extends this boundary to all business resources, including User/Tenant/configuration commands. No DTO field may override server-owned ownership, actors, timestamps, deletion or audit facts.

## 12. Collection query contract

This is the current approved target, not a claim of implementation completion. Application Service determines authority before combined filters/search, full-result server ordering and SQL pagination. Current implementation gaps and separate gates are in [the implementation plan](docs/plans/COLLECTION_QUERY_IMPLEMENTATION_PLAN.md).

### Shared rules

Ordinary pageable collections use page 0/size 20 by default, backend sizes 1–100 and frontend choices **10/20/30/40/50/100**. Each response includes current page, size, scoped count and items (hasNext may be derived). Beyond-end requests return empty items and current metadata, not a different page. Omitted page/size never means unlimited.

Ordinary business default order is **createdAt DESC**; AuditLog/history use **occurredAt DESC**. A default field is not automatically client-selectable. Equal sort values require no application-level UUID secondary order. Existing extra SQL ordering is not a mandatory Domain contract. Reference search may order by readable name. No current-page client sorting/filtering.

Pending typed text and applied criteria are separate. Typed collection text sends no query until Search/Apply; page/sort use applied criteria. Reviewed immediate selectors (including Product Category and Catalog ownership) combine with current applied text and criteria as soon as they change. Advanced-filter dialog edits remain draft-only until Confirm; Cancel does not apply them. Applying changed search/filter/sort/size resets page 0. Collection Reset restores initial page, size, sort and local criteria, including applicable URL criteria, while preserving the top-level operating Tenant/context. Departure ends page query state; return starts defaults. This presentation state does not trigger a business dirty-write guard.

Search trims outer whitespace; all-whitespace means no search. `q` matches the listed fields with OR; structured predicates combine with AND. Ordinary text criteria use literal case-insensitive substring matching, escaping %, underscore and backslash. Use a practical consistent **128 UTF-16-code-unit limit after trimming** for query text (not stored business-field/password normalization); frontend constrains obviously overlong input and backend remains authoritative with normal validation errors. No fuzzy matching or inferred search of joined display labels.

Reference/search dropdowns use immediate server lookup and **20-item incremental batches**, with no ordinary page-size selector. Reaching the continuation boundary loads the next bounded page and appends UUID-keyed options while more results exist. No uncontrolled loop; deduplicate IDs without replacing existing semantic nodes. A query/context change invalidates the old candidate generation; late responses cannot append to the new generation. Preserve selected UUID separately from query/label and keep stale candidates non-actionable during replacement. Readable ordering is permitted for lookup, not the ordinary list default.

### Resource capability matrix

Pagination notation 0/20/100 means default page/default size/max size. Soft-deleted business rows are excluded without a deleted filter. `tenantId` below is GLOBAL-authorized narrowing only; TENANT effective scope never comes from that transport field.

| Resource | Pagination | Search fields | Structured filters | Selectable sorts | Default order |
|---|---|---|---|---|---|
| Category | REQUIRED 0/20/100 | name | visibility enum | name, visibility, tenantName | createdAt DESC |
| Manufacturer | REQUIRED 0/20/100 | name | visibility enum | name, visibility | createdAt DESC |
| Product | REQUIRED 0/20/100 | name, manufacturerNumber | visibility; categoryId/manufacturerId UUID; manufacturerNumber literal substring | name, manufacturerNumber, visibility | createdAt DESC |
| Location | REQUIRED 0/20/100 | name | GLOBAL authorized aggregate or concrete-Tenant scope; no ownership filter | name | createdAt DESC |
| Owner | REQUIRED 0/20/100 | name | GLOBAL authorized aggregate or concrete-Tenant scope; no ownership filter | name | createdAt DESC |
| Asset | REQUIRED 0/20/100 | assetTag, localIdentifier, serialNumber, externalInventoryNumber | lifecycle/condition enums; loaned/loanable booleans; locationId/ownerId/categoryId/manufacturerId/productId UUID; localIdentifier/serialNumber/externalInventoryNumber literal substring; authorized GLOBAL tenantId | assetTag, createdAt, lifecycle, condition | createdAt DESC |
| User | REQUIRED 0/20/100 | displayName | scope/role/status enums; authorized GLOBAL tenantId | displayName, scope, role, status | createdAt DESC |
| AuditLog | REQUIRED 0/20/100 | none | exact action/targetType; targetId/actorId UUID; optional inclusive from/until; authorized GLOBAL ADMIN tenantId/globalOnly narrowing, mutually exclusive | occurredAt ASC/DESC | occurredAt DESC |
| AssetEvent/history | REQUIRED 0/20/100 | none | owning Asset path + authoritative scope; no added filters | none | fixed occurredAt DESC |
| Tenant | INTENTIONALLY UNPAGED, ACTIVE GLOBAL ADMIN/USER | none on administration list | authoritative scope only; TA own detail, no list | none | createdAt DESC |
| IServ/OIDC configuration | GLOBAL ADMIN only; REQUIRED 0/20/100 | none | all existing configurations, including disabled | none | Tenant name ASC, Tenant ID ASC |
| Public organization routing lookup | bounded incremental batches 20 | organization name | server-owned enabled/configured inclusion | none | readable name ASC |

Product number participates in both q and its dedicated filter; blank optional stored values do not match nonblank text. Name/number are not unique identity. Product Category/Manufacturer filtering uses IDs, not joined-name search/sort. Manufacturer has no manufacturerNumber field.

Catalog reference visibility remains Global entries or same-Tenant entries only; a GLOBAL aggregate read never permits a foreign-Tenant association. Category, Manufacturer and Product root collections implement the shared-catalog aggregate rule through their existing endpoints. Location/Owner root collections aggregate authorized Tenant rows for GLOBAL principals and remain signed-own for TENANT principals; writes remain concrete-Tenant-only. GLOBAL aggregate Asset reads may narrow, but cannot mutate without a concrete authorized Tenant. DEREGISTERED is visible business state, not soft deletion. Reference-filter validation must preserve retained-history semantics without enabling assignment of deleted references. See the [resource-authority Plan](docs/plans/global-tenant-resource-authority.md) for implementation gates rather than treating target rules as completion evidence.

User administration keeps forced limits: ACTIVE GLOBAL ADMIN may narrow the authorized set; the approved ACTIVE TENANT ADMIN read target is Users of the signed Tenant, while existing mutation commands retain their narrower role/status/self-protection guards. The read implementation includes all roles within the signed Tenant; mutation guards remain narrower. A transport tenantId never determines or proves TENANT authority. Requested role/scope cannot remove the signed-Tenant service predicate. Ordinary readers/PENDING remain denied administrative collection access. Email belongs to GLOBAL credential/account design, not an added universal User search/sort or TENANT username. No generic displayName/profile edit is authorized without the [resource-authority Plan's](docs/plans/global-tenant-resource-authority.md#user-field-boundary-and-human-decision) human decision.

Audit authority is captured target Tenant attribution, not actor ownership, live target ownership or display name. Actor UUID filters do not grant access. For GLOBAL ADMIN, no organizational parameter means all authorized records; `globalOnly=true` means captured Global attribution (`tenantId` null); `tenantId` means that Tenant's captured attribution. Those two narrowing parameters are mutually exclusive. TENANT ADMIN remains forced to its signed Tenant. Date boundaries are independently optional and inclusive: from <= occurredAt <= until; reject reversed supplied bounds and malformed instants. No arbitrary maximum range duration or username dependency is introduced. Bounded query pagination remains required even with no date bound.

### Field-level boundary tables

All persisted business resources additionally carry §13 server-owned creation/update metadata. createdAt drives the default, but is not selectable except where the resource matrix permits it. IDs are reference identity, never automatic q fields or selectable sorts; no mandatory UUID ties. All unspecified fields are not exposed as query inputs.

| Category / Manufacturer field | Search/filter | Selectable sort | Boundary |
|---|---|---|---|
| name | q literal substring | yes | Readable label, not unique identity |
| visibility | exact enum | yes | Narrows authorized Catalog set |
| id | none | no | Stable reference identity, UNKNOWN guards retained |
| tenantId | service-owned scope | no | Not TENANT caller authority |
| deletedAt/deletedBy | none | no | Internal retention; excluded normal collections |
| manufacturerNumber (Manufacturer) | not a field | no | Product-only concept |

| Product field | Search/filter | Selectable sort | Boundary |
|---|---|---|---|
| name | q | yes | Literal substring |
| manufacturerNumber | q + dedicated literal substring | yes | Optional/nonunique; not Manufacturer field |
| visibility | exact enum | yes | Authorized set only |
| categoryId/manufacturerId | dedicated UUID references | no | Readable DTO names, ID-authoritative filters |
| Category.name/Manufacturer.name | none | no | Presentation/disambiguation only |
| id/tenantId/deletion metadata | identity/scope/exclusion only | no | Never authority from body/query |

| Location / Owner field | Search/filter | Selectable sort | Boundary |
|---|---|---|---|
| name | q | yes | Readable stable-ID resource |
| note | none | no | Nullable editing metadata; future field gate |
| id/tenantId | identity/authoritative scope only | no | Same-Tenant references |
| deletion metadata | none | no | No ordinary deleted-data UI |

| Asset field | Search/filter | Selectable sort | Boundary |
|---|---|---|---|
| assetTag | q | yes | Immutable generated tag; external API alias currently tag |
| localIdentifier/serialNumber/externalInventoryNumber | q + dedicated literal substring | no | Nonunique independent identifiers |
| lifecycle/condition | exact enums | yes | Machine-value order, not translated labels |
| createdAt | default ordering | yes | UTC registration instant |
| loanedTo | derived loaned boolean | no | No borrower-text/User-identity inference |
| loanable | exact boolean | no | Does not change loan/action guards |
| locationId/ownerId/categoryId/manufacturerId/productId | dedicated UUID | no | Visible/same-Tenant authoritative references |
| tenantId | authorized GLOBAL narrowing; TENANT service scope | no | No aggregate-write permission |
| associated readable names | none | no | Names displayed, IDs submitted; ambiguous names never resolved arbitrarily |
| id/version/update metadata/deletion metadata | none | no | Identity/concurrency/server-owned retention |
| note/loanNote/loanedAt/expectedReturnAt | none | no | No unapproved extra query capabilities |

| User field | Search/filter | Selectable sort | Boundary |
|---|---|---|---|
| displayName | q | yes | Human presentation, not username |
| scope/role/status | exact enums | yes | Intersect forced administrative scope |
| tenantId | authorized GLOBAL narrowing; TENANT scope server-owned | no | No Tenant.name sorting |
| Email/verified/approved validity | none in collection query | no | GLOBAL account validity, not TENANT local login |
| id/external issuer/subject | none | no | Immutable identity/exact external mapping |
| hashes/passwords/tokens/provider secrets | never | no | No secret projections |

| AuditLog field | Search/filter | Selectable sort | Boundary |
|---|---|---|---|
| occurredAt | optional inclusive from/until | yes | Timeline UTC instant |
| actorId/targetId | exact UUID | no | Immutable identities |
| action/targetType | exact machine strings | no | No guessed text-search semantics |
| tenantId | captured scope; GA narrowing | no | Null is known global attribution |
| actorDisplayName | none | no | Primary human-facing immutable snapshot |
| id/data/Email/login label | none | no | No UUID sort mandate, JSON DSL or login snapshot requirement |

| Other resource fields | Search/filter | Selectable sort | Boundary |
|---|---|---|---|
| Tenant id/name | scope / display only | no | Intentionally complete ACTIVE GLOBAL ADMIN/USER list for Schulträger; TA own detail only |
| Tenant createdAt | default ordering only | no | UTC audit metadata |
| Provider tenantId/name | authorized singleton path; GA list projection only | no | One configuration per Tenant; GA list has no caller Tenant filter |
| issuer/clientId/enabled/version | no collection query | no | Safe GA list and authorized singleton projections; disabled configurations remain listed |
| groups/callback/secretConfigured | no collection query | no | Authorized singleton projection only; excluded from GA list |
| provider secrets/keys/ciphertext | never | no | Secret lifecycle unchanged |
| AssetEvent assetId/tenantId | owning Asset + scope only | no | No cross-Asset collection |
| AssetEvent occurredAt | fixed ordering only | no | Timeline, no caller filter |
| AssetEvent id/type/actor/data/note/creation attribution | none | no | Immutable historical snapshots |
| Routing organization name | q | fixed readable order | Authentication routing only |
| Routing UUID | none | no | Handle, never membership authority |
| Enabled/configured routing status | server inclusion only | no | No disabled-provider enumeration |

## 13. Persistence metadata, association presentation and time

Persisted business Domains (including Tenant, User, Catalog, Location, Owner and Asset) carry **createdBy, createdAt, updatedBy, updatedAt**, in addition to logical deletion where applicable. Actor IDs come from the authoritative identity model; timestamps are absolute UTC instants supplied server-side. createdBy is an actor, never a timestamp. Inserts establish creation/update metadata; updates preserve creation and change update attribution. Immutable AuditLog/AssetEvent preserve appropriate creation/actor/time metadata without introducing an update path or altering snapshots. Internal counters/guard rows are technical persistence, not business Domain lists; system-owned bootstrap seeds require deliberate nonhuman attribution, never invented human authority.

Tenant, User, Category, Manufacturer, Product, Asset, Location and Owner persist an optional immutable `createdByDisplayName` alongside the authoritative creator UUID. New writes snapshot only a trusted actor display label actually available in that creation transaction; bootstrap and system writes use explicit system/seed attribution, and OIDC JIT creation without a provider display label leaves the snapshot null. Existing rows are not backfilled by guessing from current Users or Audit history. Null remains unresolved except for the reserved system actor's explicit label. Update-actor presentation remains separate and may resolve the current actor; AuditLog and AssetEvent historical snapshots do not change.

DTOs, application models, persistence entities and frontend projections may differ. Exposed associations include ID plus necessary readable values; ordinary UI displays name, selectors submit ID. Duplicate names need scope/other useful disambiguation; UUID may supplement when necessary. Do not eager-load full graphs for these lightweight projections or reflectively copy arbitrary request fields into entities.

Frontend displays absolute timestamps in client-local timezone and converts Audit Von/Bis date-and-time input to UTC instants. Inclusive from/until bounds are independently optional. The UI has no whole-day mode or date-only interpretation; users enter desired times explicitly. Backend timestamp/range semantics, timezone conversion and PostgreSQL microsecond precision remain unchanged.

## 14. Future export and development-data policy

Separately approved Excel export will produce the **complete authorized applied-filter result**, not the current frontend page. Backend reauthorizes, scopes and applies the same supported criteria; export uses readable associated business values, excludes secrets and handles spreadsheet-formula injection. Resource bounds, streaming/job shape and failure behavior require a future design gate. This supports uncommon secondary sorting/grouping/reporting without expanding interactive Asset sort capabilities indiscriminately.

KELIOS has no production business dataset requiring preservation. Future approved schema/identity changes may discard development data. No login-name compatibility, dual schema, backfills, dual reads/writes or transition machinery solely for that data. Migration files remain source history and are not casually rewritten; schema/reset procedures require separate approval. This documentation performs no reset, migration, delivery, export or implementation.

## 15. Resource command and detail-read contract

This complements §§11–14 without changing collection capabilities. **Target requirement is not implementation completion.** Each entity-like resource has authoritative detail by persistent UUID, separately from list search. Read authorization matches its collection's authorized set; knowledge of an ID is not authority. TENANT scope comes from authenticated identity/context and GLOBAL authority/narrowing follows §1. Readers cannot gain mutations from a detail page; PENDING/DISABLED and minimal self-status exceptions remain unchanged.

### Authoritative resource capability matrix

“Required” means Domain-approved target. “Undefined” does not authorize implementation. GA=ACTIVE GLOBAL ADMIN; TA=ACTIVE TENANT ADMIN. Business read columns retain approved scope/role/status rules; administration uses the explicit ACTIVE ADMIN matrix. Normal business soft deletion retains reference/history semantics. IServ configuration physical DELETE is explicitly required below; no restore/reset or other hard-delete capability is inferred.

| Resource | Create | Collection/list | Detail by ID | Edit/general update | Delete/soft delete | Special commands | Primary scope and guards |
|---|---|---|---|---|---|---|---|
| Asset | Required registration | Required paginated | Required Asset UUID | Explicit approved commands only; no arbitrary PATCH | No user-facing deletion, undelete or hard delete | Text metadata, Catalog, Location, Owner, loanability, Lifecycle, Condition, loan, return; bounded events | Reads: authorized GLOBAL/own TENANT. Writes: ADMIN, one concrete Tenant; expectedVersion, visible Catalog/same-Tenant places, Product-derived facts; graphs/loan guards unchanged; DEREGISTERED is lifecycle |
| Category | Required | Required paginated | Required UUID | Guarded name edit | Guarded soft delete | Publish; explicit advisory Merge preview/fresh Merge execution | Visible GLOBAL/same-Tenant Catalog; authorized Catalog ADMIN writes; retained refs block edit/delete; UNKNOWN protection; GA-only publish/Merge, visibility/dependency guards |
| Manufacturer | Required | Required paginated | Required UUID | Guarded name edit | Guarded soft delete | Publish; explicit Merge preview/execution | Category-family guards; no manufacturerNumber property |
| Product | Required | Required paginated | Required UUID | Guarded approved product fields | Guarded soft delete | Publish; no Merge | Visibility-compatible Category/Manufacturer IDs; ordinary edit permanently freezes after historical Asset registration; deletion needs no retained current Asset ref; publication independently validated |
| Location | Required | Required paginated | Required UUID | Name + separately gated nullable note | Guarded soft delete | No Merge | Concrete Tenant/ADMIN writes; referenced Location remains editable with same ID; retained Asset ref blocks deletion; metadata edit is not Asset movement |
| Owner | Required | Required paginated | Required UUID | Guarded name + separately gated nullable note | Guarded soft delete | No Merge | Concrete Tenant/ADMIN writes; retained Asset refs block all edit/delete including note; no note-only exception |
| User | GA-created GLOBAL user; guarded deployment bootstrap; exact TENANT OIDC JIT | Required bounded administrative collection | Required administrative UUID detail | Explicit identity commands; GLOBAL Email editing under I1; generic profile update undefined | Generic deletion/anonymization deferred | PENDING approval, role change, disable; GLOBAL credential reset/own password; no automatic enable/revive | GA authorized management; TA only own-Tenant ordinary USER, no ADMIN grant/management; destructive GA self-protection; JIT USER/PENDING, exact issuer/subject; no TENANT local Email/password |
| Tenant | GA creation | Intentionally unpaged ACTIVE GLOBAL ADMIN/USER list | Required UUID: ACTIVE GLOBAL ADMIN/USER, TA own signed Tenant only | Undefined: Tenant property editing needs human decision | Undefined: delete/removal lifecycle needs human decision | No hierarchy/membership transfer commands | ACTIVE GLOBAL ADMIN/USER list/detail; GA-only create; TA own detail only, no list/switching; TU denied; ownership unchanged |
| IServ/OIDC configuration | Initial configure via existing Tenant PUT | GA-only bounded read of all existing configurations | Required authorized Tenant singleton read | Versioned configure/update incl allowed groups and enabled flag | Required physical DELETE; no retained configuration generation | Enabled=false/true remains distinct; reset/restore undefined | GA list and authorized concrete-Tenant singleton/write; TA signed-own singleton/write only; USER denied; Tenant exists; first secret required; optimistic version; exact provider/group eligibility; responses omit secrets |

**Status operations:** User approval is PENDING -> ACTIVE; disable is the existing authorized status operation. No generic status PATCH, DISABLED -> ACTIVE reactivation/enable or arbitrary status assignment is approved. A future enable/reactivation policy requires human decision, not inference from provider enabled or approval. GLOBAL validity/Email operations follow I1; no generic User displayName edit is invented here. Own minimal self read remains authentication behavior, not an unrestricted resource detail endpoint.

**Tenant and provider decisions:** Tenant rename/other property editing, Tenant deletion, User reactivation/profile/deletion/privacy and provider reset/restore are UNDEFINED/DEFERRED pending explicit lifecycle decisions. Physical provider DELETE is required under the contract below. Provider disable via the existing enabled setting is already approved and distinct from removing configuration; no new reset semantics are implied. Initial configuration is PUT/create-or-configure with null initial version and required secret; later updates require version and preserve/re-encrypt omitted secret according to the existing safe mechanism.

### IServ/OIDC singleton physical deletion

ACTIVE GLOBAL ADMIN may create/read/update/delete an authorized concrete Tenant configuration and read a bounded, paged collection of all existing configurations, enabled and disabled. The collection orders by Tenant name then ID and exposes only Tenant ID/name, issuer, client ID, enabled and version. ACTIVE TENANT ADMIN may perform singleton operations only for its signed Tenant and cannot read the aggregate. Caller Tenant UUID is a scoped locator, never authority; service independently resolves signed scope/persisted ownership and rejects foreign targets without an existence oracle. USER/PENDING/DISABLED are denied. Aggregate read grants no additional mutation authority. Anonymous enabled-organization login routing remains separate.

DELETE /api/v1/tenants/{id}/oidc physically removes the current configuration, its encrypted client-secret material and owned allowed-group data, atomically with exactly one OIDC_CONFIG_DELETED AuditLog. Capture the authoritative target Tenant and actor UUID/displayName; never include plaintext/ciphertext/tokens/raw claims. The AuditLog is the retained deletion history. No deleted configuration row, tombstone, restore or retained generation is required. Deletion attribution/time belongs to that immutable audit; do not invent deletedAt/deletedBy business rows. Existing create/update metadata ownership remains unchanged while configuration exists.

Disabled means configuration still exists, occupies the singleton, retains its secret/settings and is readable/editable, but cannot authenticate. Deleted means configuration is missing for ordinary GET/update/runtime lookup and no longer occupies the singleton. Repeated DELETE uses ordinary missing-resource behavior, with no duplicate successful audit. Recreation uses normal initial configure, explicit fresh secret and existing initial-version semantics; deleted secrets cannot be retained/reused implicitly. Tenant, provisioned Users and exact external issuer/subject mappings remain. No User disable or JWT/session revocation is introduced.

Callback and final provisioning must validate against the current existing, enabled, authorized configuration and all existing OIDC protocol, version/issuer/client, group and identity checks. A flow may not succeed solely using pre-delete configuration state. Final locked validation serializes against deletion; missing/disabled current configuration prevents completion. A previously completed authentication retains existing token semantics. Routing/start must not use missing configuration. Keep Tenant/configuration lock order consistent and mutation/audit rollback atomic; no locks across provider network calls.

If configuration is recreated and its current configuration/credentials make an already-started flow valid under all existing checks, that flow may complete. Its start preceding DELETE is not itself an error or invalidation condition. The Domain has no configuration-generation identity. Do not introduce a generation UUID, freshness discriminator, retained deleted configuration state or schema solely for delete/recreate flow identity; do not require pre-delete flows to fail merely because deletion occurred.

### Detail API target and authorization

| Resource | Detail REST contract | Status of established route |
|---|---|---|
| Asset | GET `/api/v1/assets/{id}` | Existing; preserve |
| Category | GET `/api/v1/catalog/categories/{id}` | Existing; preserve |
| Manufacturer | GET `/api/v1/catalog/manufacturers/{id}` | Existing; preserve |
| Product | GET `/api/v1/catalog/products/{id}` | Existing; preserve |
| Location | GET `/api/v1/locations/{id}` | Existing; preserve |
| Owner | GET `/api/v1/owners/{id}` | Existing; preserve |
| User | GET `/api/v1/users/{id}` | Required target; absent from current controller |
| Tenant | GET `/api/v1/tenants/{id}` | Required target; absent from current controller |
| IServ configuration | GET `/api/v1/tenants/{id}/oidc` | Singleton read: GA authorized Tenant or TA own signed Tenant; USER denied |

A caller must not use q/UUID text search or scan a list to obtain detail. User detail applies the same forced administrative read set as Users collection, including all roles in the TA signed Tenant. This read scope does not widen TA mutation authority. Tenant detail allows ACTIVE GLOBAL ADMIN/USER or TA signed own Tenant. TA has no collection; TU denied. Scoped selection/projection occurs before response; absent/out-of-scope detail preserves the existing non-disclosure/problem contract, without an unscoped existence query or secret-bearing fallback. Missing configuration uses the existing missing-resource behavior, not an invented empty secret response.

### Established command transport families

These route families record established shapes; their Domain guards and authorized scopes apply to every invocation. No implementation or cosmetic rename is authorized.

| Family | Existing transport boundary |
|---|---|
| Asset registration | POST /api/v1/tenants/{tenant}/assets |
| Asset text/Catalog/loanability/Location/Owner | PUT /api/v1/assets/{id}/metadata, /catalog, /loanability, /location, /owner respectively |
| Asset Lifecycle/Condition/loan/return | POST /api/v1/assets/{id}/lifecycle-transitions, /condition-transitions, /loan, /return respectively |
| Asset history | GET /api/v1/assets/{id}/events |
| Catalog creation/list | /api/v1/catalog/{categories\|manufacturers\|products}, with existing /tenants/{tenant}/catalog/... variants |
| Catalog edit/soft delete/publication | PUT /api/v1/catalog/{kind}/{id} accepts optional `publishGlobally` for atomic edit plus publication; DELETE retains guarded deletion; POST .../{id}/publish remains supported |
| Category/Manufacturer preview/Merge | POST /api/v1/catalog/{kind}/merge-preview and /merge; sourceId/targetId UUIDs |
| Place creation/list | POST and scoped GET /api/v1/tenants/{tenant}/{locations\|owners}; aggregate GET /api/v1/{locations\|owners}; PUT/DELETE /api/v1/{locations\|owners}/{id} |
| User creation/admin actions | POST /api/v1/users/global accepts optional creation role; PUT /api/v1/users/{id}/administration atomically edits changed Email and/or role; existing POST /users/{id}/approve and /disable and PUT /users/{id}/role and /local-credential remain supported |
| Own GLOBAL password change | PUT /api/v1/auth/local/password; current-password verified, not an admin bypass |
| Tenant creation/list | GA-only POST /api/v1/tenants; ACTIVE GLOBAL ADMIN/USER GET collection/detail; TA own detail only |
| Provider singleton (revised target) | GET/PUT/DELETE /api/v1/tenants/{id}/oidc; DELETE awaits implementation; PUT retains allowedGroupIds/enabled/version and write-only secret |

GLOBAL credential/create/reset payloads evolve to Email under I1; this inventory does not preserve login-name compatibility. Exact new detail DTO/routes for currently missing User/Tenant reads require R1 approval; the target detail paths above do not constitute edited OpenAPI.

### Detail DTO and frontend contract

List DTOs remain concise; detail DTOs may include richer safe fields, metadata and current resource version/state needed by the page. Neither exposes persistence entities or credentials, password/hash, JWT, OIDC client secret/ciphertext, keys or raw provider claims. GLOBAL Email may appear only in an approved safe account projection under I1, never as a route locator or TENANT identity guess.

Association examples: Product contains Category/Manufacturer ID + name; Asset contains authorized Tenant and Category/Manufacturer/Product/Location/Owner projections; User contains Tenant ID + name where applicable; provider detail contains configured Tenant ID + name. Null optional references remain explicit. Every projection is independently permitted by the resource context. Display readable values, use IDs as identity and provide disambiguation for duplicate labels. Do not perform avoidable per-association frontend calls merely to render safe labels; do not fetch full graphs or unauthorized association data.

All eight entity resources have a target route-backed detail page/capability. Collection rows may open it, but direct URL and refresh reconstruct from the ID/detail API, not a saved collection row or cached list membership. Existing Asset browser path `/inventory/assets/{id}` remains; routes for currently modal-only resources are finalized during R1 API/UI review, without gratuitous established-route renaming. Tenant dashboard is not by itself a substitute for Tenant resource detail. Detail-page controls retain A/B rendering identity, request ordering, form exit and authorization semantics.

### Commands, trust and atomic evidence

Requests carry genuine mutable values/reference IDs only. Product creation loads Category/Manufacturer UUIDs and validates visibility; Asset Product registration loads Product and derives associated facts; Location/Owner selection uses IDs rather than name authority. User/Tenant scope comes from the service/identity model. Creation/update/deletion metadata and actors/audit snapshots are server-owned. Reject unknown/forbidden command fields; never blindly persist a DTO or trust copied association labels/ownership. Revalidate persisted facts in the operation's required transaction, not only at frontend preview time.

| Operation family | Required atomic record |
|---|---|
| Asset register | REGISTERED AssetEvent |
| Asset approved text/Catalog/loanability mutation | UPDATED with authoritative before/after snapshot |
| Location/Owner reassignment | LOCATION_CHANGED / OWNER_CHANGED |
| Nonterminal Lifecycle / deregistration | LIFECYCLE_CHANGED / exactly one DEREGISTERED respectively |
| Condition / loan / return | CONDITION_CHANGED / LOANED / RETURNED respectively |
| Category/Manufacturer/Product create/edit/delete/publish; place create/edit/delete | Existing sanitized administrative AuditLog with target attribution |
| Category/Manufacturer Merge | One source-attributed administrative AuditLog plus UPDATED events for each redirected Asset, atomic with references/source deletion |
| Tenant creation; User creation/JIT/approval/role/disable; GLOBAL credentials/bootstrap; provider configuration | Existing sanitized administrative AuditLog; physical provider DELETE requires exactly one OIDC_CONFIG_DELETED with captured target Tenant, atomically; no secrets |
| Detail/list/history reads and advisory preview | No permanent AuditLog/AssetEvent by default |

Immutable history uses stored actor/display/reference snapshots after rename/deletion, independently of normal detail/list behavior. Do not rewrite snapshots, hide retained-reference facts, duplicate Asset business events into administrative audit, or turn read endpoints into repair interfaces. Current transaction/locking/version and execution-revalidation boundaries are preserved. No operation or implementation phase is authorized solely by this matrix.

### U1 Tenant read-authority correction — 2026-10-12

U1 HUMAN RESOLVED / CLOSED: ACTIVE GLOBAL USER may GET the existing complete Tenant collection/detail representation, including id/name and metadata createdBy/createdAt/updatedBy/updatedAt/createdByDisplayName/updatedByDisplayName. Bounded actor UUID/timestamp disclosure is approved; no new endpoint or reduced DTO. Read does not grant administration. GA-only create; TA own detail/no list; TU/status/anonymous denial unchanged. OIDC/User/Audit reads unchanged. GLOBAL USER initially Global with future authoritative UUID/name dropdown, optionally lazy loaded; context selection changes neither identity nor authority. TENANT principals never switch. New backend correction HUMAN ACCEPTED / CLOSED, 2026-10-12; previous Step7 reports remain historical. [Evidence](docs/history/backend-domain.md). F1b/D05–D07 HUMAN ACCEPTED / CLOSED, 2026-10-12; no later gate authorized.
