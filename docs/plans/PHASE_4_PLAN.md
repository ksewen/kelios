# Phase 4 plan — Stabilization, localIdentifier and English/German frontend

**Historical accepted implementation record.** Preserve the decisions and evidence below as implementation-time history. Current normative Domain/identity/query requirements are in [DOMAIN.md](../../DOMAIN.md) and [ARCHITECTURE.md](../../ARCHITECTURE.md); separately approved future work follows [the consolidated implementation gates](COLLECTION_QUERY_IMPLEMENTATION_PLAN.md). Earlier login identifiers/password minima, paging defaults, deleted selectors, sort ties and phase-specific exclusions are not competing target requirements.


## 1. Status and approval boundary

**Finalized approved specification; Phase 4 implementation is explicitly authorized. User acceptance is pending review.** Phases 0–3 are accepted by the user. Real IServ interoperability remains **MANUAL VERIFICATION PENDING**, separately tracked and not a blocker for this plan. KELIOS has not been deployed.

This document records the user's subsequent Phase 3 acceptance without rewriting historical evidence/ADR status text that still says pending review. It extends only the explicitly approved metadata/search scope and preserves all other accepted behavior. The user explicitly approved L1–L19, N1, M1, S1, the combined stabilization/localIdentifier scope and I18N-1–I18N-13. Domain now records the approved observable identifier rules; Architecture records the presentation-layer boundary. No production code, schema, API contract or tests change in this specification step. The user subsequently granted explicit implementation approval. ADR 0005 and Phase 4 verification record implementation choices and actual results; the implementation inventory below preserves the planning-time baseline. Technical recommendations below do not reopen these product decisions.

## 2. Authoritative inputs

Authority remains [AGENTS](../../AGENTS.md), [Architecture](../../ARCHITECTURE.md), [Domain](../../DOMAIN.md), and [Implementation Task](../../CODEX_IMPLEMENTATION_TASK.md). Domain owns business invariants; Architecture owns technical constraints; approved plans refine phased scope. Later explicit user acceptance advances status without changing settled behavior.

Reviewed plans: [Phase 0](PHASE_0_PLAN.md), [Phase 1](PHASE_1_PLAN.md), [Phase 2](PHASE_2_PLAN.md), [Phase 3](PHASE_3_PLAN.md). Reviewed ADRs: [0001](../adr/0001-phase-0-foundation.md), [0002](../adr/0002-phase-1-domain-persistence.md), [0003](../adr/0003-phase-2-authentication-and-authorization.md), [0004](../adr/0004-phase-3-inventory-and-target-owned-audit.md). Reviewed evidence: [Phase 0](../verification/PHASE_0.md), [Phase 1](../verification/PHASE_1.md), [Phase 2](../verification/PHASE_2.md), [Phase 3](../verification/PHASE_3.md), and [manual IServ verification](../verification/PHASE_2_ISERV_MANUAL.md).

Implementation inspection includes backend modules/security/services, frontend authentication/inventory/branding, current tests, [OpenAPI](../api/openapi.yaml), Flyway V1–V5, Dockerfiles, Compose and smoke scripts. Concrete file references below use repository-relative paths. Repository searches for Phase 4, deferred/future requirements and TODO/FIXME found no source TODO/FIXME markers allocating another product feature to Phase 4.

## 3. Phase 0–3 accepted baseline

Preserve Java 25 / Boot 4.1.1 / Maven 3.9.16 Wrapper / Node 24, PostgreSQL/Flyway and modular monolith; independent non-root multi-stage images, toolchain-free runtimes, nginx same-origin proxy and runtime-mounted branding. Preserve React/TypeScript/Vite/shadcn/Tailwind/TanStack; no new frontend framework is justified; the narrowly recommended i18n dependencies are described in §17.2.

Preserve explicit GLOBAL/TENANT scope, ADMIN/USER role, PENDING/ACTIVE/DISABLED status and single-Tenant membership. Authentication remains memory-only Access Bearer, HttpOnly Refresh cookie, same-origin HTTPS, CSRF/exact Origin, RS256, absolute Refresh expiry/current User reload, no revocation/session infrastructure. IServ remains database-configured, documented UUID groups, exact issuer/subject, JIT PENDING and login-only group eligibility. Do not add continuous synchronization or per-request database revocation.

Preserve Asset identity/tag allocation; Product-derived references, permanent Product edit freeze, retained-reference guards, protected UNKNOWN, only Category/Manufacturer Merge and existing visibility rules. Preserve both exact state graphs and structured deregistration reasons/event convention, independent loans/states/Location, Return's current-loan-only guard and loanability's outstanding-loan guard. No user-facing Asset deletion. Preserve @Version, READ COMMITTED, targeted locks, atomic AssetEvent, immutable historical interpretation and no silent replay.

Preserve target-owned nullable immutable AuditLog Tenant attribution, no AuditScope/third state/live Tenant FK, pre-publication and source-pre-Merge ownership, no audit fan-out, SQL-scoped Tenant Admin reads and GLOBAL Admin all-audit authority.

## 4. Current implementation baseline and gap analysis

