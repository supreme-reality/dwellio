# Dwellio Web Phase 7 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` (inline, sequential) or `superpowers:subagent-driven-development`. Execute **one task at a time**. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Spec:** `docs/superpowers/specs/2026-09-19-dwellio-web-phase7-design.md`  
> **Domain:** `projectDocs/v5/` (UX Flows, UI Style Guide, API Contract)  
> **MVP map:** Tasks 33–39 in `docs/superpowers/plans/2026-09-18-dwellio-mvp.md`

**Goal:** Build the authenticated Dwellio Next.js app (org/property shell + workflow UIs) against the existing `/api/v1` Spring API.

**Architecture:** URL-first context `/o/[orgId]/p/[propertyId]/…`; App Router hybrid (RSC reads + client mutations); Tailwind local primitives; shared `lib/dwellio-api` client. Route-per-domain pages.

**Tech Stack:** Next.js 15 App Router, React 19, TypeScript, Tailwind 4, Auth0 Next.js SDK v4, Razorpay Checkout.js (client key only).

## Global Constraints

- Product brand in UI: **Dwellio** (not StayFlow).
- Prefer existing API; no new endpoints unless gap found (fix API in a dedicated task first).
- AuthZ: OWNER / MEMBER; property MANAGER; Member with zero assignments → empty property list (valid).
- Money labels and UX rules from UI Style Guide v5.0 / UX Flows v5.0.
- **Do not commit** unless the user explicitly asks (skip all Commit steps).
- After each task: `cd apps/web && npm run build` + that task’s Verify checklist.
- Visual: functional light neutrals; defer brand polish.

---

## File responsibility map

| Path | Responsibility |
|---|---|
| `apps/web/lib/dwellio-api/client.ts` | `apiFetch`, `DwellioApiError`, base URL |
| `apps/web/lib/dwellio-api/types.ts` | Shared TS types mirroring API responses |
| `apps/web/lib/dwellio-api/orgs.ts` | Organizations + members |
| `apps/web/lib/dwellio-api/properties.ts` | Properties + managers |
| `apps/web/lib/dwellio-api/inventory.ts` | Rooms, beds, block/unblock |
| `apps/web/lib/dwellio-api/rent.ts` | Rent config + resolve |
| `apps/web/lib/dwellio-api/services.ts` | Service catalog + tenancy enrollments |
| `apps/web/lib/dwellio-api/tenants.ts` | Tenants + stay history + tenancy get |
| `apps/web/lib/dwellio-api/moveIns.ts` | Move-in draft + payment |
| `apps/web/lib/dwellio-api/transfer.ts` | Transfer preview/execute |
| `apps/web/lib/dwellio-api/checkout.ts` | Checkout preview/confirm/refund |
| `apps/web/lib/dwellio-api/finance.ts` | Payments, invoices, deposits |
| `apps/web/lib/dwellio-api/tickets.ts` | Tickets |
| `apps/web/lib/dwellio-api/notices.ts` | Notices |
| `apps/web/lib/dwellio-api/documents.ts` | Documents upload lifecycle |
| `apps/web/lib/dwellio-api/expenses.ts` | Expense types + expenses |
| `apps/web/lib/dwellio-api/analytics.ts` | Analytics reads |
| `apps/web/lib/dwellio-api/exports.ts` | CSV exports |
| `apps/web/lib/auth.ts` | `requireSession`, `getAccessToken` helpers |
| `apps/web/lib/format.ts` | Currency/date/status formatters |
| `apps/web/components/ui/*` | Button, Input, Select, Textarea, Badge, Modal, ConfirmDialog, Alert, EmptyState, Stepper, PreviewPanel |
| `apps/web/components/shell/*` | AppShell, AuthHeader, OrgSwitcher, PropertySwitcher, SidebarNav |
| `apps/web/components/markdown/*` | Light MD toolbar + preview |
| `apps/web/app/(app)/layout.tsx` | Auth gate for app routes |
| `apps/web/app/(app)/onboarding/page.tsx` | Create organization (AuthHeader + Log out) |
| `apps/web/app/(app)/o/[orgId]/layout.tsx` | Org context load |
| `apps/web/app/(app)/o/[orgId]/page.tsx` | Org home / create property |
| `apps/web/app/(app)/o/[orgId]/p/[propertyId]/layout.tsx` | Property shell + sidebar |
| `apps/web/app/(app)/o/[orgId]/p/[propertyId]/*/page.tsx` | Domain pages |
| `apps/web/app/page.tsx` | Public login; redirect signed-in users into app |

