#!/usr/bin/env bash
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "Run this installer with sudo: sudo bash beeper-oracle-install.sh" >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
if ! apt-get install -y docker.io docker-compose-v2 curl openssl; then
  apt-get install -y docker.io docker-compose curl openssl
fi
systemctl enable --now docker

install_dir=/opt/beeper
mkdir -p "$install_dir/data"
chmod 700 "$install_dir/data"

public_ip="$(curl -4 --fail --silent --show-error https://api.ipify.org)"
case "$public_ip" in
  *[!0-9.]*|'') echo "Could not determine this VM's public IPv4 address." >&2; exit 1 ;;
esac

beeper_host="${public_ip}.sslip.io"
setup_user=artist
setup_password="$(openssl rand -hex 16)"

cat >"$install_dir/.env" <<EOF
BEEPER_HOST=$beeper_host
SETUP_USER=$setup_user
SETUP_PASSWORD=$setup_password
EOF
chmod 600 "$install_dir/.env"

cat >"$install_dir/Caddyfile" <<'EOF'
{$BEEPER_HOST} {
  encode gzip
  reverse_proxy beeper:8000
}
EOF

cat >"$install_dir/docker-compose.yml" <<'EOF'
services:
  beeper:
    image: ghcr.io/alenoa88-alt/a-search-relay/beeper-cloud:latest
    restart: unless-stopped
    environment:
      PORT: "8000"
      HOME: /var/lib/beeper
      BEEPER_CLI_CONFIG_DIR: /var/lib/beeper/cli
      BEEPER_CLI_BINARY_CACHE_DIR: /opt/beeper-cli-cache
      BEEPER_SERVER_BIN: /usr/local/bin/beeper-server-hostless
      SETUP_USER: ${SETUP_USER}
      SETUP_PASSWORD: ${SETUP_PASSWORD}
      NODE_OPTIONS: --max-old-space-size=384
    volumes:
      - ./data:/var/lib/beeper
    expose:
      - "8000"

  caddy:
    image: caddy:2.10-alpine
    restart: unless-stopped
    depends_on:
      - beeper
    environment:
      BEEPER_HOST: ${BEEPER_HOST}
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy_data:/data
      - caddy_config:/config

volumes:
  caddy_data:
  caddy_config:
EOF

cd "$install_dir"
if docker compose version >/dev/null 2>&1; then
  docker compose pull
  docker compose up -d
else
  docker-compose pull
  docker-compose up -d
fi

if command -v ufw >/dev/null 2>&1 && ufw status | grep -q '^Status: active'; then
  ufw allow 22/tcp
  ufw allow 80/tcp
  ufw allow 443/tcp
fi

echo
echo "Oracle Beeper server started with persistent storage."
echo "Setup URL: https://$beeper_host"
echo "Setup username: $setup_user"
echo "Setup password: $setup_password"
echo
echo "After setup is complete, use this ChatGPT MCP URL:"
echo "https://$beeper_host/v0/mcp"
echo
echo "Credentials are also saved in $install_dir/.env (root-only)."