| Inspected implementation | Current behavior and proposed extension |
|---|---|
| `backend/src/main/java/com/github/ksewen/kelios/inventory/Asset.java` | Nullable text serialNumber/externalInventoryNumber/note; immutable Tenant/tag, @Version and embedded loan. No localIdentifier/name. Add one nullable string only after approval. |
| `inventory/AssetService.java` | Registration record and View have existing three text fields. `optional` maps null/blank to null and applies Java `strip()` to nonblank values. `editText` replaces all three fields; omission at HTTP currently becomes null, not partial-update retention. Extend deliberately under §12. |
| `AssetService.snapshot`, `inventory/AssetHistory.java` | Full before/after JSONB, canonical productId paths, server actor/time; REGISTERED/UPDATED and all workflow events. A new snapshot key requires no new history table/type. Preserve Product usage paths. |
| `platform/security/AssetController.java`, `InventoryOperations.java`, `SecuredDomainOperations.java` | Explicit closed registration and metadata command fields; authorized loaded Asset Tenant and expectedVersion. New field currently rejected. Extend all supported internal adapters, not just HTTP. |
| `inventory/InventoryReads.java`, `platform/ListInput.java`, `JdbcRows.java` | Scoped SQL count/select/detail; `a.*` maps snake_case to camelCase. Literal escaped ILIKE on tag/serial/external; q bounded to 128, fixed sorts and filters. New DB column would enter SQL read maps; View/OpenAPI still need explicit extension. |
| `frontend/src/InventoryApp.tsx`, `inventory.ts` | Registration, metadata draft initialization/submission and detail/list explicit field arrays; heading uses assetTag. History field allowlist omits proposed field. `nullable` checks trimmed blank but returns nonblank input unchanged; backend currently supplies authoritative stripping. Extend these concrete points and clear terminology. |
| V1–V5 | V2 contains Asset text fields, references/version/events/indexes; V3 append-only/UNKNOWN protection; V4 identity; V5 target-owned audit clean cutover/index. No localIdentifier column/constraint/index. |
| OpenAPI | 52 paths / 73 operations; closed AssetRegistration variants, MetadataCommand, Asset/AssetDetail and paged read schemas. No localIdentifier field yet. No new endpoint needed for approved metadata extension. |
| Tests/evidence | Accepted Phase 3 reports 290 backend cases (74 unit / 216 PostgreSQL integration), 33 frontend unit cases, 24 browser cases; explicitly identifies UI-only fixtures. These are historical results, not tests executed during this planning task or promised Phase 4 counts. |

Domain §4 says not to add Asset `name` without a new decision; the Phase 3 plan excludes Asset name. This is the precise existing boundary the newly requested identifier would extend through a reviewed Domain change. It does not authorize a generic descriptive name, repurposing another field or silently changing existing semantics.

## 5. Explicitly deferred requirements discovered

The original Task **Phase 4 — Stabilization** contains exactly: (1) automated tests for Domain invariants; (2) production multi-stage images/reproducible documented local deployment; (3) no-rebuild branding verification; (4) API/configuration/bootstrap/OIDC/operational documentation; (5) list deferred features instead of implementing them. Many checks already run in Phase 3; Phase 4 should consolidate coverage and close demonstrated gaps, not count earlier success as a new run or duplicate every test.

| Class | Repository evidence / finding | Proposed Phase 4 treatment |
|---|---|---|
| A: explicitly assigned | Task Phase 4 items 1–5, Architecture §6, ADRs/build boundaries | Stabilization coverage mapping, justified missing tests, fresh verification and operational documentation, including approved disposable backup/restore verification. |
| A: deferred, not assigned | Domain §9 CSV/XLSX preview/validation/import through registration | Candidate for a separately approved future phase; **exclude** from recommended Phase 4. No import format, preview or partial-failure rules approved. |
| A: deferred, not assigned | Domain §9 labeling; QR not required, printer unspecified | Keep deferred; no label/QR/printer implementation. |
| A: deferred policy | Domain §§1, 8–9 User/Identity deletion/anonymization/DSGVO; Architecture §10 future Tenant lifecycle | Keep deferred. No retention/deletion/repair workflow or Tenant FK change. |
| A: deferred deployment | Architecture §§4, 7, ADR 0001: cross-origin/cross-site auth, subpath support, CI provider selection | Document supported topology/limits; no new deployment support or CI platform without approval. |
| A: pending external evidence | Phase 2 manual IServ procedure and Phase 3 limits | Preserve separate manual gate; do not request credentials or pretend fixture success is interoperability. |
| A: intentionally unexposed | Phase 3 plan §§11–12: Asset deletion; Tenant rename/delete, User delete/reactivation; no additional account recovery | Preserve boundaries; none becomes Phase 4 scope by omission alone. |
| B: newly requested | User's optional organization-assigned Asset identifier | Approved narrow Asset metadata extension under §7. |
| B: newly approved presentation | I18N-1–I18N-13 in the latest user approval | Complete English/German frontend localization in Phase 4 stabilization; no backend/domain localization. |
| C: technical debt/cleanup candidates | Historical status sentences, raw property labels/UUID filter inputs, densely written source, limited platform evidence | Record observations; no general refactor/UI redesign/dependency upgrade. Owned labels are converted as required for approved bilingual presentation, without general UI redesign. |
| D: speculative/excluded | Repair/procurement, PhysicalDevice, loans as entity, hierarchies, dashboard/search/RBAC platforms, realtime infrastructure | Exclude. No authoritative allocation to Phase 4. |

