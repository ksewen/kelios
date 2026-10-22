# Private-network certificates (Deployment Workflow v2, Gate 2)

The CLI can create a self-signed certificate for a local or explicitly trusted private-network hostname before installation. It stores the hostname, certificate and key in the selected instance state directory. The default validity is **730 days**; there is no automatic renewal or certificate authority. Browsers and other clients must explicitly trust the certificate. For a publicly trusted service, supply an existing certificate and unencrypted private key during install instead. KELIOS does not manage ACME or public trust.

Run the CLI from a checkout containing `kelios` and the deployment files. Its default instance state is `${XDG_DATA_HOME:-$HOME/.local/share}/kelios/default`; use the same `--state-dir` on every command if you choose another absolute location.

## Provision before installation

```sh
./kelios certificate init
./kelios install
```

`certificate init` asks for a DNS hostname or IPv4 address and includes it in the certificate SAN. `localhost` also includes `127.0.0.1`. It does not require Docker. `install` discovers the provisioned certificate and hostname, does not ask for TLS paths, and never regenerates that certificate on a rerun. If state already exists, `certificate init` refuses to overwrite it. Save and trust the certificate at the state directory's `secrets/tls/<version>/certificate.pem` on each intended client; never copy the adjacent private key to clients. Inspect the active version in `certificate.conf`. Avoid copying the whole state directory to a client because it contains application secrets.

For unattended provisioning, put `BROWSER_HOST=your.internal.name` in a private configuration file and run `./kelios --config <config-file> --non-interactive certificate init`. The same hostname must be used for the installation's HTTPS origin. The configuration file does not contain the generated key.

If provisioning was skipped, `install` asks for certificate and private-key PEM paths. For unattended install, provide `TLS_CERTIFICATE` and `TLS_PRIVATE_KEY` paths in the validated input config. The CLI checks expiry, key match and browser-host coverage before copying them into private instance state. It does not generate a certificate in this path.

## Explicit replacement

```sh
./kelios certificate replace
./kelios start
```

Replacement requires typing `REPLACE <persisted-hostname>` exactly. It generates a new 730-day self-signed certificate for that hostname, validates it, and switches the active certificate pointer atomically. The old version remains in private state for recovery. `start` applies the new TLS mount to the running frontend; replacement itself does not restart services. Clients that trusted the old self-signed certificate will need to trust the new one. Check the certificate expiry and plan replacement before it expires.

To replace with an operator-managed PEM pair, set `TLS_CERTIFICATE=<absolute-path>` and `TLS_PRIVATE_KEY=<absolute-path>` in an input config and run `./kelios --config <config-file> certificate replace`. In unattended operation, add `--non-interactive --confirm-replace`. A missing, expired, mismatched or wrong-host pair is rejected before the active pointer changes. `BROWSER_HOST`, if supplied during replacement, must match the persisted identity. Certificate replacement does not change the application's hostname, JWT issuer, database or authentication keys.

An interrupted initial provisioning attempt may leave an incomplete private state directory. Inspect it before retrying; the CLI will not delete or overwrite a state directory automatically. If a replacement fails, the previous active version remains selected. Check `./kelios status` and `./kelios doctor` after correction. Normal install, start, stop and administrator recovery do not rotate certificates.
