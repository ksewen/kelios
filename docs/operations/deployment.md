# Deployment Workflow v2 operator guide

This guide describes the current v2 CLI on `feat/deployment-workflow-v2`. Linux AMD64 production support still needs the disposable-server verification recorded in the [Plan](../plans/deployment-workflow-v2.md). Use an ordinary operator account with approved access to a **local** Docker Engine and its Compose v2 plugin. Docker socket access grants high-privilege control of the host. The CLI does not install Docker, change host permissions during normal operation, or require root. See [Docker host access](docker-host-access.md) before granting that access.

The initial publishing profile is containerized Nginx with same-origin HTTPS. The HTTPS port serves the frontend, runtime branding, and `/api/` proxy. The separate HTTP port serves `/healthz` only. The operator provides a trusted certificate or explicitly trusts a [private-network certificate](certificates.md); the CLI does not issue a public certificate or renew one automatically. External reverse proxies, host Nginx, rootless runtimes, and public certificate automation are outside this profile.

## Source and persistent state

Run `./kelios` from a **clean Git checkout** containing `kelios`, `deploy/`, `backend/`, and `frontend/`. Install and update derive a release tag from that checkout's commit. The checkout may be replaced for an update; `start`, `status`, and other routine commands still read the same instance state. The default state is `${XDG_DATA_HOME:-$HOME/.local/share}/kelios/default`. To choose another absolute directory, pass `--state-dir "$KELIOS_STATE_DIR"` on **every** command after setting `KELIOS_STATE_DIR` to that absolute directory. The CLI resolves source files from its own location, regardless of the current working directory. It creates private temporary work itself; do not prepare `.cache`.

State contains the installation manifest, database password, authentication keys, active TLS certificate/key, branding, and private build diagnostics. Managed PostgreSQL rows live in the installation's named Docker volume; an external database remains under its operator's control. Keep state, the managed volume, and backups together according to your recovery policy. Never place state or secret files in Git.

## First installation

1. Install Docker Engine, Compose v2, OpenSSL, Git, and Linux `ss` (iproute2). Ensure the daemon is running and the operator has approved local-socket access. No host Java or Node installation is required.
2. Choose free HTTP and HTTPS host ports (defaults `8080` and `8443`) and a DNS name or IPv4 browser hostname covered by the certificate. Register the exact HTTPS origin/redirect URI with any intended OIDC provider.
3. From the clean checkout, run:

   ```sh
   ./kelios doctor
   ./kelios certificate init
   ./kelios install
   ./kelios status
   ```

`certificate init` is optional. It creates a self-signed certificate for the chosen private hostname, valid for 730 days by default. Trust that certificate on the clients that will use the deployment. If you skip it, `install` asks for existing certificate and unencrypted private-key PEM paths, validates them, and copies them into private state. The certificate's hostname must match the configured browser hostname. Public deployments need an operator-supplied publicly trusted certificate.

Install prompts for the bind address, ports, database mode, branding, and first GLOBAL ADMIN Email/password. Managed PostgreSQL and fresh authentication keys are the defaults. For an existing encrypted database, supply its original authentication key directory; generating new keys would make existing encrypted data unreadable. `install` refuses an existing or prepared instance and does not reset it. A prepared install after a failed build or startup resumes with `./kelios resume-install` from the **same clean source release**; noninteractive recovery must again supply administrator input. The backend permits administrator creation only under its zero-usable-admin rule.

For unattended installation, use `--config <private-config-file> --non-interactive` with non-secret settings and paths to private password files. The config is `KEY=value`, not a shell script. For example, `BROWSER_HOST`, `ADMIN_EMAIL`, and `ADMIN_PASSWORD_FILE` may be supplied; use `TLS_CERTIFICATE` and `TLS_PRIVATE_KEY` only when no certificate was provisioned. Password values never belong in the config or command arguments. See the [certificate procedure](certificates.md) for unattended certificate input.

## Update and routine operation

Before a real update, take and check a usable database backup through your existing database procedure. Flyway migrations may commit before a later deployment failure; the CLI does not roll them back or provide a general backup/restore product. Obtain a clean checkout of the target release, run `./kelios doctor`, and invoke `./kelios update` from that checkout for the default state. For another state directory, set `KELIOS_STATE_DIR` to its absolute path and use `./kelios --state-dir "$KELIOS_STATE_DIR" update` (and the same option on `doctor`). Update builds separate release-tagged backend/frontend images before switching the saved target and runtime. A failed build leaves the current runtime selected. If startup fails after the target is saved, inspect `status` and `logs`, correct the cause, and rerun `start` or the same `update`. Existing database data, keys, configuration, and certificates are retained.

```sh
./kelios status
./kelios logs
./kelios stop
./kelios start
./kelios restart
```

`status` reports service health and the configured URL; it does not prove an actual browser login. `logs` prints at most the latest 100 lines per Compose container and may contain private operational information. Nginx disables access logging for `/api/v1/auth/`, including the OIDC callback; the CLI also redacts query strings on that path from displayed logs. Review backend and database logs before sharing them. `stop` preserves state and data. `start` reapplies the saved image release and configuration without rebuilding or generating secrets. `restart` recreates services from the same saved inputs. Neither is a substitute for `update`.

## Configuration and TLS

`./kelios config` stages interactive changes to the bind address, ports, theme, and logos. Choose **Save and exit** to validate and save; **Exit without changes** discards the session. Run `./kelios start` afterward to apply changes, and reload the browser for branding. No frontend image rebuild is needed. To change common settings without prompts, use `./kelios --config <private-config-file> --non-interactive config`; this command accepts only `BIND`, `HTTP_PORT`, `HTTPS_PORT`, `THEME`, and `ORGANIZATION_LOGO`. An empty `ORGANIZATION_LOGO=` clears that mark. New ports are checked before saving. The HTTPS port changes the public origin and OIDC redirect URI; update provider registration accordingly. The installation's JWT issuer remains stable, and changing the bind address does not change the browser hostname or certificate identity.

Use `./kelios certificate replace` for an explicit certificate change, then `./kelios start` to apply it. The prior certificate version remains in private state. Clients may need to trust the replacement. No routine lifecycle command regenerates authentication keys or replaces TLS material.

## Failure and removal

`./kelios doctor` diagnoses prerequisites and saved deployment identity. A failed image build retains private output at `<state-dir>/diagnostics/build-output`; inspect it locally. A missing managed PostgreSQL volume on an initialized instance is a stop condition: the CLI refuses to create an empty replacement. If a prepared install's administrator may already have been created immediately before an interruption, inspect the database/account state before retrying recovery; the CLI does not force a second administrator.

`./kelios uninstall` removes runtime containers and network only after confirmation. It preserves configuration, secrets, and the managed volume by default. Permanent managed-volume deletion is a separate, exact typed confirmation; it is irreversible. External PostgreSQL is never dropped. Use the [authentication procedure](authentication.md) for protected `recover-admin` eligibility. Do not rerun `install` as a repair or restart action.
