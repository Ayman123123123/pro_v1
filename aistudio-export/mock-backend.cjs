const http = require('http');

const PORT = 8080;

const server = http.createServer((req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    return res.end();
  }

  const url = req.url;

  let body = '';
  req.on('data', chunk => { body += chunk; });
  req.on('end', () => {
    try {
      if (url === '/health' || url === '/sfu-health') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({ status: 'UP', timestamp: new Date().toISOString(), workers: 4, rooms: 2, peers: 5 }));
      }

      if (url === '/api/auth/login') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({
          accessToken: 'mock_access_token_younes_admin',
          refreshToken: 'mock_refresh_token_younes_admin',
          user: { id: 'admin-001', username: 'admin', role: 'ADMIN' }
        }));
      }

      if (url === '/api/auth/refresh') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({
          accessToken: 'mock_access_token_younes_admin_refreshed',
          refreshToken: 'mock_refresh_token_younes_admin'
        }));
      }

      if (url.startsWith('/api/admin/monitor/stats') || url.startsWith('/api/master/v1/stats')) {
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

      if (url.startsWith('/api/admin/users')) {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({
          users: [
            { id: 'usr-101', username: 'younes_sovereign', status: 'APPROVED', registeredAt: '2026-08-01' },
            { id: 'usr-102', username: 'ahmed_dev', status: 'PENDING', registeredAt: '2026-08-08' },
            { id: 'usr-103', username: 'ali_red', status: 'PENDING', registeredAt: '2026-08-09' }
          ]
        }));
      }

      res.writeHead(200, { 'Content-Type': 'application/json' });
      return res.end(JSON.stringify({ status: 'SUCCESS', message: 'Mock API Response', data: {} }));
    } catch (e) {
      res.writeHead(500, { 'Content-Type': 'application/json' });
      return res.end(JSON.stringify({ error: e.message }));
    }
  });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Mock Backend API listening on 0.0.0.0:${PORT}`);
});
