# Dwellio MVP Implementation Plan

> **For agentic workers:** Execute **one task at a time, sequentially**. Do **not** run parallel agents. After each task, stop and run that task’s **Verify** section; only proceed when it passes. Prefer `superpowers:executing-plans` (inline, checkpointed) or single-task subagent handoff — never parallel dispatch.
>
> **Domain source of truth:** `projectDocs/v5/` (StayFlow-named docs; product brand is **Dwellio**). Do not implement from older PDFs under `projectDocs/`.

**Goal:** Build Dwellio (PG / co-living management) as a monorepo: Spring Boot API + workers on AWS, Next.js web on Vercel, Auth0 auth, Route 53 subdomain, Terraform for AWS.

**Architecture:** Organization is the SaaS boundary. Stay model is Tenant ? Tenancy ? Occupancy ? Bed. Sync domain ops run in the API with transactional boundaries; monthly billing is async via EventBridge ? SQS ? workers. Frontend is a separate Vercel deployment calling the API; DNS for the app subdomain lives in Route 53 (domain purchased in AWS).

**Tech Stack:**
- Monorepo (npm/pnpm workspaces optional for web; Gradle multi-project for Java)
- API: Java 21, Spring Boot 3.3+, Spring Security OAuth2 Resource Server (Auth0 JWT), Flyway, JPA or JDBC
- DB: PostgreSQL 15+ locally; Aurora PostgreSQL in AWS
- Web: Next.js 15 (App Router), TypeScript, Auth0 Next.js SDK
- Workers: one Spring Boot app (`apps/worker`) sharing `apps/api` domain modules; `WORKER_ROLE=billing|invoice` selects consumers; deploy same image as separate ECS services for independent scale
- Infra: Terraform (VPC, ALB, ECS, Aurora, SQS, S3, IAM/OIDC, monitoring)
- Frontend host: Vercel + Route 53 CNAME/ALIAS for `app.<your-domain>`
- Payments: Razorpay + CASH + BANK_TRANSFER (per API v5.0)

## Global Constraints

- Product brand in UI/copy/README: **Dwellio** (not StayFlow).
- Domain rules: `projectDocs/v5/StayFlow_Project_Context_v5.0.md` and peers — StayFlow is doc name only.
- API base path: `/api/v1`; money `NUMERIC(12,2)`; UUIDs; dates `YYYY-MM-DD`; timestamps ISO-8601 with TZ.
- AuthZ: OWNER / MEMBER org roles; property MANAGER only; **no Viewer**; server enforces scope.
- Tenant profiles are **organization-scoped**.
- Bed availability derived: `BLOCKED` (block_reason set) ? else `OCCUPIED` ? else `AVAILABLE`.
- No free-standing Tenancy/Occupancy CRUD; workflow-bound paths only.
- No partial payments; no client invoice allocation; no public manual billing trigger.
- Infrastructure/Terraform: implement v5 topology; do **not** invent extra AWS services for tickets/bed-block/etc.
- Execution: **sequential tasks only**; each task ends with a verifiable check.

## Monorepo layout (locked)

```
dwellio/
  apps/
    api/                 # Spring Boot API
    worker/              # Shared worker image (WORKER_ROLE=billing|invoice)
    web/                 # Next.js (Vercel)
  packages/
    (optional later)     # shared TS types if needed
  terraform/
    modules/             # vpc, ecs, alb, aurora, s3, sqs, iam, monitoring
    environments/        # dev, prod
  projectDocs/v5/        # canonical specs (read-only for implementers)
  docs/superpowers/      # plans/specs
  README.md
```

## File responsibility map

| Path | Responsibility |
|---|---|
| `apps/api/src/main/java/com/dwellio/api/` | HTTP API, auth filter, domain services, repositories |
| `apps/api/src/main/resources/db/migration/` | Flyway SQL from DB Schema v5.0 |
| `apps/worker/` | Shared worker image; `WORKER_ROLE=billing` fan-out; `WORKER_ROLE=invoice` MONTHLY create + idempotency |
| `apps/web/` | Dwellio UI (Auth0 login, org/property context, workflows) |
| `terraform/` | AWS infra as code (two ECS worker services, one worker image) |
| `.github/workflows/` | Infra plan/apply + API/worker images ? ECR ? ECS; web deploy via Vercel Git integration |

---

## Phase 0 — Foundation

