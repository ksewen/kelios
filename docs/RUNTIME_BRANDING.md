# Runtime branding

The frontend serves root-path deployments. It fetches `/runtime/config.json` before rendering, without cache and with a three-second timeout. Branding is public client-side data: never include credentials or secrets.

Set the frontend container environment variable `KELIOS_CONFIG_FILE` to an absolute path of a read-only mounted JSON file. Its default is the neutral config shipped at `/usr/share/nginx/html/runtime/config.json`. At startup, the unprivileged entrypoint links the source to `/tmp/kelios/config.json`, which nginx serves at the stable URL. An unavailable source produces neutral defaults. Changes to a readable mounted file take effect on the next page load; switching source paths requires recreating the container, without rebuilding its image. Config and logo responses use `Cache-Control: no-store`.

```json
{
  "schemaVersion": 1,
  "logoUrl": "/runtime/logos/logo.svg",
  "theme": {
    "background": "#f4f7ff",
    "foreground": "#172033",
    "primary": "#2458d3",
    "primaryForeground": "#ffffff",
    "accent": "#e1eaff",
    "accentForeground": "#243b75"
  }
}
```

`schemaVersion` must be the number 1. `logoUrl` and `theme` are optional. Only the six documented tokens are applied, as CSS variables. Colors must be six-digit hexadecimal values including `#`; arbitrary CSS is rejected. Unknown fields are ignored. Each invalid field falls back independently. Missing/malformed JSON, unsupported schema, a failed/slow fetch, or config larger than 32 KiB uses all defaults. The mounted file should remain small; the size check runs after reading the response.

Logo URLs resolve against the origin root and must be same-origin HTTP(S), without embedded credentials, and at most 2048 characters. `javascript:`, `data:`, and external origins are rejected. A missing/broken logo displays the KELIOS text fallback. SVG files supplied by operators are loaded as images, never injected as markup. No district-specific assets ship in the repository/image; the blue/green fixtures are generic samples.

Mount logos under `/usr/share/nginx/html/runtime/logos`. Config can live outside the static document root; only the selected JSON is exposed. Ensure mounted files are readable by nginx's non-root UID 101. A missing `/runtime/` or `/assets/` URL returns 404 rather than the SPA document. Subpath deployment is not currently supported.

To demonstrate the same image with two brands, start Compose using the blue sample, note the frontend image ID, and recreate only the frontend with `KELIOS_BRAND=green` and `--no-build`. Reload the page and verify colors/logo changed while image ID stayed identical. See `README.md` and the smoke test script.
