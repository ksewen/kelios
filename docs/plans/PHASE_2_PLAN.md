# Phase 2 plan — Authentication and authorization

**Historical accepted implementation record.** Preserve the decisions and evidence below as implementation-time history. Current normative Domain/identity/query requirements are in [DOMAIN.md](../../DOMAIN.md) and [ARCHITECTURE.md](../../ARCHITECTURE.md); separately approved future work follows [the consolidated implementation gates](COLLECTION_QUERY_IMPLEMENTATION_PLAN.md). Earlier login identifiers/password minima, paging defaults, deleted selectors, sort ties and phase-specific exclusions are not competing target requirements.


Status: **Approved for Phase 2 implementation.** The user approved D1–D5 and D7–D9 on 2026-10-10, including modified D2 and replacement D3. D6 now has official documented protocol evidence; real-provider verification remains an acceptance gate. Phase 0 and Phase 1 are accepted and complete. The user subsequently explicitly approved implementation and clarified that real IServ access is unavailable this cycle: do not block implementation or ask for provider credentials. Actual implementation/verification results are recorded in [Phase 2 evidence](../verification/PHASE_2.md), not inferred from this plan.

Authority: [AGENTS.md](../../AGENTS.md), [DOMAIN.md](../../DOMAIN.md), [ARCHITECTURE.md](../../ARCHITECTURE.md), and the existing Phase 2 section of [CODEX_IMPLEMENTATION_TASK.md](../../CODEX_IMPLEMENTATION_TASK.md). Also reviewed: [ADR 0001](../adr/0001-phase-0-foundation.md), [ADR 0002](../adr/0002-phase-1-domain-persistence.md), [Phase 0 plan](PHASE_0_PLAN.md), [Phase 1 plan](PHASE_1_PLAN.md), [Phase 0 evidence](../verification/PHASE_0.md), [Phase 1 evidence](../verification/PHASE_1.md), current backend/frontend sources, Docker/Compose configuration, OpenAPI, and V1–V3 migrations.

**Decision labels:** **Required** means mandated by specification; **Approved** means accepted through the user's D1–D9 review; **Planned** means an implementation detail within that approved architecture submitted with the final plan; **Gate** means external acceptance evidence still needed. D1, D2, revised D3, D4, D5, D7, D8 and D9 are no longer approval questions. D6 research is cited in §8. Final implementation approval has been given; Phase 3 remains gated. The revised Domain/Architecture carry observable/technical decisions respectively; historical ADR 0001/0002 remain unchanged.

## 1. Scope and non-scope

**Required:** secure local GLOBAL authentication; deployment-side GLOBAL ADMIN bootstrap/recovery; database-configured per-Tenant IServ OIDC Discovery; verified `(issuer, subject)` identity; verified IServ group allowlisting; mandatory JIT `TENANT / USER / PENDING`; approval/role administration; stateless Access/Refresh JWTs; backend scope, role, status and Tenant enforcement; minimal frontend authentication/session states.

Preserve explicit `UserScope { GLOBAL, TENANT }`, `Role { ADMIN, USER }`, and `AccountStatus { PENDING, ACTIVE, DISABLED }`. GLOBAL requires null Tenant; TENANT requires exactly one non-null Tenant. Scope determines operating boundary, role permitted actions, status current business eligibility. Never infer User scope from null Tenant or add multi-Tenant membership.

**Excluded:** Phase 3 inventory REST/UI, lifecycle/condition transition services, loans, import, QR/labels/printers, repair/procurement, organization hierarchy, generalized RBAC/permission platform, speculative provider abstraction, automatic IServ ADMIN assignment, guessed group claims, Product/Owner/Location Merge, duplicate detection, Redis, blacklist, token/session database, revocation metadata (`authVersion`, `tokensValidAfter`), per-device sessions, unrelated UI redesign, or invented privacy/deletion policies. Authentication administration APIs are in scope; their full administration UI remains Phase 3.

## 2. Existing foundation to reuse

- Preserve Java 25 LTS, Spring Boot 4.1.1, Maven 3.9.16 Wrapper, Node 24, PostgreSQL, independently built images, unprivileged nginx and runtime branding. Use Boot-managed Spring Security/OAuth2 components; verify concrete dependency compatibility during implementation.
- Existing `Tenant` has UUID/name. `User` has UUID, explicit scope, Tenant, role, status and required display name, intentionally without authentication fields. Extend identity with separate credential/provider/mapping tables; preserve these core invariants.
- Reuse internal Tenant/User services and AuditLog infrastructure. Replace externally reachable caller-provided `TrustedActor` inputs with authenticated server context at secured application entry points. Actor UUID/display-name snapshots continue without live-User FKs.
- Preserve Catalog/Location/Owner/Asset mutation guards, retained references including DEREGISTERED and soft-deleted rows, Product historical edit freeze, explicit Category/Manufacturer Merge, optimistic Asset versions, atomic history, UTC tag allocation and narrow V3 triggers. Do not restore superseded flags or locking schemes.
- The generic state-machine engine remains independent of persistence, Asset, OIDC and security. Phase 2 protects existing internal operations; it does not implement new transition workflows.
- Current HTTP surface is technical status; current frontend is the branding/technical-status shell. Preserve public `/api/v1/status`, internal management endpoints, `/api/` nginx proxy and checked-in OpenAPI. Authentication features extend the shell without inventory screens.

## 3. Approved persistence and planned migrations

**Planned:** additive `V4__phase_2_identity_authentication.sql` (split into later numbered migrations only if review or delivery needs it). Do not modify V1, V2 or V3, migrate historical actors, or generate schema through Hibernate. Continue Flyway plus JPA validation.

| Addition | Fields and constraints | Purpose |
|---|---|---|
| `local_global_credential` | `user_id` PK; constant `user_scope='GLOBAL'` CHECK; non-blank unique canonical `login_name`; non-blank encoded `password_hash`; composite FK `(user_id,user_scope)` to new UNIQUE `app_user(id,scope)` | GLOBAL-only credentials, separate from the minimal User. No local Tenant credentials. |
| `tenant_oidc_config` | `tenant_id` PK/FK Tenant; required exact issuer/client ID; encrypted client-secret envelope with key identifier; enabled flag; optimistic configuration version | One database-configured connection per Tenant, including allowlist updates under the same version/transaction. Required protocol scopes `openid profile iserv:groups` are derived by the documented adapter rather than stored as redundant editable configuration. No generalized provider registry. |
| `tenant_oidc_allowed_group` | `(tenant_id,group_id)` PK; `group_id` PostgreSQL UUID; FK to configuration | Documented group UUID identifiers rather than mutable names; §8 records evidence. Empty allowlist denies login. No arbitrary normalization or group-role mapping. |
| `external_identity` | UNIQUE/PK `(issuer,subject)`; unique `user_id`; `tenant_id`; constant `user_scope='TENANT'` CHECK; composite FK `(user_id,user_scope,tenant_id)` to new UNIQUE `app_user(id,scope,tenant_id)`; FK Tenant | Stable external mapping, constrained to the correct TENANT User. One external identity per user is sufficient for v1. |
| `global_admin_recovery_guard` | singleton PK with CHECK; seed the guard row only; no completion timestamp/flag or credential fields | Narrow transaction mutex for bootstrap/recovery zero-usable-admin validation. It imposes no lifetime one-time rule. |