README already requires protected backups, explicit image tags/digests, HTTPS and restricted management networking. `docs/AUTHENTICATION.md` requires backing up encryption keys with the database. Document a minimal operator-controlled backup/restore verification procedure using existing PostgreSQL/deployment tools; do not invent retention schedules, RPO/RTO, managed backup services or privacy policy. A disposable restoration drill is approved stabilization evidence, not a new business subsystem.

## 6. New Asset localIdentifier requirement and identifier distinctions

The requested concept is an **optional Tenant/organization-assigned human operational identifier string**, e.g. R421-PC7 or Lehrer-NV. Examples prove neither uniqueness nor syntax rules. The approved Domain/API field name is `localIdentifier`. It is broader than a numeric inventory number and narrower than a generic Asset name. Approved English UI label: **Local identifier**. German bundle label: **Lokale Kennung**. Help text explains organization assignment separately from the KELIOS Asset Tag; labels are presentation only, never translated property names.

| Field | Identity/meaning today | Relationship to localIdentifier |
|---|---|---|
| UUID `id` | Internal persistent registration identity; API/reference key | Remains immutable identity even if labels match/change. |
| `assetTag` | Immutable KELIOS-generated human-readable identity, UTC yyyyMMdd-NNN global daily sequence | No rename, format change, custom input or replacement. |
| `serialNumber` | Nullable non-unique external/device/manufacturer serial, not generated | Independent; same serial is not physical identity or linkage. |
| `externalInventoryNumber` | Existing nullable external inventory field, distinct from tag; accepted editable optional text | Retain its current meaning/normalization; do not infer uniqueness, import provenance or fallback/copy rules. |
| `localIdentifier` | Optional human organization-assigned operational label/identifier | New nullable string with approved semantics in §7. Equal visible values across fields do not merge concepts. |

## 7. Resolved product decisions — L1–L19, N1, M1 and S1

All entries below are **APPROVED final requirements**. Earlier uniqueness, immutability, dedicated search/index and client-compatibility alternatives were rejected; none belongs in implementation.

| Decision | Final requirement |
|---|---|
| L1 — Tenant uniqueness | Not unique; UUID/tag remain distinguishing identities. |
| L2 — uniqueness membership | No uniqueness membership or lifecycle/deletion subset because no uniqueness applies. |
| L3 — duplicates | Intentionally allowed within/across Tenants, regardless of lifecycle/retained/internal soft deletion. No duplicate precheck/reservation/key. |
| L4 — stored case | Preserve case; no uniqueness/equality canonicalization. Search separately remains case-insensitive. |
| L5 — whitespace | Outer whitespace stripped using existing Java optional-text semantics; internal whitespace significant/not collapsed. |
| L6 — normalization | Blank becomes null; no lowercase/uppercase/additional Unicode normalization. |
| L7 — length | Maximum 128 Unicode code points after stripping; backend authoritative, not UTF-16 units. |
| L8 — characters | Unicode human text allowed; reject General Category Cc, U+2028/U+2029. Not GLOBAL login regex/HTML. |
| L9 — edit/clear | Ordinary mutable/clearable Asset metadata through existing replacement command, expectedVersion/@Version; no new terminal/loan guard. |
| L10 — history | Existing UPDATED before/after; REGISTERED and every new authoritative state snapshot include field; no new type/AuditLog action or old-event rewrite. |
| L11 — registration | Optional in both registration variants; omitted means null, no generation. |
| L12 — q | Include alongside assetTag/serialNumber/externalInventoryNumber in existing bounded escaped literal case-insensitive substring q. |
| L13 — filter/sort | No dedicated localIdentifier filter/sort in Phase 4. |
| L14 — UI | Secondary separate human label; KELIOS Asset Tag stays primary, duplicates visibly distinguished. |
| L15 — transfer | No automatic copy; new destination UUID/tag, localIdentifier null if omitted, operator may explicitly enter through normal registration. No transfer linkage/copy workflow. |
| L16 — DEREGISTERED | Retain value; no new metadata-edit freeze or clearing. |
| L17 — internal soft deletion | Retain value/history; no public deleted-Asset workflow. |
| L18 — index | No dedicated index/UNIQUE/trigram/full-text infrastructure. Report demonstrated performance problems rather than speculate. |
| L19 — external field | Completely independent externalInventoryNumber; no copy/fallback/exclusion/uniqueness coupling/reinterpretation. |
| N1 — name | Property localIdentifier; English Local identifier; German Lokale Kennung; not generic Asset name. |
| M1 — replacement | Four nullable metadata fields replace current state; omission and explicit null clear, no PATCH/backward-client exception. Frontend sends complete four-field state. |
| S1 — stabilization | Invariant coverage matrix, full regressions/build/deployment/HTTPS/branding/docs plus one isolated disposable PostgreSQL backup/restore with required external encryption keys. No backup service or retention/RPO/RTO/HA policy. |

