#!/usr/bin/env bash
# Triggers the same admin "execute actions" e-mail in both realms and prints the
# expiration sentence of each message, straight out of Mailpit.
#
#   LIFESPAN=300   -> "5 minutos"   (default)
#   LIFESPAN=43200 -> "12 horas"
set -euo pipefail

KC="http://localhost:${KC_PORT:-8080}"
MP="http://localhost:${MAILPIT_PORT:-8025}"
LIFESPAN="${LIFESPAN:-300}"

token=$(curl -sf -d client_id=admin-cli -d username=admin -d password=admin -d grant_type=password \
  "$KC/realms/master/protocol/openid-connect/token" \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["access_token"])')

curl -sf -X DELETE "$MP/api/v1/messages" > /dev/null

for realm in bug-demo fix-demo; do
  uid=$(curl -sf -H "Authorization: Bearer $token" \
    "$KC/admin/realms/$realm/users?username=maria&exact=true" \
    | python3 -c 'import sys,json; print(json.load(sys.stdin)[0]["id"])')
  curl -sf -X PUT -H "Authorization: Bearer $token" -H "Content-Type: application/json" \
    -d '["UPDATE_PASSWORD"]' \
    "$KC/admin/realms/$realm/users/$uid/execute-actions-email?lifespan=$LIFESPAN"
done

sleep 2

python3 - "$MP" <<'EOF'
import json, sys, urllib.request
mp = sys.argv[1]
msgs = json.load(urllib.request.urlopen(f"{mp}/api/v1/messages"))["messages"]
for m in sorted(msgs, key=lambda m: m["To"][0]["Address"]):
    body = json.load(urllib.request.urlopen(f"{mp}/api/v1/message/{m['ID']}"))["Text"]
    line = next((l.strip() for l in body.splitlines() if "expirará" in l or "caducará" in l),
                "(no expiration sentence found)")
    print(f"{m['To'][0]['Address']:24s} -> {line}")
EOF
