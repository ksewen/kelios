# Backend authentication operations

**Accepted authentication and administration implementation.** I1 GLOBAL Email identity, Steps 6–9 and the Step 7/8 authorization/physical DELETE corrections are HUMAN ACCEPTED / CLOSED. Frontend integration is [HUMAN ACCEPTED / CLOSED](../history/frontend-integration.md). Public OIDC routing alignment [B1/D29](../history/backend-domain.md), CLI Email handoff [O1](../history/deployment-cli.md), and the real supported install/status/uninstall [DCLI-B1 closeout](../history/deployment-cli.md) are HUMAN ACCEPTED / CLOSED. O1's isolated verification and later DCLI live installation remain distinct; no real Email delivery or Provider-interoperability claim is added. Current requirements are defined by [DOMAIN.md](../../DOMAIN.md) and [ARCHITECTURE.md](../../ARCHITECTURE.md); [backend history](../history/backend-domain.md) preserves dated gate results.


KELIOS supports same-origin HTTPS browser authentication. Separate frontend/backend images are retained: nginx or a trusted production ingress proxies `/api/`. CORS/cross-site authentication and subpaths are not supported. Public branding JSON contains no authentication configuration or secrets.

The [OpenAPI contract](../api/openapi.yaml) describes authentication and identity administration. [ADR 0003](../adr/0003-phase-2-authentication-and-authorization.md) records the original design; [Phase 0–4 history](../history/phase0-4.md) records its accepted results. Inventory and later backend/frontend implementation are separately accepted in the linked histories.

## Mounted keys and origin

Supply these **deployment-owned** files through a private secret mount readable only by the runtime administration boundary and the unprivileged backend process:

| File in `KELIOS_AUTH_KEY_DIRECTORY` | Format / use |
|---|---|
| `<signing-kid>.private.pem` | PKCS8 RSA private key, minimum 3072 bits; RS256 signing |
| `<kid>.public.pem` | X509 SubjectPublicKeyInfo RSA public key; only mounted trusted IDs can verify tokens |
| `<encryption-kid>.aes` | Base64-encoded 32-byte random AES/HMAC key; AES-256-GCM authenticated envelopes and purpose-separated signed CSRF proof |

Select active IDs with `KELIOS_AUTH_SIGNING_KEY_ID` / `KELIOS_AUTH_ENCRYPTION_KEY_ID` (default `main`). IDs contain ASCII letters/digits/underscore/hyphen, 1–64 characters. Mount at `/run/secrets/kelios-auth` or set backend `kelios.auth.key-directory`. Never place keys in source, image, database, frontend config or evidence. Do not reuse local smoke fixture keys in production. Use managed secrets and suitable host/mount ownership; local file-mode examples do not replace production ownership controls.

`KELIOS_AUTH_PUBLIC_ORIGIN` is the exact externally reachable HTTPS origin, including any non-default port. `KELIOS_AUTH_ISSUER` is the explicit KELIOS JWT issuer; consumers verify it and fixed audiences `kelios-api` / `kelios-refresh`. Callback URIs are derived from trusted public origin and Tenant UUID, never caller Host/forwarded headers. Access is 15 minutes; Refresh 30 days **absolute**, unchanged by refresh. Accepted skew is 30 seconds; maintain synchronized deployment clocks.

Key rotation requires externally supplied matching private/public material for the new signing ID and retaining old trusted public keys for intended outstanding-token lifetime. Removing a verification key invalidates its tokens. Retain old AES files while ciphertext/flow cookies use their IDs; updating OIDC configuration re-encrypts retained client secrets under the current AES ID. No automatic key generation/rotation or revocation database exists. Back up authentication encryption keys together with protected database backups; losing a key requires explicitly replacing affected provider secrets through authorized PUT, not an invented reset endpoint.

## HTTPS Compose overlay

Base `deploy/compose.yaml` retains HTTP technical/health/branding regression. Authenticated browsers require HTTPS. `deploy/compose.auth.yaml` adds nginx TLS without rebuilding either image. Supply deployment certificate `certificate.pem` and private key `private.pem` in `KELIOS_TLS_DIRECTORY`, plus the separate backend authentication key directory. The overlay's HTTPS host port is loopback `KELIOS_HTTPS_PORT` (default 8443); production may terminate TLS at an ingress with the same approved origin boundary instead.

```sh
# Set paths/origin/issuer to actual private deployment configuration; no secret values here.
docker compose -f deploy/compose.yaml -f deploy/compose.postgres.yaml -f deploy/compose.auth.yaml config --quiet
docker compose -f deploy/compose.yaml -f deploy/compose.postgres.yaml -f deploy/compose.auth.yaml up -d --wait
```

