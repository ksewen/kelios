# Phase 0 verification evidence

Status: Phase 0 acceptance passed on 2026-10-10. No later phase implemented. All changes remain uncommitted; no remote is configured.

## Environment and verified versions

- Host: macOS arm64, Java 25.0.2, Node 24.15.0, Docker Engine 29.8.2 with Compose/BuildKit.
- Backend: Spring Boot 4.1.1 parent artifact returned HTTP 200 from Maven Central; resolved dependencies, compiled tests, and deployed application all succeeded. Runtime startup reports Spring Boot 4.1.1 and Java 25.0.4.1.
- Apache Maven Wrapper 3.3.4 selects Maven 3.9.16. `./mvnw --version` returned `Apache Maven 3.9.16`; SHA-256 validation is enabled. Java/Maven compatibility is also enforced by the POM.
- Frontend: React 19.3.0, Vite 8.3.4, TypeScript 7.0.2, Tailwind 4.3.3, Vitest 5.0.3, Playwright 1.64.0. Exact dependency versions and transitive dependencies are in `frontend/package-lock.json`.
- PostgreSQL startup reports 17.11. Only `flyway_schema_history` exists in the public schema.

## Acceptance results

| Requirement | Actual check and outcome |
| --- | --- |
| Architecture reviewed before code | Original specifications read in order; technical selections recorded in ADR 0001 before scaffolding |
| Maven 3 / Wrapper / no Gradle | Official `mvnw`, `mvnw.cmd`, wrapper properties and distribution checksum supplied; Maven 3.9.16 verified; no Gradle files |
| Backend verification | `MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw -B verify`: BUILD SUCCESS; 2 tests, 0 failures/errors/skips |
| Frontend verification | Node 24 `npm test`: 11 tests passed; `npm run build`: TypeScript check and Vite production build passed |
| Independent multi-stage images | Separate build contexts/Dockerfiles produced distinct backend/frontend runtime image IDs; backend builder executed `./mvnw -B verify`, frontend builder ran tests/build |
| BuildKit caching | Docker smoke build logs show cache mounts and cached build steps on the subsequent run |
| Runtime separation | Smoke assertions: both images non-root; backend has neither `mvn` nor `javac`; frontend has neither Node nor npm; no builder workspace in frontend runtime; backend JAR contains bootstrap, technical status, config, and migration only |
| Deployment | Compose validation and `up -d --wait` passed for PostgreSQL, backend, frontend; all healthy |
| Health/migration | Backend readiness/liveness returned UP; frontend health returned ok; Flyway version 1 `platform baseline` succeeded; no business tables |
| API routing | Browser connection check and HTTP `/api/v1/status` passed through nginx; missing business API, runtime and asset paths returned 404 |
| Runtime config injection | Nondefault `KELIOS_CONFIG_FILE=/etc/kelios/branding/config.json` source served at `/runtime/config.json`; response had `Cache-Control: no-store` |
| Same-image branding | Blue then green mounts used the identical frontend image ID, without rebuilding; distinct SVGs fetched and rendered; computed primary/background colors matched both palettes |
| Validation/fallback | 11 unit tests cover schema, field validation, unsafe logo schemes/origins, and CSS injection. Six browser tests passed for each brand, including absent/malformed/unsupported/timeout config and broken logo |
| Scope/secrets | No authentication, business entities, authorization, domain workflows, Redis, or other unapproved infrastructure. Local database secret is ignored by Git, file-mounted, and outside both build contexts |

## Image identity

```text
kelios-backend:phase0
sha256:330b928b20a847a58b7159030544a963f74a075d907e4dca5d40c675aca41de4
user=kelios size=250266581 bytes

kelios-frontend:phase0 (identical for both brands)
sha256:5142572498844abf9f78e880995b4138b7f342ec6d58d593f81f98dc9bb8fb76
user=101 size=54079552 bytes
```

Both Dockerfiles pin their builder/runtime base images by digest. Local image IDs above are evidence for this build, not registry publication digests; no image was pushed.

## Reproduction and retained artifacts

Run the build commands in `README.md`, then `./scripts/smoke-phase0.sh` and, with Node 24 on PATH and Chromium installed inside `.cache`, `./scripts/smoke-branding.sh`.

- [Backend verification output](backend-verify.txt): actual selected lines from the local Maven log.
- [Backend startup log](backend-startup.txt): Spring Boot/JDK, database, and migration execution.
- [Container smoke log](container-smoke.txt): cached build, health/migration/API checks, and identical image IDs for both mounts.
- [Browser smoke log](browser-smoke.txt): 6 blue + 6 green browser tests passed.
- [Blue screenshot](blue.png) and [green screenshot](green.png): inspected visually; distinct logos/palettes and successful backend connection.

The stack is left running at `http://127.0.0.1:8080` with the green sample. Stop with `docker compose -f deploy/compose.yaml stop`; the PostgreSQL volume is retained.

## Resolved failures and verification limits

Sandbox DNS initially blocked dependency/browser downloads, Docker daemon access was denied, and macOS blocked browser process startup. Command-scoped approvals were used; no sudo, unrestricted filesystem permission request, or workspace sandbox setting change was used. Browser tests explicitly enable Chromium sandboxing; browser profiles/downloads and build caches are inside KELIOS.

The first backend container build failed checksum validation because the only-script Wrapper substituted a tar archive when `unzip` was missing. Adding `unzip` to the builder preserved ZIP checksum validation and the build passed. Initial frontend type checks exposed a removed TypeScript `baseUrl` option and a readonly mapped type; both were fixed before successful builds. One smoke run was interrupted by editing its script during execution; rerunning the final script passed.

Verified on arm64 only. Windows Wrapper execution, other CPU architectures, production ingress/TLS, registry publication, cross-origin and subpath deployment, and future authentication/IServ integration have not been tested. They are outside the Phase 0 deployment scope; no success is claimed for them. No commits were created despite the specification's version-control wording; Wrapper files are ready for a future explicitly authorized commit.
