// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
// Serves the production wasm distribution under a /euchre/ path prefix, mimicking GitHub Pages'
// project-site layout (https://rotundtapir.github.io/euchre/).
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join, normalize } from 'node:path';
import { fileURLToPath } from 'node:url';

const dist = fileURLToPath(new URL('../build/dist/wasmJs/productionExecutable', import.meta.url));
const types = {
  '.html': 'text/html',
  '.js': 'text/javascript',
  '.mjs': 'text/javascript',
  '.wasm': 'application/wasm',
  '.png': 'image/png',
  '.ogg': 'audio/ogg',
  '.ttf': 'font/ttf',
  '.map': 'application/json',
  '.txt': 'text/plain',
  '.xml': 'application/xml',
};

createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  if (url.pathname !== '/euchre' && !url.pathname.startsWith('/euchre/')) {
    res.writeHead(404);
    res.end('only /euchre/ is served');
    return;
  }
  const rel = url.pathname.replace(/^\/euchre\/?/, '') || 'index.html';
  const file = normalize(join(dist, rel));
  if (!file.startsWith(dist)) {
    res.writeHead(403);
    res.end();
    return;
  }
  try {
    const body = await readFile(file);
    res.writeHead(200, {
      'content-type': types[extname(file)] ?? 'application/octet-stream',
      // Local dev server only: never cache, so a plain reload always picks up a fresh rebuild
      // (the loader euchre.js has a stable name, so browsers would otherwise serve a cached copy
      // pointing at the previous, now-deleted content-hashed wasm). Production Pages is
      // unaffected — this file is not deployed.
      'cache-control': 'no-store, must-revalidate',
    });
    res.end(body);
  } catch {
    res.writeHead(404);
    res.end('not found');
  }
}).listen(9600, () => console.log(`serving ${dist} at http://localhost:9600/euchre/`));
