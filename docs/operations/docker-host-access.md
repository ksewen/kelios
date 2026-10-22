# Docker host access (Deployment Workflow v2, Gate 3)

Run `./kelios` as the ordinary account that owns the instance state. The CLI requires an installed Docker Engine, a working `docker compose` plugin (v2 or later), and access to a **local Unix-socket** daemon. It does not install Docker, start the daemon, change the active Docker context, or run normal deployment commands through `sudo`. The current containerized Nginx profile uses unprivileged host ports; no host Nginx or systemd setup is required. Remote Docker contexts and rootless Docker/Podman are outside this version's tested contract because the deployment binds local instance files into containers.

Docker socket access grants high-privilege control of the host. [Docker's Linux post-installation guidance](https://docs.docker.com/engine/install/linux-postinstall/) calls Docker group access root-level privilege and documents the targeted `usermod -aG` step plus the need to log in again. Treat group membership accordingly. Use your host security policy to decide which operator account may receive it; do not add users merely to silence a permission error.

## Diagnose before installation

```sh
./kelios doctor
```

`doctor` checks OS/architecture, Docker and Compose availability, daemon access, the local context, OpenSSL, and requested ports before installation. It rejects ambiguous `DOCKER_HOST`/`DOCKER_CONTEXT` settings; [Docker's CLI reference](https://docs.docker.com/reference/cli/docker/) documents that `DOCKER_CONTEXT` otherwise overrides `DOCKER_HOST`. On an existing instance it also checks saved files, Compose rendering, and resource ownership labels. Linux port checks require `ss` from iproute2; macOS development checks use `lsof`. Port detection is a preflight snapshot, so another process can claim a port later. `install` repeats the port check before writing instance state and renders Compose before building images.

| Problem reported | Corrective action |
|---|---|
| Docker executable or Compose plugin absent | Install Docker Engine and a supported Compose plugin using your distribution's documented procedure. |
| Daemon unavailable | Start the installed daemon, then rerun `doctor`. Inspect the selected Docker context if it remains unavailable. |
| Socket access denied | Have an administrator approve Docker access for the operator account. Review the privilege effect below before using host preparation. |
| Nonlocal Docker context | Select a local Unix-socket context. Bind-mounted KELIOS state must be on the Docker host. |
| Host port already listening | Stop the conflicting service or select different HTTP/HTTPS ports in install configuration. |
| Network, volume, or container ownership mismatch | Inspect the named Docker resource and its Compose labels. Do not delete it or force KELIOS to adopt it. |

The CLI validates an existing managed volume's Compose project/volume labels, the project network's project/network labels, and project container service labels. A fresh install refuses a colliding project network before building. An initialized instance still refuses to create an empty replacement if its managed PostgreSQL volume is missing.

## Optional Linux Docker-access preparation

On a conventional Linux Engine installation where the selected context is exactly `unix:///var/run/docker.sock`, the socket belongs to the `docker` group, and its group has write permission, an approved sudo-capable operator may run:

```sh
./kelios host prepare docker-access
```

The command first checks whether Docker access already works and makes **no change** when it does. It also refuses to run when the daemon is stopped, the socket policy differs, effective or account group membership already includes `docker`, or the session is noninteractive. If a group change is actually needed, it displays the exact effect and requires typing `GRANT DOCKER ACCESS`. Only then does it invoke `sudo usermod -aG docker -- <operator-account>`. The operator may need to log out and back in before the new group applies; rerun `doctor` afterward. The CLI never runs the application as root, modifies socket permissions, or installs host packages.

No Linux AMD64 server verification has yet been recorded for Gate 3. Docker Desktop on a macOS ARM64 development host is a different environment and does not establish Linux production support. Keep this limitation visible until a disposable Linux AMD64 host has exercised missing/stopped/inaccessible daemon, Compose absence, busy ports, resource collisions and the optional host-access path.