I18N-1–I18N-13 are also approved, detailed in §17. No unresolved business decision remains in this scope. Import/privacy/deletion/labeling and other deferred policies stay excluded rather than blockers.

## 8. Approved Phase 4 scope

Implement, after explicit implementation approval, **original stabilization + narrow localIdentifier metadata extension + complete English/German frontend localization**. Trace every current Domain acceptance invariant to existing concrete tests and add only meaningful gaps/new cases; do not assume counts alone prove complete coverage. Review deployment/API/operational docs against actual code/config; rerun accepted verification with separate Phase 4 destinations. Include one-column identifier persistence, commands/read/history/q and minimal identifier UI changes, plus complete frontend-owned text localization under §17.

Any bug actually found while stabilizing should be reported against the violated accepted rule and fixed narrowly after implementation authorization; this plan is not approval for arbitrary cleanup. Bulk import and other deferred features require separate scope decisions and are not recommended here.

## 9. Explicit non-goals

No Phase 5, import, labeling/QR/printers, Repair/procurement, PhysicalDevice/serial uniqueness, Loan entity/reservations/notifications, automatic return/movement, hierarchy/PIM, duplicate detection/automatic Merge, Product/Owner/Location Merge, generic custom fields/identifier/alias entity, naming service, extra history table, generalized RBAC/query/provider framework, Redis/WebSockets/dashboard, new authentication/session/revocation model, continuous IServ sync, multi-Tenant membership, account/Tenant delete/reactivation/privacy policy or user-facing Asset deletion. No cross-origin/subpath/CI/registry publication/platform expansion by default. No production historical repair or audit reconstruction. No extra languages, locale Domain columns/APIs/JWT/session, Tenant language policy, translated user data/master data, backend Accept-Language business behavior or translation-management platform.

## 10. Persistence implications

Minimal shape: nullable `asset.local_identifier` PostgreSQL text mapped to String; no generated value, separate table, new FK, UNIQUE, index or normalized key. Small field-specific server validation enforces approved normalization/length/character rules. A declarative CHECK may enforce character count and character exclusion if real PostgreSQL tests establish exact equivalence for accepted values. Do not assume btrim equals Java strip or POSIX control classes equal Unicode Cc. If a CHECK cannot clearly reproduce a rule, retain authoritative service validation at the supported boundary rather than introduce a trigger/framework. Exact CHECK design is a delegated technical detail, not a product gate.

No identifier reservation/duplicate query. No i18n database/schema changes; language preference lives only in browser presentation storage.

## 11. Application/service implications

Extend Asset mapping/accessor, Registration, View, `register`, `editText`, secured internal wrappers/InventoryOperations and authoritative snapshots. Use a small explicit field validator under approved rules, not a generalized metadata framework. Retain current identity/dependency loading, clocks, actor snapshots, @Version and atomic finish/history. Local identifier never determines Tenant, Product, actor, visibility or registration identity.

Review all snapshot-producing paths, including Merge redirects/internal soft deletion, so new snapshots include the field consistently. No new parent lock required for a scalar non-unique metadata field. Blank clearing and validation must agree between internal registration/services and HTTP, not depend on frontend cleanup.

## 12. API/OpenAPI implications

After approval only, extend both closed AssetRegistration variants and existing MetadataCommand, Asset, AssetDetail/AssetPage projections with optional nullable string and approved constraints. Keep `/api/v1/tenants/{tenantId}/assets`, `/api/v1/assets/{id}/metadata` and existing reads/history; no new endpoint/PATCH. Preserve explicit Catalog/loanability/state/loan commands. Reject unknown or server-owned input and wrong scalar types using existing safe INVALID_INPUT handling. Backend machine errors remain language-independent; frontend localization maps known stable codes without parsing exception messages.

**Approved M1 replacement:** extend the current field set to serialNumber, externalInventoryNumber, note and localIdentifier. Omitted nullable fields mean clearing, including localIdentifier; explicit null also clears. Registration omission means null. The frontend submits all four current fields. No PATCH behavior or undeployed-client compatibility exceptions. Document/test omission versus null and complete-state resubmission. OpenAPI changes occur only after implementation approval; current schemas intentionally still describe accepted Phase 3 implementation.

## 13. Authorization/Tenant isolation implications

Reuse GLOBAL ADMIN any-Tenant and TENANT ADMIN own-Tenant mutation; GLOBAL USER all-Tenant read / TENANT USER own-Tenant read. PENDING denied; accepted JWT status/permission snapshots unchanged. GLOBAL URL Tenant is authorized operating context, not membership/session. Target-loaded Tenant controls edits. Same identifier across Tenants must not disclose foreign matches, counts, details or existence to Tenant users. Identifier never permits lookup that bypasses UUID/Tenant boundaries. Preserve SQL-scoped list/detail/count and concealed cross-Tenant 404.

## 14. AuditLog implications