All required identifiers/strings get nullability and non-blank constraints; existing scope/role/status CHECKs remain. Add indexes for scoped user administration and FK/query paths actually used. Do not add unique email/name constraints. External identity issuer comparison is exact OIDC issuer identity, not arbitrary URL normalization.

**Approved D5 JIT detail:** make the external-identity-to-User composite FK `DEFERRABLE INITIALLY DEFERRED`. First reserve `(issuer,subject)` using `INSERT ... ON CONFLICT DO NOTHING RETURNING`, then create its UUID User if the insert wins. If it loses, a fresh READ COMMITTED query loads the committed mapping. The deferred FK permits this insert order while preventing orphan mappings at commit. A failed provisioning transaction rolls back both rows. This handles simultaneous first logins without a generic retry framework or duplicate/orphan Users.

Do not FK the external mapping's historical issuer to the configuration's mutable issuer. Changing a provider must not relink old identities, transfer a User to another Tenant, or match by display name/email. Same issuer configured for another Tenant cannot claim an existing mapping; reject the mismatch.

**Required:** encrypt OIDC secrets at rest; no environment-specific secrets, account passwords or JWT keys in migrations. **Approved D5:** AES-256-GCM using the JDK crypto library, fresh nonce, authenticated context binding Tenant/client/issuer/purpose, and versioned encryption-key ID. Encryption keys are mounted outside the database and public runtime JSON. Support deliberate key replacement/re-encryption without treating ciphertext as identity. Configuration reads never return plaintext secrets. Existing Phase 1 Users remain intact; no automatic authentication linkage or fabricated credentials.

No new database triggers are proposed. These additions use FK/CHECK/UNIQUE, narrow transactions and supported service boundaries. Existing AssetEvent/UNKNOWN protection remains unchanged.

## 4. GLOBAL local-authentication design

**Required:** local authentication is specific to GLOBAL Users. Credentials do not create a second scope model.

**Approved D2:** Spring Security `PasswordEncoder` with Argon2id through its supported implementation and required crypto dependency, encoded algorithm/work parameters/salt, and an upgrade-compatible encoder identifier. Initial minimum is 19 MiB memory, two iterations, parallelism one; benchmark bounded deployment resources before raising cost. Use generated salts, never reversible passwords or custom hashing. This follows the [OWASP password-storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html) and [Spring password-encoder contract](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/password-encoder.html).

**Approved D2:** login identifiers are separate 3–64-character ASCII identifiers using letters, digits, `.`, `_`, `-`, canonicalized to lowercase and uniquely stored, not email/display name/OIDC identity. Passwords contain 15–128 Unicode code points, are not trimmed, and require at least one uppercase letter, lowercase letter, digit and special/non-alphanumeric character; no periodic mandatory rotation. Apply uniformly to bootstrap/recovery, User creation, credential creation/reset and own-password change. Use Unicode code-point classification consistently; do not truncate, normalize or silently alter passwords. Bound request size before hashing.

The operator/administrator-provided initial password is the actual credential. Do not add generated temporary-password semantics, `passwordChangeRequired`, or a forced first-login change workflow. Benchmark Argon2id under bounded resources and record selected parameters and measurements in Phase 2 evidence; do not claim the initial cost is verified before execution.

Login loads credentials by canonical identifier, verifies the hash, reloads the User, and requires explicit GLOBAL scope. Missing login and incorrect password return the same generic error; perform a bounded dummy-hash verification for missing credentials. Correct credentials for DISABLED users issue no tokens. PENDING accounts may obtain only restricted authentication/status access, consistent with the status model.

Expose minimal own-password change with current-password verification and GLOBAL ADMIN credential creation/reset for GLOBAL targets. No email recovery, local Tenant login, MFA, enrollment platform or permanent default password. Password updates are hashed before storage and audited without plaintext/hash contents. Do not promise token invalidation on password change: the approved JWT model has no such revocation mechanism.

**Approved D7:** GLOBAL ADMIN explicitly creates exactly `GLOBAL / USER / ACTIVE`, `tenantId=null`; the creation constitutes approval. ADMIN grant is a separate privileged operation. Bootstrap/recovery creates `GLOBAL / ADMIN / ACTIVE`. These rules never change mandatory TENANT/USER/PENDING JIT.

## 5. Deployment-side GLOBAL ADMIN bootstrap/recovery

**Approved replacement D3:** one deployment-side command serves installation and zero-usable-admin disaster recovery. No browser/HTTP endpoint, automatic normal-startup bootstrap, permanent completion state, email recovery, recovery token or separate recovery domain.

A **usable GLOBAL administrator** has explicit GLOBAL scope, ADMIN role, ACTIVE status and usable GLOBAL local credentials. Planned credential-usability check: a canonical valid login and a well-formed supported Argon2id hash that the configured encoder can verify. This is evaluated from authoritative credential data, not a persisted usability flag or caller count. Missing/invalid credentials do not qualify; unknown/forgotten password alone does not change stored credential usability, and existing plaintext passwords cannot be inferred from hashes. All supported credential writes validate the encoding and D2 password policy.

Operator supplies login identifier/display name and a private mounted/input password file. The supplied password is the actual credential and must satisfy D2. Never pass it as a normal CLI argument or put it in source/image/Flyway/frontend config/logs/evidence; store only the normal Argon2id hash. Remove/unmount the input operationally after use, without agent writes outside the repository. No default/generated reusable password or mandatory first-login change.

1. Hash/validate supplied input outside the short database transaction; do not log secrets.
2. Lock `global_admin_recovery_guard` singleton `FOR UPDATE`.
3. Freshly load/count usable GLOBAL administrators. If at least one exists, **refuse** creation; this is a safe refusal on repeated commands, not silent creation/reset.
4. At exactly zero, create one `GLOBAL / ADMIN / ACTIVE`, `tenantId=null`, and its credential. Append `GLOBAL_ADMIN_BOOTSTRAPPED` with sanitized context and commit all together.
5. Rollback leaves no User/credential/audit, and a deliberate later invocation can retry. A duplicate login fails atomically; it does not overwrite/reset another User.

