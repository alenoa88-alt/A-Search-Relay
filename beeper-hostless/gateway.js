const crypto = require('crypto');
const fs = require('fs');
const http = require('http');
const httpProxy = require('http-proxy');

const port = Number(process.env.PORT || 8000);
const readyFile = `${process.env.HOME || '/data'}/.beeper-hostless-ready`;
const setupUser = process.env.SETUP_USER || 'artist';
const setupPassword = process.env.SETUP_PASSWORD || '';
const proxy = httpProxy.createProxyServer({ ws: true, xfwd: true });
const failedAttempts = new Map();
const verifiedTokens = new Map();

function same(a, b) {
  const left = Buffer.from(a || '');
  const right = Buffer.from(b || '');
  return left.length === right.length && crypto.timingSafeEqual(left, right);
}

function setupAuthorized(req) {
  const header = req.headers.authorization || '';
  if (!header.startsWith('Basic ')) return false;
  let decoded = '';
  try { decoded = Buffer.from(header.slice(6), 'base64').toString('utf8'); } catch { return false; }
  const split = decoded.indexOf(':');
  return split >= 0
    && same(decoded.slice(0, split), setupUser)
    && same(decoded.slice(split + 1), setupPassword);
}

function setupRateLimited(req) {
  const key = req.socket.remoteAddress || 'unknown';
  const now = Date.now();
  const recent = (failedAttempts.get(key) || []).filter(time => now - time < 60000);
  failedAttempts.set(key, recent);
  return recent.length >= 10;
}

function recordSetupFailure(req) {
  const key = req.socket.remoteAddress || 'unknown';
  failedAttempts.set(key, [...(failedAttempts.get(key) || []), Date.now()]);
}

function isPublicOAuthRoute(url) {
  const path = new URL(url, 'http://localhost').pathname;
  return path === '/v1/info'
    || path.startsWith('/.well-known/')
    || path === '/oauth/authorize'
    || path === '/oauth/token'
    || path === '/oauth/register'
    || path === '/oauth/revoke';
}

function tokenFrom(req) {
  const match = /^Bearer\s+(\S+)$/i.exec(req.headers.authorization || '');
  return match ? match[1] : '';
}

function verifyWithBeeper(token, callback) {
  const key = crypto.createHash('sha256').update(token).digest('hex');
  const cachedUntil = verifiedTokens.get(key) || 0;
  if (cachedUntil > Date.now()) return callback(true);

  const check = http.request({
    host: '127.0.0.1',
    port: 23373,
    path: '/oauth/userinfo',
    method: 'GET',
    headers: { authorization: `Bearer ${token}` },
    timeout: 5000
  }, response => {
    response.resume();
    const valid = response.statusCode >= 200 && response.statusCode < 300;
    if (valid) verifiedTokens.set(key, Date.now() + 30000);
    callback(valid);
  });
  check.on('timeout', () => { check.destroy(); callback(false); });
  check.on('error', () => callback(false));
  check.end();
}

function reject(res, status, headers, message) {
  res.writeHead(status, { 'content-type': 'application/json', ...headers });
  res.end(JSON.stringify({ error: message }));
}

function authorize(req, res, callback) {
  if (!fs.existsSync(readyFile)) {
    if (setupRateLimited(req)) {
      reject(res, 429, { 'retry-after': '60' }, 'too_many_setup_login_attempts');
      return;
    }
    if (!setupAuthorized(req)) {
      recordSetupFailure(req);
      reject(res, 401, { 'www-authenticate': 'Basic realm="Beeper setup"' }, 'setup_login_required');
      return;
    }
    callback();
    return;
  }

  if (req.method === 'OPTIONS' || isPublicOAuthRoute(req.url)) {
    callback();
    return;
  }

  const token = tokenFrom(req);
  if (!token) {
    reject(res, 401, { 'www-authenticate': 'Bearer realm="Beeper MCP"' }, 'oauth_bearer_token_required');
    return;
  }
  verifyWithBeeper(token, valid => {
    if (!valid) {
      reject(res, 401, { 'www-authenticate': 'Bearer realm="Beeper MCP", error="invalid_token"' }, 'invalid_oauth_token');
      return;
    }
    callback();
  });
}

function target() {
  return fs.existsSync(readyFile) ? 'http://127.0.0.1:23373' : 'http://127.0.0.1:7681';
}

function prepare(req) {
  req.headers['x-forwarded-proto'] = 'https';
  req.headers['x-forwarded-host'] = req.headers.host || '';
}

const server = http.createServer((req, res) => {
  const path = new URL(req.url, 'http://localhost').pathname;
  if (req.method === 'GET' && (path === '/health' || path === '/healthz')) {
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end(JSON.stringify({ status: 'ok' }));
    return;
  }
  authorize(req, res, () => {
    prepare(req);
    proxy.web(req, res, { target: target(), changeOrigin: false });
  });
});

server.on('upgrade', (req, socket, head) => {
  const response = {
    writeHead(status, headers) {
      const lines = [`HTTP/1.1 ${status} Unauthorized`];
      for (const [name, value] of Object.entries(headers || {})) lines.push(`${name}: ${value}`);
      socket.end(`${lines.join('\r\n')}\r\n\r\n`);
    },
    end() { if (!socket.destroyed) socket.end(); }
  };
  authorize(req, response, () => {
    prepare(req);
    proxy.ws(req, socket, head, { target: target(), changeOrigin: false });
  });
});

proxy.on('error', (_error, _req, res) => {
  if (res && !res.headersSent) res.writeHead(502, { 'content-type': 'text/plain' });
  if (res && typeof res.end === 'function') res.end('Beeper service is starting. Please retry shortly.');
});

server.listen(port, '0.0.0.0', () => console.log(`Secure Beeper gateway listening on ${port}`));
