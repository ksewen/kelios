# Phase 1 — Domain foundation plan

Status: Approved by the user on 2026-10-10. The subsequent approval explicitly authorizes Phase 1 implementation and verification only; Phase 2 remains outside scope. Implementation evidence is recorded separately in [Phase 1 verification](../verification/PHASE_1.md).

## Authority, traceability, and starting point

The approved contracts are [Architecture](../../ARCHITECTURE.md), [Domain](../../DOMAIN.md), and [Implementation Task](../../CODEX_IMPLEMENTATION_TASK.md). The user-approved Domain revision of 2026-10-10 supersedes the previous Product Merge requirement and resolves the mutation, tag, input, minimal identity, actor, self-transition, and retained-reference membership questions. These domain decisions are approved; the revised technical enforcement strategy was subsequently approved by the user.

[ADR 0001](../adr/0001-phase-0-foundation.md) remains the Phase 0 technical baseline. [Phase 0 plan](PHASE_0_PLAN.md) and [Phase 0 evidence](../verification/PHASE_0.md) remain unchanged. Phase 0 supplies Java 25, Spring Boot 4.1.1, Maven 3.9.16 Wrapper, JDBC/Flyway/PostgreSQL, technical status/health, and separate images with runtime branding. V1 executes `SELECT 1` and creates no business tables.

Plans use `docs/plans/PHASE_N_PLAN.md`, evidence uses `docs/verification/PHASE_N.md`, and ADRs use `docs/adr/`. The accepted technical choices are recorded in [ADR 0002](../adr/0002-phase-1-domain-persistence.md); actual test results belong in Phase 1 verification evidence.

## Approved scope and exclusions

Phase 1 covers the ten specified persistence entities, Tenant/reference invariants, protected UNKNOWN Category/Manufacturer entries, a generic state-machine engine, transactional Asset registration/history, optimistic locking, and internal Catalog operations needed for foundation tests. Keep business services internal; no business controllers/UI, impersonation endpoint, or authentication substitute. Phase 2 supplies authentication, user approval/role workflows, and HTTP authorization. Phase 3 supplies inventory transitions, loans, searches, and administrative UI.

Tenant has UUID `id` and required non-blank `name`. User has UUID `id`, explicit `scope`, `tenantId` matching scope, `role`, `status`, and required non-blank `displayName`. No email, username, password/local credentials, OIDC identity, tokens/sessions, or additional authentication fields in Phase 1. Trusted actor context is not a business request field; tests supply fixtures, and Phase 2 will supply authenticated context. Foundation checks do not claim completed authentication/authorization acceptance.

No Product, Owner, or Location Merge; no automatic duplicate detection/merge or name-based uniqueness. No Redis, microservices, extra district hierarchy/memberships, PhysicalDevice, bulk import, labeling/QR/printers, RepairOrder, procurement, or PIM. Do not change Phase 0 branding/deployment choices or implement the future JWT strategy. User deletion/anonymization/DSGVO remains deferred.

## Approved mutations and command contracts

| Entity / operation | Required behavior | Authority |
| --- | --- | --- |
| Category / Manufacturer edit | Reject any current business references. Equal names do not cause Merge or uniqueness errors. UNKNOWN cannot be renamed. | Domain §3 |
| Category / Manufacturer soft deletion | Reject current business references; do not reassign references implicitly. UNKNOWN cannot be deleted. | Domain §§3, 9 |
| Category / Manufacturer Merge | Explicit source ID + target ID, GLOBAL ADMIN operation. Redirect current Product/Asset references, preserve target, soft-delete source, audit; append UPDATED events for redirected Assets, all atomically. UNKNOWN cannot be source. | Domain §§3, 8 |
| Product ordinary edit | Permanently reject after any Asset registration reference, even if no current references remain. No implicit Asset mutation. Category/Manufacturer Merge redirection is a distinct sanctioned operation, not an ordinary edit. | Domain §3 |
| Product soft deletion | Reject current Asset references; allow normal soft deletion once none remain. Never Merge Products or rewrite Assets to resolve duplicate Products. | Domain §§3, 9 |
| Publication | Separate operation, same ID; load current visibility/dependencies and validate promotion. Product publication is distinct from ordinary editing. | Domain §3 |
| Owner edit / deletion | Reject current Asset references; no Owner Merge. | Domain §5 |
| Location edit | Allowed while referenced, same stable ID; Assets reflect current display data. No LOCATION_CHANGED event for metadata-only edits. | Domain §§5, 8 |
| Location deletion | Reject current Asset references; no Location Merge. | Domain §5 |