The backend key directory is mounted read-only. Keep backend/PostgreSQL/management endpoints private. For private-network IServ or explicitly trusted secondary endpoint hosts, supply exact comma-separated `KELIOS_AUTH_TRUSTED_PROVIDER_HOSTS`. Provider calls otherwise reject private/loopback/link-local addresses at connection-time DNS resolution, and metadata endpoints outside the issuer host. No wildcard hosts. HTTPS hostname and certificate verification remains mandatory, including trusted private hosts.

For an administrator-operated private CA, mount a PKCS12 **trust store** inside the key/secret directory and set `KELIOS_AUTH_PROVIDER_TRUST_STORE` to its container path, optionally `KELIOS_AUTH_PROVIDER_TRUST_STORE_PASSWORD`. This configured store replaces provider TLS default trust; include every necessary root. Never use a trust-all setting. Verified-configuration metadata/JWKS caches are bounded to 128 entries each with 60-second expiry; a new signing key may fail closed until the bounded cache expires. UserInfo/membership is never cached. Provider network responses are bounded to 128 KiB, 5-second connection/pool waits and 10-second response timeout; redirects/retries/cookie storage are disabled.

## First installation and zero-admin recovery

Recovery is an **explicit deployment command**, not normal startup or HTTP. Operator supplies canonicalizable login identifier, display name and a private mounted UTF-8 password file. Preserve exact bytes without an incidental newline; passwords are not trimmed. Do not use password command arguments/environment values, defaults or checked-in secrets. The file is an input, not a permanent recovery credential.

Run a one-off backend with the same database/secret configuration:

```sh
docker compose -f deploy/compose.yaml -f deploy/compose.postgres.yaml -f deploy/compose.auth.yaml run --rm --no-deps backend \
  --spring.main.web-application-type=none \
  --kelios.recovery.enabled=true \
  --kelios.recovery.email=admin@example.org \
  '--kelios.recovery.display-name=Operator chosen display name' \
  --kelios.recovery.password-file=/run/secrets/kelios-auth/operator-password
```

This locks a singleton PostgreSQL guard, freshly checks GLOBAL/ADMIN/ACTIVE users with verified Email and usable GLOBAL credentials, and atomically creates exactly one GLOBAL/ADMIN/ACTIVE User, credential and `GLOBAL_ADMIN_BOOTSTRAPPED` AuditLog when the count is zero. Repeating while a usable administrator exists **refuses**, without creating anything. The mutex stores no completion marker: the same command supports later zero-usable-admin recovery. Missing/malformed credentials do not count; forgetting an otherwise valid stored password does not make the stored credential unusable.

Remove the private input password file after operator-controlled completion. The actual supplied password is the credential; no forced first-login change or temporary-password state. Normal GLOBAL ADMIN self-disable/demotion is rejected; removal is not exposed. Other administrators may disable/demote accounts without a permanent last-admin invariant. No email recovery, reusable recovery token or automatic rebootstrap exists.

## Implemented GLOBAL Email account and administration behavior

GLOBAL-only local credentials use Email, separate from displayName and exact Tenant issuer/subject. The practical ASCII mailbox contract accepts a dot-atom local part (maximum 64 characters) and dotted DNS-style domain, at most 254 characters overall. Outer whitespace is stripped; Email is stored lowercase and a PostgreSQL lower(email) unique index enforces case-insensitive uniqueness. No provider-specific alias/plus-address rewriting occurs. Quoted mailboxes, internationalized mailbox/domain input and local-only domains are not supported by this implementation. No mail is sent.

Bootstrap/admin creation and reset/edit stamp emailVerifiedAt with the authoritative UTC Clock; null means unverified. ACTIVE is the existing approved/active state; PENDING does not grant GLOBAL login. DISABLED denies login. GLOBAL login and Refresh require ACTIVE plus verified, usable credential; approval alone never fabricates verification. TENANT PENDING minimal authentication and exact OIDC mapping are unchanged. Already-issued Access snapshots still expire naturally.

POST /api/v1/auth/local/login accepts only email/password. POST /api/v1/users/global accepts email/displayName/password and creates GLOBAL/USER/ACTIVE. PUT /api/v1/users/{id}/local-credential resets email/password. PUT /api/v1/users/{id}/email is GLOBAL ADMIN-only, changes Email without changing the hash, applies pre-delivery manual verification and updates User metadata/AuditLog atomically. GET User detail exposes only a safe globalAccount {email,emailVerifiedAt} projection (null for TENANT); Email is not a universal User query field or audit identity. Unknown/retired login input is rejected; no alias/dual login exists.

Passwords: 12–128 Unicode code points, exact/untrimmed, uppercase + lowercase + digit + special/non-alphanumeric. Spring Security Argon2id remains 19,456 KiB, two iterations, parallelism one, 16-byte salt and 32-byte output. All password writers independently enforce the same policy.

