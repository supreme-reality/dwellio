# Dwellio Web Phase 7 Design (Tasks 33–39 + post-39 ops)

**Date:** 2026-09-19  
**Status:** Draft for review (not committed by request)  
**Product brand:** Dwellio  
**Domain source of truth:** `projectDocs/v5/` (UX Flows v5.0, UI Style Guide v5.0, API Contract v5.0)  
**Implementation plan reference:** `docs/superpowers/plans/2026-09-18-dwellio-mvp.md` Phase 7 (Tasks 33–39)

---

## 1. Goals

Build the authenticated Dwellio web app so operators can run property workflows against the existing Spring API:

- Org → property context always visible
- Inventory, rent/services, tenants/move-in, transfer/checkout, finance, tickets/notices/documents
- After Task 39 in the same delivery pass: expenses, analytics, exports
- Prefer existing API endpoints; no new endpoints unless a gap is found (then fix API in a dedicated task first)

**Non-goals (MVP UI exclusions from UX v5.0):** transfer history resource UI; bed-tied services; meters; ALLOCATION; transfer refund controls; Complete Move-In; partial payments; manual invoice allocation; Run Billing; notice targeting; archive flows; taxes; Razorpay deposit payout.

---

## 2. Locked decisions

| Topic | Decision |
|---|---|
| Delivery | Design/spec first, then sequential task implementation; **no git commits** unless user asks |
| Architecture | Route-per-domain (Approach 1) |
| UI stack | Tailwind + local primitives only (no shadcn) |
| Visual | Functional first (clean light neutrals); brand polish later |
| Context | URL-first: `/o/[orgId]/p/[propertyId]/…` |
| Payments | CASH + BANK_TRANSFER + RAZORPAY (Checkout.js; key via env when available) |
| Data fetching | Hybrid: RSC for reads; client for mutations / stepper / Razorpay |
| Org bootstrap | Create org + create property in Task 33 |
| Owner admin | Members, managers, property settings under Admin |
| Move-in drafts | List + deep link `/move-ins/[moveInId]` |
| Documents | Full lifecycle: intent → upload → complete → delete |
| Notices | Light Markdown helper editor (toolbar) + live preview |
| Verify | After each task: `npm run build` + that task’s Verify checklist from MVP plan |
| Post-39 | Expenses, analytics, exports in same pass after Task 39 |

---

## 3. Architecture

### 3.1 Routing

```
/                                 # public login/marketing (existing)
/onboarding                       # create organization (no org yet)
/o/[orgId]                        # org home; empty property / create property
/o/[orgId]/p/[propertyId]/inventory
/o/[orgId]/p/[propertyId]/tenants
/o/[orgId]/p/[propertyId]/tenants/[tenantId]
/o/[orgId]/p/[propertyId]/tenancies/[tenancyId]
/o/[orgId]/p/[propertyId]/move-ins/new
/o/[orgId]/p/[propertyId]/move-ins/[moveInId]
/o/[orgId]/p/[propertyId]/rent
/o/[orgId]/p/[propertyId]/services
/o/[orgId]/p/[propertyId]/finance
/o/[orgId]/p/[propertyId]/tickets
/o/[orgId]/p/[propertyId]/tickets/[ticketId]
/o/[orgId]/p/[propertyId]/notices
/o/[orgId]/p/[propertyId]/documents
/o/[orgId]/p/[propertyId]/expenses
/o/[orgId]/p/[propertyId]/analytics
/o/[orgId]/p/[propertyId]/exports
/o/[orgId]/p/[propertyId]/settings/members
/o/[orgId]/p/[propertyId]/settings/managers
/o/[orgId]/p/[propertyId]/settings/property
```

Transfer and checkout are flows launched from tenancy/stay context (dedicated sub-routes or modal+page with preview is acceptable; prefer dedicated routes for preview shareability):

- `…/tenancies/[tenancyId]/transfer`
- `…/tenancies/[tenancyId]/checkout`

### 3.2 App shell

Authenticated `(app)` layout:

- Brand: **Dwellio**
- Org switcher + property switcher
- Sidebar nav groups under property context:
  - **Stay:** Inventory, Tenants, Move-in (entry to drafts/new)
  - **Money:** Rent, Services, Finance, Expenses
  - **Ops:** Tickets, Notices, Documents
  - **Insights:** Analytics, Exports
  - **Admin (Owner):** Members, Managers, Property settings
- User identity + Log out (`AuthHeader`; also on onboarding, org home, and signed-in error screens — no switchers/sidebar there)
- Member with zero property assignments: empty property list (valid, not an error)

**Default landing after login:** first org → first visible property → `/inventory`; else onboarding or org empty-property state.

### 3.3 Data layer

`apps/web/lib/dwellio-api/`:

- Shared HTTP helper (`apiFetch`): `NEXT_PUBLIC_API_BASE_URL`, Bearer token, JSON, structured errors (`status`, `message`, `requestId` when present)
- Domain modules: orgs, properties, inventory, rent, services, tenants, moveIns, transfer, checkout, payments, invoices, deposits, tickets, notices, documents, expenses, analytics, exports
- RSC pages/layouts call modules with Auth0 access token
- Client mutations use the same helper via server actions or thin authenticated helpers (avoid scattering token logic)

Money/date formatting: property `default_currency`; consistent `YYYY-MM-DD` / ISO display. No partial-pay or allocation UI.