Commands take entity reference IDs and actual mutable user input, never caller copies of authoritative visibility, dependency facts, reference counts, or snapshots. Load records by ID and derive/validate facts server-side. Changing Location/Owner takes Asset ID and the desired reference ID; deleting/publishing takes target ID; Category/Manufacturer Merge takes source and target IDs. Actor and affected-reference discovery are server-owned.

Registration has two input shapes: with `productId`, load Product and derive Category/Manufacturer, with no redundant Category/Manufacturer input fields; without Product, require Category ID and Manufacturer ID. Require Location ID and explicit `loanable`; Owner is optional. Accept serial number, external inventory number, note, and other specified mutable fields. Normalize blank optional text to null; require non-blank required text. Generate UUID and tag server-side; initialize REGISTERED/UNKNOWN and all current loan fields to null. Do not silently accept caller-supplied generated fields or active-loan data.

Tags use UTC registration date and `yyyyMMdd-NNN`, minimum three sequence digits expanding above 999, an application-wide atomic daily sequence, and database uniqueness. Gaps are acceptable. No gapless promise, generated manufacturer serial number, or uniqueness rule on serial/manufacturer numbers.

The two approved state graphs reject self-transitions and absent edges. The engine is generic and independent of Asset, JPA, OIDC, authorization, transactions, and event storage; inventory supplies its two graph definitions.

Retained-reference membership is approved: every stored reference ID in a retained business row counts, including all four Asset lifecycle states, soft-deleted Assets, and soft-deleted Products. Guard and Merge queries have no lifecycle or deletedAt filters. DEREGISTERED does not deactivate Location or any other relationship. History snapshots do not count as relational references; their evidence of a Product's past usage is a separate historical edit guard. No extra active/current-reference lifecycle concept exists.

## Proposed persistence and module structure

Add Spring Data JPA under existing Boot dependency management for mappings and Asset `@Version`; Hibernate validates Flyway-created schema rather than generating it. Use direct JDBC where atomic counter allocation or explicit merge updates are clearer. No Maven submodules.

Ownership: tenancy → Tenant; identity → User; catalog → Category/Manufacturer/Product; inventory → Asset/Location/Owner/AssetEvent; audit → AuditLog; engine.statemachine → generic engine. Modules expose narrow application interfaces instead of sharing repositories. One application service coordinates cross-module transactions; module dependencies follow ADR 0001. No generic workflow framework or speculative infrastructure.

Historical actor UUIDs are stored as values with non-blank display-name snapshots, **without foreign keys requiring live User rows**. Do not cascade historical deletion or dereference a live User to render immutable history. This leaves future privacy policy open without making it a Phase 1 schema blocker. Current User-to-Tenant relationships still have normal foreign keys.

## Concrete proposed PostgreSQL enforcement

Use declarative constraints for structural equalities and nullability. Use operation-specific transactional validation and targeted locks for business predicates across rows. Do not claim that CHECK constraints can inspect other tables or that foreign keys enforce soft-deletion/visibility predicates. No new infrastructure is needed.

### Foreign keys, composite keys, CHECKs, and UNIQUE constraints