V8 is a clean target cutover: populated old local credentials cause migration refusal, with no fabricated Email/verification or data clearing. Applying to such development state requires separate human-authorized clearing/reset and fresh Email bootstrap. No reset or migration of the manual environment is authorized or performed by I1. The deployment CLI now uses the shared explicit-Email helper and required `kelios.recovery.email` handoff for install/recover-admin, with no account default or Login compatibility. O1.1–O1.7 and overall O1 are HUMAN ACCEPTED / CLOSED; [O1 execution plan](../history/deployment-cli.md) records the isolated CLI control and PostgreSQL service evidence. This is not fresh live Docker recovery or real Email-delivery evidence.

Browser obtains a signed CSRF proof from `GET /api/v1/auth/csrf` then sends `X-CSRF-Token` with the matching HttpOnly cookie and exact HTTPS Origin for unsafe `/auth/` endpoints. Login returns Access JWT and User; Refresh is only a Secure/HttpOnly/host-only SameSite=Strict cookie at `/api/v1/auth/`. The frontend holds Access only in memory, sends Bearer to protected resources, and obtains fresh Access after reload/expiry without sending an expired Bearer to refresh. No localStorage/sessionStorage token persistence.

GLOBAL ADMIN can create GLOBAL users (`USER/ACTIVE`, null Tenant), create/reset GLOBAL credentials, approve users, separately grant/change ADMIN, disable managed accounts, create Tenants and configure OIDC. TENANT ADMIN can list/approve/disable permitted ordinary USER accounts in its signed Tenant and cannot grant or manage ADMIN. Tenant collection/detail reads allow ACTIVE GLOBAL ADMIN/USER; create remains GLOBAL ADMIN-only; TENANT ADMIN can read only own Tenant detail, not list/switch Tenants. USER roles have no User/Tenant/configuration/Audit administration. All administration requires ACTIVE ADMIN. TENANT ADMIN may manage its own configuration singleton; Audit retains GA/own captured-Tenant ADMIN scopes. Own password change requires current-password verification. Administrator credential resets and own changes apply the same policy and **do not independently revoke JWTs**.

Refresh reloads current User; DISABLED/missing User fails. GLOBAL requires verified ACTIVE credentials; TENANT PENDING has only minimal authentication/self status and no business data. Issued Access contains permission/status snapshots and expires naturally. Logout discards memory and clears cookies; it does not revoke copied tokens or log out an IServ SSO session. Copied Refresh can remain usable until absolute expiry unless current User is DISABLED or its signing key becomes invalid.

## Tenant IServ configuration — revised normative target

GET/PUT/DELETE /api/v1/tenants/{id}/oidc are concrete-Tenant singleton administration. ACTIVE GLOBAL ADMIN may manage an authorized Tenant; ACTIVE TENANT ADMIN only its own signed Tenant. USER/PENDING/DISABLED are denied. Service resolves authoritative scope independently of path/body/selected context; foreign targets never grant access. No configuration collection or Tenant switching/discovery is added. TENANT ADMIN sidebar Tenant navigation goes directly to own detail. TENANT ADMIN/USER selector reliance on /api/v1/tenants is obsolete coupling to remove; GLOBAL ADMIN retains that unpaged collection for administration/selection.

PUT accepts only issuer/clientId/write-only clientSecret/enabled/version/allowedGroupIds; actor/ownership/metadata overposting is rejected. Enabled is an explicit boolean; version null/nonnegative integer and groups bounded UUID input. Initial secret required; null/omitted update secret retains/re-encrypts the current secret. Initial version null; updates require the returned version. Detail includes Tenant ID/name, safe metadata, secretConfigured and trusted-origin callbackUri, never plaintext/ciphertext. Empty allowlist blocks login. Preserve exact issuer/subject/JIT/groups and existing JWT/status semantics.

DELETE requires the current nonnegative version query parameter (stale state 409; ordinary missing/repeated deletion 404). It physically removes the current configuration, encrypted secret and owned allowed-group rows, then writes exactly one OIDC_CONFIG_DELETED audit with authoritative captured Tenant and actor, all in one transaction. Repeated DELETE is missing, with no second successful audit. No tombstone/deleted generation or history interface remains; AuditLog retains deletion history. Disabled configuration remains stored/readable/editable with secret and occupies the singleton but cannot authenticate. Deleted configuration is missing for GET/update/login; it may be configured anew through initial PUT with a fresh secret. No Tenant, User or external identity mapping is removed/disabled, and no JWT/session revocation is added. Reset/restore are not implied.

