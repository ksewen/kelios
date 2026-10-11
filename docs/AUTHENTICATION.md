# Phase 2 authentication operations

KELIOS supports same-origin HTTPS browser authentication. Separate frontend/backend images are retained: nginx or a trusted production ingress proxies `/api/`. CORS/cross-site authentication and subpaths are not supported. Public branding JSON contains no authentication configuration or secrets.

The [OpenAPI contract](api/openapi.yaml) describes authentication and identity administration. [ADR 0003](adr/0003-phase-2-authentication-and-authorization.md) records the approved design; [verification](verification/PHASE_2.md) records actual results. Inventory REST/UI and workflow services remain Phase 3.

## Mounted keys and origin

Supply these **deployment-owned** files through a private secret mount readable only by the runtime administration boundary and the unprivileged backend process:

| File in `KELIOS_AUTH_KEY_DIRECTORY` | Format / use |
|---|---|
| `<signing-kid>.private.pem` | PKCS8 RSA private key, minimum 3072 bits; RS256 signing |
| `<kid>.public.pem` | X509 SubjectPublicKeyInfo RSA public key; only mounted trusted IDs can verify tokens |
| `<encryption-kid>.aes` | Base64-encoded 32-byte random AES/HMAC key; AES-256-GCM authenticated envelopes and purpose-separated signed CSRF proof |

Select active IDs with `KELIOS_AUTH_SIGNING_KEY_ID` / `KELIOS_AUTH_ENCRYPTION_KEY_ID` (default `main`). IDs contain ASCII letters/digits/underscore/hyphen, 1–64 characters. Mount at `/run/secrets/kelios-auth` or set backend `kelios.auth.key-directory`. Never place keys in source, image, database, frontend config or evidence. Do not reuse local smoke fixture keys in production. Use managed secrets and suitable host/mount ownership; local file-mode examples do not replace production ownership controls.

`KELIOS_AUTH_PUBLIC_ORIGIN` is the exact externally reachable HTTPS origin, including any non-default port. `KELIOS_AUTH_ISSUER` is the explicit KELIOS JWT issuer; consumers verify it and fixed audiences `kelios-api` / `kelios-refresh`. Callback URIs are derived from trusted public origin and Tenant UUID, never caller Host/forwarded headers. Access is 15 minutes; Refresh 30 days **absolute**, unchanged by refresh. Accepted skew is 30 seconds; maintain synchronized deployment clocks.

Key rotation requires externally supplied matching private/public material for the new signing ID and retaining old trusted public keys for intended outstanding-token lifetime. Removing a verification key invalidates its tokens. Retain old AES files while ciphertext/flow cookies use their IDs; updating OIDC configuration re-encrypts retained client secrets under the current AES ID. No automatic key generation/rotation or revocation database exists. Back up authentication encryption keys together with protected database backups; losing a key requires resetting affected provider secrets.

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
  --kelios.recovery.login=operator-chosen-login \
  '--kelios.recovery.display-name=Operator chosen display name' \
  --kelios.recovery.password-file=/run/secrets/kelios-auth/operator-password