---

### Task 1: API client core + UI primitives + auth helpers (foundation for Task 33)

**Files:**
- Create: `apps/web/lib/dwellio-api/client.ts`
- Create: `apps/web/lib/dwellio-api/types.ts`
- Create: `apps/web/lib/auth.ts`
- Create: `apps/web/lib/format.ts`
- Create: `apps/web/components/ui/button.tsx`, `input.tsx`, `select.tsx`, `textarea.tsx`, `badge.tsx`, `modal.tsx`, `confirm-dialog.tsx`, `alert.tsx`, `empty-state.tsx`
- Modify: `apps/web/lib/dwellio-api.ts` → re-export from new modules or delete after migration of `fetchMe`

**Interfaces:**
- Consumes: Auth0 `auth0.getSession()` / `auth0.getAccessToken()`; `NEXT_PUBLIC_API_BASE_URL`
- Produces:
  - `apiFetch<T>(path, { method?, body?, token }): Promise<T>`
  - `class DwellioApiError extends Error { status: number; code?: string; requestId?: string }`
  - `requireAccessToken(): Promise<string>` (throws/redirects if unauthenticated)
  - Types: `Organization`, `Property`, `Me` matching API JSON (UUID as `string`)

- [ ] **Step 1: Add `DwellioApiError` + `apiFetch`**

```typescript
// apps/web/lib/dwellio-api/client.ts
export class DwellioApiError extends Error {
  constructor(
    message: string,
    public status: number,
    public code?: string,
    public requestId?: string,
  ) {
    super(message);
    this.name = "DwellioApiError";
  }
}

type ApiFetchOptions = {
  method?: string;
  body?: unknown;
  token: string;
};

export async function apiFetch<T>(path: string, options: ApiFetchOptions): Promise<T> {
  const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL;
  if (!baseUrl) throw new Error("NEXT_PUBLIC_API_BASE_URL is not configured");

  const response = await fetch(`${baseUrl}${path}`, {
    method: options.method ?? "GET",
    headers: {
      Authorization: `Bearer ${options.token}`,
      Accept: "application/json",
      ...(options.body !== undefined ? { "Content-Type": "application/json" } : {}),
    },
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    cache: "no-store",
  });

  if (!response.ok) {
    let code: string | undefined;
    let message = `Request failed (${response.status})`;
    let requestId: string | undefined;
    try {
      const json = (await response.json()) as {
        error?: { code?: string; message?: string; requestId?: string };
      };
      code = json.error?.code;
      message = json.error?.message ?? message;
      requestId = json.error?.requestId;
    } catch {
      /* ignore non-JSON */
    }
    throw new DwellioApiError(message, response.status, code, requestId);
  }

  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}
```

- [ ] **Step 2: Add core types + `fetchMe` in types/orgs migration**

```typescript
// apps/web/lib/dwellio-api/types.ts
export type Me = { id: string; email: string; name: string; status: string };

export type Organization = {
  id: string;
  name: string;
  status: string;
  role: string; // OWNER | MEMBER
};

export type Property = {
  id: string;
  organizationId: string;
  name: string;
  address: string | null;
  status: string;
  paymentDueDays: number;
  defaultCurrency: string;
};
```

Keep Jackson/Spring default camelCase field names as returned by API.

- [ ] **Step 3: Auth helper**

```typescript
// apps/web/lib/auth.ts
import { redirect } from "next/navigation";
import { auth0 } from "@/lib/auth0";

export async function requireSession() {
  const session = await auth0.getSession();
  if (!session) redirect("/auth/login");
  return session;
}

export async function requireAccessToken(): Promise<string> {
  await requireSession();
  const { token } = await auth0.getAccessToken();
  if (!token) redirect("/auth/login");
  return token;
}
```

- [ ] **Step 4: Minimal UI primitives**

Implement thin Tailwind components:
- `Button` — variants `primary` | `secondary` | `danger`; `type`, `disabled`, `onClick`
- `Input` / `Select` / `Textarea` — label + error message props
- `Badge` — children text required (status not color-only)
- `Modal` — title, children, onClose
- `ConfirmDialog` — title, body, confirmLabel, onConfirm, onCancel
- `Alert` — tone `error` | `info`; show `requestId` if provided
- `EmptyState` — title + description + optional action slot

- [ ] **Step 5: Verify foundation builds**

Run: `cd apps/web && npm run build`  
Expected: exit 0

- [ ] **Step 6: Commit** — SKIP unless user asks

