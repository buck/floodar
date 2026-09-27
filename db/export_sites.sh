#!/usr/bin/env bash
# Regenerate app/src/main/assets/sites.json from floodar-db (see db/sites.sql).
set -euo pipefail
cd "$(dirname "$0")/.."
docker exec -i floodar-db psql -q -At -U floodar -d floodar -v ON_ERROR_STOP=1 \
  -c "SET client_min_messages = warning" -f - < db/sites.sql \
  > app/src/main/assets/sites.json
jq -r '.sites[] | "\(.id): ground \(.ground_ft_navd88) ft, " +
  ([.scenarios[] | "\(.id) \(.depth_ft)"] | join(", "))' app/src/main/assets/sites.json
