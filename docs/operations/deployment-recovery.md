# Deployment recovery

Use the same state directory with every command (`--state-dir "$KELIOS_STATE_DIR"` when it is not the default). Run the CLI as the ordinary operator with approved Docker access. Keep the state directory, its authentication keys, the managed PostgreSQL volume or external database, and a usable database backup together. Do not run `install` over a prepared or initialized instance, regenerate missing keys, or create an empty replacement database.

Before an update, take and check a database backup through your existing database procedure. The CLI has no general backup or restore command. Flyway migrations can commit before a later startup failure; switching the application image back is not a database rollback. Determine database and application compatibility before any manual recovery from a backup.

## Identify the failure

Run `./kelios doctor`, `./kelios status`, and `./kelios logs` from an available checkout. Review logs locally before sharing; backend and database output can contain private information. A failed build leaves private diagnostics at `<state-dir>/diagnostics/build-output`. Avoid printing the state manifest, Compose interpolation or secret files into a support ticket.

| Observed state | Safe next action |
|---|---|
| Fresh installation is `prepared` after a build or startup failure | Correct the reported prerequisite, then run `./kelios resume-install` from the **same clean source release**. It reuses saved secrets. If administrator creation may have completed immediately before interruption, inspect account/database state first; the backend will not force a second administrator. |
| Update build failed | The saved release and running runtime were not switched. Fix the build, then rerun `update` from the intended clean checkout. |
| Update was interrupted or failed after the target release was saved | Persistent keys and database remain. Check `status` and `logs`, correct the cause, then run `start` to apply the saved release, or rerun `update` from that same clean checkout. Do not assume partially applied migrations were undone. |
| Initialized managed volume is missing or has the wrong ownership label | Stop. The CLI refuses to create an empty replacement. Investigate Docker context, volume name/labels and the correct backup or host. Restore only through an operator-controlled, verified database recovery procedure. Do not edit the manifest to bypass the guard. |
| Required image is missing | Rebuild the intended clean release with `update` after verifying the database backup and migration compatibility. `start` never pulls or rebuilds images. |
| Runtime was removed by default `uninstall` | Run `start` against the same state and managed volume or external database; saved secrets and configuration are reused. |
| Confirmed managed-volume deletion completed | The manifest is marked `data-deleted` and `start` refuses an empty database. Recovery requires an operator-controlled database restore with the original keys and a separately planned state reconciliation. The CLI has no reset or automatic restore path. |

`stop`, `start`, `restart`, `update`, and frontend publication do not request Docker volume deletion. Default `uninstall` removes only project runtime containers/network after confirmation. Permanent managed-volume removal requires a second yes and the exact `DELETE <project>` phrase; the CLI checks the volume's project/type labels and refuses volumes with consumers. External PostgreSQL is never dropped. Keep unrelated Docker projects and volumes outside the recorded KELIOS project identity.

If a failure is not covered here, preserve the state and volume, record the exact CLI error and release from `status`, and investigate on a disposable copy before changing production data. Do not share credentials, private keys, raw database dumps or unreviewed logs.
