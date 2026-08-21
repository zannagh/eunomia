# deploy-hook

A tiny [`webhook`](https://github.com/adnanh/webhook) receiver that auto-deploys the Eunomia server
when a new image is published to GHCR. It verifies GitHub's HMAC signature, then runs
`docker compose pull eunomia && docker compose up -d eunomia` against the host docker daemon.

The published image is `ghcr.io/zannagh/eunomia/eunomia-server` (see `.github/workflows/docker-publish.yml`),
tagged `:latest` on a GitHub release and `:prerelease` on a push to `main`.

## Flow

```
GitHub release/package published
  └─ POST  https://<your-domain>/ci/released   (X-Hub-Signature-256, JSON body)
       └─ reverse proxy routes /ci/released → <host>:9000
            └─ deploy-hook container (this service, serving /ci/{id} via -urlprefix ci)
                 ├─ verify HMAC against $WEBHOOK_SECRET   → reject if it doesn't match
                 ├─ require payload.action == "published" → ignore pings / edits
                 └─ docker compose pull eunomia && up -d eunomia   (host daemon via socket)
```

The receiver serves hooks at `/ci/<id>` (`-urlprefix ci`); this hook's id is `released`, so the
path is **`/ci/released`** — matching the configured GitHub Payload URL.

## Setup (things only you can do)

1. **Secret.** `openssl rand -hex 32`, put it in `csharp/.env` as `WEBHOOK_SECRET=…`, and paste the
   **same** value into the webhook's *Secret* field on GitHub (repo → Settings → Webhooks). Set the
   Payload URL to `https://<your-domain>/ci/released`, content type `application/json`, and let it
   send **Releases** events (optionally Packages too).
2. **GHCR token (only if the package is private).** Create a token with `read:packages`
   (github.com/settings/tokens) and set `GHCR_TOKEN=` in `csharp/.env`. *(Or make the package
   public and leave it empty.)*
3. **Host path.** Set `COMPOSE_DIR` in `csharp/.env` to the absolute host path of the stack (the
   directory that contains `docker-compose.yml`).
4. **Route the path.** Send the webhook path to this receiver (`:9000`) while everything else goes
   to the app. Example (Caddy):

   ```caddy
   <your-domain> {
       handle /ci/released {
           reverse_proxy <host>:9000
       }
       handle {
           reverse_proxy <host>:8080
       }
   }
   ```

Then: `docker compose up -d --build deploy-hook`

## App secrets on redeploy

The recreated `eunomia` container reads its secrets from the `environment:` passthrough list in
`docker-compose.yml`, resolved against the environment of whoever runs `docker compose` — here,
this container. Put them in `csharp/.env` (gitignored; compose reads it automatically).

Optionally, if the `bw` (Bitwarden) CLI and `jq` are available here with an unlocked vault holding
a note of `export VAR=value` lines, `run-deploy.sh` reads it and passes those on instead. If any of
that is missing it's silently skipped and the stack just uses `csharp/.env`. Note name defaults to
`zshenv`, override with `ENV_NOTE_ITEM`.

## Verify

- `docker logs -f eunomia-deploy-hook-1` — receiver + deploy output.
- GitHub → the webhook → **Recent Deliveries**: a green ✓ with body `deploy queued`. Use
  **Redeliver** to test a past event without cutting a new release.

## Security

This container mounts `/var/run/docker.sock`, i.e. it is effectively root on the host. Its only
trigger is a request whose HMAC matches `WEBHOOK_SECRET`, so keep that secret strong and never
expose `/ci/released` without it. Terminate TLS at your reverse proxy.

## Notes

- If the GHCR package is private, the receiver runs `docker login ghcr.io` with `GHCR_TOKEN` before
  pulling.
- A release can fire both a `release` and a `package` delivery; `run-deploy.sh` takes a `flock` so
  the two don't race. Pulling `:latest` when nothing changed is a no-op and won't recreate the
  container.