| Invariant | Proposed database mechanism | Additional service responsibility |
| --- | --- | --- |
| User scope / Tenant | Tenant FK; CHECK expressing exactly GLOBAL/null or TENANT/non-null; enum and non-blank display-name checks | Load current actor context rather than accept fabricated scope/role/status |
| Catalog scope | Tenant FK; CHECK expressing GLOBAL/null or TENANT/non-null visibility | Enforce caller scope/operation eligibility and permitted visibility changes |
| Asset → Location | Location `UNIQUE(id, tenant_id)`; Asset `(location_id, tenant_id)` composite FK; both Asset fields NOT NULL | Load Location, reject deleted assignment |
| Asset → Owner | Owner `UNIQUE(id, tenant_id)`; Asset `(owner_id, tenant_id)` composite FK using MATCH SIMPLE; owner_id nullable | Load Owner when present, reject deleted assignment; null Owner is permitted |
| Asset → Product-derived Category/Manufacturer | Independent Category/Manufacturer FKs; Product non-deferrable `UNIQUE(id, category_id, manufacturer_id)`; Asset composite FK `(product_id, category_id, manufacturer_id)` to that key, MATCH SIMPLE, DEFERRABLE INITIALLY DEFERRED, ON UPDATE NO ACTION | Derive IDs from Product; no cascade that bypasses events/versioning. With null Product the composite check is skipped, while required Category/Manufacturer FKs remain enforced |
| Product dependencies | Product Category/Manufacturer FKs, both NOT NULL | Cross-row GLOBAL/TENANT visibility predicates below |
| AssetEvent ownership | Asset `UNIQUE(id, tenant_id)`; event `(asset_id, tenant_id)` composite FK | Capture server-derived snapshots in the mutation transaction |
| Tags / states / current loan | Global Asset tag UNIQUE; enum CHECKs, required fields, non-negative version, CHECK that null borrower implies all other current loan fields null | UTC format/registration defaults and transition validation; no unique names, serial numbers, or manufacturer numbers |
| Required strings | NOT NULL plus non-blank CHECK for Tenant name, User displayName, and required domain text | Normalize optional blank text before persistence |

The Product composite FK is deferred specifically so a Category/Manufacturer Merge can update Product and dependent Asset IDs explicitly before commit; the database rejects any inconsistent final state. Neither historical snapshots nor User records are composite FK targets for actor attribution. Every retained Asset row remains subject to its structural FKs, including after deregistration/soft deletion.

### Normal transactions and operation-specific concurrency

Use normal Spring `@Transactional` behavior (`Isolation.DEFAULT`), with PostgreSQL READ COMMITTED verified as the deployment default. Do not configure a universal SERIALIZABLE policy, KELIOS-wide lock order, generic locking abstraction, or automatic whole-transaction retry framework. Plain metadata operations unrelated to reference guards need only their ordinary write transaction; mutable Asset updates use `@Version` and atomically persist their event. A stale expected version is a conflict, never silently retried as a new user edit.

For the specific parent/reference races below, use explicit SQL `FOR SHARE` when validating an existing referenced parent, and `FOR UPDATE` for a guarded parent mutation. The two modes conflict on the same row; assignment validators can still share a parent with each other. `FOR KEY SHARE` from an ordinary FK is insufficient because it does not prevent non-key changes such as soft deletion. Keep locks until the command commits, including its event/audit inserts. Put the relevant locking SQL and validation in the operation's service, not an unrelated mutation pipeline.

After taking a guarded target lock, run the reference/history EXISTS checks in **subsequent SQL statements**, so READ COMMITTED takes a fresh snapshot after any wait. Do not combine pre-wait reference discovery with the locking query and assume it is fresh. Reload locked state instead of relying on previously cached JPA entities or earlier caller facts. Reference assignment validates loaded parents after acquiring its shared locks; no caller-supplied Tenant, visibility, dependencies, reference counts, existing state, or snapshots are trusted.

