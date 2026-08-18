FROM ghcr.io/alenoa88-alt/a-search-relay/beeper-cloud:latest

ENV PORT=8000 \
    HOME=/tmp/beeper-data \
    BEEPER_CLI_CONFIG_DIR=/tmp/beeper-data/cli \
    BEEPER_CLI_BINARY_CACHE_DIR=/opt/beeper-cli-cache \
    BEEPER_SERVER_BIN=/usr/local/bin/beeper-server-hostless \
    NODE_OPTIONS=--max-old-space-size=192

EXPOSE 8000
