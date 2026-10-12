**Current decision: U1 HUMAN RESOLVED / CLOSED, 2026-10-12. Prior pending-projection approval discussion below is historical audit evidence. Complete existing Tenant collection/detail representation is approved for ACTIVE GLOBAL USER.**

# GLOBAL USER read authority — bounded audit

2026-10-12. Documentation/static inspection only. Backend baseline `939c7373552d676822d0f9d0fe92798fa8618100`. No authorization implementation, runtime verification or new test run. [Integration plan](../plans/FRONTEND_BACKEND_INTEGRATION_PLAN.md) owns execution.

## 1. Authority and conclusion

Human clarification: inability to administer does not imply inability to read. GLOBAL USER starts in Global, has a Tenant selector offering authoritative ID/name, may lazily load it on opening, and enters ordinary business context by UUID without identity/permission change. Administration destinations/actions remain independently restricted. TENANT identities remain signed-own and never switch. Previous A/B/C workarounds are rejected.

**CONFIRMED current restriction:** Tenant list allows only ACTIVE GLOBAL ADMIN; detail allows ACTIVE GLOBAL ADMIN or own ACTIVE TENANT ADMIN. This implements the previous accepted contract, not the revised intended ordinary GLOBAL read behavior. Neither HTTP routing nor the word administration proves that a GET should remain ADMIN-only.

**Assessment:** existing Tenant GET is a safe ordinary-read candidate; reuse is the smallest correction. It contains no configuration/account secrets. The exact metadata disclosure below must be included in human approval; this audit does not silently authorize it. No demonstrated unsafe secret field requires a new endpoint. If human policy excludes actor attribution/timestamps from ordinary readers, approve a role-specific reduced `{id,name}` projection on the existing GET before implementation, rather than inventing `/tenant-contexts`. Whether GLOBAL USER detail should also be opened is an explicit approval boundary: collection is required by the clarified UX; safe detail is plausible but not necessary for the selector.

## 2. Exact Tenant representation and guards

Sources under `backend/src/main/java/com/github/ksewen/kelios/`:

- `platform/security/TenantDtos.java:15–21`: Item and Detail have identical JSON fields `id`, `name`, `metadata`. Metadata: `createdBy` UUID, `createdAt` UTC instant, `updatedBy` UUID, `updatedAt` UTC instant, `createdByDisplayName`, `updatedByDisplayName`. The display-name fields resolve only the reserved system/seed actor; ordinary human actor IDs yield null (`audit/SystemActor.java:12`). No live User lookup, Email, credentials, role/membership, issuer, client ID/secret, allowed groups or provider state.
- Metadata is provenance, not permission. Actor UUIDs permit correlation and timestamps reveal creation/update timing; selector needs neither. Similar safe business metadata is already projected by ordinary business resources, but the new Tenant-read approval must explicitly account for these fields. This is bounded disclosure, not a claim that all internal metadata is public.
- `tenancy/TenantReads.java:18–25`: selects only ID/name/four persisted metadata fields; unpaged list ordered createdAt DESC. Detail applies signed Tenant predicate before selecting; foreign and absent use same404. No audit on GET.
- `tenancy/TenantOperations.java:14–18`: list calls `Authorization.globalAdmin`; detail calls `Authorization.administrator` and forces TENANT actor Tenant; create separately calls globalAdmin inside its transaction. `platform/security/IdentityAdminController.java:14–16` parses/maps, delegates authority. No method-role annotation. `SecurityConfiguration.java:47–49` requires authentication on these routes; application service owns role/status/scope, so security is layered, not annotation-based role enforcement.
- Tenant collection accepts no query parameters (`TenantDtos.query`); response is array, intentionally unpaged. Detail ID is independent of list. POST takes only name, rejects overposting, emits UUID and atomic TENANT_CREATED audit. No Tenant edit/delete commands (`TenantResourceIT.undefinedMutationsAreNotExposed`).

Smallest proposed correction, **NOT AUTHORIZED**: resource-local read predicates allowing ACTIVE GLOBAL USER as well as GLOBAL ADMIN for list, and (if approved) detail; retain ACTIVE requirement, TA own-detail/non-disclosure, no TA collection, TU denial. Do not weaken shared administrator/globalAdmin helpers, create, User/OIDC/Audit services or concrete-Tenant writes. No schema change needed. Tenant principals are unaffected if predicates branch explicitly on scope. Add direct service and HTTP tests for GU success, denied mutations, status denial, unchanged TA/TU and anonymous behavior, exact safe projection/no-read-audit. Keep administration navigation hidden from GU even if safe GET opens.

Restrictive tests: `backend/src/test/java/com/github/ksewen/kelios/identity/auth/TenantResourceIT.java:81–89` authoritativeServiceScopeAndStatus and `:112–120` revisedHttpAdministrationMatrix encode GU list/detail403. Tests at65–79 cover projection equality, no-read-audit and foreign/absent. Revise only approved read cases; preserve create403 and guards. Existing tests are evidence of current behavior, not an argument against revised intent.

## 3. Read classification matrix

