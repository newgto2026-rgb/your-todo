import http from 'node:http';

const PORT = Number.parseInt(process.env.AI_TODO_ROUTER_PORT ?? '8786', 10);
const APP_SERVER_URL = process.env.YOURTODO_APP_SERVER_URL ?? 'http://127.0.0.1:8080';
const AI_SERVER_URL = process.env.YOURTODO_AI_PROXY_URL ?? 'http://127.0.0.1:8787';

const server = http.createServer(async (req, res) => {
  const targetBase = req.url?.startsWith('/ai/') ? AI_SERVER_URL : APP_SERVER_URL;
  const targetUrl = new URL(req.url ?? '/', targetBase);

  try {
    const headers = new Headers(req.headers);
    headers.delete('host');

    const response = await fetch(targetUrl, {
      method: req.method,
      headers,
      body: ['GET', 'HEAD'].includes(req.method ?? '') ? undefined : req,
      duplex: 'half',
      redirect: 'manual'
    });

    res.writeHead(response.status, Object.fromEntries(response.headers.entries()));
    if (response.body) {
      for await (const chunk of response.body) {
        res.write(chunk);
      }
    }
    res.end();
  } catch (error) {
    res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({
      error: 'router_proxy_failed',
      message: error instanceof Error ? error.message : String(error)
    }));
  }
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`YourTodo ngrok router listening on http://127.0.0.1:${PORT}`);
  console.log(`App server -> ${APP_SERVER_URL}`);
  console.log(`AI server -> ${AI_SERVER_URL}`);
});
