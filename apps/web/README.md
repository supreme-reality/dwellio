# Dwellio Web

Next.js 15 (App Router) frontend for **Dwellio**, with Auth0 login.

## Local development

1. Copy env and fill secrets (see `docs/auth0-setup.md`):

```bash
cp .env.local.example .env.local
# Set AUTH0_SECRET (openssl rand -hex 32), AUTH0_CLIENT_SECRET, AUTH0_DOMAIN, AUTH0_CLIENT_ID
# AUTH0_AUDIENCE=https://api.dwellio.local
```

2. Auth0 app must be **Regular Web Application** with:
   - Callback: `http://localhost:3000/auth/callback`
   - Logout / Web Origins: `http://localhost:3000`

3. Run API with `SPRING_PROFILES_ACTIVE=local`, then:

```bash
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000) → Log in / Sign up → home shows Auth0 session and `GET /api/v1/me`.

## Build

```bash
npm run build
```
