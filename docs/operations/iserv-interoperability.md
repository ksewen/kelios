# Phase 2 — Manual real-IServ interoperability verification

Status: **Pending manual verification.** No real IServ environment is available during this implementation cycle. Production contract implementation and deterministic tests are recorded separately in [Phase 2 evidence](../history/phase0-4.md); they do not prove real-provider compatibility. Perform the following using deployment/provider configuration, without changing source or rebuilding images.

## Prerequisites and provider configuration

Use a real Tenant IServ installation, an IServ administrator, a KELIOS ACTIVE GLOBAL ADMIN, two test users (allowed and outside all allowed groups), trusted HTTPS/DNS, and backend network access to the provider. Record deployed IServ version as an observation: official documentation does not specify a minimum version for this contract.

Primary references: [IServ SSO configuration and claims](https://doku.iserv.de/manage/system/sso/) and [IServ developer UserInfo example](https://doku.iserv.de/development/oauth/). Configure a client under IServ Administration → System → Single-Sign-On, enable authorization-code grant, register the exact KELIOS callback URI, and allow required scopes. Users need the provider's documented OAuth-use permission. Verify consent/client restrictions rather than assuming access is granted.

KELIOS requests only `openid profile iserv:groups`. It does not request WebUntis scopes or IServ roles for authorization. KELIOS requires S256 PKCE and validates nonce/state; confirm the installed provider accepts the flow. Do not downgrade validation if configuration/version is incompatible.

For the configured trusted KELIOS public origin `https://kelios.example` and Tenant UUID `<tenantId>`, register exactly:

```text
https://kelios.example/api/v1/auth/oidc/<tenantId>/callback
```

Replace origin/Tenant using actual deployment values. `KELIOS_AUTH_PUBLIC_ORIGIN` must match the external HTTPS origin; redirects are not inferred from browser Host/forwarded headers. Backend discovery uses the configured issuer and its Discovery document, not copied WebUntis endpoint examples. Confirm the discovered issuer matches exactly, with working authorization/token/UserInfo/JWKS endpoints and appropriate client authentication. Production egress/TLS trust configuration must allow the real provider; explicitly allow private/provider-secondary hosts where required. Never disable TLS validation or use an arbitrary redirect.

## KELIOS configuration and group selection

1. Sign in as ACTIVE GLOBAL ADMIN to create/select a concrete Tenant from the authorized Tenant collection, or as ACTIVE TENANT ADMIN to manage only the signed-own Tenant configuration. TENANT ADMIN never uses the Tenant collection as a selector. Administrator credentials and mounted signing/encryption keys must already be configured; see [authentication operations](authentication.md).
2. Obtain the intended group UUID from the documented `iserv:groups` UserInfo list's `id` field, through an approved administrator-controlled provider/tool interaction using the required scope. If the provider's administration interface exposes UUIDs, confirm it is the same identifier. Treat UI/tool availability as an observation, not a promised undocumented IServ API. Do not match `act` or `name`, substitute a role ID, or record the complete personal payload.
3. `PUT /api/v1/tenants/<tenantId>/oidc` as an authorized ACTIVE administrator with issuer, client ID, client secret, enabled flag, version and `allowedGroupIds` UUID array. On creation version is null; subsequent updates use the GET response version. Secret is required initially and may be omitted/null when retaining it. Submit secret through a private HTTPS administration client/input, never a shell argument or checked-in example/evidence.
4. Verify sanitized `GET` shows the correct Tenant/issuer/client ID, `secretConfigured=true`, group UUIDs, version and derived `callbackUri`, but no client secret/ciphertext. Register that exact callback on IServ. Changing provider values/allowlist needs no image rebuild.
5. Open `/login/tenant/<tenantId>` or enter the Tenant UUID in the authentication form. Start uses the dynamically loaded database configuration. Observe redirect to the discovered provider endpoint; never capture authorization codes or full callback URLs in evidence.

If the administration UI does not expose the UUID, use an administrator-controlled OIDC inspection client: obtain Discovery from the exact issuer, perform authorization-code login with `openid profile iserv:groups`, nonce and S256 PKCE for a representative group member, exchange the code privately, and call the discovered UserInfo endpoint with that request-local Bearer token. Select the intended group object and copy only its UUID `id` into KELIOS configuration. Names may help the operator identify the intended object but are never the stored matching key. Keep credentials/codes/tokens/payloads private and discard them after inspection; use a separately registered inspection redirect if the tool requires one. This uses the documented UserInfo contract, not an invented groups API. KELIOS deliberately does not expose raw provider memberships on a failed login; do not bypass its allowlist to discover IDs. Exact UI/tool availability is a later setup observation.

## Positive, negative and account lifecycle tests

Use an isolated browser profile for each positive/negative account, or explicitly sign out of KELIOS and select the intended IServ account. KELIOS logout does not terminate provider SSO. A failed new login issues no new KELIOS tokens but does not revoke a previously issued Refresh JWT; do not mistake an older session for a successful negative test.

| Step | Action | Required KELIOS result / provider observation |
|---|---|---|
| Allowed first login | Authenticate an unused identity belonging to an allowed UUID group. | Successful callback; a single User with TENANT scope, this Tenant, USER role, PENDING status. Pending contact-administrator message; no business/admin data. Verify actual claim shape/UUID serialization and matching UserInfo subject privately as provider observations. |
| Negative login | Authenticate a different user outside every allowed group. | Denied callback; no new User/external mapping or issued KELIOS refresh cookie. Confirm the denied group condition, without recording the full membership payload. |
| Approval | GLOBAL ADMIN approves `/api/v1/users/<id>/approve`, or same-Tenant ADMIN approves an ordinary USER. | ACTIVE. Refresh or subsequent OIDC login yields ACTIVE access; TENANT ADMIN cannot grant ADMIN. |
| Identity continuity | Repeat login for the same account; optionally change its display name/email in IServ and repeat. | Same KELIOS User UUID from the same issuer/subject, no duplicate. Display/email are not identity keys. Record continuity outcome; keep subject/user values private or pseudonymized. |
| Disable | Authorized administrator calls `/api/v1/users/<id>/disable`. | New refresh rejected; OIDC login issues no new tokens. Previously issued ACTIVE Access JWT remains valid until its normal approximately 15-minute expiry. |
| Later group removal | For another approved ACTIVE account, remove provider membership or remove its UUID from the KELIOS allowlist. | A new OIDC login is denied. Existing KELIOS User remains ACTIVE; existing refresh still works until absolute expiry, with no provider lookup. No automatic sync/disable/revocation. Manually DISABLE to deny future refresh. |
| Empty allowlist / configuration failure | In a controlled test, empty the allowlist; separately check unavailable/misconfigured provider behavior. | Fail closed; no new provisioning/tokens. Restore configuration with the current version before retesting. |

PENDING can authenticate/refresh and inspect minimal self status only. Access-token permissions/status are snapshots; refresh reloads current KELIOS User. Logout discards browser access and clears refresh cookie, without server revocation or a claim to log out the IServ SSO session. No forced first-login password change applies to GLOBAL bootstrap/recovery; that is independent of Tenant OIDC.

## Troubleshooting observations

- **Discovery:** compare configured and returned issuer exactly; verify HTTPS chain, backend DNS/egress, timeouts, supported signing algorithm, and discovered endpoints. Check installed version/configuration; do not replace Discovery with hard-coded paths.
- **Redirect:** compare GET `callbackUri`, registered provider URI and externally reachable origin byte-for-byte. Check scheme, port, Tenant path and reverse-proxy routing; do not trust arbitrary forwarded headers.
- **Scopes/permissions:** confirm `openid profile iserv:groups` is requested/permitted and the user's OAuth permission/consent is valid. Do not add undocumented scopes or role mappings.
- **State/nonce/PKCE:** start a fresh browser flow; confirm Secure/Lax flow-cookie delivery on the GET callback and S256 support. Verify all checks without publishing verifier/state/nonce/code values.
- **UserInfo:** confirm endpoint/token authorization succeeds and `sub` equals the validated ID-token subject. A mismatch denies login; do not repair it by username/email matching.
- **Missing/malformed groups:** observe that `iserv:groups` is the documented list with valid UUID `id` fields. Missing claim, wrong type, invalid UUID, unavailable UserInfo or uncertain membership must deny. Capture only the sanitized shape/outcome.
- **UUID mismatch:** compare the selected group `id` with configured UUIDs privately; mutable display/account names and IServ role IDs are unsuitable. Record whether IDs match, not the whole provider response.
- **Cookies/refresh:** use same-origin HTTPS, trusted Origin and the CSRF exchange; CORS is disabled. Existing HTTP technical smoke is not an authenticated browser deployment. Check certificate trust, cookie path/flags and browser clock without weakening validation.
- **Configuration change:** a flow started before a provider-version change is rejected; restart login. Disabled/missing provider config fails closed. Errors/logs must not reveal codes/tokens/secrets.

## Evidence needed to close the real-provider gate

Record date, tester, deployed IServ/KELIOS versions, deployment topology, sanitized client/redirect/scopes configuration checks, observed group response structure/UUID type and subject-binding outcome, positive/negative/empty-group results, JIT fields, identity continuity, approval/disable/D8 behavior and failures/resolutions. Record that production code and group validation were used unchanged.

Do not include passwords/hashes, client secrets, JWT/provider tokens, authorization codes, cookies, encryption/signing keys, full callback URLs or complete personal/provider/group payloads. Use pseudonymous test identifiers and redact screenshots/network captures before storing them. Actual wire/version details unavailable today are verification observations, not fabricated outcomes.

Record sanitized real-provider results in a separately human-reviewed closeout and then update [remaining work BQ1](../plans/remaining-work.md). Until then: implementation and deterministic documented-contract verification passed, while real IServ interoperability remains pending manual evidence.
