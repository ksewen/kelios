# ADR 0004 — Phase 3 inventory commands and target-owned administrative audit

Status: Accepted technical decisions; implementation explicitly authorized by the user. Phase 3 user acceptance remains pending review of implementation evidence.

Date: 2026-10-10

## Context

Phases 0–2 and Phase 3 P1–P8 / DEREGISTERED convention are accepted specifications. Phase 1 provides persistence, reference guards, Merge, tags and the independent state engine. Phase 2 provides authentication and explicit scope/role/status authorization. Phase 3 exposes usable inventory and administration without changing these foundations. KELIOS has not been deployed; pre-Phase-3 development audit rows are disposable.

## Decisions

- Concrete authenticated `/api/v1` controllers call explicit secured inventory application operations. Asset registration, metadata, Catalog, loanability, Lifecycle, Condition, Location, Owner, loan and Return remain separate commands with closed input fields. Server loads references and supplies ownership, actors, generated identity and time. Every mutable Asset command requires expectedVersion; no generic PATCH or Asset deletion API/UI.
- Browser detail uses `/inventory/assets/{id}`, avoiding the accepted nginx `/assets/` static bundle namespace and preserving missing-static-file 404s without deployment changes. API detail remains `/api/v1/assets/{id}`.
- Tenant collection paths `/tenants/{tenantId}/...` are authorized operating context. GLOBAL principals can select a Tenant or use the GLOBAL-only all-Tenant Asset read. TENANT principals remain fixed to their signed authenticated Tenant. No membership, JWT mutation or Tenant session.
- SQL-scoped bounded offset paging: page zero, size 25/default and 100/maximum. Asset fixed filters/sorts, default createdAt/id descending, deterministic UUID ties, literal escaped case-insensitive substring search on tag/serial/external number, q at most 128 characters. No snapshot-consistent pagination guarantee or search infrastructure.
- Reuse READ COMMITTED, reference FOR SHARE, guarded target FOR UPDATE, fresh guard queries and Asset @Version. New workflow mutation and corresponding immutable AssetEvent commit together; losers roll back and receive explicit conflict without silent retries. No universal lock/isolation framework or deadlock-freedom claim. Preview holds no lock across UI interaction and reserves nothing; Merge execution discovers/validates fresh.
- Deregistration emits exactly one DEREGISTERED event with structured reason; other lifecycle transitions emit LIFECYCLE_CHANGED. Loans do not alter either approved graph or automatically clear fields/move Assets. Return checks only current loan, clears four loan fields and emits RETURNED. Loanability rejects an outstanding loan.
- V5 clears only disposable audit_log rows, adds nullable historical tenant_id UUID and `(tenant_id, occurred_at DESC, id DESC)` index. V1–V4 remain unchanged; all other data is preserved. No historical backfill/repair, AuditScope, legacy marker or third attribution state.
- AuditLog captures known target ownership explicitly at write time: creation new target; existing mutation pre-operation target; publication pre-publication Tenant; Category/Manufacturer Merge source pre-operation ownership. Null means known GLOBAL/installation ownership, never unknown. Actor snapshots are independent. The attribution-less writer overload is removed. All supported writer variants use the same transaction as the administrative change.
- Historical AuditLog Tenant UUID has no live Tenant FK. CASCADE/SET NULL would corrupt historical meaning; RESTRICT would predetermine an undefined future Tenant deletion policy. This authorizes no Tenant deletion today. Immutable mapping and supported write-only service boundaries preserve attribution; no new generic triggers.
- ACTIVE GLOBAL ADMIN can query all audit, optionally narrowing. ACTIVE TENANT ADMIN selection/count/detail include authenticated tenant_id equality in SQL before pagination; out-of-scope detail equals absent. USER/PENDING cannot read audit. No live-target joins or actor-derived visibility, Merge audit fan-out or ordinary AssetEvent duplication.
- Frontend reuses one AuthSession, memory Access Bearer and existing HttpOnly Refresh/CSRF. Explicit forms preserve drafts on conflicts/uncertain outcomes and never replay mutations. Stale read effects/session generations cannot restore obsolete Tenant/logout data. TanStack Table uses existing v9 APIs and shadcn/Tailwind controls; no new dependency/framework. Branding remains runtime-mounted.
- Existing Phase 2 Tenant/user/OIDC APIs gain minimal UI without adding account lifecycle/privacy/authentication policies. OIDC secrets remain write-only. Real IServ interoperability remains separately pending manual verification.

## Consequences and validation

Independent images and the accepted JWT/OIDC foundation remain intact. Internal Asset soft deletion and retained reference rules remain available but unexposed. Backend constraints and service guards remain authoritative; frontend controls are usability only. API/schema/transaction/query, deterministic OIDC, browser and image evidence are recorded in [Phase 3 verification](../verification/PHASE_3.md). Historical ADR 0001–0003 are not rewritten.