### Task 1: Monorepo scaffold + Dwellio README

**Files:**
- Create: `README.md`, `.gitignore`, `settings.gradle.kts`, `build.gradle.kts`, `apps/api/build.gradle.kts`, `apps/web/package.json`, `apps/web/README.md`
- Create: `apps/api/src/main/java/com/dwellio/api/DwellioApiApplication.java`
- Create: `apps/api/src/main/resources/application.yml`

**Interfaces:**
- Produces: runnable empty Spring Boot app on `:8080`; empty Next.js app on `:3000`

- [ ] **Step 1:** Initialize Gradle multi-project with `api` module; Spring Boot 3.3+, Java 21, Actuator.
- [ ] **Step 2:** Scaffold Next.js 15 TypeScript App Router in `apps/web` with brand title **Dwellio**.
- [ ] **Step 3:** Root README describes monorepo layout, local run commands, and points to `projectDocs/v5/`.

**Verify:**
```bash
cd apps/api && ./gradlew bootRun   # or from root
curl -s http://localhost:8080/actuator/health   # expect {"status":"UP"}
cd apps/web && npm run build                    # exit 0
```

**Commit:** `chore: scaffold Dwellio monorepo (api + web)`

---

### Task 2: Local Postgres + Flyway baseline (identity tables)

**Files:**
- Create: `docker-compose.yml` (Postgres 15, port 5432, db/user/pass `dwellio`)
- Create: `apps/api/src/main/resources/db/migration/V1__identity_org.sql`
- Modify: `apps/api/.../application.yml` (datasource + flyway)

**SQL must create (DB Schema v5.0 §§2.1–2.4):**
`organization`, `app_user`, `organization_membership`, `property_membership` with constraints listed in schema.

**Verify:**
```bash
docker compose up -d
cd apps/api && ./gradlew bootRun
# Flyway runs; psql: \dt shows the four tables
docker compose exec postgres psql -U dwellio -c '\dt'
```

**Commit:** `feat(db): add identity and org membership schema`

---

### Task 3: Shared API error model + requestId filter

**Files:**
- Create: `.../common/ApiException.java`, `ErrorCode.java`, `GlobalExceptionHandler.java`, `RequestIdFilter.java`
- Create: test `.../common/GlobalExceptionHandlerTest.java`

**Produces:** Error JSON shape from API Contract §13:
```json
{ "error": { "code": "...", "message": "...", "details": {}, "requestId": "uuid" } }
```

**Verify:**
```bash
./gradlew test --tests '*GlobalExceptionHandlerTest'
# Manual: hit unknown route ? 404 body includes requestId
```

**Commit:** `feat(api): standard error envelope and request id`

---

## Phase 1 — Auth0

### Task 4: Auth0 tenant/application setup (manual + docs)

**Files:**
- Create: `docs/auth0-setup.md` (checklist for humans)
- Create: `apps/api/src/main/resources/application-local.yml.example`
- Create: `apps/web/.env.local.example`

**Steps (document exactly; execute in Auth0 dashboard):**
1. Create Auth0 tenant (or use existing).
2. Create **Regular Web Application** for Dwellio web (callbacks: `http://localhost:3000/api/auth/callback`, logout URLs, web origins).
3. Create **API** audience e.g. `https://api.dwellio.local` with RS256.
4. Enable Authorization Code + Refresh; note Domain, Client ID, Client Secret, Audience.
5. (Optional MVP) Add Action to put `email` + `name` on access token; org roles stay in **our DB**, not Auth0 roles.

**Verify:**
- `docs/auth0-setup.md` lists every value needed in `.env` / Spring config.
- Auth0 test login returns JWT; jwt.io shows `aud` matching API audience.

**Commit:** `docs: Auth0 setup checklist for Dwellio`

---

### Task 5: API JWT resource server + user upsert

**Files:**
- Create: `.../security/SecurityConfig.java`, `Auth0JwtConfig.java`, `CurrentUserService.java`
- Create: `.../user/AppUserEntity.java`, `AppUserRepository.java`
- Create: `.../user/MeController.java` ? `GET /api/v1/me`
- Test: `.../security/JwtSecurityIT.java` (WireMock or spring-security-test)

**Behavior:**
- Validate Auth0 JWT (issuer + audience).
- On first authenticated request, upsert `app_user` by email from claims.
- Unauthenticated ? 401 with standard error envelope.