| Resource / endpoint | Current read authority | Projection | Intended/safe GLOBAL USER read? | Mutation authority | Required change | Confidence |
|---|---|---|---|---|---|---|
| GET /api/v1/tenants | ACTIVE GA | Unpaged Item array: ID/name/metadata above | Safe ordinary-read candidate; selector required, metadata approval explicit | POST GA; edit/delete undefined | Approve and widen resource-local GLOBAL read only; reuse API | CONFIRMED shape/guard; assessment pending approval |
| GET /api/v1/tenants/{id} | ACTIVE GA or own TA | Same fields as Item | Safe candidate; not required for selector; human choose whether to widen alongside collection | Same unchanged | If approved GU UUID detail, preserving TENANT isolation | CONFIRMED; approval boundary open |
| GET /api/v1/tenants/{id}/oidc | ACTIVE GA concrete Tenant or own TA | tenantId, issuer, clientId, secretConfigured, enabled, version, allowedGroupIds, callbackUri, metadata, tenant{id,name} | Administration-sensitive configuration/eligibility topology even though secret-safe; retain ADMIN-only | Same admin scope PUT/physical DELETE | None proposed | CONFIRMED |
| GET /api/v1/users | ACTIVE GA or TA forced signed Tenant ordinary USER population | Page: ID, scope, tenantId, role, status, displayName, tenant{id,name}, metadata | Administration-sensitive identity/status enumeration; retain ADMIN-only | Existing guarded commands, GA credential/Email | None; ordinary person directory would need separate product decision, not reuse assumed | CONFIRMED |
| GET /api/v1/users/{id} | Same independently authorized admin scope | Item fields plus globalAccount{email,emailVerifiedAt} | Administration-sensitive account detail; retain ADMIN-only | Existing commands/self-protection | None proposed | CONFIRMED |
| GET /api/v1/audit-log and /{id} | ACTIVE GA; TA captured signed-Tenant attribution | Page/item ID, tenantId, actorId, actorDisplayName, action,targetType,targetId,occurredAt,data | Administration-sensitive historical/security operations, retain ADMIN-only | Immutable; writers owned by commands | None proposed | CONFIRMED |
| GET /api/v1/auth/me | Authenticated current identity | Own Identity id/scope/tenantId/role/status/displayName | Already ordinary own-session read; not account directory | Own session commands | ALIGNED, no broader discovery | CONFIRMED |
| GET /api/v1/auth/oidc/organizations | Public authentication routing | Enabled routing organization ID/name Page | Not authenticated membership/context discovery | No admin mutation | Preserve explicit provider routing; never use for selector | CONFIRMED |

OIDC evidence: `OidcConfigurationDtos.java:14–17`, `OidcConfigurationService.java:43–53`, `OidcConfigurationIT` safe-projection/admin-scope tests. User: `UserDtos.java:23–34`, `AuthenticationService.java:60–74`, `UserAdministrationIT` scope/status tests. Audit: `AuditDtos.java:23–25`, `InventoryOperations.java:40–42`, `AuditController.java:8–9`; Q3 Audit query/security tests preserve captured scope. Configuration secret ciphertext never enters Detail; absence of secrets alone does not establish ordinary-user need.

No additional administration-labelled business GET was found requiring widening: Catalog/Place/Asset collections/details already permit appropriate ACTIVE ordinary read independent of write ADMIN checks. Asset history is owning-resource business history, not Audit access. Public status/branding/authentication GETs are not administration directories. No decision about new ordinary User/provider/Audit projections is inferred here.

## 4. Frontend and documentation impact

`frontend/src/app/Shell.tsx:19–43` currently requests Tenant list unconditionally, presents dropdown to every GLOBAL role, exposes Tenant admin destination to every GLOBAL, and derives TENANT name from that same list. Revised GU selector can legitimately reuse list only after backend correction; GU administrative navigation/actions must still disappear. TA/TU must not use list; their signed context remains authoritative. `Dashboard.tsx:10–11` unconditionally uses Tenant list for names/count: permitted read does not automatically approve an admin count/widget for GU. F1b must distinguish context-label use from administration presentation. `pages.tsx:167–175` contains Tenant/User admin consumers; no changes here.

Current ARCHITECTURE.md administration section, DOMAIN.md administration/resource matrix, docs/AUTHENTICATION.md role matrix and backend plan Step7 correction record still state the older restrictive read policy. They must be narrowly reconciled in a separately approved normative consolidation. Historical reports remain historical. This task edits only the requested integration plan/decision preparation and this audit. No globally reopened backend program; proposed Tenant read correction is independent of B1/D29 and B2/D28.

U1 UX is now specified, not a choice among rejected workarounds. Remaining approval: existing complete Tenant GET projection versus role-specific reduction, and collection-only versus collection+detail read widening. U2 canonical routes/presentation is HUMAN RESOLVED / CLOSED,2026-10-12. F1b stays NOT AUTHORIZED.

## 5. Verification boundary

Static controllers/services/security/DTOs/read SQL/tests and frontend callers inspected. No broad suite rerun needed; no current-backend/runtime evidence claimed. Documentation links, task-delta preservation and whitespace checks are recorded in the task response. No implementation, authorization change, environment or business data change.

## Approved U1 resolution — 2026-10-12

Complete existing Tenant collection/detail ID/name/metadata projection and bounded actor UUID/timestamp disclosure approved for ACTIVE GLOBAL USER. No new endpoint/reduced DTO. GA-only create; TA own detail/no list; TU/status/anonymous denial unchanged. Other admin reads unchanged. U1 HUMAN RESOLVED / CLOSED. Global-first UUID/name dropdown optionally lazy loaded is approved future F1b behavior; identity/admin authority unchanged. [Backend correction](../verification/TENANT_GLOBAL_USER_READ_CORRECTION.md) HUMAN ACCEPTED / CLOSED, 2026-10-12. F1b/D05–D07 HUMAN ACCEPTED / CLOSED, 2026-10-12. Original F1b evidence/limitations preserved; no later gate authorized.
