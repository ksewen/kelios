# Remaining Domain/query implementation decisions

Status: **APPROVED contract; no implementation gate authorized by this Plan.** M1, backend Steps 2–9, I1, Step 7/8 corrections, B1/D29 and B2/D28, plus frontend F0–F6 and dependent routing/Product-feedback work are **HUMAN ACCEPTED / CLOSED**. Their dated decisions, corrections, verification counts and limitations are in [backend history](../history/backend-domain.md) and [frontend integration history](../history/frontend-integration.md). The [remaining-work index](remaining-work.md) owns current status. This Plan keeps only the still-deferred I2 and X1 design boundaries; it does not reopen accepted collections or detail APIs.

## Accepted contract that future work must preserve

[DOMAIN](../../DOMAIN.md) §§1–2, 8–9, 12–15 and [ARCHITECTURE](../../ARCHITECTURE.md) §§4–5, 12–16 govern authoritative Tenant scope, resource commands and typed queries. Controllers parse bounded endpoint-owned criteria; application services derive identity/authorization/effective Tenant; persistence applies combined predicates, full-result ordering, count and pagination. Persistent UUID is detail/reference identity. Association labels are readable projections, never authority. List/page defaults and per-resource q/filter/sort rules are defined there and in [OpenAPI](../api/openapi.yaml). Do not add a universal CRUD/query framework, fake frontend paging, write-time metadata input, historical-data compatibility machinery or implicit Tenant/Provider authority.

The backend scope is accepted, including GLOBAL USER read-only Tenant collection/detail, ACTIVE ADMIN mutation boundaries, physical OIDC configuration DELETE, public routing batch20 and Product 409 `CONCURRENT_OPERATION` versus Asset `STALE_ASSET_VERSION`. Frontend integration is accepted with its intercepted/live limitations. These statements are current status, not new implementation authorization.

## X1 — future Excel export (T1)

**FUTURE / DECISION REQUIRED.** First approve the resource/bounds/delivery design, then separately authorize implementation. Export the complete authorized result for applied filters, never the current frontend page. Backend reauthorizes at dispatch and reuses authoritative predicates; readable associations are presentation and IDs may supplement them. Decide row/time/memory/concurrency bounds, sync versus stream/job delivery, truncation/refusal and error behavior. Apply formula-injection protection to every user-controlled spreadsheet value, exclude secrets and do not broaden interactive Asset sorts just for spreadsheet analysis. Future backend/API/UI work needs authorization, resource-bound and formula-safety tests. No Excel generation is approved now.

## I2 — real Email delivery and verification (T2)

**FUTURE / DECISION REQUIRED.** I1's manual verified GLOBAL Email identity, administrator bootstrap/recovery and credential policy are accepted. Real delivery/verification, Email-change validity, Email reset and self-registration require separate product/security design for token lifetimes/one-time use, approval/activation, abuse controls, configuration/redaction and delivery failures. Do not create a parallel credential lifecycle or give TENANT OIDC users local Email/password login. No mail integration or sending is approved now.

## Gate discipline

T1 and T2 are independent future design gates and are not sequenced by this document. A human decision on the particular design must precede implementation authorization and focused verification. O3/O4/O6/O7 and blocked real-provider evidence have their own owners in [remaining work](remaining-work.md); this Plan gives them no authorization. No closed gate becomes pending because its old execution report used that word at the time.
