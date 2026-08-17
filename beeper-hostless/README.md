# Beeper Server on Hostless

This container runs the official `beeper-cli` headless server behind an HTTP
gateway. During first launch, the public URL shows a password-protected,
rate-limited and restricted setup flow. After setup, the gateway allows only
Beeper OAuth discovery/authorization routes without a token. Every MCP, chat,
message, contact and send request must carry a bearer token that the gateway
validates against Beeper's own `/oauth/userinfo` endpoint before forwarding.

Hostless settings:

- Branch: `codex/beeper-hostless`
- Build system: Dockerfile
- Working directory: `/beeper-hostless`
- Build context directory: `/beeper-hostless`
- Dockerfile path: `Dockerfile`
- Port: `8080`
- Environment variable: `SETUP_PASSWORD` set to a strong unique secret
- Optional environment variable: `SETUP_USER` (defaults to `artist`)

After setup completes, use `https://YOUR-HOSTLESS-DOMAIN/v0/mcp` as the
ChatGPT custom plugin server URL and choose OAuth authentication.