**Verify:**
```bash
./gradlew test --tests '*JwtSecurityIT'
# With real token: curl -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/me
```

**Commit:** `feat(api): Auth0 JWT validation and app_user upsert`

---

### Task 6: Web Auth0 login shell

**Files:**
- Modify: `apps/web` — Auth0 Next.js SDK (`@auth0/nextjs-auth0` or Auth0 SPA + BFF pattern)
- Create: `apps/web/app/page.tsx` (Dwellio branded home), `app/api/auth/[...auth0]/route` as required by SDK
- Create: logged-in shell that calls `GET /api/v1/me` with access token

**Verify:**
```bash
cd apps/web && npm run dev
# Browser: login ? logout works; Network shows /api/v1/me 200
```

**Commit:** `feat(web): Auth0 login and me bootstrap`

---

## Phase 2 — Org & inventory (API)

### Task 7: Organizations API (create/list/get)

**Files:**
- Create: org entities/repos/services/controllers under `.../org/`
- Endpoints (API §2): `POST/GET /organizations`, `GET /organizations/{id}`
- On create: creator ? `organization_membership` role `OWNER`
- Tests: Owner bootstrap; list only memberships

**Verify:** `./gradlew test --tests '*Organization*Test'` + curl create/list with JWT

**Commit:** `feat(api): organization create and list`

---

### Task 8: Org members API

**Endpoints:** `GET/POST .../members`, `PATCH .../members/{membershipId}`  
**Rules:** Owner only for POST/PATCH; roles `OWNER`/`MEMBER` only; no Viewer.

**Verify:** Member cannot add members (403); Owner can; tests green.

**Commit:** `feat(api): organization membership management`

---

### Task 9: Properties API + authz scoping

**Flyway:** `V2__property_inventory.sql` — `property`, `room`, `bed` (+ block columns per DB §3.3)

**Endpoints:** property CRUD per API §2; soft-delete/deactivate; Owner-only create/update/delete.

**Verify:** Owner creates property; Member with no assignment sees empty list; Manager later tasks.

**Commit:** `feat(api): properties with org scope`

---

### Task 10: Property managers API

**Endpoints:** `GET/POST/DELETE /properties/{id}/managers` — Owner only; assignee must be org `MEMBER`.

**Verify:** Manager assignment enables property visibility in list tests.

**Commit:** `feat(api): property manager assignment`

---

### Task 11: Rooms & beds CRUD

**Endpoints:** rooms/beds create/update/list per API §2.  
**AuthZ:** Owner any property; Manager assigned only.

**Verify:** Manager on property A cannot mutate property B (403).

**Commit:** `feat(api): rooms and beds CRUD`

---

### Task 12: Derived bed availability + block/unblock

**Service:** `BedAvailabilityService.resolve(bedId) ? AVAILABLE|OCCUPIED|BLOCKED`  
Rules: block_reason ? BLOCKED; else ACTIVE occupancy ? OCCUPIED; else AVAILABLE (catalog ACTIVE).

**Endpoints:** `POST /beds/{id}/block` `{reason}`, `POST .../unblock`; list beds returns derived availability.  
Occupied beds **may** be blocked.

**Verify:** Unit tests for derivation matrix; block without reason ? 422; unblock clears columns.

**Commit:** `feat(api): bed availability and blocking`

---

### Task 13: Rent config + resolve

**Flyway:** `V3__rent_config.sql`  
**Endpoints:** API §5; resolution Bed ? Room ? Property; no overlapping ranges at same level.

**Verify:** Resolve returns most-specific amount; history preserved (no overwrite).

**Commit:** `feat(api): effective-dated rent configuration`

---

## Phase 3 — Stay core (API)

### Task 14: Tenant APIs (org-scoped)

**Flyway:** `V4__tenant_stay.sql` — `tenant`, `tenancy`, `occupancy`, `move_in`, `move_in_service`  
**Endpoints:** API §3 tenants; unique `(organization_id, phone)` and email.

**Verify:** Same phone in two orgs allowed; duplicate in one org ? 409/422.

**Commit:** `feat(api): organization-scoped tenants`

---

### Task 15: Move-in draft create/update/cancel

**Endpoints:** API §4 draft lifecycle.  
**Invariants:** Creates new Tenancy; **no** ACTIVE Occupancy; does **not** change bed availability.

**Verify:** After draft, bed still AVAILABLE; GET move-in resumes fields.

