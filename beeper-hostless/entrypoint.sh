#!/bin/sh
set -eu

state_dir="${HOME:-/data}"
ready_file="$state_dir/.beeper-hostless-ready"

mkdir -p "$state_dir" "$BEEPER_CLI_CONFIG_DIR"

if [ ! -f "$ready_file" ] && [ -z "${SETUP_PASSWORD:-}" ]; then
  echo "SETUP_PASSWORD is required until Beeper setup is complete." >&2
  exit 1
fi

node /app/gateway.js &
gateway_pid=$!
trap 'kill "$gateway_pid" 2>/dev/null || true' EXIT INT TERM

if [ ! -f "$ready_file" ]; then
  echo "Starting the restricted Beeper setup service."
  ttyd --interface 127.0.0.1 --port 7681 --writable /usr/local/bin/beeper-setup-flow &
  setup_pid=$!

  while [ ! -f "$ready_file" ]; do
    kill -0 "$gateway_pid" 2>/dev/null || wait "$gateway_pid"
    kill -0 "$setup_pid" 2>/dev/null || wait "$setup_pid"
    sleep 2
  done

  kill "$setup_pid" 2>/dev/null || true
  wait "$setup_pid" 2>/dev/null || true
fi

echo "Starting Beeper Server."
beeper targets start || true

attempt=0
until curl --silent --fail --max-time 2 http://127.0.0.1:23373/v1/info >/dev/null; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 60 ]; then
    echo "Beeper Server did not become ready. Check the Hostless app logs." >&2
    exit 1
  fi
  sleep 2
done

echo "OAuth-validated Beeper MCP is online."
wait "$gateway_pid"
