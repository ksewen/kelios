# Remaining frontend UX review gates

Status: **APPROVED planning boundaries; no implementation gate authorized by this Plan.** Sidebar Phase A and interaction/Merge Phase B are HUMAN ACCEPTED / CLOSED; their accepted behavior, failures and limitations are in [sidebar history](../history/frontend-sidebar.md) and [interaction history](../history/frontend-interactions.md). F0–F6/B1/B2 frontend/backend integration and O2 Product Name width are also closed; see [integration history](../history/frontend-integration.md) and [O2 history](../history/o2-product-width.md). [Remaining work](remaining-work.md) owns present status and dependencies.

All review gates preserve [Domain](../../DOMAIN.md), [architecture](../../ARCHITECTURE.md), backend authorization, UUID identity, server-side query semantics, mounted React node/focus/selection/scroll stability, pending versus applied criteria, latest-response/context guards and ephemeral mutation sessions. No mutation auto-replay, universal mapper, operator-data seeding or accepted A/B/integration redesign is authorized here.

## O3 / Phase C — creation-path diagnosis

**OPEN — diagnosis only for Location/Owner.** The historical Asset/Location/Owner creation report preceded the accepted Assets collection/create correction. The Asset Create interaction was corrected and HUMAN ACCEPTED / CLOSED on 2026-10-15; its decisions and limits are in [frontend interaction history](../history/frontend-interactions.md#assets-collection-and-creation-iteration). Do not reopen it as an unresolved Phase C symptom. For Location/Owner, reproduce with isolated/intercepted responses first and distinguish context visibility, CTA, dialog, required validation, request, status/error and navigation. Exercise active GLOBAL ADMIN with no selected Tenant and with a concrete Tenant, signed-own TENANT ADMIN, ordinary USER, PENDING, invalid inputs and wrong-Tenant attempts. The GLOBAL aggregate view cannot authorize a Tenant-owned write; the backend remains authoritative. Only if a concrete failure is confirmed may a separate bounded repair be proposed. Do not invent endpoints, weaken guards or create operator business data to manufacture evidence.

## O4 / Phase D — Audit actor presentation review

**OPEN — presentation review only.** The accepted actor display target is stored historical `actorDisplayName` with stable actor UUID for identity/correlation/filtering. Audit target Tenant is captured at mutation. Existing backend/DTO and frontend detail already expose these values; first establish whether any presentation, accessibility or correlation gap remains. If none, close with evidence. Do not infer a username/Email identity key, live User join, actor schema migration, historical backfill or TENANT username rule.

## O6 — broader table/filter presentation design

**OPEN — design decision first.** Consider supported column-associated filter presentation beyond accepted F3 controls. O2's 390px Product Name width is closed and separate. The design must use only actual resource-specific backend queries, preserve applied-query/paging coherence and stable nodes/focus/scroll, and avoid fake client filtering/sorting. Do not implement until the product/control-placement decision and a separately approved bounded gate exist.

## O7 — bounded auth/admin error-contract review

**OPEN — review first.** Inspect optional/missing problem codes and non-401 `AuthFailure`/`FORBIDDEN` mapping against current backend and frontend feedback. This is debt noted in the accepted integration/UX work, **not** evidence of an authorization bypass. Record a specific mismatch before proposing a correction; retain safe unknown/transport-uncertain behavior and no automatic mutation replay.

## BQ3 / Phase E and test boundary

A residual C/D checkpoint is **FUTURE / DECISION REQUIRED** until O3/O4 outcomes are known; do not repeat accepted F6 integration. O5's isolated real-write-suite modernization is separately OPEN in [remaining work](remaining-work.md) and must never target operator data. Automated intercepted fixtures establish UI control flow, not backend SQL/security. Real school IServ interoperability remains [blocked on operator prerequisites](../operations/iserv-interoperability.md). Each approved future gate must record exact focused tests, runtime/manual limits, human acceptance and its own closeout; this Plan itself does not authorize implementation.
