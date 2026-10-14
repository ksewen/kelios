# Guided local KELIOS deployment

**Current O1 boundary — 2026-10-13:** The [accepted O1 execution plan](plans/DEPLOYMENT_CLI_O1_EMAIL_RECOVERY_PLAN.md) governs only the Email bootstrap/recovery handoff. The shared CLI helper now passes explicit Email through `kelios.recovery.email`; O1.1–O1.7 and overall O1 are HUMAN ACCEPTED / CLOSED. Current operator instructions below reflect that implementation. Verification comprises isolated PTY/fake-Docker controls and separate PostgreSQL service tests; no fresh live Docker recovery or real Email delivery is claimed. Historical CLI v1 design/evidence retains its original behavior, results and limitations.

`./kelios` is a repository-local shell tool. Prerequisites: Docker with Compose v2/BuildKit, a running daemon and OpenSSL. Initial builds need registry/dependency network access. Host Java/Node/Python are not installer dependencies. Python is only used by repository verification scripts.

## First install

Run `./kelios install` in an interactive terminal. Defaults are managed PostgreSQL, loopback bind, HTTP technical port 8080 and HTTPS application port 8443, browser hostname localhost and green branding. Supply an existing readable PEM certificate/chain and unencrypted private-key file covering your browser hostname. The CLI validates match/expiry/hostname, copies them into its private state and mounts the existing nginx HTTPS configuration. It does not issue certificates. Trust your local certificate through your normal browser/OS procedure; an untrusted operator certificate may produce a browser warning. HTTP is not an authentication fallback.

Choose managed PostgreSQL for a fresh local installation. For external PostgreSQL, supply a dedicated database reachable from the backend container, username and non-echoed password. Container localhost is not the host machine; Docker Desktop commonly provides host.docker.internal. Flyway migrates the chosen database normally. An existing encrypted KELIOS database needs its original authentication encryption keys; supply its key directory, never infer or regenerate those keys. The supplied-key input requires `main.private.pem`, matching `main.public.pem` and `main.aes` (base64 32 bytes); additional versioned `.aes`/`.private.pem`/`.public.pem` files with valid key IDs are retained. Do not include unrelated files or recovery passwords in that directory.

The tool builds the existing independent images, waits for health and asks for explicit GLOBAL ADMIN Email (no account default), display name and password/confirmation. Existing backend password policy applies: 12–128 Unicode code points, with uppercase, lowercase, digit and special/non-alphanumeric characters; password content is not trimmed and no newline is appended. Input uses a temporary mounted file, removed on completion/refusal/interruption. Successful install reports the actual HTTPS URL. A failed command does not prove credentials or a usable installation.

Both `install` and `recover-admin` use the same protected Email recovery helper. The CLI rejects empty/all-POSIX-whitespace Email and passes nonblank input as one quoted `kelios.recovery.email` argument; the backend owns Email normalization, syntax/length, case-insensitive uniqueness and recovery eligibility. Email is not persisted in installation configuration. Bootstrap uses the accepted pre-delivery manual-verification contract; no verification Email is sent. See [authentication operations](AUTHENTICATION.md) for the authoritative account and zero-usable-admin rules.

## Seven commands

| Command | Meaning |
|---|---|
| `./kelios install` | Guided first install; refuses existing or incomplete state. |
| `./kelios start` | Reapply saved configuration and start healthy runtime without build, regenerated keys or bootstrap. Repeated start is safe. |
| `./kelios stop` | Stop runtime, preserve all data/configuration/keys. |
| `./kelios status` | Minimal service state/health, actual HTTPS URL and database mode. Health does not prove OIDC interoperability or login. |
| `./kelios config` | Staged port/bind, theme/six existing color tokens and local logo menu. Save or cancel. Run start to apply; reload browser for branding. No image rebuild. |
| `./kelios uninstall` | Remove runtime only by default. Managed permanent deletion is an optional separate typed confirmation; external database is never removed. |
| `./kelios recover-admin` | Ensure the saved runtime is available, prompt for explicit GLOBAL ADMIN Email (no default), display name and password/confirmation, then invoke the shared protected recovery. Refuses when any usable GLOBAL ADMIN exists. No force, Login fallback or automatic retry. |

Changing HTTPS port changes the exact public origin/callback. Update your IServ client's registered redirect URI accordingly; old browser tabs must use the new URL. The JWT issuer stays installation-stable. Changing bind does not change browser hostname or certificate. Wider network binds are explicit operator decisions; this remains a local/test deployment tool, not production ingress configuration. Config does not change TLS material, database mode/connections, users, OIDC, authorization or business settings.

## Private local state

`.kelios/` is ignored by Git, mode 0700, and contains:

```text
installation.conf                 # validated non-secret manifest; never sourced
secrets/db-password
secrets/auth/main.private.pem
secrets/auth/main.public.pem
secrets/auth/main.aes
secrets/tls/certificate.pem
secrets/tls/private.pem
branding/config.json
branding/logos/logo.<extension>
temporary/                        # transient Compose env/recovery/build input
```

Mutating operations use a separate `.kelios.lock` directory. An abandoned lock requires inspecting whether another invocation is active before manually removing that empty lock. Never remove active locks. Transient Compose env contains only nonsecret paths/settings and is removed on exit; it is not a second config source.

Runtime-readable secret files are read-only, behind a host-private parent. Do not make the parent public. Docker access itself is privileged. Do not print/copy keys, passwords, tokens or resolved secret configuration into logs/evidence. Database and original AES keys must remain together for encrypted OIDC data to be recoverable. Stop/default uninstall preserve all keys; start never regenerates missing keys. V1 does not rotate secrets or manage backups.

## Failures and removal

A failed build/start/bootstrap leaves private installation state and any created data intact. Read the actionable error; do not rerun install to overwrite keys. A build failure retains local private build output. Once existing images build successfully, start resumes runtime; recover-admin finishes protected initial creation if zero usable administrators remain. Missing/invalid state or keys requires operator inspection, not an automatic reset. Recovery refusal may mean an existing usable administrator or invalid input; the CLI does not dump raw backend stack traces or bypass policy.

Default uninstall preserves the managed database, config and keys: start recreates the same installation. Install still refuses that state. Permanent managed deletion requires typing `DELETE <recorded-project>` exactly after a separate warning. Configuration/keys remain; start then refuses the data-deleted installation. Explicitly archive/remove this private state before choosing a genuinely new installation. There is no reset command. External uninstall never drops databases/schemas or owns the external server.

For direct maintained Compose use, managed mode now requires both `-f deploy/compose.yaml -f deploy/compose.postgres.yaml`; add `-f deploy/compose.auth.yaml` with supplied HTTPS/key environment for authenticated use. Existing smoke callers include the managed overlay. Do not run old smoke scripts into accepted evidence as a CLI test.

Real IServ interoperability remains MANUAL VERIFICATION PENDING; CLI installation does not configure or verify a provider.

Verification tooling deliberately refuses existing `.cache/deployment-cli/managed` or `external` fixture directories. After confirming their runtime is removed, archive those private fixture directories before another verification run; do not adopt an unrelated database or project. The tested host uses OpenSSL 3. Conservative v1 path/database-identifier validation and DNS/IPv4 browser-host selection are documented verification limits. Advanced provider trust settings remain available in the maintained manual Compose overlay; the CLI configuration menu does not administer them. An existing database with a usable GLOBAL ADMIN refuses initial bootstrap rather than creating another administrator.