### 3.4 UI primitives

Local Tailwind components only:

- `Button`, `Input`, `Select`, `Textarea`, `Badge`, `Modal` / `ConfirmDialog`, `Alert`, `EmptyState`, `Stepper`, `PreviewPanel`
- Notices: Markdown toolbar (insert bold/italic/link/list markers) + live preview pane
- Status must not rely on color alone (badge text required)

### 3.5 Env

- Existing Auth0 + `NEXT_PUBLIC_API_BASE_URL`
- Add `NEXT_PUBLIC_RAZORPAY_KEY_ID` when Razorpay Checkout is used (secrets remain on API)
- Document upload uses API-returned upload URL (presigned / MinIO-compatible)

---

## 4. Screen specs by task

### Task 33 — App shell + org/property bootstrap

- Wire Auth0-gated `(app)` layout, switchers, sidebar, empty states
- Onboarding: create organization → create first property → inventory
- Owner Admin: members, manager assign/remove, property settings (`default_currency`, `payment_due_days`, name)
- **Verify:** Member with zero assignments sees empty list; Owner sees all properties. `npm run build`

### Task 34 — Inventory

- Rooms/beds list; badges `AVAILABLE` / `OCCUPIED` / `BLOCKED`
- Block requires reason; blocked beds show reason; Unblock confirm
- Room/bed create/update per authz (Owner/Manager)
- **Verify:** Badges correct; block requires reason; unblock confirm. `npm run build`

### Task 35 — Rent + services

- Rent hierarchy Property → Room → Bed; effective vs scheduled; resolve by bed+date
- Property service catalog + config
- Tenancy enrollments from stay context (not bed-tied); end / variable charge as API allows
- **Verify:** Create property/room/bed rent; catalog + tenancy enrollments from stay context. `npm run build`

### Task 36 — Tenants + move-in stepper

- Tenant list/create/detail; tenancies with active stay distinguished
- Stepper: Tenant → Bed → Details → Services → Upfront → Confirm & Pay / Record Payment
- Copy: new stay creates **new Tenancy**; draft does not reserve bed
- Draft save/resume via `/move-ins/[moveInId]`
- Payments: CASH / BANK_TRANSFER / RAZORPAY
- Success → ACTIVE occupancy; bed conflict → reselect
- No second Complete Move-In
- **Verify:** Draft save/resume; successful payment shows ACTIVE occupancy; bed conflict forces reselect. `npm run build`

### Task 37 — Transfer + checkout

- Transfer: same-property AVAILABLE bed → PreviewPanel (destination rent proration, optional override, current services informational) → confirm; deposit stays; no transfer refund UI
- Checkout: PreviewPanel separates unpaid / new charges / deposit application / netReceivable / refundDue; pay full net **or** explicit leave-receivable; no partial; show open receivables after checkout; refund when refundDue > 0 (CASH / BANK_TRANSFER)
- **Verify:** Preview contents and leave-receivable / no partial / open receivables. `npm run build`

### Task 38 — Finance views

- Invoices, payments, deposit ledger under Finance
- Labels: Amount Due / Payment Confirmed / Refundable Balance / Refund Paid
- Ledger types only RECEIPT / DEDUCTION / REFUND
- No Run Billing / allocation / partial pay controls
- **Verify:** Labels and ledger types. `npm run build`

### Task 39 — Tickets, notices, documents

- Tickets: list/detail; badges Open / In progress / Resolved / Closed
- Notices: light MD editor + preview; draft / publish / delete
- Documents: Pending upload → Uploaded; upload lifecycle; delete
- **Verify:** Document statuses + delete; notices draft/publish/delete; ticket badges. `npm run build`

### Post-39 — Expenses, analytics, exports

- Expenses: types + expenses CRUD
- Analytics: occupancy (point-in-time) + revenue/expenses (range)
- Exports: CSV for tenants / expenses / invoices
- **Verify:** `npm run build` + smoke against API contract fields

---

## 5. Error handling

- Surface API error message; include `requestId` when available
- Bed unavailable/blocked: actionable copy requiring new selection (never silent substitute)
- Empty states for no org / no property / no assignments / empty collections — no fake placeholder data

---

## 6. Execution rules

1. Implement **one task at a time** in order: 33 → 34 → 35 → 36 → 37 → 38 → 39 → post-39
2. After each task: run `npm run build` and complete that task’s Verify checklist
3. Do **not** commit unless the user explicitly asks
4. If an API gap blocks a screen: stop, document the gap, fix API in a dedicated change before continuing UI

---

## 7. Success criteria

Phase 7 is done when an authenticated Owner/Manager can, through the web UI against a running API:

1. Create org/property (Owner), switch context, see correct empty states for Members
2. Manage inventory with availability badges and block/unblock
3. Configure rent and services; enroll services on a tenancy
4. Run move-in through payment to ACTIVE occupancy (including draft resume)
5. Transfer and checkout with preview-before-commit and correct money rules
6. View invoices/payments/deposit with required labels
7. Manage tickets, notices, documents
8. Use expenses, analytics, and exports pages

---

## 8. Open items (non-blocking)

- Exact Razorpay order/checkout field mapping follows API response at implementation time
- Transfer/checkout may use dedicated routes or stay-scoped pages; prefer dedicated routes if preview state is large
- Visual brand pass deferred (decision 4C)
