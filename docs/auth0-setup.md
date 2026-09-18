# Auth0 setup for Dwellio

Manual checklist for local DEV. Org roles live in **our DB**, not Auth0 roles.

## 1. Tenant

1. Sign in at [Auth0 Dashboard](https://manage.auth0.com/).
2. Create a tenant (or use an existing one).
3. Note the **Domain** (e.g. `your-tenant.us.auth0.com`).

## 2. API (resource server)

1. **Applications → APIs → Create API**
2. Name: `Dwellio API`
3. Identifier (audience): `https://api.dwellio.local`  
   (keep this exact value in local configs unless you deliberately change all envs)
4. Signing Algorithm: **RS256**
5. Save. Leave default scopes unless you add custom ones later.

## 3. Regular Web Application

1. **Applications → Applications → Create Application**
2. Name: `Dwellio Web`
3. Type: **Regular Web Applications**
4. Technology: Next.js (optional)

### Application settings

| Setting | Local value |
|---|---|
| Allowed Callback URLs | `http://localhost:3000/auth/callback` |
| Allowed Logout URLs | `http://localhost:3000` |
| Allowed Web Origins | `http://localhost:3000` |

Enable **Authorization Code** and **Refresh Token** grant types (default for Regular Web).

### Credentials to copy

| Auth0 field | Used as |
|---|---|
| Domain | Issuer base / `AUTH0_ISSUER_BASE_URL` / Spring `issuer-uri` |
| Client ID | `AUTH0_CLIENT_ID` |
| Client Secret | `AUTH0_CLIENT_SECRET` |
| API Identifier | Audience (`AUTH0_AUDIENCE` / Spring audience) |

## 4. Authorize the web app for the API

1. Open the **Dwellio Web** application.
2. **APIs** tab → authorize **Dwellio API** (audience `https://api.dwellio.local`).
3. When requesting tokens from the web app, pass `audience=https://api.dwellio.local` so the access token is a JWT for the API (not an opaque Auth0 token).

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
| API audience | `AUTH0_AUDIENCE=https://api.dwellio.local` | `dwellio.auth0.audience` |
| API base for browser | `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080` | — |

Auth0 callback for SDK v4: `http://localhost:3000/auth/callback` (not `/api/auth/callback`).

Issuer URI must match token `iss` (trailing slash is fine; Spring normalizes).

## 7. Verify (human)

1. Use Auth0 **Test** / login via the web app once Task 6 is wired.
2. Capture an access token (browser Network tab on `/api/v1/me`, or Auth0 debugger).
3. Paste into [jwt.io](https://jwt.io):
   - `aud` includes `https://api.dwellio.local` (or your chosen audience)
   - `iss` matches your tenant issuer
   - Prefer presence of `email` (and `name`) after the optional Action

Without the optional Action, Auth0 access tokens often omit `email`. `GET /api/v1/me` requires an email claim (or a namespaced claim ending in `/email`) and returns 401 if missing.

```bash
# After Task 5 is implemented:
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/me
```

## Production later

When `app.<your-domain>` is live (Phase infra), update Auth0 Allowed Callback / Logout / Web Origins to the HTTPS URLs and mirror the same Domain, Client, Audience values in Vercel + ECS secrets.
