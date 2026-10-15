# Guided local KELIOS deployment

**Historical O1 verification boundary — 2026-10-13:** The [accepted O1 execution plan](plans/DEPLOYMENT_CLI_O1_EMAIL_RECOVERY_PLAN.md) governs only the Email bootstrap/recovery handoff. The shared CLI helper now passes explicit Email through `kelios.recovery.email`; O1.1–O1.7 and overall O1 are HUMAN ACCEPTED / CLOSED. Current operator instructions below reflect that implementation. Verification comprises isolated PTY/fake-Docker controls and separate PostgreSQL service tests; no fresh live Docker recovery or real Email delivery is claimed. Historical CLI v1 design/evidence retains its original behavior, results and limitations.

`./kelios` is a repository-local shell tool. Prerequisites: Docker with Compose v2/BuildKit, a running daemon and OpenSSL. Initial builds need registry/dependency network access. Host Java/Node/Python are not installer dependencies. Python is only used by repository verification scripts.

**Current deployment closeout — 2026-10-15:** DCLI-B1 and its real supported install/status/uninstall verification are HUMAN ACCEPTED / CLOSED. See [deployment evidence](verification/DEPLOYMENT_FRONTEND_DOCKER_CONTEXT_CORRECTION.md). The isolated environment was subsequently removed through the CLI; retained private files are not a running installation.

## Where to start

This is the authoritative operator guide. Commands run from the **source directory containing `kelios`, `frontend/`, `backend/` and `deploy/`**, not from `deploy/`. Each such directory owns its `.kelios/` installation state. Use an interactive terminal, Docker with a running daemon and Compose/BuildKit, OpenSSL 3, free local ports, and network access for initial images/dependencies. You do not need host Java or Node. Git is needed for the fresh-source preparation below.

For the current workstation, open this guide from `/Users/ksewen/IdeaProjects/github/kelios`. That repository already has `.kelios` state; do **not** run a fresh install over it. Prepare a separate source directory, leaving existing deployments intact:

```sh
cd /Users/ksewen/IdeaProjects/github/kelios
mkdir -p .cache
LOCAL_SOURCE=$(mktemp -d "$PWD/.cache/manual-local.XXXXXXXX")
git archive HEAD | tar -x -C "$LOCAL_SOURCE"
cd "$LOCAL_SOURCE"
printf 'Save this installation directory: %s\n' "$PWD"
```

This recipe intentionally uses committed source, not unrelated dirty files. Use a revision containing the accepted DCLI-B1 Docker-context correction. Save the printed path: all later CLI commands for this installation must run there. For other machines, use the actual repository path. A separate clean checkout containing the correction is equally suitable. Do not use a source revision that still excludes the shared unit fixture from its Docker build context.

## Prepare localhost TLS

The CLI accepts supplied certificates; it does not create or trust them. For isolated localhost testing, the following OpenSSL 3 commands create a new self-signed certificate outside `.kelios`, without overwriting a previous certificate:

```sh
umask 077
mkdir -p .cache
LOCAL_TLS=$(mktemp -d "$PWD/.cache/local-tls.XXXXXXXX")
openssl req -x509 -newkey rsa:2048 -sha256 -nodes -days 30 \
  -subj /CN=localhost -addext 'subjectAltName=DNS:localhost,IP:127.0.0.1' \
  -keyout "$LOCAL_TLS/private.pem" -out "$LOCAL_TLS/certificate.pem"
chmod 600 "$LOCAL_TLS/private.pem"
printf 'Certificate: %s\nPrivate key: %s\n' "$LOCAL_TLS/certificate.pem" "$LOCAL_TLS/private.pem"
```

Enter these absolute paths at the install prompts. Use `localhost` as the browser hostname. The browser will warn because the certificate is self-signed: trust that specific certificate using your OS/browser procedure, or use the browser's permitted local exception. Do not disable browser security globally or use HTTP for login. Certificates expire after 30 days in this example; the CLI validates expiry/hostname/key match but does not renew certificates. Use an operator-managed trusted certificate for other hostnames; this is not production TLS automation.

