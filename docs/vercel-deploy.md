# Vercel deployment (web)

Dwellio’s Next.js app (`apps/web`) is deployed via **Vercel Git integration**, not a duplicate GitHub Actions workflow.

## Setup

1. In Vercel: **Add New Project** → import `supreme-reality/dwellio`.
2. **Root Directory:** `apps/web`.
3. Framework: Next.js (auto-detected).
4. Environment variables (Production + Preview as needed):
   - Auth0: `AUTH0_SECRET`, `AUTH0_DOMAIN`, `AUTH0_CLIENT_ID`, `AUTH0_CLIENT_SECRET`, `AUTH0_AUDIENCE`
   - `APP_BASE_URL` / Auth0 URLs for the Vercel domain
   - `NEXT_PUBLIC_API_BASE_URL` → DEV API `https://api-dev.vikranthreddy.com` (PROD: `https://api.vikranthreddy.com`)
5. Enable Production + Preview deployments from Git.

## DNS

`app.<domain>` → Vercel is Phase 8 (`docs` / Route 53). API hostnames are managed by Terraform (`api-dev` / `api`).

## Local vs Vercel

| | Local | Vercel |
|---|---|---|
| Command | `npm run dev` in `apps/web` | Git push → Vercel build |
| API URL | `http://localhost:8080` | `NEXT_PUBLIC_API_BASE_URL` |

See also `apps/web/README.md` and `docs/auth0-setup.md`.
