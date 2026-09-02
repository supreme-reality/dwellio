# StayFlow — Database Schema v5.0

**Implementation-ready relational model · PostgreSQL / Aurora PostgreSQL**  
**Canonical pack:** Project Context v5.0 · Functional Requirements v5.0 · API Contract v5.0 · Architecture v5.0 · UX Flows v5.0 · UI Style Guide v5.0  
**Infrastructure / Terraform:** v5.0 alignment-only (no material AWS/IaC change from v1.3 / v1.2)

Supersedes DB Schema v1.5. Incorporates tickets, bed blocking, tenant organization scoping, and the canonical document upload lifecycle.

---

## 0. Conventions

| Concern | Rule |
|---|---|
| Money | `NUMERIC(12,2)` — never floating point |
| Identifiers | UUID, application-generated |
| Timestamps | `TIMESTAMPTZ` |
| Business dates | `DATE` |
| Financial history | Finalized invoices, confirmed payments, finalized deposit ledger rows, and confirmed settlement snapshots are immutable |

---

## 1. Core model

- **Property hierarchy:** Organization → Property ? Room ? Bed  
- **Stay hierarchy:** Tenant → Tenancy ? Occupancy ? Bed  
- Every new stay creates a new Tenancy; closed Tenancy is never reopened.  
- At most one `ACTIVE` Occupancy per Tenancy and per Bed.  
- DRAFT/CANCELLED Move-In does not reserve a Bed and does not create an `ACTIVE` Occupancy.  
- Transfer is an application operation (no Transfer table).  
- Organization is the strict SaaS boundary.

---

## 2. Identity, organization & access

### 2.1 `organization`

| Column | Type | Null | Notes |
|---|---|---|---|
| id | UUID | NO | PK |
| name | VARCHAR(160) | NO | |
| status | VARCHAR(20) | NO | `ACTIVE` / `INACTIVE` |
| created_at | TIMESTAMPTZ | NO | |
| updated_at | TIMESTAMPTZ | NO | |

### 2.2 `app_user`

| Column | Type | Null | Notes |
|---|---|---|---|
| id | UUID | NO | PK |
| email | VARCHAR(320) | NO | Unique |
| name | VARCHAR(160) | NO | |
| status | VARCHAR(20) | NO | `ACTIVE` / `INACTIVE` |
| created_at | TIMESTAMPTZ | NO | |
| updated_at | TIMESTAMPTZ | NO | |

Auth secrets are outside this schema unless the chosen auth stack requires them.

### 2.3 `organization_membership`

| Column | Type | Null | Notes |
|---|---|---|---|
| id | UUID | NO | PK |
| organization_id | UUID | NO | FK → organization.id |
| user_id | UUID | NO | FK → app_user.id |
| role | VARCHAR(20) | NO | **`OWNER` / `MEMBER`** |
| status | VARCHAR(20) | NO | `ACTIVE` / `INACTIVE` |
| created_at | TIMESTAMPTZ | NO | |
| updated_at | TIMESTAMPTZ | NO | |

- Unique `(organization_id, user_id)`.  
- **`OWNER`:** organization-wide access to all properties and admin operations (no `property_membership` required).  
- **`MEMBER`:** belongs to the organization; **no property access until** the Owner assigns them via `property_membership`. A MEMBER with zero property assignments may authenticate and see the org with an empty property list.  
- **Viewer is out of MVP** — do not store `VIEWER`.  
- Only an Owner may create/deactivate organization memberships (except bootstrap of the first Owner when the organization is created).

### 2.4 `property_membership`

| Column | Type | Null | Notes |
|---|---|---|---|
| id | UUID | NO | PK |
| organization_membership_id | UUID | NO | FK → organization_membership.id (must be a `MEMBER`, not Owner) |
| property_id | UUID | NO | FK → property.id |
| role | VARCHAR(20) | NO | **`MANAGER` only in MVP** |
| created_at | TIMESTAMPTZ | NO | |
| updated_at | TIMESTAMPTZ | NO | |