## First install

Run `./kelios install` in an interactive terminal. Defaults are managed PostgreSQL, loopback bind, HTTP technical port 8080 and HTTPS application port 8443, browser hostname localhost and green branding. Supply an existing readable PEM certificate/chain and unencrypted private-key file covering your browser hostname. The CLI validates match/expiry/hostname, copies them into its private state and mounts the existing nginx HTTPS configuration. It does not issue certificates. Trust your local certificate through your normal browser/OS procedure; an untrusted operator certificate may produce a browser warning. HTTP is not an authentication fallback.

From the fresh source directory, run:

```sh
./kelios install
```

| Prompt | Fresh localhost choice |
|---|---|
| Listen address | `127.0.0.1` (default; keep local) |
| HTTP technical/health port | Default `8080`, or another free port such as `18080`; not the login URL |
| HTTPS application port | Default `8443`, or another free port such as `18443` |
| Browser hostname | `localhost`, covered by your certificate |
| TLS certificate / private-key paths | Absolute paths printed above |
| Database mode | `managed` for a new isolated PostgreSQL volume |
| Runtime theme / logo | Accept green/default logo, or supply supported choices |
| Existing authentication key directory | Leave blank for a **new** database |
| GLOBAL ADMIN Email | Explicit Email you choose; no default account identifier |
| Display name | Default `KELIOS Administrator` or your chosen name |
| Administrator password / confirmation | Enter the same policy-compliant password twice; input is hidden |

Choose and save your administrator password in your password manager. The CLI does **not** generate or retain a recoverable administrator password file. Do not confuse the generated database password with the administrator password. The backend stores the administrator credential hash in PostgreSQL. No business sample data or Tenant creation is part of these operator instructions.

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

## URL, start, stop and status

After successful install, open the exact HTTPS URL printed by the CLI (normally `https://localhost:8443`). To rediscover it and check health, run `./kelios status` from the same source directory. Status may still print the configured URL when services are absent; require running/healthy services before expecting that URL to work.

```sh
./kelios status
./kelios stop
# When you intentionally want the SAME installation running again:
./kelios start
```

`stop` leaves stopped containers, the network, managed volume and all files intact. `start` does not rebuild images or bootstrap another administrator. Do not use `install` as a restart command. After removal with data preserved, `start` recreates the runtime against the same data/keys. Missing images, expired certificates or missing original keys require troubleshooting, not a reset.

## Inspect logs (Docker procedure; no CLI logs command)

There is no `./kelios logs` command. From the installation's source directory, read only its recorded project identifier and select its service container by Compose labels:

```sh
LOCAL_PROJECT=$(sed -n 's/^PROJECT=//p' .kelios/installation.conf)
case "$LOCAL_PROJECT" in kelios-local-*) ;; *) echo 'Missing/invalid project; stop here'; exit 1;; esac
docker ps -a --filter "label=com.docker.compose.project=$LOCAL_PROJECT"
LOCAL_SERVICE=frontend  # use backend, or postgres for managed mode
LOCAL_CONTAINER=$(docker ps -aq \
  --filter "label=com.docker.compose.project=$LOCAL_PROJECT" \
  --filter "label=com.docker.compose.service=$LOCAL_SERVICE")
if [ -n "$LOCAL_CONTAINER" ]; then
  docker logs --tail 100 "$LOCAL_CONTAINER"
else
  printf 'No container for %s; inspect retained build output if installation failed.\n' "$LOCAL_SERVICE"
fi
```

Use `docker logs --follow --tail 100 "$LOCAL_CONTAINER"` to follow an identified container; Ctrl-C stops log following, not the service. This label-scoped Docker procedure was verified against the CLI-created frontend container. It avoids reconstructing the CLI's transient Compose environment or accidentally selecting another project's services. Do not source `installation.conf`, print secrets, or publish raw logs without reviewing them for sensitive information. Container logs cease to be available after uninstall removes containers.

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