Current schema permits child-first deletion: tenant_oidc_allowed_group references tenant_oidc_config without cascading delete, and external_identity references Tenant/User rather than configuration. Parent-Tenant and configuration locking must serialize create/update/delete without lock-order cycles. Existing final OidcProvisioning.complete holds configuration FOR SHARE through its transaction; deletion uses a conflicting lock. Callback and final provisioning validate the current existing, enabled, authorized configuration, never solely a pre-delete flow/cache snapshot. Missing/disabled current configuration blocks completion; all existing protocol/version/issuer/client/group/identity checks remain. Existing token semantics for completed authentication are unchanged. No provider-network-long database lock is required.

A flow begun before DELETE is allowed to complete if recreated/current configuration and credentials legitimately satisfy all existing checks. DELETE between start and callback is not an independent rejection reason. No configuration-generation identity, generation UUID, freshness discriminator or retained deleted state is required or authorized. Do not add schema solely for delete/recreate flow identity or reject old flows merely because they predate deletion. Physical deletion itself fits the current schema. This documentation does not authorize implementation.

Every login dynamically loads enabled configuration, uses Discovery, state/nonce/PKCE S256, encrypted authenticated five-minute flow cookie, verified ID token and UserInfo subject binding. Preserve existing bounded caches, trusted endpoints/SSRF controls and client-secret redaction. Only openid/profile/iserv:groups are requested. Allowed groups are securely verified UUIDs, not display labels or ADMIN grants. Public enabled-organization routing is authentication routing, not administration or membership; removed configuration is absent from it. Existing group-removal/refresh/JWT behavior remains unchanged. Real IServ interoperability remains separate/manual verification.

## Logging and operational limits

Successful administrative mutations audit sanitized targets/context atomically. No permanent login AuditLog, AssetEvent authentication noise, tokens/codes/cookies/hashes/secrets/raw claims in audit or logs. nginx auth access logs are disabled because callback query strings contain codes; apply equivalent redaction/exclusion at production ingress and never log HTTP bodies/Authorization/Cookie headers.

Identity mutation request bodies are bounded to 32 KiB before JSON/password processing, including chunked bodies. Process-local attempt throttles cap 4,096 keys per minute: login identifier 10 attempts/minute, unsafe auth/client and callback/client 60/minute. Failures emit at most 100 sanitized status-only operational entries/minute. The backend deliberately ignores caller-supplied forwarding headers. Behind nginx, address limits apply to proxy connections; deploy trusted ingress per-client/global limits for a public installation or multiple replicas. No Redis, shared lockout state or security-log retention domain is added.

`./scripts/smoke-phase2.sh` generates repository-local disposable TLS/auth/password fixtures, runs original container regression, tests same-origin HTTPS auth and both brands, and writes only Phase 2 evidence. It refuses unexpected bootstrap success and leaves the green-brand TLS stack running. Self-signed browser trust is a **test-only Playwright setting**, never a production provider bypass. Accepted Phase 0/1 evidence remains unchanged.

## Browser organization selection

The root login iServ button opens a searchable organization selector. Anonymous `GET /api/v1/auth/oidc/organizations` defaults to a Page of 20 enabled minimal `{routingId,name}` entries; explicit paging is zero-based, maximum size is 100, and normalized trimmed q is bounded to 128 UTF-16 units with literal substring matching. It exposes no issuer, client details, secrets, allowlist or identity information. A routing handle selects `/api/v1/auth/oidc/{tenant}/start`; it is not Tenant membership, user identity or authorization. Organization-specific `/login/tenant/{uuid}` links remain supported. There is no default/first-provider inference. Exact verified `(issuer,subject)` and the stored KELIOS User govern scope/Tenant/role/status. Incompatible Tenant mappings are rejected. iServ-initiated login is out of scope; real interoperability remains pending manual verification.

The repository-level [Deployment CLI](local-deployment.md) wraps this same recovery command and existing HTTPS overlay. It accepts supplied TLS files and adds no HTTP authentication or alternate administrator creation path.

### U1 Tenant read-authority correction — 2026-10-12

U1 HUMAN RESOLVED / CLOSED: ACTIVE GLOBAL USER may GET the existing complete Tenant collection/detail representation, including id/name and metadata createdBy/createdAt/updatedBy/updatedAt/createdByDisplayName/updatedByDisplayName. Bounded actor UUID/timestamp disclosure is approved; no new endpoint or reduced DTO. Read does not grant administration. GA-only create; TA own detail/no list; TU/status/anonymous denial unchanged. OIDC/User/Audit reads unchanged. GLOBAL USER initially Global with future authoritative UUID/name dropdown, optionally lazy loaded; context selection changes neither identity nor authority. TENANT principals never switch. New backend correction HUMAN ACCEPTED / CLOSED, 2026-10-12; previous Step7 reports remain historical. [Evidence](../history/backend-domain.md). F1b/D05–D07 HUMAN ACCEPTED / CLOSED, 2026-10-12; no later gate authorized.
