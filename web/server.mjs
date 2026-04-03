import { createReadStream, existsSync, statSync } from 'node:fs'
import { readFile } from 'node:fs/promises'
import http from 'node:http'
import { extname, join, normalize } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = fileURLToPath(new URL('.', import.meta.url))
const args = new Map()
for (let i = 2; i < process.argv.length; i += 1) {
  const current = process.argv[i]
  const next = process.argv[i + 1]
  if (current.startsWith('--') && next && !next.startsWith('--')) {
    args.set(current.slice(2), next)
    i += 1
  }
}

const rootDir = args.get('root') ?? join(__dirname, 'dist')
const port = Number(args.get('port') ?? 4173)
const backend = new URL(args.get('backend') ?? 'http://localhost:18080')

const mimeTypes = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.ico': 'image/x-icon'
}

function sendJson(res, statusCode, payload) {
  res.writeHead(statusCode, { 'Content-Type': 'application/json; charset=utf-8' })
  res.end(JSON.stringify(payload))
}

function proxyRequest(req, res) {
  const target = new URL(req.url, backend)
  const proxy = http.request(
    {
      hostname: target.hostname,
      port: target.port,
      path: `${target.pathname}${target.search}`,
      method: req.method,
      headers: {
        ...req.headers,
        host: backend.host
      }
    },
    (proxyRes) => {
      res.writeHead(proxyRes.statusCode ?? 502, proxyRes.headers)
      proxyRes.pipe(res)
    }
  )
  proxy.on('error', (error) => {
    sendJson(res, 502, { error: 'proxy_failed', detail: error.message })
  })
  req.pipe(proxy)
}

async function serveStatic(req, res) {
  let requestPath = req.url?.split('?')[0] ?? '/'
  if (requestPath === '/') {
    requestPath = '/index.html'
  }

  const safePath = normalize(requestPath).replace(/^([.][.][/\\])+/, '')
  let filePath = join(rootDir, safePath)
  if (!existsSync(filePath) || (existsSync(filePath) && statSync(filePath).isDirectory())) {
    filePath = join(rootDir, 'index.html')
  }

  if (!existsSync(filePath)) {
    sendJson(res, 404, { error: 'not_found', path: requestPath })
    return
  }

  const ext = extname(filePath).toLowerCase()
  const contentType = mimeTypes[ext] ?? 'application/octet-stream'
  res.writeHead(200, { 'Content-Type': contentType })
  createReadStream(filePath).pipe(res)
}

if (!existsSync(rootDir)) {
  console.error(`Frontend dist directory not found: ${rootDir}`)
  process.exit(1)
}

const server = http.createServer(async (req, res) => {
  if (!req.url) {
    sendJson(res, 400, { error: 'missing_url' })
    return
  }

  if (req.url.startsWith('/api/') || req.url.startsWith('/actuator/')) {
    proxyRequest(req, res)
    return
  }

  if (req.url === '/healthz') {
    const payload = {
      status: 'UP',
      root: rootDir,
      backend: backend.toString()
    }
    const body = await readFile(join(rootDir, 'index.html')).then(() => payload).catch(() => payload)
    sendJson(res, 200, body)
    return
  }

  serveStatic(req, res)
})

server.listen(port, '0.0.0.0', () => {
  console.log(`Travel Agent frontend server listening on http://0.0.0.0:${port}`)
  console.log(`Serving static files from ${rootDir}`)
  console.log(`Proxying API traffic to ${backend}`)
})
