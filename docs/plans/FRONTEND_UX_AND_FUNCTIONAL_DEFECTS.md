# Remaining frontend UX review gates

Status: **APPROVED planning boundaries; no implementation gate authorized by this Plan.** Sidebar Phase A and interaction/Merge Phase B are HUMAN ACCEPTED / CLOSED; their accepted behavior, failures and limitations are in [sidebar history](../history/frontend-sidebar.md) and [interaction history](../history/frontend-interactions.md). F0–F6/B1/B2 frontend/backend integration and O2 Product Name width are also closed; see [integration history](../history/frontend-integration.md) and [O2 history](../history/o2-product-width.md). [Remaining work](remaining-work.md) owns present status and dependencies.

All review gates preserve [Domain](../../DOMAIN.md), [architecture](../../ARCHITECTURE.md), backend authorization, UUID identity, server-side query semantics, mounted React node/focus/selection/scroll stability, pending versus applied criteria, latest-response/context guards and ephemeral mutation sessions. No mutation auto-replay, universal mapper, operator-data seeding or accepted A/B/integration redesign is authorized here.

## O3 / Phase C — creation-path diagnosis

**HUMAN ACCEPTED / CLOSED on 2026-10-20 — investigated, not reproduced; no justified repair.** The historical Asset/Location/Owner creation report preceded the accepted Assets collection/create correction; the Asset symptom was closed separately in [frontend interaction history](../history/frontend-interactions.md#assets-collection-and-creation-iteration). Read-only Location/Owner diagnosis on clean HEAD `e415a7686d410c18deb7bf448ba5c4382810af06` traced the shared frontend Create form, concrete-Tenant POST routes, authorization, validation, transactional persistence and AuditLog. Current OpenAPI/source and existing intercepted Chromium and isolated PostgreSQL tests support successful authorized creation for both resources. In Global context, Save without a selected concrete Tenant is correctly disabled; root aggregate reads grant no write authority. The historical live review had no concrete Tenant and recorded no failing request, response or build identity. Its actual failure cause remains **UNKNOWN**: this conclusion does not prove that it never occurred or establish a present production defect. No tests or live writes were run for this read-only diagnosis, and no code, contract or operator data changed. A future concrete reproduction would require its own bounded repair authorization.

## O4 / Phase D — Audit actor presentation review

**OPEN — presentation review only.** The accepted actor display target is stored historical `actorDisplayName` with stable actor UUID for identity/correlation/filtering. Audit target Tenant is captured at mutation. Existing backend/DTO and frontend detail already expose these values; first establish whether any presentation, accessibility or correlation gap remains. If none, close with evidence. Do not infer a username/Email identity key, live User join, actor schema migration, historical backfill or TENANT username rule.

## O6 — broader table/filter presentation design

**OPEN — design decision first.** Consider supported column-associated filter presentation beyond accepted F3 controls. O2's 390px Product Name width is closed and separate. The design must use only actual resource-specific backend queries, preserve applied-query/paging coherence and stable nodes/focus/scroll, and avoid fake client filtering/sorting. Do not implement until the product/control-placement decision and a separately approved bounded gate exist.

## O7 — bounded auth/admin error-contract review

**OPEN — review first.** Inspect optional/missing problem codes and non-401 `AuthFailure`/`FORBIDDEN` mapping against current backend and frontend feedback. This is debt noted in the accepted integration/UX work, **not** evidence of an authorization bypass. Record a specific mismatch before proposing a correction; retain safe unknown/transport-uncertain behavior and no automatic mutation replay.

## BQ3 / Phase E and test boundary

A residual C/D checkpoint is **FUTURE / DECISION REQUIRED** after the O4 outcome; closed O3 supplies no repair scope. Do not repeat accepted F6 integration. O5's isolated real-write-suite modernization is separately OPEN in [remaining work](remaining-work.md) and must never target operator data. Automated intercepted fixtures establish UI control flow, not backend SQL/security. Real school IServ interoperability remains [blocked on operator prerequisites](../operations/iserv-interoperability.md). Each approved future gate must record exact focused tests, runtime/manual limits, human acceptance and its own closeout; this Plan itself does not authorize implementation.