**Commit:** `feat(api): move-in draft workflow`

---

### Task 16: Minimal tenancy stay context read

**Endpoint:** `GET /tenancies/{tenancyId}` — status, property, current occupancy summary only.

**Verify:** Out-of-scope tenancy ? 404 (not 403 leak).

**Commit:** `feat(api): tenancy stay context read`

---

## Phase 4 — Money (API)

### Task 17: Payment + invoice schema + generic payment create

**Flyway:** `V5__payments_invoices_deposit.sql` — `payment`, `payment_invoice`, `invoice`, `invoice_line_item`, `deposit_ledger`, `settlement_snapshot`

**Endpoints:** `POST /payments`, `GET /payments/{id}`, `POST /payments/{id}/confirm`  
Methods: `RAZORPAY|CASH|BANK_TRANSFER`; bank ref required for bank transfer; idempotency key unique.

**Verify:** Confirm is immutable; duplicate idempotency ? 409; no partial pay.

**Commit:** `feat(api): generic payment model`

---

### Task 18: Move-in payment façade (transactional activation)

**Endpoint:** `POST /move-ins/{id}/payment`  
**Atomic success path:** confirm payment ? deposit `RECEIPT` if deposit collected ? revalidate bed `AVAILABLE` ? ACTIVE Occupancy ? activate enrollments ? complete move-in.  
Failure leaves DRAFT; no occupancy.

**Verify:** Integration test happy path + bed becomes OCCUPIED; blocked/occupied bed ? 409 `BED_UNAVAILABLE`.

**Commit:** `feat(api): move-in payment activates occupancy`

---

### Task 19: Deposit summary + ledger read

**Endpoints:** API §8. Types only `RECEIPT|DEDUCTION|REFUND`.

**Verify:** After move-in with deposit, ledger has RECEIPT; balance matches.

**Commit:** `feat(api): deposit ledger reads`

---

### Task 20: Invoice reads

**Endpoints:** list by property/tenant/tenancy; get invoice + lines. Finalized invoices immutable (reject mutations).

**Verify:** Tests for immutability guard.

**Commit:** `feat(api): invoice read APIs`

---

### Task 21: Razorpay webhook (verify + idempotent)

**Endpoint:** `POST /webhooks/razorpay`  
Verify signature; idempotent confirm; map to payment.

**Verify:** Replay same event ? no double confirm; bad signature ? 401/400.

**Commit:** `feat(api): Razorpay webhook verification`

---

### Task 22: Services catalog, config, tenancy enrollment

**Flyway:** `V6__services.sql`  
**Endpoints:** API §6; tenancy-level only; end enrollment; variable charges.

**Verify:** No bed-scoped enrollment columns; restart = new enrollment row.

**Commit:** `feat(api): services and tenancy enrollments`

---

## Phase 5 — Stay ops (API + workers)

### Task 23: Transfer preview + execute

**Endpoints:** `POST /transfers/preview`, `POST /transfers`  
Preview: destination availability (same property), destination rent **proration diff**, **informational** current enrollments.  
Execute: close source occupancy + create destination under **same** Tenancy; destination must be AVAILABLE; optional manager **manual override** for proration amount; no deposit refund.

**Verify:** Atomic swap; failed availability leaves source ACTIVE; override amount used when provided.

**Commit:** `feat(api): bed transfer workflow`

---

### Task 24: Checkout preview + confirm + settlement read

**Endpoints:** `POST /checkout/preview`, `POST /checkout`, `GET /settlements/{id}`  
Body requires `tenancyId`. Optional damages/manual charge inputs on preview/confirm.  
**Math:** `owed = unpaidInvoices + newCharges`; `depositDeduction = min(SD, owed)`; `netReceivable = owed ? deduction`; `refundDue = SD ? deduction`.  
New charges: prorated rent + unbilled variable (+ unbilled fixed MONTHLY_ARREARS if not invoiced) + damages/manual.  
Confirm: immutable `settlement_snapshot`; `DEDUCTION` when deduction > 0; CHECKOUT invoice for **new charges only**; close occupancy/tenancy; end enrollments.  
If `netReceivable > 0` without full payment: require `leaveReceivable: true` (no partial pay).

**Verify:** Preview numbers match fixture formulas; unpaid finish with ack works; second checkout ? 409; paid-full-net path settles invoices.