```

This locks a singleton PostgreSQL guard, freshly checks GLOBAL/ADMIN/ACTIVE users with usable GLOBAL credentials, and atomically creates exactly one GLOBAL/ADMIN/ACTIVE User, credential and `GLOBAL_ADMIN_BOOTSTRAPPED` AuditLog when the count is zero. Repeating while a usable administrator exists **refuses**, without creating anything. The mutex stores no completion marker: the same command supports later zero-usable-admin recovery. Missing/malformed credentials do not count; forgetting an otherwise valid stored password does not make the stored credential unusable.

Remove the private input password file after operator-controlled completion. The actual supplied password is the credential; no forced first-login change or temporary-password state. Normal GLOBAL ADMIN self-disable/demotion is rejected; removal is not exposed. Other administrators may disable/demote accounts without a permanent last-admin invariant. No email recovery, reusable recovery token or automatic rebootstrap exists.

## Local account and administration contract

GLOBAL-only credential identifiers: 3–64 ASCII letters/digits/`.`/`_`/`-`, canonical lowercase, unique and separate from email/display/OIDC identity. Passwords: 15–128 Unicode code points, exact/untrimmed, uppercase + lowercase + digit + special/non-alphanumeric; no periodic mandatory rotation. Spring Security Argon2id stores salted hashes only: 19,456 KiB, two iterations, parallelism one, 16-byte salt and 32-byte output; bounded benchmark evidence is linked above.

Browser obtains a signed CSRF proof from `GET /api/v1/auth/csrf` then sends `X-CSRF-Token` with the matching HttpOnly cookie and exact HTTPS Origin for unsafe `/auth/` endpoints. Login returns Access JWT and User; Refresh is only a Secure/HttpOnly/host-only SameSite=Strict cookie at `/api/v1/auth/`. The frontend holds Access only in memory, sends Bearer to protected resources, and obtains fresh Access after reload/expiry without sending an expired Bearer to refresh. No localStorage/sessionStorage token persistence.

GLOBAL ADMIN can create GLOBAL users (`USER/ACTIVE`, null Tenant), create/reset GLOBAL credentials, approve users, separately grant/change ADMIN, disable managed accounts, create Tenants and configure OIDC. TENANT ADMIN can list/approve/disable ordinary USER accounts in its Tenant and cannot grant or manage ADMIN. Readers cannot administer. Own password change requires current-password verification. Administrator credential resets and own changes apply the same policy and **do not independently revoke JWTs**.

Refresh reloads current User; DISABLED/missing User fails, PENDING has only minimal authentication/self status and no business data. Issued Access contains permission/status snapshots and expires naturally. Logout discards memory and clears cookies; it does not revoke copied tokens or log out an IServ SSO session. Copied Refresh can remain usable until absolute expiry unless current User is DISABLED or its signing key becomes invalid.

## Tenant IServ configuration

Use GLOBAL ADMIN `GET/PUT /api/v1/tenants/{id}/oidc`; the request contains `issuer`, `clientId`, write-only `clientSecret`, `enabled`, optimistic `version` and `allowedGroupIds` UUID array. Initial secret required; null/omitted update secret retains and re-encrypts it. Initial version null; updates require returned version. Sanitized response adds `secretConfigured` and derived `callbackUri`, never plaintext/ciphertext. An empty allowlist denies all logins. Changes are atomic and invalidate an older in-flight flow.

Every login dynamically loads enabled configuration, uses Discovery, state/nonce/PKCE S256, encrypted authenticated five-minute HttpOnly SameSite=Lax flow cookie, verified ID token and UserInfo subject binding. Only `openid profile iserv:groups` is requested. The official group contract is a UserInfo `iserv:groups` list of objects with UUID `id`, `act`, `name`; only UUID `id` participates in intersection. Missing/malformed/unavailable membership fails closed. No guessed group claim, mutable-name matching or automatic ADMIN role mapping.

New exact `(issuer,subject)` identity creates `TENANT/USER/PENDING` in the verified Tenant. Existing mapped users keep ID/role/status/display name. Wrong-Tenant mapping is denied. Provider tokens are request-local and discarded. Group eligibility is evaluated at OIDC login only; later provider/allowlist changes do not synchronize/disable KELIOS accounts and refresh never queries IServ. An administrator manually DISABLES to remove future refresh access.

Later real-provider setup needs database configuration and deployment trust/origin values only, with **no image rebuild/code changes**. Follow [manual real-IServ verification](verification/PHASE_2_ISERV_MANUAL.md). Deterministic fixture success proves the documented adapter contract, not actual IServ interoperability.

## Logging and operational limits

Successful administrative mutations audit sanitized targets/context atomically. No permanent login AuditLog, AssetEvent authentication noise, tokens/codes/cookies/hashes/secrets/raw claims in audit or logs. nginx auth access logs are disabled because callback query strings contain codes; apply equivalent redaction/exclusion at production ingress and never log HTTP bodies/Authorization/Cookie headers.

Identity mutation request bodies are bounded to 32 KiB before JSON/password processing, including chunked bodies. Process-local attempt throttles cap 4,096 keys per minute: login identifier 10 attempts/minute, unsafe auth/client and callback/client 60/minute. Failures emit at most 100 sanitized status-only operational entries/minute. The backend deliberately ignores caller-supplied forwarding headers. Behind nginx, address limits apply to proxy connections; deploy trusted ingress per-client/global limits for a public installation or multiple replicas. No Redis, shared lockout state or security-log retention domain is added.

`./scripts/smoke-phase2.sh` generates repository-local disposable TLS/auth/password fixtures, runs original container regression, tests same-origin HTTPS auth and both brands, and writes only Phase 2 evidence. It refuses unexpected bootstrap success and leaves the green-brand TLS stack running. Self-signed browser trust is a **test-only Playwright setting**, never a production provider bypass. Accepted Phase 0/1 evidence remains unchanged.

## Browser organization selection

The root login iServ button opens a searchable organization selector. Anonymous `GET /api/v1/auth/oidc/organizations?page=0&size=25&q=` returns enabled configured organization names and UUID routing handles only; it exposes no issuer, client details, secrets, allowlist or identity information. Maximum size is 100 and query length is 128. A handle selects the existing `/api/v1/auth/oidc/{tenant}/start` flow; it is not proof of membership. Organization-specific `/login/tenant/{uuid}` links remain supported. There is no default/first-provider inference or user-entered technical identifier. Exact verified `(issuer,subject)` and the stored KELIOS User govern scope/Tenant/role/status. Existing incompatible Tenant mappings are rejected. iServ-initiated login is out of scope; real interoperability remains pending manual verification.

The repository-level [Deployment CLI](DEPLOYMENT_CLI.md) wraps this same recovery command and existing HTTPS overlay. It accepts supplied TLS files and adds no HTTP authentication or alternate administrator creation path.