| Concrete operation and actual race | Rows protected and smallest mechanism | Why a committed state stays valid |
| --- | --- | --- |
| Category/Manufacturer ordinary edit or deletion versus a new Product/Asset reference | Mutator locks the one target FOR UPDATE, then checks all retained Product/Asset rows for its ID. Product create/reassignment and Asset association take FOR SHARE on the relevant Category/Manufacturer before validation and retain it through persistence. | If assignment wins, its committed reference is visible to the subsequent guard query, which rejects. If mutation wins, assignment waits and loads edited/deleted state; it can only use a valid, non-deleted target. No predicate locks or lock on every child are needed to protect an empty scan. |
| Owner edit/deletion or Location deletion versus Asset assignment | Guard locks the target Owner/Location FOR UPDATE then queries all retained Assets. Assignment holds FOR SHARE on desired references until Asset/event commit; when changing a reference, also protect old parents if their facts are used for before snapshots. | The conflicting parent locks serialize the guard with incoming references. Removing an outgoing reference can only make deletion safer; a rejected guard may be retried explicitly after that removal. Location metadata editing has no no-reference predicate and uses its ordinary UPDATE; it never changes Asset locationId or emits LOCATION_CHANGED. |
| Product ordinary edit versus its first Asset association, or Product deletion versus an Asset reference | Edit/delete locks that Product FOR UPDATE, then queries retained Assets and, for ordinary edit only, immutable event usage evidence. Asset association holds FOR SHARE on that Product until Asset and history commit. A Product relationship removal records the old ID in the same transaction as the changed Asset. | If association commits first, the fresh guard sees retained Asset or immutable history and editing is rejected; if edit commits first, association loads the edited Product and derives its facts. Deletion checks relational references only; history evidence keeps editing frozen but does not prevent otherwise valid soft deletion. |
| Product create/edit/publication or Asset association versus dependency mutation | Lock only the Category/Manufacturer/Product records actually used by the command. Validation uses FOR SHARE on dependency facts; publication/edit takes FOR UPDATE on the entry being changed. For Product-backed registration, read dependency IDs, shared-lock Category/Manufacturer and Product, re-read Product, and return a concurrency conflict if those IDs changed during acquisition. | GLOBAL Product requires both dependencies GLOBAL; TENANT Product requires GLOBAL or same-Tenant dependencies. Asset requires all Catalog entries GLOBAL or its own Tenant. Locked values cannot change before commit. Promotion preserves ID and only broadens visibility, but its dependencies are still validated. No visibility trigger or additional caller facts are needed. |
| Category/Manufacturer Merge versus incoming references, competing Merge, or dependent updates | Merge locks only same-type source/target FOR UPDATE in UUID order before discovering references. It then locks affected Products and Assets FOR UPDATE, with UUID order inside each affected row set, and shared-locks any additional Catalog dependencies used for validation. Incoming assignments shared-lock source/target as above. Re-read discovered rows after locking, and update only references still pointing to source. | While source is exclusively locked, no supported writer can add a new reference to it. Existing references can be safely discovered with fresh queries, including soft-deleted/deregistered rows. Explicit updates preserve visibility and Product equality; deferred composite FK checks reject an inconsistent final state. Source deletion, Asset version increments, UPDATED events and AuditLog all roll back on any failure. No implicit FK cascade can bypass history. |
| Asset mutation versus another Asset mutation or Merge | Ordinary edits use optimistic version in the UPDATE predicate and atomic AssetEvent insert. Merge locks each affected Asset before reading its before-state, then advances its version while redirecting IDs. | A stale edit fails rather than overwrite redirected facts. An edit that completes before Merge is included in the freshly loaded before-state. Any failed event/audit insert rolls back the command. |
| Concurrent daily tag allocation | Atomic UPSERT on the single counter row for the UTC registration date, with RETURNING, inside registration; Asset tag UNIQUE remains a separate backstop. No explicit shared Catalog/global lock for the counter. | PostgreSQL serializes updates to that day's counter row; registrations receive distinct allocations. Format expands past 999; sequence overflow fails instead of wrapping. Gaps remain acceptable. |

For Product edits changing dependencies, protect both old and requested dependency facts needed for validation/history. For a supported reference reassignment, validation and snapshot creation take the relevant old/new parent locks before committing the changed child; no relationship change may omit this where it would bypass a guard. This is a requirement of those concrete services, not a requirement that unrelated commands lock Tenant/User/all Catalog tables.

Same-type Merge source/target UUID ordering and ordering affected Products/Assets are local deadlock precautions, not a KELIOS-wide hierarchy. Overlapping Category/Manufacturer merges or dependency-changing commands can still deadlock while acquiring different needed parents. PostgreSQL aborts one transaction; surface a concurrency conflict, roll back its entire command, and allow an explicit fresh retry. No automatic retry framework is proposed. A blocked lock waits until release or the operation's configured lock timeout; timeout similarly rolls back without partial effects. Do not claim deadlock freedom or retry a stale expected Asset version.