---

### Task 2: App shell — org/property switcher + onboarding + admin (MVP Task 33)

**Files:**
- Create: `apps/web/lib/dwellio-api/orgs.ts`
- Create: `apps/web/lib/dwellio-api/properties.ts`
- Create: `apps/web/components/shell/app-shell.tsx`
- Create: `apps/web/components/shell/auth-header.tsx`
- Create: `apps/web/components/shell/org-switcher.tsx`
- Create: `apps/web/components/shell/property-switcher.tsx`
- Create: `apps/web/components/shell/sidebar-nav.tsx`
- Create: `apps/web/app/(app)/layout.tsx`
- Create: `apps/web/app/(app)/onboarding/page.tsx`
- Create: `apps/web/app/(app)/o/[orgId]/layout.tsx`
- Create: `apps/web/app/(app)/o/[orgId]/page.tsx`
- Create: `apps/web/app/(app)/o/[orgId]/p/[propertyId]/layout.tsx`
- Create: `apps/web/app/(app)/o/[orgId]/p/[propertyId]/inventory/page.tsx` (placeholder “Inventory coming in Task 34” OK only if labeled; prefer empty inventory stub that lists “No rooms yet”)
- Create: settings pages under `…/settings/{members,managers,property}/page.tsx`
- Create: client forms for create org/property/members as needed
- Modify: `apps/web/app/page.tsx` — signed-in users redirect to first org/property or `/onboarding`

**Interfaces:**
- Consumes: `apiFetch`, `requireAccessToken`, `Organization`, `Property`
- Produces:
  - `listOrganizations(token): Promise<Organization[]>`
  - `createOrganization(token, { name }): Promise<Organization>`
  - `listProperties(token, orgId): Promise<Property[]>`
  - `createProperty(token, orgId, { name, address?, paymentDueDays, defaultCurrency }): Promise<Property>`
  - Shell reads `params.orgId` / `params.propertyId`

- [ ] **Step 1: Implement org/property API modules**

```typescript
// apps/web/lib/dwellio-api/orgs.ts
import { apiFetch } from "./client";
import type { Organization } from "./types";

export function listOrganizations(token: string) {
  return apiFetch<Organization[]>("/api/v1/organizations", { token });
}

export function createOrganization(token: string, body: { name: string }) {
  return apiFetch<Organization>("/api/v1/organizations", {
    method: "POST",
    token,
    body,
  });
}

export function getOrganization(token: string, organizationId: string) {
  return apiFetch<Organization>(`/api/v1/organizations/${organizationId}`, { token });
}
```

```typescript
// apps/web/lib/dwellio-api/properties.ts
import { apiFetch } from "./client";
import type { Property } from "./types";

export function listProperties(token: string, organizationId: string) {
  return apiFetch<Property[]>(
    `/api/v1/organizations/${organizationId}/properties`,
    { token },
  );
}

export function createProperty(
  token: string,
  organizationId: string,
  body: {
    name: string;
    address?: string;
    paymentDueDays: number;
    defaultCurrency: string;
  },
) {
  return apiFetch<Property>(
    `/api/v1/organizations/${organizationId}/properties`,
    { method: "POST", token, body },
  );
}

export function getProperty(token: string, propertyId: string) {
  return apiFetch<Property>(`/api/v1/properties/${propertyId}`, { token });
}
```

Also add members/managers/update property functions matching:
- `GET/POST /api/v1/organizations/{orgId}/members`
- `PATCH /api/v1/organizations/{orgId}/members/{membershipId}`
- `GET/POST/DELETE /api/v1/properties/{propertyId}/managers`
- `PATCH /api/v1/properties/{propertyId}`

Read Java request records for exact field names before coding forms.

- [ ] **Step 2: `(app)/layout.tsx` auth gate**

```tsx
import { requireSession } from "@/lib/auth";

export default async function AppLayout({ children }: { children: React.ReactNode }) {
  await requireSession();
  return <>{children}</>;
}
```

- [ ] **Step 3: Onboarding page — create organization**

Client form posts via server action or client fetch-through server action that uses `requireAccessToken` + `createOrganization`. On success `redirect(/o/{id})`. Use `AuthHeader` (user + Log out); do not wrap in full `AppShell`.

- [ ] **Step 4: Org page — list/create property + empty Member state**

- Owner: show properties + “Create property” form (`name`, `address`, `paymentDueDays`, `defaultCurrency` default `INR`).
- Member with `properties.length === 0`: `EmptyState` title “No properties assigned” (valid).
- Click property → `/o/{orgId}/p/{propertyId}/inventory`.