**Commit:** `feat(api): checkout and settlement snapshot`

---

### Task 25: Settlement refund payout

**Endpoint:** `POST /settlements/{id}/refund` ? deposit ledger `REFUND` (not payment reversal).  
Only when snapshot `refundDue > 0`. Methods: `CASH` | `BANK_TRANSFER` (Razorpay payout deferred). Owner/Manager manual action.

**Verify:** Refund amount = remaining deposit after deductions; cannot over-refund; blocked when refundDue = 0.

**Commit:** `feat(api): deposit refund payout`

---

### Task 26: Billing worker role + fan-out

**Files:** `apps/worker/` Spring Boot app (`WORKER_ROLE=billing`); shares persistence/domain module with API.  
Triggered by Billing Trigger SQS (EventBridge 23:00 IST on the 1st). Local: internal test trigger **only in local/test**, never deployed.  
**Does not create invoices.** Idempotent billing-run key; **fans out** one Invoice-queue message per active stay (stay-level idempotency key).

**Verify:** Worker test with Testcontainers; duplicate trigger does not double-enqueue for same period/stay.

**Commit:** `feat(worker): monthly billing fan-out`

---

### Task 27: Invoice worker role + SQS consumer

**Files:** `apps/worker/` (`WORKER_ROLE=invoice`)  
Consumes Invoice SQS: create/finalize MONTHLY invoice for the stay; bind variable charges; due_date = billing_date + snapshotted `payment_due_days`.  
At-least-once: delete message only after success; uniqueness/idempotency protects money; txn timeout ? 5 min; DLQ after max receives.

**Verify:** Duplicate message does not double-post lines; DLQ path documented in README.

**Commit:** `feat(worker): invoice queue consumer`

---

## Phase 6 — Side features (API)

### Task 28: Expenses

**Flyway:** `V10__ops_side.sql` (expense_type, expense, notice, document, ticket)  
**Endpoints:** API §11 expense-types + expenses (GET/POST/PATCH/DELETE). Property-scoped authz.

**Verify:** Cross-property access ? 404/403; PATCH/DELETE work; delete expense-type with children ? 409/422.

**Commit:** `feat(api): property expenses`

---

### Task 29: Notices (Markdown)

**Endpoints:** CRUD + publish; `DELETE /notices/{id}`; statuses `DRAFT`/`PUBLISHED` only; sanitize/disable raw HTML on render path.

**Verify:** Script tags stripped or rejected in test; DELETE removes notice.

**Commit:** `feat(api): property notices`

---

### Task 30: Documents upload lifecycle

**Endpoints:** create intent `PENDING_UPLOAD` (presigned S3 URL), `POST .../complete` ? HeadObject ? `UPLOADED`, `DELETE` ? row + best-effort S3. No `ARCHIVED`.  
Local: **MinIO** in docker-compose (S3 API).

**Verify:** Complete without object ? fail; with object ? UPLOADED; DELETE removes metadata.

**Commit:** `feat(api): document upload lifecycle`

---

### Task 31: Tickets

**Endpoints:** list/create/get/patch; statuses `OPEN|IN_PROGRESS|RESOLVED|CLOSED`; assignee must be Owner or property Manager.

**Verify:** Manager only on assigned property; invalid assignee ? 422; deactivate unassigns open tickets.

**Commit:** `feat(api): property tickets`

---

### Task 32: Analytics + exports (read-only)

**Endpoints:** API §12 shapes — occupancy (now), revenue/expenses (range), CSV exports `tenants`|`expenses`|`invoices`. Derived only; no financial writes.

**Verify:** Smoke tests return 200 with locked shape; exports `Content-Type: text/csv`; do not mutate DB.

**Commit:** `feat(api): analytics and exports`

---

## Phase 7 — UI flows (Web)

> UI follows `StayFlow_UX_Flows_v5.0.md` + `StayFlow_UI_Style_Guide_v5.0.md` with **Dwellio** branding. Keep org ? property context visible. Prefer existing API; no new endpoints unless gap found (then fix API in a dedicated task first).

### Task 33: App shell — org/property switcher

**Files:** `apps/web/app/(app)/layout.tsx`, org/property selectors, empty property list state for Member with no assignments.

**Verify:** Manual: Member with zero assignments sees empty list (valid); Owner sees all properties.

**Commit:** `feat(web): org and property context shell`

---