Approved ordinary identifier edit is Asset business history only, not an administrative action. Do not add AuditLog entries/action types or duplicate UPDATED events. Existing audit writers/query security/known-null meaning remain unchanged. No AuditScope, live Tenant FK, legacy or reconstruction. A Catalog Merge may snapshot the retained Asset identifier while retaining its existing one administrative source-attributed audit; no extra audit fan-out.

## 15. AssetEvent/history implications

REGISTERED after snapshot includes optional value; metadata edit uses UPDATED before/after and existing actor/time/version/transaction. Include key in all new authoritative Asset snapshots and frontend history field list. Older immutable events lacking the key remain unchanged; distinguish unavailable historical data from explicitly captured null, using current history rendering behavior. No fill from current Asset or retroactive JSON updates. Event failure must roll back value/version/time changes. No identifier history table or event type.

## 16. Search/filter/sort implications

Add one parameterized escaped ILIKE term to existing q OR predicate used for both scoped count and rows; reuse max-128 q literal behavior. Preserve default createdAt/id descending, current primary sorts/filter list, size=25/max100, no snapshot-consistent paging claim, no deleted filter. Test `%`, `_`, backslash, Unicode and case against PostgreSQL. Search case-insensitivity is independent of stored-case/equality policy. No separate localIdentifier sort/filter/index under approved L13/L18.

## 17. Frontend/UI implications

Use “KELIOS Asset Tag”, “Local identifier”, “Serial number”, “External inventory number” rather than labeling every value ID. Show Product name separately. Registration adds one optional text input with examples and approved length/character help, retaining derived Product dependencies and explicit loanable. Metadata includes the field, editable/clearable; list/search results show it secondarily beside tag; detail shows it separately; history shows captured changes. Missing values render the existing neutral placeholder. Do not replace URL UUID, tag heading or generate local values.

Keep current shared AuthSession, memory tokens, server pagination, context/session cancellation, draft preservation, safe text and no auto mutation retry. Client validation is usability only; Unicode code-point boundaries and normalization remain server authoritative. No image rebuild to change branding, no new framework/custom-field builder, no asset delete control or copied-transfer workflow. Wording/priority follow approved N1/L14; full bilingual conversion below is required in this phase.

### 17.1 Current hard-coded language inventory

There is no current i18n dependency or translation bundle in frontend/package.json. App.tsx hard-codes shell/connection/branding-alt text; Authentication.tsx hard-codes login/pending/session messages; auth.ts throws English messages and ApiFailure embeds backend code/detail. InventoryApp.tsx owns hard-coded navigation/forms/commands/pagers/confirmations/admin messages, often prints raw property keys and enum strings; inventory.ts owns history field labels and display helpers. Status text stored as English strings will need semantic state/codes so switching also updates already-visible statuses. Conversion must cover all these areas, accessible names, placeholder/help/error/empty/loading/success text and document language/title in main.tsx/index.html as appropriate. Primitive UI components need inspection for owned defaults, not gratuitous translation wrappers for text supplied by callers.

Stored AssetEvent/AuditLog payloads currently shown as JSON/data remain unmodified. Localized headings, keys and supported machine values can wrap their display; arbitrary notes, labels, actor snapshots, identifiers, borrower text or payload strings remain verbatim.

### 17.2 Minimal i18n implementation recommendation