- [ ] **Step 5: Property layout shell**

`AppShell` with:
- Dwellio brand link
- `OrgSwitcher` (list orgs; navigating changes `/o/...`)
- `PropertySwitcher` (list properties for org; empty list message for none)
- `SidebarNav` groups Stay | Money | Ops | Insights | Admin(if role OWNER)
- Logout link `/auth/logout`

Stub sidebar hrefs to routes that will exist in later tasks; for missing pages, create minimal placeholder pages that say the feature name (so nav does not 404). Prefer creating empty route stubs in this task for all sidebar links.

- [ ] **Step 6: Home redirect for signed-in users**

In `app/page.tsx`, if session: list orgs → if none `/onboarding`; else list properties for first org → if any, redirect to `…/inventory`; else `/o/{orgId}`.

- [ ] **Step 7: Owner settings pages**

Minimal working UI:
- Members: list + add by email/role + deactivate
- Managers: list + assign org member + remove
- Property: edit name, address, paymentDueDays, defaultCurrency

- [ ] **Step 8: Verify Task 33**

Run: `cd apps/web && npm run build`  
Expected: exit 0  

Manual checklist:
- [ ] Member with zero assignments sees empty property list (valid)
- [ ] Owner sees all properties
- [ ] Org + property create works when API is up

- [ ] **Step 9: Commit** — SKIP unless user asks

---

### Task 3: Inventory UI (MVP Task 34)

**Files:**
- Create: `apps/web/lib/dwellio-api/inventory.ts`
- Modify: `apps/web/app/(app)/o/[orgId]/p/[propertyId]/inventory/page.tsx`
- Create: client components for room/bed forms, block modal, unblock confirm

**Interfaces:**
- Consumes: `apiFetch`, propertyId from params
- Produces: list rooms, list beds (property-level), create/patch room/bed, `blockBed(bedId, { reason })`, `unblockBed(bedId)`
- Bed type includes `availability: "AVAILABLE" | "OCCUPIED" | "BLOCKED"` and `blockReason`

- [ ] **Step 1: Inventory API module** — map to `/properties/{id}/rooms`, `/rooms/{id}/beds`, `/properties/{id}/beds`, `/beds/{id}/block|unblock`
- [ ] **Step 2: Inventory page** — rooms with beds; `Badge` text AVAILABLE/OCCUPIED/BLOCKED; blocked shows reason
- [ ] **Step 3: Block modal** — reason required; Unblock uses `ConfirmDialog`
- [ ] **Step 4: Create/edit room and bed forms**
- [ ] **Step 5: Verify** — `npm run build` + badges/block/unblock checklist
- [ ] **Step 6: Commit** — SKIP

---

### Task 4: Rent + services UI (MVP Task 35)

**Files:**
- Create: `apps/web/lib/dwellio-api/rent.ts`, `services.ts`
- Create/modify: `…/rent/page.tsx`, `…/services/page.tsx`
- Modify: tenancy stay page section for enrollments (may stub tenancy page until Task 5; if so, services catalog only here and enrollments in Task 5)

**Preferred:** catalog + rent fully in this task; tenancy enrollment UI on `…/tenancies/[tenancyId]` created here with read of stay context so Verify (“enrollments from stay context”) passes.

- [ ] **Step 1: Rent API + page** — property/room/bed rent create; show effective vs scheduled; resolve helper
- [ ] **Step 2: Services catalog API + page**
- [ ] **Step 3: Tenancy services enroll/end/charge on stay page**
- [ ] **Step 4: Verify** — `npm run build` + Task 35 checklist
- [ ] **Step 5: Commit** — SKIP

---

### Task 5: Tenants + move-in stepper (MVP Task 36)

**Files:**
- Create: `apps/web/lib/dwellio-api/tenants.ts`, `moveIns.ts`
- Create: `…/tenants/page.tsx`, `…/tenants/[tenantId]/page.tsx`
- Create: `…/move-ins/new/page.tsx`, `…/move-ins/[moveInId]/page.tsx`
- Create: `apps/web/components/move-in/move-in-stepper.tsx` (client)
- Create: Razorpay helper `apps/web/lib/razorpay.ts` loading Checkout.js when `NEXT_PUBLIC_RAZORPAY_KEY_ID` set

**Stepper steps:** Tenant → Bed → Details → Services → Upfront → Confirm & Pay / Record Payment  