### Task 34: Inventory UI — rooms, beds, availability badges, block/unblock

**Verify:** Badges AVAILABLE/OCCUPIED/BLOCKED; block requires reason; unblock confirm.

**Commit:** `feat(web): inventory and bed blocking UI`

---

### Task 35: Rent + services admin UI

**Verify:** Create property/room/bed rent; service catalog + tenancy enrollments from stay context.

**Commit:** `feat(web): rent and services UI`

---

### Task 36: Tenants + move-in stepper UI

**Stepper:** Tenant ? Bed ? Details ? Services ? Upfront ? Confirm & Pay / Record Payment.  
Copy: new stay creates **new Tenancy**; draft does not reserve bed.

**Verify:** Draft save/resume; successful payment shows ACTIVE occupancy; bed conflict forces reselect.

**Commit:** `feat(web): move-in stepper`

---

### Task 37: Transfer + checkout UIs (preview before commit)

**Verify:** Transfer preview shows destination rent proration (+ override); checkout preview separates unpaid / new charges / deposit / netReceivable / refundDue; explicit leave-receivable confirm; no partial pay; open receivables visible after checkout.

**Commit:** `feat(web): transfer and checkout flows`

---

### Task 38: Payments, invoices, deposit ledger UI

**Verify:** Labels Amount Due / Payment Confirmed / Refundable Balance / Refund Paid; ledger types only RECEIPT/DEDUCTION/REFUND.

**Commit:** `feat(web): financial views`

---

### Task 39: Tickets, notices, documents UI

**Verify:** Document statuses Pending upload ? Uploaded; delete works; notices Markdown draft/publish/delete; ticket status badges.

**Commit:** `feat(web): tickets notices documents UI`

---

## Phase 8 — Vercel + Route 53 DNS

### Task 40: Vercel project for `apps/web`

**Steps:**
1. Import monorepo; set Root Directory `apps/web`.
2. Env: Auth0 + `NEXT_PUBLIC_API_BASE_URL` (DEV API URL).
3. Production + Preview deployments enabled.

**Verify:** Vercel deploy succeeds; preview URL loads Dwellio login.

**Commit:** `chore(web): Vercel project config (vercel.json if needed)`

---

### Task 41: Route 53 subdomain ? Vercel

**Assumes:** Domain purchased in Route 53 / AWS.

**Steps:**
1. In Vercel: add domain `app.<your-domain>` (or chosen subdomain).
2. In Route 53 hosted zone: create CNAME (or ALIAS per Vercel docs) to Vercel DNS target.
3. Wait for TLS issued on Vercel.
4. Update Auth0 Allowed Callback/Logout/Web Origins to `https://app.<your-domain>/...`.
5. Document exact records in `docs/dns-vercel.md`.

**Verify:**
```bash
dig +short app.<your-domain> CNAME
curl -I https://app.<your-domain>   # 200/302, valid cert
# Login round-trip on production subdomain
```

**Commit:** `docs: Route 53 subdomain wiring for Vercel`

---

## Phase 9 — AWS Infrastructure (conceptual apply via Terraform in Phase 10)

### Task 42: Infra runbook aligned to Infra v5.0

**Files:** `docs/infrastructure-runbook.md`  
Document DEV/PROD capacity tables, SG rules, health check `/actuator/health`, SQS settings, S3 document bucket rules, DEV sleep/wake — **no new AWS services** beyond v5.

**Verify:** Checklist maps 1:1 to `projectDocs/v5/StayFlow_Infrastructure_v5.0.md`.

**Commit:** `docs: infrastructure runbook for Dwellio`

---

## Phase 10 — Terraform & CI

### Task 43: Terraform modules skeleton

**Files:** `terraform/modules/{vpc,alb,ecs,aurora,s3,sqs,iam,monitoring}/`, `terraform/environments/dev/`, `prod/`  
Remote state: S3 backend (bootstrap noted in README).  
ECS `ignore_changes` on `desired_count` for sleep/wake.

**Verify:**
```bash
cd terraform/environments/dev && terraform init && terraform validate
terraform fmt -check
```

**Commit:** `chore(terraform): module skeleton and dev env`

---

### Task 44: Terraform DEV core network + data + queues + buckets

Implement VPC `10.10.0.0/16`, ALB, Aurora Serverless v2 (0–2 ACU), SQS+DLQs, private S3 docs, Secrets Manager stubs, security groups per Infra v5.

