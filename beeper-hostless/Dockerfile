FROM node:22-bookworm-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl procps tini \
    && rm -rf /var/lib/apt/lists/* \
    && ttyd_arch="$(dpkg --print-architecture)" \
    && case "$ttyd_arch" in \
         amd64) ttyd_asset=ttyd.x86_64; ttyd_sha=8a217c968aba172e0dbf3f34447218dc015bc4d5e59bf51db2f2cd12b7be4f55 ;; \
         arm64) ttyd_asset=ttyd.aarch64; ttyd_sha=b38acadd89d1d396a0f5649aa52c539edbad07f4bc7348b27b4f4b7219dd4165 ;; \
         *) echo "Unsupported architecture: $ttyd_arch" >&2; exit 1 ;; \
       esac \
    && curl --fail --location --output /usr/local/bin/ttyd "https://github.com/tsl0922/ttyd/releases/download/1.7.7/$ttyd_asset" \
    && echo "$ttyd_sha  /usr/local/bin/ttyd" | sha256sum --check - \
    && chmod 0755 /usr/local/bin/ttyd \
    && npm install --global beeper-cli@0.6.2

ENV HOME=/data \
    BEEPER_CLI_CONFIG_DIR=/data/cli \
    BEEPER_CLI_BINARY_CACHE_DIR=/opt/beeper-cli-cache \
    PORT=8080

WORKDIR /app
RUN npm init --yes >/dev/null \
    && npm install --omit=dev http-proxy@1.18.1 \
    && mkdir -p /data /opt/beeper-cli-cache \
    && beeper version

COPY gateway.js /app/gateway.js
COPY entrypoint.sh /usr/local/bin/beeper-hostless-entrypoint
COPY setup-flow.sh /usr/local/bin/beeper-setup-flow
RUN chmod 0755 /usr/local/bin/beeper-hostless-entrypoint /usr/local/bin/beeper-setup-flow

EXPOSE 8080

ENTRYPOINT ["/usr/bin/tini", "--", "/usr/local/bin/beeper-hostless-entrypoint"]
