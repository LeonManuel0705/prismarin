# PRISMARIN Infrastructure

Docker Compose stack for the PRISMARIN network backends: MongoDB (profiles/economy), Redis (cross-server message bus), MariaDB (reserved for future relational data), plus Prometheus and Grafana for monitoring.

## Starting

```
cd infra
cp .env.example .env
docker compose up -d
```

The `.env` copy is optional: every variable has a sane default baked into `docker-compose.yml`, so `docker compose up -d` works out of the box. Change the passwords in `.env` for anything that is not a throwaway local setup.

Check the stack:

```
docker compose ps
docker compose logs -f mongodb redis
```

All ports except Grafana are bound to `127.0.0.1` only, so nothing is reachable from the outside:

| Service | Address |
| --- | --- |
| MongoDB | `127.0.0.1:27017` |
| Redis | `127.0.0.1:6379` |
| MariaDB | `127.0.0.1:3306` |
| Prometheus | `127.0.0.1:9090` |
| Grafana | `0.0.0.0:3000` |
| redis-exporter | `127.0.0.1:9121` |
| mongodb-exporter | `127.0.0.1:9216` |

Grafana login: `admin` / `prismarin` (override via `GRAFANA_ADMIN_PASSWORD`). Anonymous access is disabled. The Prometheus datasource and the "PRISMARIN Infra" dashboard are provisioned automatically from `grafana/provisioning`.

## Connecting the Minecraft servers

Every core plugin (`core-paper`, `core-velocity`) writes a default `config.yml` on first start. To move a server from standalone mode onto this stack, set:

```yaml
server-id: lobby-1
storage:
  mode: MONGO
  mongo:
    uri: mongodb://localhost:27017
    database: prismarin
redis:
  enabled: true
  host: localhost
  port: 6379
  password: ""
```

`storage.mode: MONGO` switches profile and economy persistence from local JSON files to MongoDB. `redis.enabled: true` switches the message bus from local-only to Redis pub/sub, which is required for cross-server messaging. Give every server a unique `server-id`.

## Current state of the live network

Today the network runs with `storage.mode: LOCAL` and Redis disabled, without Docker: each server keeps its data in local JSON files and there is no cross-server state. This stack is the target setup for multi-server operation; nothing on the live Windows host depends on it yet (see `windows/README.md` for how the live servers are run).

## Monitoring

Prometheus scrapes itself, the redis-exporter and the mongodb-exporter every 15 seconds (`prometheus/prometheus.yml`). The provisioned dashboard shows Redis ops/sec and memory, MongoDB connections and opcounters, and the up-status of all scrape targets.
