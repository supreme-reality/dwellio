# Dwellio

PG / co-living management for organizations that run paying guest and shared housing properties.

## Monorepo layout

```
dwellio/
  apps/
    api/              # Spring Boot API (Java 21)
    billing-worker/   # Monthly billing worker (later)
    invoice-worker/   # Invoice SQS consumer (later)
    web/              # Next.js App Router (Vercel)
  terraform/          # AWS infra (later)
  projectDocs/v5/     # Canonical product specs (StayFlow-named docs; product brand is Dwellio)
  docs/superpowers/   # Plans and specs
```

## Specs

Domain source of truth: [`projectDocs/v5/`](projectDocs/v5/). Do not implement from older PDFs under `projectDocs/`.

## Local run

### API (port 8080)

Requires Postgres (see Docker below).

```bash
docker compose up -d
./gradlew :apps:api:bootRun
curl -s http://localhost:8080/actuator/health
```

### Postgres (Docker)

```bash
docker compose up -d
# Host port 5433 → container 5432 (avoids clashes with other local Postgres)
# db/user/pass: dwellio / dwellio / dwellio
```

### Web (port 3000)

Requires Auth0 env in `apps/web/.env.local` (see `apps/web/.env.local.example` and `apps/web/README.md`) and a running API.

```bash
cd apps/web
npm install
npm run dev
```

After login, the app uses org → property URL context (`/o/{orgId}/p/{propertyId}/…`) for inventory, stays, finance, and ops screens.

Production-like web build:

```bash
cd apps/web && npm run build
```

## Stack

- API: Java 21, Spring Boot 3.3+, Actuator, Auth0 JWT
- Web: Next.js 15, TypeScript, App Router, Auth0 SDK
- Auth: Auth0 (Regular Web Application + API audience)
- DB: PostgreSQL 15+ locally / Aurora in AWS