Recommend **i18next + react-i18next**, exact compatible versions selected/locked during authorized implementation. Two small established dependencies provide static resource lookup/interpolation, English fallback and reactive switching across shared React components instead of a custom parallel language system. No HTTP backend, browser-detector plugin or translation-service dependency is needed. Version/dependency compatibility must be verified then; nothing is installed now. Official references: [React integration/context](https://react.i18next.com/latest/i18nextprovider), [resource/supported-language/fallback configuration](https://www.i18next.com/overview/configuration-options), [interpolation](https://www.i18next.com/translation-function/interpolation). This is a technical recommendation within approved I18N-9, not an unresolved business choice or implementation authorization.

Proposed `frontend/src/i18n.ts` initializes one shared instance before rendering. Static `frontend/src/locales/en.json` and `de.json` are bundled in the same image, with semantic nested keys such as common, auth, inventory, catalog, places, administration, audit, enums and errors; no component duplication. Use useTranslation at UI boundaries and interpolation for dynamic data, with React text escaping/no unsafe HTML or arbitrary user-supplied translation keys. Technical/auth services retain stable structured error reasons; localize only at presentation, not by throwing a locale-specific backend detail or changing transport/security.

### 17.3 Detection, switching and storage

Exactly en/de supported; English fallback. Initialize from a valid explicit localStorage preference (`kelios.ui.language`, value en/de only). With none, examine navigator.languages in preference order (navigator.language fallback): resolve en/de regional variants to base language and select the first supported preference; German selects de, English selects en. If no supported preference exists, choose en. Thus de-DE chooses de, fr alone chooses en, and [fr, de-DE] chooses de as the first supported preference; [en, de-DE] chooses en. No geolocation/Tenant/account inference.

A visible unauthenticated **English / Deutsch** switch changes the shared UI language immediately, sets document.lang and persists only the explicit en/de choice. Bad/unavailable storage falls back safely to browser detection and still permits switching in memory; persist no auto-detected identity/profile. No auth prerequisite, language API, User/Tenant setting, JWT or server session. Logout does not convert/remove this independent presentation preference; never store tokens/credentials in localStorage/sessionStorage. Names English/Deutsch are self-labels, stable across languages.

Missing resource keys fall back to English as a runtime safeguard; completeness gates must prevent incomplete German from being shipped as supported. Unknown machine values/error codes use a localized generic safe display/failure, without changing stored/API values or exposing raw exception details.

### 17.4 Translation and formatting boundaries

All normal frontend-owned text is translated: shell/navigation, tables/pagination, form/help/validation, loading/empty/status/confirmation/success/errors, authentication/pending, all inventory workflows/history, Catalog/Location/Owner, Tenant/User/OIDC administration, audit and Merge preview/confirmation. Map stable Lifecycle/Condition/reason/role/status/scope/AssetEvent types and all supported AuditLog actions to localized labels; form option **values** remain machine values and submitted commands remain unchanged. Local identifier/Lokale Kennung never changes property localIdentifier. KELIOS Asset Tag, Serial number and External inventory number get distinct German labels (KELIOS-Asset-Tag, Seriennummer, Externe Inventarnummer).

Known RFC 9457 codes, including INVALID_INPUT, AUTHENTICATION_REQUIRED, FORBIDDEN, NOT_FOUND, STALE_ASSET_VERSION, INVALID_TRANSITION, LOAN_GUARD, NO_CURRENT_LOAN, OUTSTANDING_LOAN, REFERENCED, PRODUCT_EDIT_FROZEN, PROTECTED_UNKNOWN, DELETED_TARGET, INVALID_DEPENDENCY, CONCURRENT_OPERATION, CONSTRAINT_CONFLICT and SERVICE_UNAVAILABLE, map to localized user messages. Include safe auth/throttling/network/client reason mappings; unknown/unmapped safe error gets a localized generic fallback. Preserve safe code/status diagnostics, not backend detail as the normal UI message; never log/display secret responses. Do not add backend Accept-Language handling.

Use Intl.DateTimeFormat/NumberFormat with selected en/de locale for ordinary display values, including history/audit instants; keep existing timezone meaning explicit and do not silently change timezone policy. Identifier strings, versions/reference keys used in commands, UTC/ISO transport and raw historical/user data remain unchanged. Input parsing and API serialization stay canonical; localization cannot send a German date/decimal or translated enum to existing endpoints.

Branding config/logo/product name remains deployment data and independent of language. Both bundled languages work with both brands on **one identical frontend image**, switching/mounting without rebuild. No extra runtime branding language field or dependency on translation network requests.

### 17.5 I18N-1–I18N-13 status

All approved: exactly en/de; browser first-use + public switch + English fallback; explicit browser preference only; complete frontend-owned text; unchanged machine enums; stable backend errors with UI mapping; locale presentation only; independent runtime branding; static maintained resources/no platform; completeness and representative bilingual tests; architecture/plan documentation; Local identifier/Lokale Kennung; no server locale/Tenant language/multilingual master-data/translation-service features. No genuine i18n product decision remains unresolved.

## 18. Migration implications

Plan the next additive migration **V6** after approval (suggested name `V6__asset_local_identifier.sql`). Do not edit V1–V5 or repeat V5's audit deletion. Existing Assets naturally receive null. Preserve all business/identity/history/audit/counter data; no inference from tag/serial/external inventory fields or destructive reset is needed. No production historical compatibility/repair service; absence in old events is handled by the existing rendering model. Fresh V1→V6 and populated V5→V6 structural tests must verify checksums, preservation, final column/type/nullability and any field-specific CHECK; assert no localIdentifier UNIQUE/index. No migration is created in this task.

## 19. Concurrency implications

Approved non-unique scalar design introduces no cross-row identifier predicate. Existing expectedVersion/@Version on metadata covers concurrent identifier edits and identifier-versus-other-Asset commands: one same-version winner, losing transaction rolls back event/state and returns conflict, no replay. Reuse existing snapshot reference locks/Merge affected-row versioning; no new global lock framework/SERIALIZABLE/retry. Test identifier edit versus Merge using independent PostgreSQL connections where the added field affects the snapshot/update surface; preserve winner identifier and correct snapshots whichever commits first.

## 20. Testing/verification strategy

**Planned only; no builds/tests/deployments executed as Phase 4 evidence during this task.** First build a Domain-invariant coverage matrix linking each acceptance item to current test method(s), level, assertions and any genuine gap. Existing backend suites: StateMachineTest, DomainFoundationIT, IdentityAuthenticationIT, Phase3InventoryIT, AuthPolicyTest, JwtAndBrowserTest, BusinessProblemsTest and StatusControllerTest. Frontend auth/branding/inventory unit suites plus authentication/inventory/branding Playwright suites are the baseline. Avoid duplicate implementation-mirroring tests.

| New/verification area | Required evidence after explicit implementation approval |
|---|---|
| Normalization/validation | Omitted/null/blank/clear, stripping/internal whitespace, case preservation, approved Unicode/control characters, code-point length boundaries; no coercion from number/object; both examples accepted. |
| Registration/metadata | Product/direct variants; no generation/copy from other identifiers; replacement omission M1; UUID/Tenant/tag unchanged; authorized role/status; stale version, same-value current command behavior, atomic snapshots and rollback. |
| Duplicate/equality policy | Same/different Tenant values, case variants, retained/DEREGISTERED/internal-deleted cases according to approved L1–L6/L16–L17; prove duplicates allowed without suggesting deduplication. |
| Retention/transfer | Deregistration and internal deletion preserve field; receiving registration defaults null and permits explicit input; no transfer linkage/copy workflow. |
| History/audit | REGISTERED/UPDATED before/after values/actor; old key absence rendered unavailable; immutable old events; no new ordinary-Asset audit; all accepted target-owned audit writer/read cases still pass. |
| Search/security | Literal wildcard escaping/case/Unicode/null; new q matches/combines with filters/count/page; Tenant SQL isolation and GLOBAL read; UUID tie-breaks unchanged; no caller-supplied target/actor authority. |
| Concurrency/migration | Real PostgreSQL/Testcontainers, independent competing edits/Merge, version/event rollback; populated V5→V6 preserving all data/checksums and fresh V1→V6; no uniqueness mechanism. |
| Frontend/browser | Registration, list/detail/metadata/history/search, distinguish labels/duplicate tags, validation/clearing, conflict draft/manual resubmit, Tenant/logout stale response, safe text, auth/branding/no delete regression. |
| Deployment/docs | Independent images/non-root/toolchain-free; healthy Compose, five-existing-plus-new migration validation; same-origin HTTPS/security; same image two brands; reproducible clean isolated deployment and docs/contract/config agreement. |
| Operations (S1 approved) | On disposable isolated data/volume, document and execute database restore plus required external-key availability and readiness/auth/data checks; secrets redacted. No retention/RPO/RTO policy or live-data destructive command. |

### Bilingual verification requirements (approved I18N-10)

Add deterministic recursive key-set equality for en/de, non-empty translations and matching interpolation placeholders; validate all supported enum/event/audit-action/problem-code mappings have both-language entries. Check referenced semantic keys exist; missing German keys fail tests rather than being hidden by English fallback. No brittle sentence-by-sentence suite.

Unit tests cover de/de-DE browser preference, unsupported fallback, ordered supported-preference selection, invalid/missing/blocked localStorage, explicit switching/reload, locale formatting without API mutation and known/unknown error mapping. Preference-storage assertions permit only the language key/value and forbid tokens/credentials. Representative UI/browser coverage spans unauthenticated login/pending, inventory registration/list/detail/metadata/history/loan/state/Return, Catalog/place/publication/Merge, Tenant/User/OIDC administration and audit in **both languages**. Assert localized enum display with stable submitted code, correct Local identifier/Lokale Kennung, unchanged data/identifier/snapshot strings and dates preserving source ISO values. Preserve role/Tenant/optimistic conflict/no-replay behavior across language switches; switching must not reset drafts or change session identity.

Run the **en/de × blue/green** branding matrix using the same frontend image ID; do not rebuild between locale/brand changes. Adapt existing English-text browser selectors narrowly with intentional locale cases/stable accessible roles, not by weakening security/workflow assertions. Fixture-only cases remain labeled; real PostgreSQL/backend security and deterministic OIDC regression still run independently. No actual IServ compatibility claim.

Proposed commands, from appropriate directories, with repository-local caches/TMPDIR: backend `./mvnw -B verify`; frontend `npm test`, `npm run build`; root new `./scripts/smoke-phase4.sh` writing only Phase 4 evidence. The new workflow is not present/implemented yet; adapt current Phase 3 checks/destinations, never rerun old evidence-writing scripts unchanged. Docker builder `-DskipITs verify` remains unit/build evidence only, not host PostgreSQL verification. Keep sandbox; request narrow access only when required. No sudo or unrestricted access.

Future `docs/verification/PHASE_4.md` / `docs/verification/phase4/` must record actual commands/versions/counts/pass-fail-skips, migrations/concurrency, frontend/browser/image identities/branding, restoration checks, failures/fixes, preservation and unsupported environments. Real IServ remains separate pending manual evidence. S1 approved envelope is existing local arm64/HTTPS + disposable restore; other architectures/ingress/CI are optional separately approved verification work, not promised completion.

## 21. Phase 0–3 regression requirements

Run full current Maven/PostgreSQL suites and frontend/build/browser suites; preserve graphs, loan terminal-state Return, retained-reference guards/Product history freeze, UNKNOWN, dependency/composite/deferred constraints, tags/UTC/concurrency, scoped queries and all audit writer variants. Preserve full JWT/CSRF/Origin/cookie/refresh/logout and documented IServ fixture tests without real-provider claims. Verify independent images, management isolation, unsupported API/static 404, current paths, runtime logo/colors and both brands on the identical frontend image.

Preserve historical ADR 0001–0004, V1–V5 and accepted Phase 0–3 evidence/manual procedure; changes are only additive migration/new approved specification and narrow implementation/test/docs updates after authorization. Accepted historical counts need not remain numerically fixed; new actual counts are recorded separately. Do not soften invariants to fit the extension.

## 22. Implementation sequence after explicit approval

1. Obtain **explicit Phase 4 implementation approval** for finalized scope; product decisions L1–L19/N1/M1/S1/I18N-1–I18N-13 are already resolved.
2. Re-read finalized Domain/Architecture/plan and record implementation choices in a new Phase 4 ADR at implementation time; preserve historical ADRs/evidence.
3. Establish invariant/test traceability and narrowly scoped stabilization gaps; prepare V6 and real PostgreSQL fresh/upgrade tests.
4. Extend entity/service/secured command/View/snapshots and contract; implement approved validation/replacement/version/event behavior.
5. Extend q and identifier UI/history; initialize static i18n resources, convert all frontend-owned text/code labels/errors and locale presentation with public language switch. Retain all session/context and command protections.
6. Add meaningful new unit/HTTP/PostgreSQL/concurrency/frontend/browser cases and confirmed stabilization coverage gaps.
7. Run full regression/image/HTTPS/branding/isolated deployment and approved operational drill; write current operational/API instructions and separate actual Phase 4 evidence.
8. Review implemented behavior against approved Domain/Architecture/plan, list remaining deferred items, stop for user acceptance. No Phase 5, commit/push/PR without separate approval.

## 23. Acceptance criteria

- Reviewed identifier semantics are traceable to explicit approval and authoritative Domain before implementation; no hidden uniqueness/normalization/transfer rules.
- One optional string field supports approved registration/edit/display/search, with independent identity concepts and no extra identifier infrastructure.
- ExpectedVersion, atomic REGISTERED/UPDATED, snapshot historical meaning and query/auth isolation are preserved; loan/state/identity and all Phase 0–3 behavior unchanged.
- V6 is additive, V1–V5 checksums unchanged, old business/audit/history/identity/counter data preserved; no reset, inference or repair.
- Domain invariant coverage is evidenced by real assertions; fresh full verification, image/deployment/branding/docs and approved operational checks pass with actual results and honest limits.
- Complete en/de supported UI, deterministic resource completeness, representative bilingual browser coverage and both languages/both brands on same image; no auth data in preference storage.
- No out-of-scope feature or assumed real IServ compatibility; historical evidence/ADRs untouched.
- Implementation completion and user acceptance remain separate. This finalized plan has **explicit implementation approval**; implementation authorization has now been granted; user acceptance remains pending review.

## 24. Final decision status and consistency review

L1–L19, N1, M1, S1, combined stabilization/localIdentifier scope and I18N-1–I18N-13 are **resolved/approved**. No genuine unresolved business decision or technical blocker was found. Exact field CHECK syntax, pinned compatible i18n versions, resource organization and test layout are conservative implementation details within these requirements, not new product gates. No uniqueness alternative belongs in the approved path. No Phase 4 implemented ADR is created now.

Consistency review against Domain/Architecture/Task/ADR 0001–0004/current OpenAPI/V1–V5:

| Checks | Result |
|---|---|
| 1–4 | Tag unchanged; localIdentifier nullable, duplicates allowed; no uniqueness mechanism. |
| 5–8 | Case preserved; Java strip/blank-null; max128 Unicode code points; Unicode human text with Cc/U+2028/U+2029 rejected. |
| 9–11 | Optional registration and mutable/clearable metadata replacement; omission/null clear; full four-field UI submission. |
| 12–14 | All new authoritative snapshots capture field; REGISTERED/UPDATED, no new event type or AuditLog action; old event JSON unchanged. |
| 15–17 | q includes field; no dedicated filter/sort/index; secondary UI label beside tag. |
| 18–20 | No automatic transfer copy; DEREGISTERED/internal soft deletion retain it; externalInventoryNumber independent. |
| 21–24 | V6 additive, V1–V5 untouched; no framework; accepted Phase 0–3 behavior preserved except explicit approved metadata/q extension; Phase 5 excluded. |
| Localization | Complete en/de presentation only; stable enums/codes/UTC/user data, browser preference separate from memory/HttpOnly auth, same-image branding; no backend locale/schema/API. |
| Stabilization | Coverage mapping/full regression/deployment/docs and isolated disposable backup/restore/key checks; no backup service or retention/HA/privacy policy. |

Original Task Phase 4 stabilization remains compatible and unchanged; user-approved metadata/i18n additions are traced here and in authoritative Domain/Architecture rather than rewriting historical ADRs. Historical pending-review status sentences predate Phase 3 acceptance. Current three-field code/OpenAPI and V5 schema are expected pre-implementation gaps, not contradictory final requirements; they change only after explicit approval. Relational retained-reference membership and target-owned AuditLog are unchanged.

The earlier specification-finalization step changed only DOMAIN.md, ARCHITECTURE.md and this plan. No production code, Flyway migration, OpenAPI, implementation tests, Phase 4 verification evidence/build/deployment, historical ADR/evidence rewrite, Phase 5, commit, push or PR.
