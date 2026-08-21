#!/bin/sh
# Pulls the newest eunomia image and recreates just that one service. Driven by deploy.sh.
set -eu

SERVICE="${DEPLOY_SERVICE:-eunomia}"
COMPOSE_FILE="${COMPOSE_DIR:?COMPOSE_DIR is not set}/docker-compose.yml"

stamp() { date -u '+%F %T'; }

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

# A single GitHub release fires both a 'release' and a 'package' delivery, so two runs can land
# nearly at once. Serialize them: the second one bails instead of racing the first.
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

echo "[deploy-hook] $(stamp) pulling ${SERVICE} image..."
docker compose -f "$COMPOSE_FILE" pull "$SERVICE"

echo "[deploy-hook] $(stamp) recreating ${SERVICE}..."
load_env_note   # opportunistic: only does anything if bw + jq + a readable note are present
docker compose -f "$COMPOSE_FILE" up -d "$SERVICE"

# Drop the now-dangling old image so the disk doesn't fill up over many deploys.
docker image prune -f >/dev/null 2>&1 || true

echo "[deploy-hook] $(stamp) done."
