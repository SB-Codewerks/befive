#!/usr/bin/env bash
# Renders each mockup HTML to a 1440x900 PNG with headless Chrome.
set -euo pipefail
cd "$(dirname "$0")"
python3 build.py
for f in overview routes-list route-edit okta-wizard consumer-detail incident-detail anomaly-rules \
         api-effective-policy access-request-approval composite-builder report-builder promotion-wizard \
         portal-catalog portal-api-detail; do
  google-chrome --headless=new --no-sandbox --disable-gpu --hide-scrollbars \
    --force-device-scale-factor=1 --window-size=1440,900 \
    --screenshot="../${f}.png" "file://$PWD/${f}.html" >/dev/null 2>&1
  echo "rendered ../${f}.png"
done
