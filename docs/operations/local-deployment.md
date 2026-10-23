# Legacy guided local deployment

This path previously described the source-local Deployment CLI v1. Its manual `.cache` preparation, `.kelios/` layout and seven-command procedure do not describe the current CLI. Do not use its historical commands with a v2 installation.

For current installation, configuration, updating and routine operation, use the [Deployment Workflow v2 operator guide](deployment.md). See the [certificate procedure](certificates.md), [Docker host-access procedure](docker-host-access.md) and [recovery procedure](deployment-recovery.md) as needed. The [accepted v1 closeout](../history/deployment-cli.md) preserves dated results and limitations.

## Change the theme or organization logo

The current [configuration procedure](deployment.md#configuration-and-tls) covers theme and organization-logo changes through `./kelios config` and `./kelios start` without rebuilding the frontend image.
