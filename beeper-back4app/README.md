# Beeper on Back4App Containers

Deploy the `codex/beeper-hostless` branch with `/beeper-back4app` as the root directory.

Environment variables:

- `PORT=8000`
- `SETUP_USER=artist`
- `SETUP_PASSWORD=<new strong password>`
- `HOME=/tmp/beeper-data`
- `BEEPER_CLI_CONFIG_DIR=/tmp/beeper-data/cli`
- `BEEPER_CLI_BINARY_CACHE_DIR=/opt/beeper-cli-cache`
- `BEEPER_SERVER_BIN=/usr/local/bin/beeper-server-hostless`
- `NODE_OPTIONS=--max-old-space-size=192`

The Dockerfile only pulls the prebuilt public GHCR image, so Back4App does not rebuild Beeper.
