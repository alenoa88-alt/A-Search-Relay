FROM node:20-bookworm-slim AS beeper-builder

RUN apt-get update \
    && apt-get install -y --no-install-recommends binutils ca-certificates curl \
    && rm -rf /var/lib/apt/lists/* \
    && npm install --global beeper-cli@0.6.2

ENV HOME=/tmp/beeper-data \
    BEEPER_CLI_CONFIG_DIR=/tmp/beeper-data/cli \
    BEEPER_CLI_BINARY_CACHE_DIR=/opt/beeper-cli-cache

WORKDIR /app
RUN npm init --yes >/dev/null \
    && npm install --omit=dev http-proxy@1.18.1 \
    && npm cache clean --force >/dev/null 2>&1 \
    && mkdir -p /opt/beeper-cli-cache /tmp/beeper-build/cli \
    && HOME=/tmp/beeper-build BEEPER_CLI_CONFIG_DIR=/tmp/beeper-build/cli beeper version >/dev/null 2>&1 \
    && rm -rf /opt/beeper-cli-cache/*/node_modules/@beeper/desktop-api/node_modules \
    && find /opt/beeper-cli-cache -type f \( -name '._*' -o -name '*.d.ts' -o -name '*.d.ts.map' \) -delete \
    && payload_entry="$(find /opt/beeper-cli-cache -path '*/bin/cli.js' -type f | head -n 1)" \
    && test -n "$payload_entry" \
    && rm -rf /opt/beeper-cli-cache/bin \
    && printf '#!/bin/sh\nexec node "%s" "$@"\n' "$payload_entry" > /usr/local/bin/beeper \
    && chmod 0755 /usr/local/bin/beeper \
    && HOME=/tmp/beeper-build BEEPER_CLI_CONFIG_DIR=/tmp/beeper-build/cli beeper install server --json >/dev/null \
    && server_bin="$(find /tmp/beeper-build -type f -name beeper-server | head -n 1)" \
    && test -n "$server_bin" \
    && cp "$server_bin" /opt/beeper-server \
    && chmod 0755 /opt/beeper-server \
    && mkdir -p /opt/beeper-server-home /tmp/beeper-server-data \
    && ( HOME=/opt/beeper-server-home timeout 20s /opt/beeper-server --host=127.0.0.1 --port=23373 --data-dir=/tmp/beeper-server-data >/dev/null 2>&1 || test "$?" -eq 124 ) \
    && server_entry="$(find /opt/beeper-server-home -path '*/app/build/main/server.mjs' -type f | head -n 1)" \
    && test -n "$server_entry" \
    && server_app="${server_entry%/build/main/server.mjs}" \
    && sdk_binding="$(find "$server_app" -name beeper_client_sdk_napi_binding.node -type f | head -n 1)" \
    && test -n "$sdk_binding" \
    && strip --strip-unneeded "$sdk_binding" \
    && find "$server_app" -type f \( -name '*.map' -o -name '._*' \) -delete \
    && rm -rf "$server_app/node_modules/better-sqlite3/deps" \
              "$server_app/node_modules/better-sqlite3/build/Release/obj" \
              "$server_app/node_modules/better-sqlite3/build/Release/obj.target" \
    && rm -f "$server_app/node_modules/better-sqlite3/build/Release/sqlite3.a" \
              "$server_app/build/opusdec.exe" \
    && find "$server_app/node_modules/classic-level/prebuilds" -mindepth 1 -maxdepth 1 ! -name linux-x64 -exec rm -rf '{}' + \
    && printf '#!/bin/sh\nexec node "%s" "$@"\n' "$server_entry" > /usr/local/bin/beeper-server-hostless \
    && chmod 0755 /usr/local/bin/beeper-server-hostless \
    && rm -f /opt/beeper-server \
    && rm -rf /tmp/beeper-server-data /tmp/beeper-build \
    && beeper version >/dev/null

FROM node:20-bookworm-slim

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
    && chmod 0755 /usr/local/bin/ttyd

ENV HOME=/tmp/beeper-data \
    BEEPER_CLI_CONFIG_DIR=/tmp/beeper-data/cli \
    BEEPER_CLI_BINARY_CACHE_DIR=/opt/beeper-cli-cache \
    BEEPER_SERVER_BIN=/usr/local/bin/beeper-server-hostless \
    PORT=8000

WORKDIR /app
COPY --from=beeper-builder /app /app
COPY --from=beeper-builder /opt/beeper-cli-cache /opt/beeper-cli-cache
COPY --from=beeper-builder /opt/beeper-server-home /opt/beeper-server-home
COPY --from=beeper-builder /usr/local/bin/beeper /usr/local/bin/beeper
COPY --from=beeper-builder /usr/local/bin/beeper-server-hostless /usr/local/bin/beeper-server-hostless
COPY gateway.js /app/gateway.js
COPY entrypoint.sh /usr/local/bin/beeper-hostless-entrypoint
COPY setup-flow.sh /usr/local/bin/beeper-setup-flow
RUN chmod 0755 /usr/local/bin/beeper /usr/local/bin/beeper-server-hostless \
    /usr/local/bin/beeper-hostless-entrypoint /usr/local/bin/beeper-setup-flow

EXPOSE 8000

ENTRYPOINT ["/usr/bin/tini", "--", "/usr/local/bin/beeper-hostless-entrypoint"]
