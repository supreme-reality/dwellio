# Auth0 setup for Dwellio

Manual checklist. Org roles live in **our DB**, not Auth0 roles.

One Auth0 **tenant** is enough for MVP. Split **Regular Web Applications** and **APIs (audiences)** per environment. Vercel wiring: [`docs/vercel-deploy.md`](vercel-deploy.md).

## 1. Tenant

1. Sign in at [Auth0 Dashboard](https://manage.auth0.com/).
2. Create a tenant (or use an existing one).
3. Note the **Domain** (e.g. `your-tenant.us.auth0.com`) — same `AUTH0_DOMAIN` / issuer for local, DEV, and PROD.

## 2. APIs (resource servers / audiences)

Create **two** APIs (plus optional local). Identifier = `AUTH0_AUDIENCE` / Spring `dwellio.auth0.audience`.

| Auth0 API name | Identifier (audience) | Used by |
|---|---|---|
| `Dwellio API Dev` | `https://api-dev.vikranthreddy.com` | `app-dev` + `api-dev` (+ local if desired) |
| `Dwellio API` (Prod) | `https://api.vikranthreddy.com` | `app` + `api` |
| (optional local) | `https://api.dwellio.local` | localhost only |

Signing Algorithm: **RS256**. Leave default scopes unless you add custom ones later.

Audience is the API **Identifier**, not the web hostname (`app` / `app-dev`).

## 3. Regular Web Applications

Create **two** apps (type **Regular Web Applications**, not SPA — Next.js Auth0 SDK needs a client secret).

| Auth0 app | Callbacks / logout / web origins |
|---|---|
| `Dwellio Web Dev` | `https://app-dev.vikranthreddy.com` (+ optional `http://localhost:3000`) |
| `Dwellio Web Prod` | `https://app.vikranthreddy.com` only (no localhost) |

Callback path for SDK v4: `/auth/callback` (not `/api/auth/callback`).

| Setting | Dev example |
|---|---|
| Allowed Callback URLs | `https://app-dev.vikranthreddy.com/auth/callback` (and localhost if needed) |
| Allowed Logout URLs | `https://app-dev.vikranthreddy.com` |
| Allowed Web Origins | `https://app-dev.vikranthreddy.com` |

Enable **Authorization Code** and **Refresh Token** grant types (default for Regular Web).

### Credentials to copy

| Auth0 field | Used as |
|---|---|
| Domain | `AUTH0_DOMAIN` / Spring `issuer-uri` (same tenant) |
| Client ID | `AUTH0_CLIENT_ID` (per web app) |
| Client Secret | `AUTH0_CLIENT_SECRET` (per web app) |
| API Identifier | `AUTH0_AUDIENCE` (per API / env) |

`AUTH0_SECRET` is **not** from Auth0 — generate with `openssl rand -hex 32` per deploy env (local / Preview / Production).

## 4. Authorize each web app for its API

| Web app | Authorize API |
|---|---|
| `Dwellio Web Dev` | `Dwellio API Dev` (`https://api-dev.vikranthreddy.com`) |
| `Dwellio Web Prod` | Prod API (`https://api.vikranthreddy.com`) |

Do not cross-authorize unless intentional. The web app must request tokens with the matching `audience` so the access token is a JWT for that API (not an opaque Auth0 token).

## 5. Optional: put `email` + `name` on the access token

Auth0 access tokens often omit profile claims. Dwellio upserts `app_user` by **email**.

1. **Actions → Flows → Login** → add a custom Action, e.g.:

```javascript
exports.onExecutePostLogin = async (event, api) => {
  if (event.authorization) {
    api.accessToken.setCustomClaim('email', event.user.email);
    api.accessToken.setCustomClaim('name', event.user.name);
  }
};
```

2. Deploy and drag the Action into the Login flow.

Alternatively, use namespaced claims (`https://dwellio.local/email`) and map them in the API later — keep docs and code in sync if you do.

**Do not** put OWNER/MEMBER/MANAGER roles in Auth0 for MVP; those stay in `organization_membership` / `property_membership`.

## 6. Local config files

Copy examples and fill secrets (never commit real values):

```bash
cp apps/api/src/main/resources/application-local.yml.example \
   apps/api/src/main/resources/application-local.yml

cp apps/web/.env.local.example apps/web/.env.local
```

Generate `AUTH0_SECRET` (web session encryption):

```bash
openssl rand -hex 32
```

### Values checklist (every env / Spring key)

| Purpose | Web (`.env.local`) | API (`application-local.yml`) |
|---|---|---|
| Session secret | `AUTH0_SECRET` | — |
| App base URL | `APP_BASE_URL=http://localhost:3000` | — |
| Auth0 domain | `AUTH0_DOMAIN=YOUR_TENANT.us.auth0.com` | `spring.security.oauth2.resourceserver.jwt.issuer-uri` = `https://YOUR_TENANT.us.auth0.com/` |
| Client ID | `AUTH0_CLIENT_ID` | — |
| Client Secret | `AUTH0_CLIENT_SECRET` | — |
| API audience | `AUTH0_AUDIENCE` (local or DEV identifier) | `dwellio.auth0.audience` / `AUTH0_AUDIENCE` |
| API base for browser | `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080` | — |
| Razorpay Checkout (public) | `NEXT_PUBLIC_RAZORPAY_KEY_ID` | `dwellio.razorpay.key-id` / `RAZORPAY_KEY_ID` |
| Razorpay secret | — (do not put in web) | `dwellio.razorpay.key-secret` / `RAZORPAY_KEY_SECRET` |
| Razorpay webhook | — | `dwellio.razorpay.webhook-secret` / `RAZORPAY_WEBHOOK_SECRET` |

Auth0 callback for SDK v4: `http://localhost:3000/auth/callback` (not `/api/auth/callback`).

Issuer URI must match token `iss` (trailing slash is fine; Spring normalizes). Prefer pointing local at the **DEV** audience if you exercise `api-dev` from a laptop tunnel; otherwise keep `https://api.dwellio.local` only when the local API is configured for it.

## 7. Hosted DEV / PROD (Vercel + ECS)

| | DEV | PROD |
|---|---|---|
| Web | `https://app-dev.vikranthreddy.com` | `https://app.vikranthreddy.com` |
| API | `https://api-dev.vikranthreddy.com` | `https://api.vikranthreddy.com` |
| Auth0 web app | `Dwellio Web Dev` | `Dwellio Web Prod` |
| Audience | `https://api-dev.vikranthreddy.com` | `https://api.vikranthreddy.com` |
| Vercel env | Preview (or dedicated DEV project) | Production |
| ECS / API secrets | DEV audience + same issuer | PROD audience + same issuer |

Mirror Client ID/Secret, Audience, and `APP_BASE_URL` in Vercel; mirror Audience + issuer in API Secrets Manager / task env. Details: [`docs/vercel-deploy.md`](vercel-deploy.md).

## 8. Verify (human)

1. Log in via local, `app-dev`, or `app` as appropriate.
2. Capture an access token (browser Network tab on `/api/v1/me`, or Auth0 debugger).
3. Paste into [jwt.io](https://jwt.io):
   - `aud` includes the expected audience for that env
   - `iss` matches your tenant issuer
   - Prefer presence of `email` (and `name`) after the optional Action
4. Confirm a DEV token is rejected by PROD API (and vice versa) when audiences are split.

Without the optional Action, Auth0 access tokens often omit `email`. `GET /api/v1/me` requires an email claim (or a namespaced claim ending in `/email`) and returns 401 if missing.

```bash
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/me
# or: https://api-dev.vikranthreddy.com/api/v1/me
```