- Unique `(organization_membership_id, property_id)`.  
- Only an **Owner** may create/remove property memberships.  
- Managers **cannot** add/remove/**update** properties, add organization members, or assign other managers to properties.  
- Managers **may** create/update rooms and beds on properties they are assigned to; Owners may for any property.  
- Owner does **not** need rows here.  
- When an org membership is set `INACTIVE`, **delete** all `property_membership` rows for that membership (no status column on this table). Reactivate does not restore assignments.


---

## 3. Property & inventory

### 3.1 `property`

| Column | Type | Null | Notes |
|---|---|---|---|
| id | UUID | NO | PK |
| organization_id | UUID | NO | FK → organization.id |
| name | VARCHAR(160) | NO | |
| address | TEXT | YES | |
| status | VARCHAR(20) | NO | `ACTIVE` / `INACTIVE` |
| payment_due_days | INTEGER | NO | `>= 0`; used to snapshot invoice `due_date` |
| default_currency | CHAR(3) | NO | ISO currency (e.g. `INR`) |
| created_at | TIMESTAMPTZ | NO | |
| updated_at | TIMESTAMPTZ | NO | |

### 3.2 `room`

| Column | Type | Null | Notes |
|---|---|---|---|
| id | UUID | NO | PK |
| property_id | UUID | NO | FK → property.id |
| name | VARCHAR(100) | NO | Unique per property |
| status | VARCHAR(20) | NO | `ACTIVE` / `INACTIVE` |
| created_at | TIMESTAMPTZ | NO | |
| updated_at | TIMESTAMPTZ | NO | |

### 3.3 `bed`

| Column | Type | Null | Notes |
|---|---|---|---|
| id | UUID | NO | PK |
| room_id | UUID | NO | FK → room.id |
| name | VARCHAR(100) | NO | Unique per room |
| status | VARCHAR(20) | NO | Catalog: `ACTIVE` / `INACTIVE` |
| block_reason | TEXT | YES | **Non-NULL ⇒ bed is BLOCKED** (v5) |
| blocked_at | TIMESTAMPTZ | YES | Set when blocked; NULL when unblocked |
| blocked_by_user_id | UUID | YES | FK → app_user.id |
| created_at | TIMESTAMPTZ | NO | |
| updated_at | TIMESTAMPTZ | NO | |

**Derived availability (canonical API/FR values):**

| Availability | Rule |
|---|---|
| `BLOCKED` | `block_reason IS NOT NULL` |
| `OCCUPIED` | not blocked AND exists Occupancy with `status = ACTIVE` for this bed |
| `AVAILABLE` | otherwise **and** bed catalog `status = ACTIVE` |

- Derived availability is returned only for catalog-`ACTIVE` beds. Inventory list/get **omit** `INACTIVE` beds (`GET /beds/{id}` → 404).  
- Setting catalog `status = INACTIVE` is rejected while an ACTIVE Occupancy exists (transfer or checkout first). Use **block** when a tenant remains but new assignments must stop.  
- Do **not** map catalog-inactive to `OCCUPIED`.  
- Blocking requires a non-empty `block_reason`.  
- Unblock sets `block_reason`, `blocked_at`, `blocked_by_user_id` to NULL.  
- **A bed may be blocked while OCCUPIED** (active occupancy continues; availability becomes `BLOCKED` so no new assignment/activation onto that bed).  
- Owner may block/unblock any bed in the org; Manager may block/unblock beds only on assigned properties.  
- Draft move-ins do not change availability.  
- No historical bed-block table in MVP (current block state only).

---

## 4. Tenant & stay lifecycle

### 4.1 `tenant`

| Column | Type | Null | Notes |
|---|---|---|---|
| id | UUID | NO | PK |
| **organization_id** | UUID | NO | **FK → organization.id (v5 SaaS scope)** |
| first_name | VARCHAR(100) | NO | |
| last_name | VARCHAR(100) | YES | |
| phone | VARCHAR(30) | NO | |
| email | VARCHAR(320) | YES | |
| date_of_birth | DATE | YES | |
| gender | VARCHAR(40) | YES | |
| address | TEXT | YES | |
| emergency_contact_name | VARCHAR(160) | YES | |
| emergency_contact_phone | VARCHAR(30) | YES | |
| government_id | VARCHAR(100) | YES | |
| notes | TEXT | YES | |
| status | VARCHAR(20) | NO | `ACTIVE` / `INACTIVE` |
| created_at | TIMESTAMPTZ | NO | |
| updated_at | TIMESTAMPTZ | NO | |

- Unique `(organization_id, phone)`.  
- Unique `(organization_id, email)` where email IS NOT NULL.  
- Tenant is **not** the stay record. Do not store bed/rent/deposit/occupancy on tenant.  
- Creating a tenant under a property still persists org-scoped tenant (`organization_id` from that property’s org).

### 4.2 `tenancy`

| Column | Type | Null | Notes |
|---|---|---|---|
| id | UUID | NO | PK |
| tenant_id | UUID | NO | FK → tenant.id |
| property_id | UUID | NO | FK → property.id |
| status | VARCHAR(20) | NO | `ACTIVE` / `CHECKED_OUT` / `CANCELLED` |
| created_at | TIMESTAMPTZ | NO | |
| updated_at | TIMESTAMPTZ | NO | |

### 4.3 `occupancy`

| Column | Type | Null | Notes |
|---|---|---|---|
| id | UUID | NO | PK |
| tenancy_id | UUID | NO | FK → tenancy.id |
| bed_id | UUID | NO | FK → bed.id |
| status | VARCHAR(20) | NO | `ACTIVE` / `COMPLETED` |
| started_at | TIMESTAMPTZ | NO | |
| ended_at | TIMESTAMPTZ | YES | |
| created_at | TIMESTAMPTZ | NO | |
| updated_at | TIMESTAMPTZ | NO | |

- Partial unique: at most one `ACTIVE` per tenancy; at most one `ACTIVE` per bed.  
- Occupancy periods for a bed must not overlap.

---

## 5. Rent configuration — `rent_config`

Unchanged from v1.5 semantics: property / room / bed effective-dated rows; most-specific wins; no overlapping ranges at same target level.

| Column | Type | Null | Notes |
|---|---|---|---|
| id | UUID | NO | PK |
| property_id | UUID | NO | FK |
| room_id | UUID | YES | NULL for property-level |
| bed_id | UUID | YES | set only for bed-level |
| amount | NUMERIC(12,2) | NO | `>= 0` |
| effective_from | DATE | NO | |
| effective_to | DATE | YES | exclusive; NULL = open |
| created_at | TIMESTAMPTZ | NO | |
| updated_at | TIMESTAMPTZ | NO | |

Exactly one target level per row: property only; property+room; property+room+bed.

---

## 6. Services

### 6.1 `service` · 6.2 `service_config` · 6.3 `service_enrollment` · 6.4 `service_charge`

Unchanged from v1.5:

- Enrollment is **Tenancy-level only** (no bed/occupancy FK).  
- `billing_type`: `FIXED` / `VARIABLE`  
- `billing_timing`: `UPFRONT` / `MONTHLY_ARREARS`  
- Enrollment: `ACTIVE` ? `ended_at IS NULL`; `ENDED` ? `ended_at` set  
- `service_charge.billing_period` = first day of billing month; unique per enrollment+period  

---

## 7. Move-in

### 7.1 `move_in`

| Column | Type | Null | Notes |
|---|---|---|---|
| id | UUID | NO | PK |
| tenancy_id | UUID | NO | FK — Tenancy may exist while DRAFT |
| bed_id | UUID | NO | Selected bed |
| status | VARCHAR(20) | NO | `DRAFT` / `COMPLETED` / `CANCELLED` |
| move_in_date | DATE | NO | |
| created_at | TIMESTAMPTZ | NO | |
| updated_at | TIMESTAMPTZ | NO | |

On successful upfront payment: revalidate bed (must be `AVAILABLE`), create `ACTIVE` Occupancy, activate selected enrollments, complete move-in — atomically.

### 7.2 `move_in_service`

| Column | Type | Null | Notes |
|---|---|---|---|
| id | UUID | NO | PK |
| move_in_id | UUID | NO | FK |
| service_id | UUID | NO | FK |
| selected_amount | NUMERIC(12,2) | YES | |
| created_at | TIMESTAMPTZ | NO | |

Unique `(move_in_id, service_id)`.

---

## 8. Deposit — `deposit_ledger`

| Column | Type | Null | Notes |
|---|---|---|---|
| id | UUID | NO | PK |
| tenancy_id | UUID | NO | FK |
| type | VARCHAR(20) | NO | `RECEIPT` / `DEDUCTION` / `REFUND` |
| amount | NUMERIC(12,2) | NO | `>= 0` |
| reference | VARCHAR(160) | YES | |
| notes | TEXT | YES | |
| created_at | TIMESTAMPTZ | NO | |

- Append-only; no `ALLOCATION`.  
- **`RECEIPT`:** written when move-in upfront deposit payment is confirmed.  
- **`DEDUCTION`:** written on confirmed checkout when deposit is consumed.  
- **`REFUND`:** written on actual remaining-deposit payout (not a payment reversal).

---

## 9. Payments

### 9.1 `payment`

| Column | Type | Null | Notes |
|---|---|---|---|
| id | UUID | NO | PK |
| organization_id | UUID | NO | FK |
| tenancy_id | UUID | YES | FK when stay-tied |
| payment_method | VARCHAR(20) | NO | `RAZORPAY` / `CASH` / `BANK_TRANSFER` |
| amount | NUMERIC(12,2) | NO | |
| currency | CHAR(3) | NO | |
| status | VARCHAR(20) | NO | `PENDING` / `CONFIRMED` / `FAILED` / `CANCELLED` |
| external_reference | VARCHAR(200) | YES | |
| bank_transfer_reference | VARCHAR(200) | YES | Required for `BANK_TRANSFER` |
| idempotency_key | VARCHAR(200) | YES | Unique when present |
| confirmed_at | TIMESTAMPTZ | YES | |
| created_at | TIMESTAMPTZ | NO | |
| updated_at | TIMESTAMPTZ | NO | |

Move-in payment API is a **workflow façade** over this table (no separate move-in payment table).

### 9.2 `payment_invoice`

Server-owned settlement mapping; unique `(payment_id, invoice_id)`; no client-side allocation; no partial payments in MVP.

---

## 10. Invoices

### 10.1 `invoice` · 10.2 `invoice_line_item`

Unchanged from v1.5:

- `invoice_type`: `MONTHLY` / `CHECKOUT` / `UPFRONT`  
- `status`: `DRAFT` / `FINALIZED` / `VOID`  
- Monthly due_date = billing_date + property.payment_due_days (snapshotted)  
- CHECKOUT invoice = new checkout charges only  

---

## 11. Checkout — `settlement_snapshot`

Immutable checkout calculation: tenancy_id, occupancy_id, checkout_date, outstanding_receivable, new_checkout_charges, deposit_balance_before, deposit_deduction, deposit_refund_due, total_receivable, net_receivable, refund_due, currency, status=`CONFIRMED`, confirmed_at, created_at.

**Math (canonical):**  
`total_receivable` / owed = `outstanding_receivable + new_checkout_charges`  
`deposit_deduction = min(deposit_balance_before, owed)`  
`net_receivable = owed − deposit_deduction`  
`deposit_refund_due` / `refund_due = deposit_balance_before − deposit_deduction`

Confirmed checkout closes ACTIVE Occupancy, sets Tenancy `CHECKED_OUT`, ends ACTIVE service enrollments, writes `DEDUCTION` when deduction > 0, and creates CHECKOUT invoice for **new charges only**. Unpaid `net_receivable` may remain on invoices when confirm used `leaveReceivable`.

---

## 12. Notices, documents, expenses, tickets

### 12.1 `notice`

| Column | Type | Null | Notes |
|---|---|---|---|
| id | UUID | NO | PK |
| property_id | UUID | NO | FK |
| title | VARCHAR(200) | NO | |
| body | TEXT | NO | **Markdown** |
| status | VARCHAR(20) | NO | `DRAFT` / `PUBLISHED` / `ARCHIVED` |
| published_at | TIMESTAMPTZ | YES | |
| expires_at | TIMESTAMPTZ | YES | |
| created_at | TIMESTAMPTZ | NO | |
| updated_at | TIMESTAMPTZ | NO | |

### 12.2 `document` — **canonical upload lifecycle (v5)**

| Column | Type | Null | Notes |
|---|---|---|---|
| id | UUID | NO | PK |
| organization_id | UUID | NO | FK |
| property_id | UUID | YES | FK |
| tenant_id | UUID | YES | FK |
| tenancy_id | UUID | YES | FK |
| name | VARCHAR(255) | NO | |
| storage_key | TEXT | NO | Object-storage key |
| content_type | VARCHAR(120) | YES | |
| size_bytes | BIGINT | YES | |
| status | VARCHAR(20) | NO | **`PENDING_UPLOAD` / `UPLOADED` / `ARCHIVED`** |
| created_at | TIMESTAMPTZ | NO | |
| updated_at | TIMESTAMPTZ | NO | |

- Create upload intent ? `PENDING_UPLOAD`.  
- Backend `HeadObject` verification on complete ? `UPLOADED`.  
- Archive ? `ARCHIVED`.  
- **`ACTIVE` is not a document status** (removed vs v1.5).

### 12.3 `expense_type` · 12.4 `expense`

Unchanged from v1.5 (property-scoped categories and expense rows).

### 12.5 `ticket` — **new in v5**

| Column | Type | Null | Notes |
|---|---|---|---|
| id | UUID | NO | PK |
| property_id | UUID | NO | FK → property.id |
| title | VARCHAR(200) | NO | |
| body | TEXT | YES | |
| status | VARCHAR(20) | NO | `OPEN` / `IN_PROGRESS` / `RESOLVED` / `CLOSED` |
| created_by_user_id | UUID | NO | FK → app_user.id |
| assigned_to_user_id | UUID | YES | FK → app_user.id |
| created_at | TIMESTAMPTZ | NO | |
| updated_at | TIMESTAMPTZ | NO | |

Property-scoped; authorization follows organization/property membership.  
`assigned_to_user_id` references `app_user` (not `property_membership`). On member deactivate or manager removal, ticket rows are **not** deleted; when implemented, clear `assigned_to_user_id` for `OPEN` / `IN_PROGRESS` tickets assigned to that user on affected properties.

---

## 13. Transactional invariants (summary)

| Workflow | Persistence behavior |
|---|---|
| New move-in | Create Tenancy + MoveIn `DRAFT`; no Occupancy; no bed reservation |
| Move-in payment success | Confirm Payment ? `RECEIPT` if deposit collected ? revalidate bed `AVAILABLE` ? ACTIVE Occupancy ? enrollments ? complete MoveIn |
| Transfer | Close source Occupancy + create destination under same Tenancy (same property); optional proration/override charge |
| Checkout | Settlement snapshot + `DEDUCTION` + CHECKOUT invoice + close Occupancy/Tenancy + end enrollments (± leaveReceivable) |
| Deposit refund | Separate manual payout → `REFUND` ledger row (CASH/BANK_TRANSFER) |
| Payment settlement | Server writes `payment_invoice` rows |
| Bed block | Set `block_reason` (+ timestamps); unblock clears them |

---

## 14. MVP exclusions

- Persisted Transfer table  
- Bed/Occupancy-tied service enrollment  
- Meter/quantity variable allocation  
- Deposit `ALLOCATION`  
- Transfer-time deposit refund  
- Partial payments / client invoice allocation  
- Separate Complete Move-In action  
- Separate move-in payment table  
- Historical bed-block history API  
- Viewer role (MVP is Owner + Manager only)  
- Taxes  
- Future bed reservation model  

---

## 15. Version change summary — v1.5 → v5.0

- Added `ticket` persistence.  
- Added bed blocking columns and derived `AVAILABLE` / `OCCUPIED` / `BLOCKED` availability.  
- Added mandatory `tenant.organization_id` and org-scoped uniqueness.  
- Replaced document `ACTIVE`/`ARCHIVED` with `PENDING_UPLOAD` / `UPLOADED` / `ARCHIVED`.  
- Org membership = `OWNER` / `MEMBER`; property assignment = `MANAGER` only (no Viewer in MVP).  
- Owner-only admin for properties and memberships; Managers cannot add managers or properties.  
- Occupied beds may be blocked.  
- Added `property.default_currency` and explicit deposit `RECEIPT` writer.  
- Aligned controlled baseline to the v5.0 document pack.
