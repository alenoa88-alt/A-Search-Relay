# Beeper on ClawCloud Run

## Application

- Source: GitHub repository `alenoa88-alt/A-Search-Relay`
- Branch: `codex/beeper-hostless`
- Build type: Dockerfile
- Context directory: `/beeper-hostless`
- Dockerfile: `/beeper-hostless/Dockerfile`
- Container port: `8000`
- Public protocol: HTTP
- Replicas: `1`
- CPU: `0.2` vCPU
- Memory: `512 MiB`

## Persistent storage

Attach at least 1 GB at `/tmp/beeper-data`. This volume holds the Beeper login, connected networks, OAuth approvals, and local index.

## Environment variables

- `PORT=8000`
- `SETUP_USER=artist`
- `SETUP_PASSWORD=<a new long random password>`
- `HOME=/tmp/beeper-data`
- `BEEPER_CLI_CONFIG_DIR=/tmp/beeper-data/cli`
- `BEEPER_CLI_BINARY_CACHE_DIR=/opt/beeper-cli-cache`
- `BEEPER_SERVER_BIN=/usr/local/bin/beeper-server-hostless`

## First setup

1. Open the public ClawCloud URL and enter the setup username and password.
2. Sign in to Beeper and connect WhatsApp, Instagram, and Facebook Messenger.
3. Type `READY` when prompted.
4. Replace the dead `trycloudflare.com` connector URL in ChatGPT with the ClawCloud URL followed by `/v0/mcp`.
5. Complete ChatGPT OAuth once.

Keep the volume attached and use one replica. The public URL remains the MCP endpoint.