Two concurrent commands serialize on the guard: the second freshly observes the first's usable administrator and refuses. Existing PENDING/DISABLED administrators, or ACTIVE admins with unusable/missing credentials, do not permanently block recovery. No bootstrap-completed flag exists, and the command can recover a later zero-usable-admin installation.

**Approved normal self-protection:** compare authenticated actor UUID against target UUID inside the administrative operation; reject self disable, removal/deletion and demotion from ADMIN. Own password change remains allowed. No deletion endpoint or privacy policy is introduced. There is no permanent minimum-active-admin invariant: another authorized administrator's actions/external circumstances can leave zero usable administrators, making deployment recovery applicable.

To protect the same zero-admin predicate from a concurrent ordinary operation that *creates usability*, only GLOBAL-target ADMIN promotion/activation or credential creation/reset that can make an ACTIVE GLOBAL ADMIN usable also acquire the guard before target-row locks. Ordinary Tenant operations and destructive operations do not enter a last-admin protocol. This narrowly serializes the actual predicate with recovery; privileged external SQL remains outside the supported application boundary.

## 6. Database-configured Tenant OIDC / IServ flow

**Required:** GLOBAL ADMIN manages the Tenant's database configuration; OIDC Discovery establishes endpoints; IServ authenticates identity while KELIOS owns roles/status/authorization.

**Planned:** Spring Security OAuth2 Client/OIDC plus Resource Server and JOSE components. Resolve the Tenant configuration through a narrow identity application service on every login start. Do not statically hard-code Tenant registration properties. Disable default HTTP session persistence, saved-request sessions and persistent authorized-client storage.

1. Browser submits the selected Tenant ID to the login-start endpoint. Planned entry is an administrator-provided `/login/tenant/{tenantId}` link (or explicit ID entry), without a public Tenant directory. This ID expresses routing intent, not authenticated membership. Backend loads the enabled configuration and validated Discovery metadata.
2. Generate random state/nonce and PKCE S256. Save expected issuer, Tenant/configuration version, client ID, fixed redirect URI, nonce and verifier in a short-lived authenticated-encrypted HttpOnly flow cookie (planned five-minute TTL). Cookie encryption uses a separate purpose/key from OIDC-secret encryption. Store no flow in a database or server-side session. One outstanding browser flow is sufficient; starting another replaces it.
3. Start returns the validated authorization URL for a top-level browser navigation, rather than relying on a fetch redirect across origins. Navigate only to the validated provider authorization endpoint. Callback route carries Tenant ID but must match the protected cookie, state and expected provider. Fixed callback URI is derived from trusted deployment public origin plus the route, not caller `Host`, forwarded headers or return URL.
4. Exchange code on the backend with the configured client authentication and PKCE verifier. Validate ID-token signature, issuer, audience/authorized party as applicable, expiry and nonce. If UserInfo supplies profile/group data, verify its subject equals the ID token's subject. Caller-submitted identity/group/role/Tenant facts are ignored.
5. Verify the actual group source, apply allowlist, confirm configuration/version is still valid, then provision/load the mapped User and issue KELIOS tokens. Provider tokens are request-local and discarded; no stored IServ refresh/access tokens.
6. Clear flow cookie, set approved refresh transport, and redirect to a fixed clean frontend completion route. Never put KELIOS tokens in URL query/fragment. The frontend obtains its Access JWT through the approved refresh exchange.

