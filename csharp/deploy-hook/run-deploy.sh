#!/bin/sh
# Pulls the newest eunomia image and recreates just that one service. Driven by deploy.sh.
#
# Fired by hooks.json ONLY on a GitHub `release` event with action == "released", i.e. an actual
# (non-pre) release. GitHub emits that event the instant you click publish - BEFORE CI has built and
# pushed the new image - so this script does not deploy blindly. It polls GHCR until a newer image
# than the running one is available (or a timeout elapses) and only then recreates the service.
set -eu

SERVICE="${DEPLOY_SERVICE:-eunomia}"
COMPOSE_DIR="${COMPOSE_DIR:?COMPOSE_DIR is not set}"

# How long to wait for CI to publish the new image, and how often to re-check. The build compiles
# the .NET server for linux/amd64 + linux/arm64 (arm64 under QEMU is slow), so give it plenty.
WAIT_TIMEOUT="${DEPLOY_WAIT_TIMEOUT:-2400}"   # seconds (default 40 min)
WAIT_INTERVAL="${DEPLOY_WAIT_INTERVAL:-20}"   # seconds between GHCR checks

stamp() { date -u '+%F %T'; }

# Run every compose command exactly as a hand-run `docker compose up` from csharp/ would:
#  - cd into COMPOSE_DIR so .env is loaded and relative bind mounts (./dpkeys) resolve. The
#    deploy-hook container mounts COMPOSE_DIR at the identical host path, so the host daemon sees
#    the same paths.
#  - list the host-local override explicitly. Passing compose files disables compose's automatic
#    override discovery, so WITHOUT this the recreate would drop the override (edge network, no
#    public port, forwarded headers, admin identifiers) and break the live deployment.
cd "$COMPOSE_DIR"
if [ -f "$COMPOSE_DIR/docker-compose.override.yml" ]; then
  export COMPOSE_FILE="$COMPOSE_DIR/docker-compose.yml:$COMPOSE_DIR/docker-compose.override.yml"
else
  export COMPOSE_FILE="$COMPOSE_DIR/docker-compose.yml"
fi

# If you have the bw (Bitwarden) CLI installed and reachable here with an unlocked vault holding an
# env-vars note (a note whose body is `export VAR=value` lines), the app's secrets are taken from
# there and passed on to the recreated container via the compose `environment:` list. If bw or jq
# isn't available, or no such note can be read, this is skipped entirely and the stack uses .env.
load_env_note() {
  note_item="${ENV_NOTE_ITEM:-zshenv}"
  command -v bw >/dev/null 2>&1 || return 0
  command -v jq >/dev/null 2>&1 || return 0
  notes="$(bw get item "$note_item" 2>/dev/null | jq -r '.notes // empty' 2>/dev/null || true)"
  [ -n "$notes" ] || return 0
  eval "$notes"   # trusted `export VAR=value` lines; the `up -d` child inherits them
  echo "[deploy-hook] $(stamp) loaded app secrets from note '$note_item'."
  unset notes
}

# The local image id the running SERVICE container is on (empty if it isn't running yet).
running_image_id() {
  cid="$(docker compose ps -q "$SERVICE" 2>/dev/null || true)"
  [ -n "$cid" ] || return 0
  docker inspect -f '{{.Image}}' "$cid" 2>/dev/null || true
}

# Serialize: keep back-to-back releases (or a redelivery) from racing the poll+recreate below.
# Held for the whole wait window, so a second trigger while one deploy is polling is skipped.
exec 9>/tmp/deploy-hook.lock
if ! flock -n 9; then
  echo "[deploy-hook] $(stamp) a deploy is already running; skipping this trigger."
  exit 0
fi

# The GHCR package may be private and the host's docker login can live in a keychain (unusable
# inside this Linux container), so authenticate with our own read:packages token when one is set.
# Skipped automatically if the package is made public (GHCR_TOKEN left empty).
if [ -n "${GHCR_TOKEN:-}" ]; then
  echo "[deploy-hook] $(stamp) logging in to ghcr.io as ${GHCR_USER:-zannagh}..."
  echo "$GHCR_TOKEN" | docker login ghcr.io -u "${GHCR_USER:-zannagh}" --password-stdin
fi

IMAGE_REF="$(docker compose config --images "$SERVICE" 2>/dev/null | head -n1)"
BEFORE="$(running_image_id)"
echo "[deploy-hook] $(stamp) waiting for a new ${SERVICE} image (${IMAGE_REF:-unknown}); running id: ${BEFORE:-none}."

deadline=$(( $(date +%s) + WAIT_TIMEOUT ))
while :; do
  # Pull the current :latest. On a fresh release this is the OLD image until CI finishes pushing.
  docker compose pull "$SERVICE" >/dev/null 2>&1 || true
  after="$(docker image inspect -f '{{.Id}}' "$IMAGE_REF" 2>/dev/null || true)"

  # New image is ready once the pulled :latest differs from what's running (or nothing was running).
  if [ -n "$after" ] && { [ -z "$BEFORE" ] || [ "$after" != "$BEFORE" ]; }; then
    echo "[deploy-hook] $(stamp) new image available: ${after}."
    break
  fi

  if [ "$(date +%s)" -ge "$deadline" ]; then
    echo "[deploy-hook] $(stamp) timed out after ${WAIT_TIMEOUT}s waiting for a new image; leaving the current deployment untouched."
    exit 0
  fi
  sleep "$WAIT_INTERVAL"
done

echo "[deploy-hook] $(stamp) recreating ${SERVICE}..."
load_env_note   # opportunistic: only does anything if bw + jq + a readable note are present
docker compose up -d "$SERVICE"

# Drop the now-dangling old image so the disk doesn't fill up over many deploys.
docker image prune -f >/dev/null 2>&1 || true

echo "[deploy-hook] $(stamp) done."