**Copy requirements:** “new stay creates a new Tenancy”; “draft does not reserve a bed”.

- [ ] **Step 1: Tenant list/create/detail + stay history**
- [ ] **Step 2: Move-in create draft + PATCH + cancel APIs**
- [ ] **Step 3: Stepper UI with draft resume**
- [ ] **Step 4: Payment** — CASH / BANK_TRANSFER / RAZORPAY; on success show ACTIVE occupancy
- [ ] **Step 5: Bed conflict handling** — force reselect
- [ ] **Step 6: Verify** — `npm run build` + draft resume / ACTIVE / conflict checklist
- [ ] **Step 7: Commit** — SKIP

---

### Task 6: Transfer + checkout UIs (MVP Task 37)

**Files:**
- Create: `apps/web/lib/dwellio-api/transfer.ts`, `checkout.ts`
- Create: `…/tenancies/[tenancyId]/transfer/page.tsx`
- Create: `…/tenancies/[tenancyId]/checkout/page.tsx`
- Use `PreviewPanel` for calculated amounts vs confirm result

- [ ] **Step 1: Transfer preview + execute UI** (override field; services informational; no refund controls)
- [ ] **Step 2: Checkout preview breakdown UI** (unpaid / new charges / deposit / netReceivable / refundDue)
- [ ] **Step 3: Confirm** — full pay or explicit leave-receivable; no partial
- [ ] **Step 4: Post-checkout open receivables + refund when refundDue > 0
- [ ] **Step 5: Verify** — `npm run build` + Task 37 checklist
- [ ] **Step 6: Commit** — SKIP

---

### Task 7: Payments, invoices, deposit ledger (MVP Task 38)

**Files:**
- Create: `apps/web/lib/dwellio-api/finance.ts`
- Create: `…/finance/page.tsx` (tabs: Invoices | Payments | Deposit by tenancy link)

**Labels required:** Amount Due, Payment Confirmed, Refundable Balance, Refund Paid  
**Ledger types only:** RECEIPT, DEDUCTION, REFUND

- [ ] **Step 1: Invoice list/detail reads**
- [ ] **Step 2: Payment views + confirm manual where API allows**
- [ ] **Step 3: Deposit summary + ledger**
- [ ] **Step 4: Verify** — `npm run build` + label/ledger checklist
- [ ] **Step 5: Commit** — SKIP

---

### Task 8: Tickets, notices, documents (MVP Task 39)

**Files:**
- Create: `apps/web/lib/dwellio-api/tickets.ts`, `notices.ts`, `documents.ts`
- Create: `…/tickets/page.tsx`, `…/tickets/[ticketId]/page.tsx`
- Create: `…/notices/page.tsx`
- Create: `…/documents/page.tsx`
- Create: `apps/web/components/markdown/markdown-editor.tsx` (toolbar + preview)

- [ ] **Step 1: Tickets list/detail with status badges**
- [ ] **Step 2: Notices draft/publish/delete + light MD editor**
- [ ] **Step 3: Documents** — Pending upload → upload PUT → complete → delete
- [ ] **Step 4: Verify** — `npm run build` + Task 39 checklist
- [ ] **Step 5: Commit** — SKIP

---

### Task 9: Expenses, analytics, exports (post-39)

**Files:**
- Create: `apps/web/lib/dwellio-api/expenses.ts`, `analytics.ts`, `exports.ts`
- Create: `…/expenses/page.tsx`, `…/analytics/page.tsx`, `…/exports/page.tsx`

- [ ] **Step 1: Expenses types + CRUD**
- [ ] **Step 2: Analytics cards + date range**
- [ ] **Step 3: Export CSV download buttons** (`tenants` | `expenses` | `invoices`)
- [ ] **Step 4: Verify** — `npm run build` + smoke
- [ ] **Step 5: Commit** — SKIP

---

## Plan self-review

| Spec section | Covered by |
|---|---|
| §3 routing / shell | Task 2 |
| §3 API client / primitives | Task 1 |
| §4 Task 33–35 | Tasks 2–4 |
| §4 Task 36–38 | Tasks 5–7 |
| §4 Task 39 + post-39 | Tasks 8–9 |
| §5 errors / verify / non-goals | Global Constraints + per-task Verify |

No intentional placeholders. Commit steps explicitly skipped per user rule.

---

## Execution

Sequential inline execution in this chat (user choice: sequential, no commits). Start at **Task 1**, then Task 2 (= MVP 33), and stop after each task’s Verify/`npm run build` before continuing.