The validation obligations come from [OIDC Core](https://openid.net/specs/openid-connect-core-1_0.html); exact issuer matching and metadata come from [OIDC Discovery](https://openid.net/specs/openid-connect-discovery-1_0.html). PKCE and redirect protections follow [OAuth security best current practice](https://www.rfc-editor.org/rfc/rfc9700.html). Use [Spring's OIDC extension points](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/advanced.html) without its default session-backed authorization-request persistence. Implementation realization: a narrow application service owns bounded flow-cookie/code/JIT choreography, with Spring OIDC ID-token validation, Nimbus JOSE and explicit protocol checks; no session-backed authorized-client stack is installed. This realizes the approved stateless boundary without a generic provider platform.

Discovery validation: require configured HTTPS issuer, exact discovered issuer, acceptable signing algorithms, trusted HTTPS metadata/JWKS/token/UserInfo endpoints, supported client authentication and response mode. Bound response sizes/timeouts and JWKS refresh. Prevent SSRF and redirect/DNS rebinding to localhost, link-local/metadata addresses or unapproved destinations; private IServ networks need an explicit deployment egress allowlist, not a blanket disabled check. Endpoints on a different hostname require reviewed allowlisting, not an unsupported assumption that all providers use one host. Cache metadata/JWKS by verified configuration, never arbitrary token `jku`/`x5u` URLs.

Approved D5 flow-cookie architecture; planned cookie attributes: Secure, HttpOnly, host-only, SameSite=Lax for the top-level GET callback, narrow auth path; require query-mode code callback for this minimum flow. No arbitrary `returnTo`. Callback error pages/logs reveal no code, token, secret or external group data. Replay tests must prove state/nonce/PKCE/code validation; do not claim globally one-use flow-cookie storage when the design is stateless. KELIOS logout does not imply terminating the IServ SSO session.

## 7. JIT provisioning

Only after protocol validation **and** successful allowlist intersection:

- Load by verified `(issuer,subject)`; never match email, username or display name.
- Existing mapping must belong to the verified provider's Tenant and a TENANT User. Reject another-Tenant mapping; no automatic migration, account linking or multi-Tenant membership.
- On first login create exactly `scope=TENANT`, the verified Tenant, `role=USER`, `status=PENDING`. Neither request fields nor IServ roles/groups can override these constants. Apply the unique/deferred-FK reservation described in section 3.
- Planned display-name source is verified standard OIDC `name` when non-blank, otherwise verified subject as a non-blank fallback. Display name is a snapshot/display value, never an identity key. Existing User role/status/display name are not automatically rewritten by login; approval and role changes remain KELIOS operations.
- PENDING receives restricted JWTs and only authentication/status interactions. Frontend shows “Your account is pending approval. Contact your administrator.” No inventory requests, data preload, admin lists or business output are allowed.
- ACTIVE receives authorized access; DISABLED receives no new tokens. Failure before allowed-group verification creates neither User nor mapping.

No `requireApproval=false`, ACTIVE JIT path, primary user import, or existing-user auto-link based on a friendly name. Phase 1 fixture Users do not silently acquire an external identity.

## 8. IServ group contract and real-provider verification gate

### Official documentation research — 2026-10-10

| Official source | What it establishes |
|---|---|
| [IServ Single-Sign-On: scopes/claims and configuration](https://doku.iserv.de/manage/system/sso/#scopes-und-claims) | Scope and claim both `iserv:groups`; a list of group objects containing UUID `id`, account name `act` and display name `name`. Also documents issuer/subject, Discovery, client registration, scope restrictions, redirects and required user OAuth permission. |
| [IServ developer OAuth/OpenID example](https://doku.iserv.de/development/oauth/) | UserInfo can return scope-dependent information, including groups. Supports selecting the discovered UserInfo response for the adapter, rather than assuming groups must be present in every ID token. |
| [Official Untis IServ OIDC integration](https://help.untis.at/hc/de/articles/4403390764690-IServ-Integrationseinstellungen-f%C3%BCr-SingleSignOn-SSO-mit-OIDC) | A working integration pattern with client credentials, scopes and UserInfo; its `iserv:webuntis`, `untis_username` and `untis_role` are application-specific, not KELIOS identity/role rules. Its example endpoint paths differ from current IServ SSO examples; use Discovery. |
| [IServ WebUntis connector documentation](https://doku.iserv.de/manage/user/webuntis/) | Separates SSO configuration from connector data synchronization. KELIOS uses neither that synchronization nor WebUntis matching/role semantics. |

These current documents do not specify a minimum IServ release or guarantee every deployment's consent/scopes, PKCE behavior, group UUID lifetime across reinstallations, or wire response. Do not invent such guarantees. The SSO documentation's deprecation notice concerns the centralized “Login mit IServ” service; this plan remains direct per-Tenant Discovery, not a silent migration to Eduplaces or that centralized service.

### Approved documented adapter contract

Request `openid`, `profile` and `iserv:groups`; email/IServ roles/WebUntis scopes are unnecessary. Fetch the discovered UserInfo endpoint with the request-local provider token. Validate UserInfo `sub` equals the cryptographically verified ID-token subject under OIDC rules; this subject-binding requirement is KELIOS's OIDC validation, not an invented claim. Never accept browser-submitted group payloads.

Read the documented list, require valid group-object UUID IDs, and compare typed UUIDs with Tenant allowlist `group_id`. UUID is the documented identifier; `act`/`name` are not matching keys. Do not invent name fallback, case-folding/string rewriting, role mapping or an undocumented secondary groups API. Treat missing/malformed/ambiguous claims, invalid UUIDs, failed UserInfo/subject validation and unavailable group information as denied authentication. Bound parsing; optional display fields cannot substitute for an ID.

Required eligibility: non-empty intersection of verified group IDs and Tenant allowed IDs. Empty allowlist/intersection denies, creates no JIT User, and issues no new KELIOS tokens. IServ membership never grants ADMIN. Apply on each OIDC login to new and existing mapped users. UUID parsing follows the documented UUID type, not arbitrary identifier normalization; actual interoperability must confirm response serialization and deployed behavior.

### Three distinct acceptance evidence levels

1. **Documentation-established:** official contract above supports the approved production adapter. D6 is no longer an unknown claim/API blocking all adapter work.
2. **Deterministic tests:** a local OIDC fixture accurately models that documented UserInfo contract, with UUID objects and matching/mismatching `sub`, positive/empty/malformed memberships and scope failures. These are automated protocol tests, not real IServ evidence.
3. **Real IServ interoperability — external acceptance gate:** verify deployed version/configuration, Discovery/JWKS, client/scopes/permissions, state/nonce/PKCE, actual UserInfo shape and subject binding, group UUID matching, denied conditions and PENDING provisioning against a real provider. Record sanitized outcomes in `docs/verification/PHASE_2.md`. No raw claims/secrets/passwords in evidence. The latest user clarification explicitly permits **Phase 2 implementation complete/passed** and **documented IServ contract verification complete/passed** when their actual acceptance tests pass, while **real IServ interoperability remains pending manual verification** because no real environment is available. This is an acceptance-evidence gap, not unfinished production code. Follow [the manual procedure](../verification/PHASE_2_ISERV_MANUAL.md); do not claim real compatibility from deterministic fixtures.

Production always fails closed when group eligibility cannot be securely determined. The production adapter/configuration API must be ready for later manual provider setup using deployment/database values only, without source changes or image rebuilds. Lack of real-provider evidence limits interoperability claims rather than requiring guessed claims, a mock-only adapter or blocked implementation.

**Approved D8 lifecycle:** check eligibility only at OIDC authentication/login. KELIOS does not continuously synchronize later IServ account/group/role changes or allowlist changes. Refresh reloads current KELIOS User without contacting IServ or storing provider tokens. Such changes do not disable the KELIOS User or revoke JWTs; administrator-set KELIOS DISABLED prevents future refresh, while issued access expires naturally. Refresh has the original absolute expiry. A new OIDC login must still satisfy current eligibility.

## 9. JWT design

**Required:** approximately 15-minute Access JWT, approximately 30-day Refresh JWT, no token/session store or revocation subsystem. Refresh reloads current User; DISABLED cannot refresh. Already-issued access permissions/status remain effective until natural expiry. This includes the accepted interval after disable/demotion; do not introduce contradictory per-request database status revocation.

**Approved D4:** RS256 with a mounted RSA private key of at least 3072 bits; trusted public verification keys indexed by `kid`, bounded accepted algorithm/key list, separate issuer/audiences for KELIOS access and refresh validation. Keep signing keys out of source/images/database/frontend config. Configure a stable deployment issuer and explicit `kelios-api` / `kelios-refresh` audiences. Never follow token-controlled key URLs. Retain old verification keys for the maximum accepted token lifetime during ordinary rotation; emergency retirement rejects that key's tokens globally, not through per-user revocation metadata.

| Token | Approved claim model | Validation/use |
|---|---|---|
| Access | `iss`, `aud`, UUID `sub`, `iat`, `nbf`, `exp`, random `jti`, explicit token type; `scope`, `role`, `status`, `tenantId`; display-name snapshot | Validate all types/enums and GLOBAL/TENANT nullability. Server-signed context supplies actor; authoritative target entities still load from the database. Never accept caller identity snapshots. |
| Refresh | Standard identity/time/issuer/audience/ID claims and distinct token type; no authoritative role/status/Tenant/group claims | Only refresh endpoint accepts it. Load User by `sub`; use current scope/role/status/Tenant/display name for a new Access token. No JIT on missing User. |

Use distinct JOSE token types and a signed purpose claim to prevent cross-use. Validate signature, algorithm/key ID, issuer, audience, purpose, expiry, `nbf`, and future `iat`, with approved maximum 30-second clock skew and injected UTC Clock. Test every rejection path. [Spring JWT support](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html) supplies signature/claim validation; add explicit purpose/audience and enum checks.

**Approved D4:** absolute 30-day refresh lifetime from login, not rolling indefinite renewal. Refresh returns new access without extending the original refresh expiry; no one-use rotation/replay detection is promised. Logout discards tokens; copied refresh tokens remain usable until expiry unless the current User is DISABLED. Password changes do not independently invalidate them. These consequences are explicitly approved by D4. Signing-key invalidation is the other approved rejection boundary.

PENDING refresh remains permitted but restricted; missing User and DISABLED deny refresh. Issuance takes a short User read lock/snapshot boundary so administrative changes are observed consistently; it cannot eliminate the accepted validity of access issued immediately before a later change.

## 10. Approved browser JWT transport — Option A

**Approved D1:** Access JWT stays in frontend memory only and is sent as `Authorization: Bearer`. Refresh JWT uses a Secure, HttpOnly, host-only cookie; planned narrow path `/api/v1/auth/`, SameSite=Strict. Browser reload obtains access through refresh. Logout clears in-memory access and the matching refresh cookie, with no server revocation state. Never persist either JWT in localStorage/sessionStorage. Previous alternatives are superseded, not remaining choices.

Supported Phase 2 browser deployment is **same-origin HTTPS**, retaining separate frontend/backend artifacts and the nginx `/api/` proxy. CORS stays disabled by default. Cross-origin/cross-site authentication and subpath support remain deferred and must not be described as verified/supported by Phase 2. No HTTP session, BFF, refresh-token database, blacklist or Redis.

**Approved:** explicit CSRF protection and exact trusted-Origin validation on unsafe endpoints carrying ambient refresh credentials. Use stateless signed double-submit CSRF: initialize a pre-login nonce/token through `/auth/csrf`, bind authenticated refresh proof to signed refresh identity/`jti`, return proof in JSON for an explicit header, and validate the paired cookie/signature/binding. Reject missing/untrusted Origin for browser unsafe credential endpoints; trusted origin is deployment configuration, never reflected caller input. No CSRF session/database. Callback uses its bound OIDC state/nonce flow. SameSite supplements this validation. [OWASP CSRF guidance](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html) supports signed double-submit binding. Ordinary business requests accept only Bearer access, not an ambient access cookie.

HttpOnly prevents direct refresh-cookie reads, but XSS can still steal in-memory access or invoke requests. Preserve CSP and safe rendering; never imply HttpOnly alone eliminates XSS. Runtime branding stays public with no secrets/tokens or arbitrary credential destination. Browser API requests remain relative to the trusted proxy; backend public origin/issuer/proxy trust come from deployment settings. Fixed callback/return locations prevent open redirects.

Current HTTP Compose is still valid for accepted technical regression checks, not proof of Secure-cookie authentication. Planned verification adds repository-local TLS fixtures/overlay and explicit test trust without production flag weakening or system trust writes. Scope and clear cookie attributes consistently; sensitive responses are no-store and logs redact cookies/tokens. Use cookie naming compatible with narrow path; `__Host-` requires `/` and is unsuitable for that planned path.

Frontend remains minimal: GLOBAL login, Tenant login link/ID entry, callback completion, pending contact message, minimal current-user status, coordinated refresh on expiry/reload, logout and denied/disabled states. No inventory preload/screens, unrelated redesign or frontend-only enforcement.

## 11. Explicit authorization enforcement

**Required matrix (ACTIVE accounts only for business/admin operations):**

| Operation | GLOBAL ADMIN | GLOBAL USER | TENANT ADMIN | TENANT USER |
|---|---|---|---|---|
| Business reads | All Tenants | All Tenants, read-only | Own Tenant | Own Tenant, read-only |
| Asset/Location/Owner/Tenant Catalog writes | All Tenants, within Domain guards | Denied | Own Tenant, within Domain guards | Denied |
| Tenant creation/management; OIDC configuration | Allowed | No writes | Denied | Denied |
| GLOBAL User/credential administration | Allowed | Denied | Denied | Denied |
| Ordinary Tenant-user management/approval | Any Tenant | Denied | Own Tenant, target role USER only | Denied |
| Grant/change ADMIN role; administer ADMIN accounts | Allowed | Denied | Denied, including other Tenant admins | Denied |
| Global Catalog writes; publish Tenant entries globally | Allowed | Denied | Denied | Denied |
| Category/Manufacturer Merge | Allowed | Denied | Denied | Denied |

GLOBAL ADMIN self disable/demotion/removal is forbidden regardless of other administrators. Other authorized administrators may act on an ADMIN target; do not add a minimum-active-admin rule. Own credential change is not a destructive self operation.

Tenant readers can use GLOBAL Catalog entries according to existing visibility rules; they cannot read another Tenant's entries. The all-Tenant read-only role is not permission to view credential secrets or OIDC configuration. Scope/role never overrides Domain edit/reference/UNKNOWN guards.

**Planned:** immutable authenticated principal with explicit validated claims; a small policy component with named checks for active access, GLOBAL ADMIN, Tenant read/write, and ordinary Tenant-user administration. Use explicit predicates and tests, not permission tables, generic RBAC DSLs or policy frameworks.

Spring Security handles HTTP token validation and route-level access; identity/security adapters supply trusted principal to secured application entry points. Application services also check authorization before invoking existing mutation services, so non-HTTP callers cannot bypass the supported boundary. Retain module dependency direction through narrow application interfaces, not cross-module repository access. Do not put security in JPA entities or the generic engine.

Load each target by ID, determine Tenant/current role/visibility/status server-side, and authorize against that loaded target. Scoped repository queries prevent cross-Tenant reads, including list filters; do not fetch all then filter in the frontend. For mutations, check target facts inside the same transaction/target lock as mutation, particularly Catalog scope publication and user-role changes. Tenant ADMIN approval accepts a User ID and requires current own-Tenant USER/PENDING; no caller-supplied target scope, existing role, reference count or approval flag.

PENDING is limited to current authentication/status, refresh and logout, with no business/admin data. DISABLED cannot obtain new tokens/refresh; a signed ACTIVE access issued earlier retains the explicitly accepted expiry window. `/auth/me` reveals only minimal self identity/status from the authenticated token; it is infrastructure, not a business-data endpoint. Current database status is authoritative for refresh, not a silent immediate-revocation mechanism.

Phase 2 tests protect existing internal Catalog/Asset/Place operations through secured application boundaries, without adding inventory controllers merely to demonstrate permissions. Never expose general `UserService.create(scope,tenant,role,status,...)` or caller-chosen actor fields as an unrestricted HTTP command.

## 12. Planned minimum Phase 2 endpoints and errors

All paths below are under `/api/v1`. Update checked-in OpenAPI only after approval. Responses containing authentication information use `Cache-Control: no-store`.

| Method/path | Intent and authoritative checks |
|---|---|
| `GET /auth/csrf` | Stateless browser CSRF initialization under the approved transport; no business data. |
| `POST /auth/local/login` | GLOBAL login identifier/password; generic credential failure; no caller scope/role/status. |
| `POST /auth/oidc/{tenantId}/start` | Select configured Tenant provider; protected browser login initiation. |
| `GET /auth/oidc/{tenantId}/callback` | Bound OIDC callback only; not a general caller identity submission. |
| `POST /auth/session/refresh` | Validate refresh purpose/transport, reload User, issue current access. |
| `DELETE /auth/session` | Clear browser tokens/cookies; no database revocation claim. |
| `GET /auth/me` | Minimal authenticated current-token profile, including pending state; Phase 2 infrastructure. |
| `GET /tenants` | Minimal accessible Tenant identities for ACTIVE principals, scoped on backend. No inventory/configuration secrets. |
| `POST /tenants` | GLOBAL ADMIN creates Tenant with required name; audit. |
| `GET`, `PUT /tenants/{tenantId}/oidc` | GLOBAL ADMIN configuration/allowlist administration; write-only secret replacement, sanitized reads, version conflict protection. |
| `GET /users` | GLOBAL ADMIN administrative User list; TENANT ADMIN only own ordinary USER targets. No credentials/secrets. |
| `POST /users/global` | GLOBAL ADMIN creates GLOBAL/USER/ACTIVE with null Tenant and D2-compliant credential; separate ADMIN grant. |
| `POST /users/{userId}/approve` | PENDING → ACTIVE under the approved target/role/Tenant restrictions; audit. |
| `PUT /users/{userId}/role` | GLOBAL ADMIN changes role; reject self demotion from ADMIN; load target/current state; audit. |
| `POST /users/{userId}/disable` | GLOBAL ADMIN, or own-Tenant ADMIN for ordinary USER; reject GLOBAL ADMIN self disable; audit. |
| `PUT /auth/local/password` | Own GLOBAL password change, verifies current password. |
| `PUT /users/{userId}/local-credential` | GLOBAL ADMIN credential creation/reset for an existing GLOBAL target; no Tenant credential support. |

OIDC settings/allowed groups update atomically through one endpoint rather than a generalized provider CRUD API. Approval/status endpoints are explicit commands, not arbitrary caller-set scope/status. No user deletion/reactivation workflow, bootstrap/recovery HTTP route, Catalog/inventory REST, AuditLog browser or inventory diagnostic dump. Account/administration UI beyond authentication states remains Phase 3. Tenant name-update/delete endpoints are not needed to verify this phase and are deferred rather than inventing mutation rules.

RFC 9457 problems: 400 malformed input; 401 invalid credentials/token (appropriate Bearer challenge for protected APIs); 403 authenticated but forbidden status/scope/role or denied OIDC eligibility; 404 missing/out-of-Tenant object without revealing its existence; 409 uniqueness/version/invalid administrative-state conflict; 429 throttling; sanitized 503 for unavailable provider verification/dependencies. Return no SQL, secret or raw provider payload. Check principal permission before exposing target details. Unknown routes retain API 404 rather than SPA fallback. OIDC callback translates failure to a fixed safe frontend error state; no token/error-secret in redirects.

## 13. Approved AuditLog and operational logging

**Approved D9:** successful `TENANT_CREATED`, `OIDC_CONFIG_CHANGED`, `USER_CREATED`, `USER_APPROVED`, `USER_ROLE_CHANGED`, `USER_DISABLED`, `LOCAL_CREDENTIAL_CHANGED` and `GLOBAL_ADMIN_BOOTSTRAPPED` commit atomically with their administrative mutation. The last action applies to both initial installation and later zero-usable-admin recovery. Existing Catalog AuditLog/AssetEvent behavior remains unchanged.

Use authenticated server actor UUID/display snapshot; bootstrap/recovery uses the server-created administrator snapshot in its transaction. No live historical User FK. Include necessary target/type/Tenant and sanitized before/after role/status/configuration context; never include plaintext password, hash, JWT, authorization code, OIDC secret, encryption key, cookie or raw provider claims/group payloads. A group-allowlist update may record that it changed without copying raw provider memberships. Credential reset must not falsely claim token revocation.

No permanent per-login success/failure AuditLog records. Login failures, provider failures and throttling use bounded redacted operational security logging, no new permanent security-log domain or retention policy. Do not fabricate historical actors for unknown failed logins. AssetEvent contains no authentication/session noise.

## 14. Transaction and security boundaries

Normal Spring/PostgreSQL READ COMMITTED remains the default. No universal SERIALIZABLE policy, KELIOS-wide lock ordering or generic concurrency subsystem.

| Operation / concrete race | Rows protected and smallest mechanism | Why the final result is valid |
|---|---|---|
| Concurrent bootstrap/recovery at zero usable admins | Singleton recovery guard `FOR UPDATE`; fresh usable-admin check and atomic User/credential/AuditLog create | First creates one usable admin; second refuses after fresh check. No lifetime completion state; later zero-admin recovery remains possible. |
| Recovery versus an ordinary operation creating admin usability | Only GLOBAL ADMIN promotion/activation or credential repair/creation capable of creating usability takes the same guard before target User/credential locks | A zero-admin check cannot race another supported operation making an administrator usable. This is predicate-specific serialization, not a minimum-admin invariant. |
| Concurrent first login for same external identity | Unique `(issuer,subject)` reservation, fresh query after conflict; deferred composite User FK | Exactly one mapped User commits; wrong-Tenant reuse is denied; no orphan User/mapping. |
| Provider/allowlist edit during OIDC network flow | Network calls outside DB transaction; final configuration `FOR SHARE`, compare version and fresh allowlist; config writes `FOR UPDATE` increment version | Changed configuration invalidates the in-flight completion; membership is evaluated against the current locked allowlist. No DB lock held during provider calls. |
| Credential reset/disable during local login | Expensive hash work outside transaction; lock User then credential `FOR SHARE` and recheck hash/status before issue; credential administration uses that same local User-then-credential order with `FOR UPDATE` | A changed credential cannot be accepted from a stale lookup. Credentials are GLOBAL-bound by FK. Issued-before-change JWT expiry remains accepted. |
| Refresh versus disable/role change | Short User `FOR SHARE` while deriving issuance snapshot; administrative User `FOR UPDATE` | Refresh loads a consistent current User; a disable that commits first denies refresh, and a later disable obeys accepted old-access lifetime. No session row. |
| Approval/role/disable competing on same target; destructive self command | Target User `FOR UPDATE`; current scope/Tenant/role/status and authenticated actor-target UUID checks; mutate/audit atomically. Guard first only for the usability-increasing cases above. | No stale target-role/Tenant predicate can authorize escalation; self disable/demotion/removal is rejected. No global last-admin count rule constrains destructive operations on other accounts. |
| Tenant-admin Catalog mutation versus global publication | Existing target mutation locks; authorize freshly loaded target visibility/Tenant within that transaction | Authorization cannot rely on a pre-publication Tenant snapshot. Preserve existing parent/merge locks and Asset versions. |
| Concurrent configuration changes | Configuration version plus single update transaction/target lock for settings and allowlist | No partial settings/secret/allowlist update; stale versions fail rather than overwrite. |

Keep concurrency logic explicit in each application service. Existing Phase 1 lock timeout/conflict handling and caller-driven fresh commands remain; no blind retry of stale writes. Use a consistent local row order only within operations that actually touch the same multiple rows (e.g. credential/User administration), not an unrelated global protocol.

Secrets and keys: private mounted files/configuration, least-privileged runtime process, database encrypted OIDC secrets, no public runtime JSON or build arguments. Deploy trusted TLS termination/proxy boundaries; never generate sensitive URLs from unrestricted forwarded headers. Authentication responses/logging must not reveal credentials or tokens.

**Planned throttling:** bounded process-local attempt limits on GLOBAL login and expensive auth endpoints, keyed by canonical login and trusted client address, with generic 429 and capped memory/expiry; production ingress adds per-IP/global limits. No Redis or persistent account lockout fields. Multi-instance app limits are not globally synchronized; document that limitation and require ingress limits before public deployment. Never trust arbitrary `X-Forwarded-For` for rate-limit identity. Avoid unreviewed permanent account lockout/DoS policies.

## 15. Testing and verification strategy

Implement JUnit/Spring tests and required PostgreSQL Testcontainers integration tests after approval. No H2 substitution, disabled-without-Docker tests or fabricated real-provider evidence.

| Test group | Required assertions |
|---|---|
| Schema and upgrade | Fresh migration and upgrade from V1–V3 preserve Phase 1 data/constraints. GLOBAL-only credential FK; Tenant-only identity composite FK; unique login/issuer-subject; required fields; secret ciphertext not plaintext. |
| Bootstrap/recovery | First creation; repeat refusal; existing usable admin blocks; PENDING/DISABLED/missing or invalid-credential admins do not count; later zero-admin recovery; duplicate login/missing secret/rollback; concurrent recovery and usability-increasing administration with independent PostgreSQL connections; one atomic User/credential/audit; no permanent completion marker/HTTP endpoint/default credentials. |
| Password/local login | Argon2id verify/wrong password/salt/encoded parameters and bounded-resource benchmark; identifier canonicalization/uniqueness; 15–128 code-point boundaries, uppercase/lowercase/digit/special composition and no trimming, including Unicode cases; uniform checks on all five credential-setting paths; no forced-change state; equivalent bad-login errors; Tenant credentials rejected; status, audits and races. |
| JWT | Signature, key ID/algorithm, issuer/audience/purpose, expiry/nbf/iat/skew, malformed/forged tokens, missing explicit scope and inconsistent Tenant claims, access/refresh cross-use, deterministic UTC Clock, logout discarding only. |
| Refresh/current User | Role/status change reflected on refresh; DISABLED/missing User denied; PENDING restricted; absolute refresh expiry; password change does not promise revocation; old access remains valid until expiry under approved rules. |
| Deterministic local OIDC protocol | Discovery and JWKS, code exchange, state/nonce/PKCE, callback Tenant/provider binding, expiry/replay/code failure, ID-token issuer/audience/subject validation, UserInfo subject mismatch, configuration-version change, malicious redirects/Host/SSRF and no persistent HTTP session/provider tokens. |
| JIT/identity | New users always TENANT/USER/PENDING; no request/group ADMIN grant; stable issuer-subject despite changed email/name; issuer/subject changes are distinct; cross-Tenant mapping denied; simultaneous first logins produce one User/mapping; rollback no orphan. |
| Documented IServ adapter and allowlist | Fixture UserInfo follows §8 contract; UUID-based intersection; matching/mismatching subject; missing/malformed/unavailable groups and empty allowlist deny without JIT; display-name changes do not change matching; IServ roles never grant ADMIN. Fixture success is not real compatibility. |
| Authorization | All four scope/role combinations and statuses; read-only/global-vs-own-Tenant restrictions; Tenant ADMIN own USER approvals and no ADMIN grant/management; GLOBAL ADMIN privileges; GLOBAL creation USER/ACTIVE and separate ADMIN grant; self disable/demotion/removal rejected; other-admin destructive actions not blocked by a last-admin invariant; cross-Tenant/forged actor/snapshot/privilege attacks. |
| Protected Phase 1 services | Invoke existing Asset/Place/Catalog operations through secured application boundaries and real PostgreSQL. Verify current visibility/target-role races without adding Phase 3 HTTP endpoints; preserve merge/history/version atomicity and all 71 existing PostgreSQL tests. |
| Browser/HTTP | Approved cookie flags/path/clearing, TLS, CSRF/Origin, no-store, no tokens in URLs/storage/logs, pending contact message/no data requests, refresh/reload/logout, coordinated refresh, disabled/denied states, technical status/branding regression, restrictive CORS. |
| Actual IServ | Separately verify deployed version/configuration, Discovery/JWKS, permitted scopes/consent, PKCE/code flow, actual groups/UUID/subject binding, positive/negative login and mandatory PENDING. Documentation and deterministic test evidence cannot substitute for actual interoperability; record precise gap if unavailable. |
| IServ lifecycle boundary | Later group/allowlist/IServ state changes trigger no synchronization/automatic KELIOS disable. Refresh calls no provider, reloads KELIOS User, and DISABLED rejects; later OIDC login still checks current eligibility. No provider-token persistence. |

Use independent database connections and controlled synchronization for JIT/bootstrap/admin/provider-config races, as with accepted Phase 1 concurrency tests. Test failures/rollback and absence of unwanted rows, not only response status. A local protocol fixture may use a bounded mock OIDC server; it is not evidence of real IServ support.

**Planned runnable commands after implementation, not executed by this planning step:**

```sh
# Repository root: caches/output stay in the repository.
mkdir -p .cache/maven-home .cache/tmp
cd backend
MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw -B verify
cd ../frontend
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm test
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm run build
cd ..
./scripts/smoke-phase0.sh
./scripts/smoke-phase2.sh
```

`smoke-phase2.sh` is a **planned new script**, not an existing command available now. It will include the retained Phase 1 deployment checks and Phase 2 TLS/auth/browser verification, writing only Phase 2 evidence/output. Inspect npm scripts at implementation time and use their non-watch verification mode. The explicit Node path describes the current macOS environment; CI uses Node 24 through its supported installation.

Full Maven `verify` must actually run PostgreSQL Testcontainers. Docker image build `-DskipITs verify` remains a separate, accurately labeled unit-build check; never mount a Docker daemon socket into the builder. Request narrowly scoped Docker approval when actual access fails under the retained sandbox; no sudo or unrestricted filesystem access.

Record exact commands, test counts, versions, failures/resolutions, real-provider evidence, Docker/TLS/browser results and unverified deployments in future `docs/verification/PHASE_2.md`. Report implementation, deterministic documented-contract verification, and real interoperability independently. Unavailable real-provider testing remains pending evidence and does not make otherwise passing implementation unfinished.

## 16. Phase 0/1 regression strategy

Run existing backend unit and PostgreSQL suites (accepted Phase 1 baseline: 107 tests, including 71 PostgreSQL integration cases), frontend tests and production build. These are historical counts, not a new result or the expected final Phase 2 total.

Preserve technical `/api/v1/status`, management isolation, missing-route JSON/404, runtime-config/logo replacement without frontend rebuild, independent non-root multi-stage images, no Maven in backend runtime, healthy Compose, Flyway startup and retained data. Exercise all existing Domain constraints/guards/state graphs, UTC tags, optimistic conflicts and merge/history atomicity.

Update only tests whose approved Phase 1 “no business controllers yet” architecture assertion needs a narrow allowlist for identity/administration endpoints; retain the prohibition on inventory controllers and unsupported merges. Do not weaken Domain assertions to accommodate security.

Existing `smoke-phase1.sh` writes accepted verification artifacts. Do not rerun it unmodified if that overwrites `docs/verification/PHASE_1.md` or its supporting files. Reuse its checks in the new Phase 2 smoke script with Phase 2 destinations. Keep all Phase 0/1 evidence unchanged. Phase 2 evidence separately identifies any regression commands requiring adaptations and all actual results.

## 17. Final decision status and implementation approval boundary

| ID | Final status | Consequence |
|---|---|---|
| D1 | **Approved Option A** | Memory-only access, refresh HttpOnly cookie, same-origin HTTPS, default-off CORS, stateless CSRF/exact Origin; cross-origin deferred. |
| D2 | **Approved with modification** | Supported Argon2id and identifier rules; required password composition on every setting path; bounded benchmark/evidence; no forced first-login change. |
| D3 | **Revised and approved; original proposal superseded** | Deployment bootstrap/recovery at zero usable admins; mutex only, no completion state; self destructive operations denied, no permanent last-admin rule. |
| D4 | **Approved** | RS256 mounted keys/kid/type/issuer/audience, 15-minute access, absolute 30-day refresh and 30-second skew; no session/revocation metadata. |
| D5 | **Approved subject to revised D3** | Dedicated credential/config/allowlist/mapping structures, AES-GCM secrets, stateless state/nonce/PKCE flow cookie and targeted locks. |
| D6 | **Official contract established; real-provider acceptance gate remains** | Sources and exact findings in §8 support the approved documented adapter. Real interoperability is not yet verified. |
| D7 | **Approved** | Explicit GLOBAL creation is GLOBAL/USER/ACTIVE with null Tenant; separate ADMIN grant. TENANT JIT remains USER/PENDING. |
| D8 | **Approved** | OIDC login checks eligibility; no ongoing synchronization or refresh provider query. Manual KELIOS DISABLED removes future refresh access. |
| D9 | **Approved** | Required administrative audit actions including installation/recovery; bounded redacted operational failures only, no per-login AuditLog/retention domain. |

**No remaining business/product decision blocks this Phase 2 scope.** Previously open bootstrap, password composition, ordinary GLOBAL status and IServ lifecycle rules are resolved. User privacy/deletion, reactivation, unrelated recovery/email/MFA and cross-origin/subpath support stay deferred. Deployment-side recovery is in scope and must not be grouped with deferred email/account-recovery features.

**No remaining technical choice requires a separate pre-implementation decision.** Final implementation approval has been given. Argon2id benchmark selection, cookie names, bounded timeouts and dependency compatibility are implementation/verification work within approved choices; resolve conservatively and record results. Planned display-name fallback to verified subject is not identity linking or role assignment. Real IServ access/configuration remains an external acceptance dependency, not a reason to guess contract fields.

Traceability: Domain §§1–2 and §8 now record business/account/lifecycle/audit clarifications; Architecture §4 and §9 record technical choices, with a related §7 forward reference. CODEX_IMPLEMENTATION_TASK Phase 2 remains broad and compatible, so it is unchanged: its item 3 is satisfied before adapter coding by the official contract evidence in §8, with actual interoperability still required for acceptance under the user's D6 clarification. ADR 0001/0002 remain historical; [ADR 0003](../adr/0003-phase-2-authentication-and-authorization.md) records approved implementation decisions. This document supersedes its earlier permanent-completion/optional-last-admin/no-composition proposals, without restoring any superseded Phase 1 requirement.

Final implementation consistency review includes these specifications, historical ADRs, ADR 0003 and this plan. The earlier documentation-only revision changed no implementation; the subsequent explicit approval authorizes Phase 2 code/migration/contract/runtime/test work only.

## 18. Implementation sequence after approval

1. Implementation approval is obtained. Record approved D1–D9 in ADR 0003, preserving historical ADRs and specifications. No real IServ environment is available this cycle; prepare the later manual verification procedure without requesting credentials or blocking implementation.
2. Add additive migrations, mappings and PostgreSQL schema/upgrade/concurrency tests. Preserve minimal User and historical actor contracts.
3. Implement deployment-side bootstrap/recovery with its narrow guard, GLOBAL credentials/login, self-protection and explicit administration/audit services. Add JWT signing/validation/refresh with no session/revocation storage.
4. Add authenticated principal and explicit secured application boundaries; verify the complete role/Tenant/status matrix against existing services before exposing administration routes.
5. Implement provider-independent database-resolved OIDC Discovery and stateless protected flow, then the minimal IServ adapter against §8 official documentation and accurate deterministic fixtures. Fail closed on any insecure/undetermined eligibility; do not claim real compatibility from fixture success.
6. Add JIT PENDING, approval/role/disable operations, sanitized configuration administration and minimal `/api/v1` auth/admin contract. Keep User facts and actors server-authoritative.
7. Add only required frontend login/completion/pending/refresh/logout states with the approved transport, preserving runtime branding and technical status.
8. Run full Maven/PostgreSQL, frontend, independent image, TLS/browser and Phase 0/1 regression verification. Record real IServ checks as pending manual evidence this cycle and provide a complete configuration-only manual procedure. Write Phase 2 evidence without altering accepted evidence.
9. Stop after Phase 2 implementation/documentation/verification. No Phase 3, commits or pushes without separate instruction.

Planning traceability: the earlier documentation revision re-read specifications/ADRs, recorded approved D1–D9 and official IServ/Untis sources, and performed no implementation. The subsequent explicit implementation approval and no-real-provider clarification are reflected above; [Phase 2 evidence](../verification/PHASE_2.md) records actual implementation results. Actual IServ interoperability remains pending manual verification.
