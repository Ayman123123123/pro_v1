const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 8088;
const DIST_DIR = path.join(__dirname, 'dist');

const MIME_TYPES = {
  '.html': 'text/html; charset=UTF-8',
  '.js': 'text/javascript; charset=UTF-8',
  '.css': 'text/css; charset=UTF-8',
  '.json': 'application/json; charset=UTF-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2'
};

const server = http.createServer((req, res) => {
  // CORS Headers
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    return res.end();
  }

  const urlPath = req.url.split('?')[0];

  // API Routes
  if (urlPath === '/health' || urlPath === '/sfu-health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    return res.end(JSON.stringify({ status: 'UP', timestamp: new Date().toISOString(), workers: 4, rooms: 2, peers: 5 }));
  }

  if (urlPath === '/api/auth/login') {
    let body = '';
    req.on('data', chunk => { body += chunk; });
    req.on('end', () => {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      return res.end(JSON.stringify({
        accessToken: 'mock_access_token_younes_admin',
        refreshToken: 'mock_refresh_token_younes_admin',
        user: { id: 'admin-001', username: 'admin', role: 'ADMIN' }
      }));
    });
    return;
  }

  if (urlPath === '/api/auth/refresh') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    return res.end(JSON.stringify({
      accessToken: 'mock_access_token_younes_admin_refreshed',
      refreshToken: 'mock_refresh_token_younes_admin'
    }));
  }

  if (urlPath.startsWith('/api/admin/monitor/stats') || urlPath.startsWith('/api/master/v1/stats')) {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    return res.end(JSON.stringify({
      active_users: 142,
      pending_approvals: 3,
      gsm_signal: 'STABLE',
      db_storage: '4.2 GB / 25 GB',
      messages_24h: 12480,
      system_load: '12%',
      cpu_usage: 18.5,
      memory_usage: 42.1,
      db_health: 'OPTIMAL'
    }));
  }

  if (urlPath.startsWith('/api/admin/users')) {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    return res.end(JSON.stringify({
      users: [
        { id: 'usr-101', username: 'younes_sovereign', status: 'APPROVED', registeredAt: '2026-08-01' },
        { id: 'usr-102', username: 'ahmed_dev', status: 'PENDING', registeredAt: '2026-08-08' },
        { id: 'usr-103', username: 'ali_red', status: 'PENDING', registeredAt: '2026-08-09' }
      ]
    }));
  }

  if (urlPath.startsWith('/api/')) {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    return res.end(JSON.stringify({ status: 'SUCCESS', message: 'Mock API Response', data: {} }));
  }

  // Static File Serving
  let filePath = path.join(DIST_DIR, urlPath === '/' ? 'index.html' : urlPath);

  fs.stat(filePath, (err, stats) => {
    if (err || !stats.isFile()) {
      filePath = path.join(DIST_DIR, 'index.html');
    }

    const ext = path.extname(filePath).toLowerCase();
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';

    fs.readFile(filePath, (err, content) => {
      if (err) {
        res.writeHead(500, { 'Content-Type': 'text/plain' });
        return res.end('500 Internal Server Error');
      }
      res.writeHead(200, { 'Content-Type': contentType });
      res.end(content);
    });
  });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`RED Admin Server listening on 0.0.0.0:${PORT}`);
});