### Remove the deployment using the CLI

From its source directory:

```sh
./kelios uninstall
```

Answer `yes` to remove runtime. The CLI stops/removes its containers and project network. For managed PostgreSQL it next asks whether to permanently delete the database:

- Answer `no` (default) to retain the database, accounts and data. `./kelios start` restores the same installation.
- Answer `yes`, then type the exact `DELETE <project>` string displayed by the CLI, to delete that installation's managed database volume permanently, including its administrator account. The CLI verifies volume ownership/consumers. It marks the preserved configuration `data-deleted`; `start` refuses to silently recreate an empty database.
- External PostgreSQL is never deleted by this command. Its data remains the external operator's responsibility.

| State/resource | Stop | Default uninstall | Uninstall + confirmed managed deletion |
|---|---|---|---|
| Containers | Stopped, retained | Removed | Removed |
| Project network | Retained | Removed | Removed |
| Managed database volume/accounts/data | Retained | Retained | Permanently deleted |
| `.kelios` configuration, DB password, auth keys, copied TLS, branding | Retained | Retained | Retained, marked data-deleted |
| Supplied certificate/key originals and any operator password-manager records | Unchanged | Unchanged | Unchanged |
| Built images/build cache/source checkout | Retained | Retained | Retained |

The CLI supports runtime removal and optional managed-data destruction, **not an all-files purge/reset**. It deliberately does not delete private host files, original certificates, images or caches. A fully automatic host-artifact purge is a separate capability decision, not an invented command or a prerequisite to fresh installation.

For a genuinely fresh installation after destruction, use another clean source directory as described above and run `./kelios install` there with new keys and administrator input. Retain/archive old private state securely according to your retention policy; the CLI's message permits explicit archival/removal but does not perform it. Never move/delete state belonging to a running or needed installation. These instructions do not require manual Docker resource deletion.

### Recovery and failure output

`./kelios recover-admin` starts the saved runtime if necessary, prompts for explicit Email/display name/password, and creates a GLOBAL ADMIN only when backend recovery eligibility permits it (zero usable GLOBAL ADMIN accounts). It is not a password reset for an existing usable administrator and has no force option. Do not attempt recovery just to test it against operator data.

Image-build failure output is retained at `.kelios/temporary/build-output` in the installation's source directory. Inspect locally with `tail -n 100 .kelios/temporary/build-output`. Successful image build removes that file. Runtime startup failure leaves state for `status` and the label-scoped logs procedure. Transient recovery password/output files are cleaned after success/refusal/interruption; sanitized refusal does not imply that credentials were created. Installation may remain `prepared` after failure. Do not rerun install over it: resolve the reported blocker first, then use supported `start`/eligible `recover-admin` as described above. Do not bypass a failing build by skipping tests.

For direct maintained Compose use, managed mode now requires both `-f deploy/compose.yaml -f deploy/compose.postgres.yaml`; add `-f deploy/compose.auth.yaml` with supplied HTTPS/key environment for authenticated use. Existing smoke callers include the managed overlay. Do not run old smoke scripts into accepted evidence as a CLI test.

Real IServ interoperability remains MANUAL VERIFICATION PENDING; CLI installation does not configure or verify a provider.

Verification tooling deliberately refuses existing `.cache/deployment-cli/managed` or `external` fixture directories. After confirming their runtime is removed, archive those private fixture directories before another verification run; do not adopt an unrelated database or project. The tested host uses OpenSSL 3. Conservative v1 path/database-identifier validation and DNS/IPv4 browser-host selection are documented verification limits. Advanced provider trust settings remain available in the maintained manual Compose overlay; the CLI configuration menu does not administer them. An existing database with a usable GLOBAL ADMIN refuses initial bootstrap rather than creating another administrator.