Incoming reference writers and guarded operations must obey the corresponding parent handshake; FKs alone do not provide that predicate guarantee. Direct administrative SQL is outside supported business-command boundaries. Declarative constraints still reject structural corruption, but ad hoc SQL is not claimed to satisfy visibility/edit/deletion/history rules. No public repository access or SQL maintenance endpoint is introduced. Two-connection tests will force the listed interleavings, including the empty-reference case and a writer blocked before guard inspection.

### Product ever-referenced guard without derived persistence state

Remove `Product.ever_asset_referenced` entirely: no column, permanent usage latch, marker-maintenance service, or marker trigger. Existing authoritative retained data and required immutable history are sufficient when the event payload contract is explicit.

The Product edit guard, under that Product's FOR UPDATE lock, evaluates:

1. EXISTS a retained Asset with `product_id = targetId`, without lifecycle/deletedAt filtering; **or**
2. EXISTS an immutable AssetEvent whose server-written structured before/after Product ID equals targetId.

Specify canonical UUID strings or JSON null at `data.before.productId` and `data.after.productId`; REGISTERED includes the after Product ID (explicit null if none), and every supported Product association/reassignment/removal includes both IDs in its existing UPDATED event along with display snapshots. Record reference IDs rather than infer identity from Product names. Events are persisted atomically with the Asset; no history pruning or rewrite is part of Phase 1. Add ordinary Asset.product_id indexing and expression indexes on these two JSONB ID paths for reliable EXISTS lookups; no new history relationship entity or denormalized Product field.

A retained Asset proves present use; after reassignment or removal its unchanged prior event proves past use. Events from deregistered/soft-deleted Assets remain searchable. Missing required usage evidence is an event-contract error and must fail the command/test, not cause a permissive guard. Fresh Phase 1 migrations start from the no-business-data V1 baseline, so no legacy history backfill problem exists. Tests must demonstrate that the guard still rejects edits after the last relational Product reference is explicitly removed and that the first-association race is protected. Future features that mutate Product relationships must implement this same existing history contract before being exposed.

This historical query does not make event snapshots relational references: they never block Category/Manufacturer/Owner/Location reference guards, Product deletion, or Merge scans. They establish only the separately approved permanent Product ordinary-edit freeze.

### Final narrow PostgreSQL trigger proposals

Only the two critical protections below were approved. Their implementation is in V3; verification is recorded separately.

| Remaining protection / trigger scope | Exact invariant | Why declarative constraints, mapping, or service boundary alone is insufficient | Smallest mechanism and limits |
| --- | --- | --- | --- |
| AssetEvent append-only: reject UPDATE/DELETE; a statement trigger rejects TRUNCATE on AssetEvent only | Committed business history and its usage evidence cannot be changed or removed by ordinary DML | CHECK/FK/UNIQUE evaluate row content/relationships, not append-only behavior. Read-only JPA mappings and absent endpoints do not protect history from repository bulk SQL, and deleting the last usage event would weaken Product's permanent guard. | One rejecting trigger function with table-local UPDATE/DELETE and TRUNCATE bindings; INSERT stays allowed. No business validation, actor authorization, snapshot generation, or privacy exceptions in the function. Privileged DDL/trigger removal is outside this runtime guarantee. |
| Protected UNKNOWN Category/Manufacturer: conditional BEFORE UPDATE/DELETE row triggers on those two tables | The two migration-seeded stable-ID UNKNOWN rows cannot be renamed, moved out of GLOBAL scope, soft-deleted, identity-changed, or hard-deleted | CHECKs can restrict a marked row but cannot prevent deleting it or removing/changing its identifying marker; FKs cannot protect an unreferenced seed. JPA/service checks do not cover ordinary bulk row mutations. | A small OLD-row/stable-ID guard checks only the two protected rows and rejects protected-field changes/deletion. Seed exactly one per table with reserved UUID PKs; no name uniqueness. Ordinary rows follow their services. No blanket Catalog UPDATE/DELETE trigger or generic TRUNCATE trigger. |

