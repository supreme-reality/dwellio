# Dwellio Web

Next.js 15 (App Router) frontend for **Dwellio**, with Auth0 login and property workflows against the Spring API.

## Local development

1. Copy env and fill secrets (see `docs/auth0-setup.md`):

```bash
cp .env.local.example .env.local
# Set AUTH0_SECRET (openssl rand -hex 32), AUTH0_CLIENT_SECRET, AUTH0_DOMAIN, AUTH0_CLIENT_ID
# AUTH0_AUDIENCE=https://api.dwellio.local
# NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
# Optional: NEXT_PUBLIC_RAZORPAY_KEY_ID for Checkout
```

2. Auth0 app must be **Regular Web Application** with:
   - Callback: `http://localhost:3000/auth/callback`
   - Logout / Web Origins: `http://localhost:3000`

3. Run API with `SPRING_PROFILES_ACTIVE=local` (and Docker Postgres/MinIO as needed), then:

```bash
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000) → Log in. Signed-in users are routed to onboarding or `/o/{orgId}/p/{propertyId}/inventory`.

## App routes (URL context)

- `/onboarding` — create organization
- `/o/[orgId]` — org home / create property; empty list is valid for Members with no assignments
- `/o/[orgId]/p/[propertyId]/…` — property shell (Stay / Money / Ops / Insights / Admin)

## Build

```bash
npm run build
```