**Verify:** `terraform plan` reviewable; apply only with explicit human approval.

**Commit:** `feat(terraform): dev VPC Aurora SQS S3 ALB`

---

### Task 45: Terraform ECS API + workers + GitHub OIDC

API service + billing/invoice ECS services (same worker image, different `WORKER_ROLE`); task sizes DEV 0.25 vCPU/512MB; IAM via GitHub OIDC (no long-lived keys in GitHub Secrets).

**Verify:** Plan shows 3 ECS services (api + billing + invoice); one worker ECR image; OIDC role trust limited to this repo.

**Commit:** `feat(terraform): ECS services and GitHub OIDC`

---

### Task 46: GitHub Actions — infra + app deploy

**Files:** `.github/workflows/terraform.yml`, `api-deploy.yml`  
Infra: PR ? fmt/validate/plan; main ? apply with approval.  
App: build API + worker images ? ECR ? ECS service update (worker image deployed to billing and invoice services with `WORKER_ROLE`).  
Web: Vercel Git integration (document; no duplicate deploy unless needed).

**Verify:** Workflow files validate (`actionlint` if available); dry-run docs in README.

**Commit:** `ci: terraform and API deploy workflows`

---

### Task 47: PROD env parity (Terraform)

PROD VPC `10.20.0.0/16`; capacity per Infra v5; sleep requires confirmation.

**Verify:** `terraform plan` for prod; no drift from capacity table.

**Commit:** `feat(terraform): prod environment`

---

## Phase 11 — Hardening

### Task 48: AuthZ regression suite

Matrix tests for Owner / Manager / Member-empty across properties, beds, tickets, financial writes. Cross-org IDOR attempts ? 404.

**Verify:** `./gradlew test --tests '*Authz*'` all green.

**Commit:** `test(api): authorization matrix regression`

---

### Task 49: End-to-end smoke (local)

Script `scripts/smoke-e2e.sh`: Auth0 test user ? create org ? property ? room ? bed ? rent ? tenant ? move-in ? pay (CASH) ? transfer ? checkout.

**Verify:** Script exits 0 against local docker + API + web API URL.

**Commit:** `test: local end-to-end smoke script`

---

### Task 50: Deployed DEV smoke

Against DEV API + `https://app.<domain>`: login, read `/me`, create org (or use seed), list properties.

**Verify:** Written checklist in `docs/dev-smoke.md` completed; note any gaps as follow-ups (do not silently skip).

**Commit:** `docs: DEV deployed smoke checklist results template`

---

## Execution rules (agents)

1. Work **Task N only**; do not start N+1 until Verify for N passes.
2. Read cited v5 sections before coding domain logic.
3. Brand strings: **Dwellio**.
4. No parallel agents; no speculative infra beyond v5.
5. Prefer TDD for domain money/stay invariants (Tasks 12, 15, 18, 23–25 especially).
6. Commit once per task after Verify (unless user asks otherwise).

## Spec coverage checklist (self-review)

| Area | Tasks |
|---|---|
| Org / members / managers / properties / rooms / beds | 7–11 |
| Bed block + availability | 12 |
| Rent | 13, 35 |
| Tenants / tenancy read / move-in | 14–16, 18, 36 |
| Payments / invoices / Razorpay / deposit | 17–21, 38 |
| Services | 22, 35 |
| Transfer / checkout / refund | 23–25, 37 |
| Billing + invoice workers | 26–27 |
| Expenses / notices / documents / tickets / analytics | 28–32, 39 |
| Auth0 | 4–6 |
| Vercel + Route 53 | 40–41 |
| Infra docs + Terraform + CI | 42–47 |
| Hardening | 48–50 |
| Branding Dwellio | 1, 6, 33–39 |
| Infra/Terraform “no new services” | 42–47 |

**Intentionally deferred (MVP exclusions from API §14):** Transfer table, partial payments, manual billing API, Viewer role, historical block API, taxes, Razorpay deposit payout.

---

## Handoff

Plan complete and saved to `docs/superpowers/plans/2026-09-18-dwellio-mvp.md`.

**Execution options (both sequential — no parallel agents):**

1. **Inline execution** — run tasks in this chat with checkpoints after each Verify  
2. **One-task-at-a-time subagent** — fresh agent per task, review between tasks (still serial)

Which approach, and shall we start at **Task 1**?