No Asset-identity trigger: UUID is the JPA identifier; tenantId and assetTag use non-updatable mappings and are absent from mutable command models. Supported services reject generated/immutable input fields and update only allowed columns; no bulk reassignment path exists. PK/FKs/UNIQUE still protect relational identity and tag uniqueness. Mapping plus the application boundary enforces immutability for supported writes; arbitrary privileged SQL is not claimed to obey that business policy. Tests cover all supported Asset commands and Merge retaining UUID/Tenant/tag.

No Product usage-metadata trigger because no usage flag remains. No generic hard-delete/TRUNCATE triggers across business tables: service/repository boundaries expose soft deletion only, disable orphan removal and delete cascades, and contain no deleteAll/truncate command. Tests prove retained rows and references survive deletion commands. UNKNOWN row guards protect their special records, and AssetEvent's table-local TRUNCATE guard protects immutable history; deliberate maintenance/privileged purge is not a supported business operation. Do not add a separate database-role deployment subsystem or reproduce application rules in SQL triggers merely to cover hypothetical future writers.

Basis: PostgreSQL 17 documentation on [constraints](https://www.postgresql.org/docs/17/ddl-constraints.html), [READ COMMITTED snapshots](https://www.postgresql.org/docs/17/transaction-iso.html#XACT-READ-COMMITTED), and [row locking](https://www.postgresql.org/docs/17/explicit-locking.html#LOCKING-ROWS). Success requires actual implementation and concurrency tests; see the separate verification report for executed results.

## Execution sequence after approval

1. Approval received; all reviewed reference-membership decisions are resolved. Record approved technical choices in a new ADR; preserve V1 and all Phase 0 evidence.
2. Add incremental Flyway migrations and JPA mappings in dependency order: minimal Tenant/User, Catalog/UNKNOWN protection, Location/Owner, Asset/counter, AssetEvent/AuditLog. Implement the constraints and narrow triggers above, including non-live historical actor values and indexed structured Product IDs in immutable event payloads; no Product usage flag. No authentication fields or Product Merge support.
3. Implement independent generic engine and the two approved inventory graph definitions; test every permitted/forbidden edge and self-transition rejection without persistence dependencies.
4. Implement internal registration with reference-ID inputs, server-derived Product facts, UTC tags, explicit loanable, normalized text, no initial loan, initial states, and atomic REGISTERED event with actor/reference snapshots.
5. Implement separate internal edit, deletion, publication, and Category/Manufacturer Merge commands. Apply entity-specific guards, operation-specific parent locking where required, Asset optimistic versions, UPDATED events for redirected retained Assets, and atomic AuditLog. Location edits leave Asset IDs and AssetEvents unchanged.
6. Verify against real PostgreSQL using JUnit/Testcontainers, integrated into Maven `verify`; no H2 substitute or silently skipped required integration checks. Keep Docker approval scoped and build/cache files inside KELIOS. Run appropriate Phase 0 image/deployment/branding regression checks.
7. Record actual results/limitations in `docs/verification/PHASE_1.md`. Stop at Phase 1; no Phase 2 work, commits, pushes, external writes, or sandbox policy changes follow automatically.

## Previous assumptions superseded by approved decisions

| Previous plan assumption / question | Replacement |
| --- | --- |
| Product Merge, including mismatched dependency handling | Removed entirely from v1; duplicates cause no automatic behavior |
| Product edits limited only by resulting consistency | Permanent ordinary-edit prohibition after any Asset registration reference |
| Allow deletion while retaining live dependents | Entity-specific reference guards now reject deletion; Location editing remains intentionally allowed |
| Possible no-op self-transition | Reject every same-state transition |
| Tag format/timezone/sequence scope pending | Approved UTC yyyyMMdd-NNN, global atomic daily allocation, expanding digits, UNIQUE protection, gaps allowed |
| Tenant/User fields and actor live-FK decision pending | Approved minimal records and actor UUID/display snapshots without mandatory live User FK; privacy deferred |
| Caller supplies Product and Category/Manufacturer and service chooses values | Product registration derives both server-side; no redundant input fields |
| loanable default/blank-input behavior pending | Explicit loanable, no initial active loan, optional blanks null, required text non-blank |
| Merge AssetEvents unapproved | UPDATED events with before/after IDs/display snapshots, atomic with redirect/source deletion/AuditLog |
| Universal SERIALIZABLE isolation / KELIOS-wide lock hierarchy / retry framework | Removed; normal READ COMMITTED transactions, optimistic Asset updates, and targeted parent locks in the specific guarded services |
| Permanent ever_asset_referenced flag and marker trigger | Removed; retained Asset references plus indexed immutable before/after Product IDs establish usage |
| Asset identity and generic business hard-delete/TRUNCATE triggers | Removed; immutable mappings/input boundaries and soft-delete-only services; only scoped history/UNKNOWN trigger protections remain |
| Retained-reference membership unresolved | Approved: all retained stored IDs count regardless of lifecycle/deletion; snapshots are history, not relational references |

## Approval gate and remaining technical choices

No reviewed business decision remains unresolved or blocks Phase 1. In particular, all retained foreign/reference IDs count; immutable history snapshots do not. User privacy policy, real IServ claims, authentication transport/authorization workflows, and inventory UI remain deferred and are not foundation blockers.

Explicit approval of this revised plan was received on 2026-10-10. Approved technical choices are Spring Data JPA with validated Flyway schema, PostgreSQL composite/deferred constraints, targeted service locking at normal isolation, indexed Product ID paths in existing JSONB history, atomic daily counter UPSERT, scoped AssetEvent/UNKNOWN triggers, and JUnit/Testcontainers integration in Maven verify. No independent global concurrency subsystem, usage latch, or additional trigger family is proposed. Dependency patch versions and operation lock-timeout settings can be documented as ordinary implementation details after approval; they do not reopen approved business rules.

## Acceptance and evidence plan

| Required evidence | Planned checks |
| --- | --- |
| Schema | Fresh install and V1 upgrade; minimal Tenant/User fields only; scope/null/state constraints; direct wrong-Tenant FK and Product-equality FK violations rejected |
| Visibility | GLOBAL/TENANT dependency matrix for Product and Asset; GLOBAL Product publication with Tenant dependency rejected; hidden/deleted references unavailable for new assignment |
| Mutation guards | All retained lifecycle/deletion combinations counted, including DEREGISTERED Location references and soft-deleted Products; Category/Manufacturer/Owner edit/delete rejected while referenced; Product edits remain frozen after reference removal; Product/Location deletion guards hold; Location rename allowed with no LOCATION_CHANGED event |
| Independent operations | Duplicate names and renamed typos cause no automatic Merge or uniqueness rule; no Product/Owner/Location Merge command/API/schema mechanism |
| Merge | Category/Manufacturer Product and Asset redirection, target identity retained, source soft-deleted; versions increment; one UPDATED event per redirected Asset with correct before/after snapshots; AuditLog/history/source update roll back together on injected failure |
| Trust boundary | ID-only operations load authoritative facts; Product registration derives IDs; unselected Product requires Category/Manufacturer; explicit loanable, optional blank normalization, and initial-loan rejection |
| Tags / optimistic locking | Concurrent global daily allocations, UTC date boundary, sequence above 999, UNIQUE rejection, no gapless promise; stale Asset version fails |
| History / protection | Direct event UPDATE/DELETE/table-local TRUNCATE rejected; UNKNOWN row guards tested; immutable identity/tag protected through supported commands; Product freeze proven by retained data/history after reference removal; actor snapshots render without live User lookup |
| Concurrency | Two-connection READ COMMITTED guard/assignment/first-use/Merge races; guarded target waits and fresh EXISTS checks demonstrated; deadlock/timeout/stale-version conflicts roll back without partial changes or duplicate events; unrelated mutations acquire no universal parent locks |
| Engine / architecture | All approved graph edges and rejected self/terminal edges; no persistence/domain/security engine dependency; Maven 3/Java 25 and independent Docker/deployment/branding regression evidence |

Phase 1 success requires actual implementation, passing checks, and an evidence report. The plan records approved requirements; the verification report records executed results and limitations. Approval does not authorize Phase 2, commits, or pushes.
