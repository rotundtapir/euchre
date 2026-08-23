<!-- SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception -->

# Running your own Euchre server

The game server is in this repository and is free software, so you never have to play on someone
else's. Point the app at your own in **Settings → Online → Game server** and nothing about the game
changes — the client speaks the same protocol either way.

You do not need much. The server idles between human taps: a hand is a few kilobytes of JSON, there
is no tick loop, and the whole thing runs comfortably in a 144 MB heap. A Raspberry Pi is enough.

## The quickest way: the published image

```bash
docker run -d --name euchre-server \
  -p 8080:8080 \
  -e ALLOWED_ORIGINS='*' \
  -v euchre_data:/data -e DATA_DIR=/data \
  ghcr.io/rotundtapir/euchre-server:latest
```

The image is multi-arch (amd64 + arm64), so the same command works on a Pi.

Then set the server address in the app to `ws://<host>:8080` — or `wss://…` once you have TLS, which
you want for anything beyond your own LAN. Browsers will refuse a plaintext `ws://` connection from
an HTTPS page, so the web build in particular needs `wss://`.

## Building it yourself

```bash
./gradlew :server:installDist
DEV_MODE=true ./gradlew :server:run          # or run server/build/install/server/bin/server
```

`DEV_MODE=true` relaxes the per-IP connection caps and honours a client-supplied deal seed. It is for
development and tests — do not run a public server with it.

## Configuration

Everything is environment variables, so the container needs no config file.

| Variable | Default | What it does |
| --- | --- | --- |
| `PORT` | `8080` | Listen port. |
| `ALLOWED_ORIGINS` | `https://rotundtapir.github.io` | Comma-separated origins allowed to open a WebSocket. `*` disables the check — fine on a LAN, careless on the public internet. This is a CSWSH defence: WebSockets are not subject to CORS, so the server checks `Origin` itself. |
| `TRUST_PROXY` | `false` | Trust `X-Forwarded-*`. Set it **only** behind a reverse proxy you control, otherwise clients can forge their own IP and defeat the rate limits. |
| `DATA_DIR` | unset | Where room snapshots live. Unset means in-memory only: a restart drops every game in progress. Set it and games survive restarts. |
| `MIN_APP_VERSION` | `0.2.0` | Older clients are told to update rather than failing oddly. |
| `MAX_CONNECTIONS_PER_IP` | `8` | Per-IP socket cap. |
| `MSG_RATE_PER_SEC` / `MSG_BURST` | `10` / `20` | Per-socket message rate limit. |
| `LOBBIES_PER_IP_PER_10MIN` | `5` | Lobby-creation throttle. |
| `MAX_ROOMS` | `500` | Refuse new lobbies beyond this. |
| `SESSION_TTL_MILLIS` | `3600000` | How long an unused session token can still reclaim a seat. |
| `LOBBY_GRACE_MILLIS` | `900000` | How long a lobby seat is held after a socket drops, so a page reload keeps its place. |

## Endpoints

- `GET /health` — `{"status":"ok","rooms":N,"activeGames":M,"draining":bool}`. Use it as your
  container healthcheck.
- `GET /metrics` — Prometheus text, namespaced `euchre_*`.
- `POST /admin/drain` / `POST /admin/undrain` — stop accepting new lobbies so running games can
  finish before a restart.

**`/metrics` and `/admin/*` have no authentication.** Block them at your proxy, as the project's own
deployment does, or keep the server on a private network.

## Behind a reverse proxy

Caddy needs nothing special — it proxies WebSocket upgrades transparently and does not time out
established streams:

```
euchre.example.com {
	encode gzip
	respond /admin* 403
	respond /metrics 403
	reverse_proxy euchre-server:8080
}
```

Set `TRUST_PROXY=true` on the server so rate limits and abuse logs see the real client IP rather than
the proxy's.

## Backups

None needed. Snapshots under `DATA_DIR` are transient: one file per live room, deleted when the room
ends. They exist so a restart does not cost anyone their game, not as a record of anything.

Note they contain **full hidden information** — every hand, the kitty, and the bearer tokens that own
the seats. They are readable by anyone who can read the volume, so treat that directory as you would
any other server secret, and do not ship it anywhere.

## Abuse handling

The server writes one-line `ABUSE event=… ip=… detail=…` records to an `abuse` logger for the things
worth banning on: connection-cap hits, rate limiting, lobby-creation floods, malformed frames, and
join-code scanning. If you run fail2ban, that format is what to match on. The project's own
deployment does exactly this; note that its bans are IPv4-only, because Docker does not maintain the
`DOCKER-USER` chain for IPv6 by default.

A filter that matches the format:

```ini
# /etc/fail2ban/filter.d/euchre-server.conf
[Definition]
failregex = ^.*ABUSE event=\S+ ip=<HOST> .*$
ignoreregex =
```

Two things about the jail are worth knowing before you write one, because both fail quietly:

- **Read the container's journald stream, not a log file**: `backend = systemd` with
  `journalmatch = CONTAINER_NAME=euchre-server`, matching the container name in your compose file.
- **Ban in `DOCKER-USER`**, e.g.
  `banaction = iptables-multiport[chain="DOCKER-USER"]`. Docker's forwarded packets bypass the
  `INPUT` chain, so a default ban leaves the client connecting happily while
  `fail2ban-client status` cheerfully counts bans it is not enforcing.

Thresholds (`maxretry`, `findtime`, `bantime`) are yours to tune: the right numbers depend on how
much traffic you carry and how tolerant you want to be of a flaky client retrying.

If you host both this and [500](https://github.com/rotundtapir/500) on one machine, give each its own
jail rather than one jail matching both containers. A shared jail means one game's noisy-but-innocent
client can ban players of the other, and thresholds tuned for one server's event rate will not suit
two.
