# Runtime branding

For the supported operator steps to select or reset the theme and add or clear an organization logo, follow [Configuration and TLS](deployment.md#configuration-and-tls). Save through `./kelios config`, apply with `./kelios start`, then reload the browser; no image rebuild is needed. A saved theme survives restarts and upgrades until changed explicitly.

The frontend serves root-path deployments. It fetches `/runtime/config.json` before rendering, without cache and with a three-second timeout. Branding is public client-side data: never include credentials or secrets.

Set the frontend container environment variable `KELIOS_CONFIG_FILE` to an absolute path of a read-only mounted JSON file. Its default is the config shipped at `/usr/share/nginx/html/runtime/config.json`. At startup, the unprivileged entrypoint links the source to `/tmp/kelios/config.json`, which nginx serves at the stable URL. An unavailable source uses the orange/charcoal KELIOS defaults. Changes to a readable mounted file take effect on the next page load; switching source paths requires recreating the container, without rebuilding its image. Config and logo responses use `Cache-Control: no-store`.

```json
{
  "schemaVersion": 1,
  "organizationLogoUrl": "/runtime/logos/organization.svg",
  "theme": {
    "background": "#f7f7f5",
    "foreground": "#18181b",
    "primary": "#cd3a06",
    "primaryForeground": "#ffffff",
    "accent": "#fff0eb",
    "accentForeground": "#8f2904"
  }
}
```

`schemaVersion` must be the number 1. `organizationLogoUrl`, legacy `logoUrl` and `theme` are optional. Only the six documented public theme tokens are accepted; the frontend derives its separate Delete-action color from the validated primary. Colors must be six-digit hexadecimal values including `#`; arbitrary CSS is rejected. Unknown fields are ignored. Each invalid field falls back independently. Missing/malformed JSON, unsupported schema, a failed/slow fetch, or config larger than 32 KiB uses all defaults. The mounted file should remain small; the size check runs after reading the response. Existing saved blue/green palettes remain unchanged until their operator selects a new theme.

The frontend derives its Delete-button action color from the selected primary: dark neutral under saturated orange/red-family brands, ordinary red under the supplied blue/green themes and other non-conflicting palettes. This does not add a configuration field. Error text, validation, confirmations, Asset status colors and User role/status badge colors keep their independent semantic or categorical tones when the operator changes brands.

Both logo URLs resolve against the origin root and must be same-origin HTTP(S), without embedded credentials, and at most 2048 characters. `javascript:`, `data:`, and external origins are rejected. `organizationLogoUrl` displays a compact organization mark beside the live-text KELIOS wordmark; the O inherits the runtime primary color. Without that key, the legacy `logoUrl` keeps its existing full-image rendering. Without either logo, or when the chosen image fails, a neutral mark appears beside live-text KELIOS. When both keys are valid, the organization mark takes precedence. SVG files supplied by operators are loaded as images, never injected as markup. No district-specific assets ship in the repository/image; the blue/green fixtures remain generic legacy samples.

Mount logos under `/usr/share/nginx/html/runtime/logos`. The guided CLI accepts an optional SVG, PNG, JPG or WebP organization logo of at most 2 MiB at install time or through `./kelios config`; an empty organization-logo path in Config clears the selection without deleting the prior file. The image is constrained to 36px height and 48px width with preserved aspect ratio. Config can live outside the static document root; only the selected JSON is exposed. Ensure mounted files are readable by nginx's non-root UID 101. A missing `/runtime/` or `/assets/` URL returns 404 rather than the SPA document. Subpath deployment is not currently supported.

To demonstrate the same image with two brands, start Compose using the blue sample, note the frontend image ID, and recreate only the frontend with `KELIOS_BRAND=green` and `--no-build`. Reload the page and verify colors/logo changed while image ID stayed identical. See [README.md](../../README.md) and the repository-relative `scripts/smoke-branding.sh` smoke test script.
