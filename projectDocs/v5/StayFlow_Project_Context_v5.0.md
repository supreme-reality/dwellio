# StayFlow — Project Context v5.0

**Authoritative Product & Domain Rules**  
**Controlled alignment:** Functional Requirements v5.0 · API Contract v5.0 · DB Schema v5.0 · Architecture v5.0 · UX Flows v5.0 · UI Style Guide v5.0  
**Infrastructure / Terraform:** v5.0 (alignment-only; no material AWS/IaC change)

Supersedes Project Context v4.0.

---

## 1. Status & authority

- Project Context defines product/domain rules.  
- Functional Requirements translate rules into acceptance criteria.  
- API Contract defines the public API surface.  
- DB Schema defines relational persistence and financial structures.  
- v5.0 resolves prior model gaps (tickets, bed blocking, tenant org scoping, document lifecycle, tenancy API policy) without redesigning the core stay model.

---

## 2. Scope & multi-tenancy

- StayFlow is a multi-tenant PG / co-living management platform.  
- **Organization is the strict SaaS boundary.** A user may belong to multiple organizations.  
- **Owner** creates the organization and properties, has full access to all properties, and alone may **create/update/soft-delete** properties, add/deactivate members, and assign managers to properties.  
- **Member** belongs to the organization; with zero property assignments they may sign in and see an empty property list. Deactivating a member clears their property manager assignments.  
- **Manager** is an org Member explicitly assigned to one or more properties by the Owner. Managers operate only on assigned properties and **cannot** create/update/delete properties or add/assign other managers. Managers **may** create and update rooms and beds within assigned properties.  
- **Viewer is out of MVP.**  
- Operational context: organization, then property.  
- Server enforces organization/property scope regardless of client-supplied identifiers.  
- **Tenant profiles are organization-scoped** (`organization_id`). A returning person is matched within the organization, not globally across SaaS tenants.

---

## 3. Core domain model

**Property hierarchy:** Organization ? Property ? Room ? Bed (Bed = primary commercial/inventory unit).  
**Stay hierarchy:** Tenant ? Tenancy ? Occupancy ? Bed.

- Tenant = person (org-scoped profile).  
- Tenancy = one stay/relationship with a property.  
- Occupancy = physical bed assignment during that tenancy.  
- Every new stay/move-in creates a new Tenancy.  
- Returning after checkout does not reuse a closed Tenancy.  
- A Tenancy may have multiple historical Occupancies (transfers) but at most one `ACTIVE` Occupancy.  
- Checkout closes the active Occupancy and ends that Tenancy’s stay lifecycle.

---

## 4. Inventory, availability & blocking

- Beds expose derived availability: **`AVAILABLE` | `OCCUPIED` | `BLOCKED`** (catalog-`ACTIVE` beds only).  
- `OCCUPIED` when an ACTIVE Occupancy exists for the bed (not when catalog is merely `INACTIVE`).  
- `BLOCKED` when a blocking reason is set; blocking **requires a reason**.  
- A bed **may be blocked while occupied**; existing occupancy continues, but no new activation/assignment onto that bed.  
- Catalog soft-delete (`INACTIVE`): omit from inventory APIs; reject while ACTIVE occupancy exists — transfer or checkout first.  
- Owner may block any org bed; Manager may block beds on assigned properties only.  
- Unblock clears the block. MVP stores current block state only (no historical block API).  
- Draft/cancelled move-ins do not reserve or block a bed.  
- Availability is current-state only; future reservation/allocation is outside MVP.  
- Move-in activation and transfer execution revalidate availability transactionally.

---

## 5. Rent

Resolution: Property default ? Room override ? Bed override (most specific wins).  
Effective-dated configuration; historical rows preserved; current-period changes use calendar-day proration; monthly rent charged upfront; finalized invoices immutable.

---

## 6. Services & utilities

- Property catalog + effective-dated configuration.  
- MVP enrollment is **Tenancy-level only** (not bed/occupancy).  
- Fixed/variable, mandatory/optional, upfront/monthly-arrears.  
- Variable charges: manager-entered final amount; missing amount ? no charge.  
- Re-enrollment creates a new enrollment row.  
- On transfer, enrollments remain on the Tenancy; preview may show them as informational context alongside destination rent (they are not re-attached to the destination bed).

---

## 7. Move-in lifecycle

- Persisted as `DRAFT` before completion; may save/resume with service selections.  
- Draft does not reserve/block bed and does **not** create an ACTIVE Occupancy.  
- Upfront: current-period rent, deposit, applicable upfront services.  
- Payment via generic Payment model (move-in payment endpoint is a workflow façade).  
- Successful payment completes move-in transactionally: revalidate bed ? activate Occupancy ? activate enrollments.  
- Failure leaves DRAFT; no Occupancy activated.  
- No separate Complete Move-In action.  
- Confirmed deposit collection writes deposit ledger `RECEIPT`.

---

## 8. Transfers

- Same Tenancy; destination bed must be on the **same property** and AVAILABLE at confirmation.  
- Preview: destination availability, destination rent, informational current services.  
- Closes source Occupancy and creates destination Occupancy transactionally.  
- Mid-cycle rent: charge **proration difference** only by default; manager may override with a manual amount.  
- Deposit remains on Tenancy; no transfer-time deposit refund.  
- No persisted Transfer table.

---

## 9. Deposits

- Belongs to Tenancy; survives transfers; new stay → new deposit lifecycle.  
- Ledger types: `RECEIPT` | `DEDUCTION` | `REFUND` (no `ALLOCATION`).  
- Checkout applies deposit inside immutable settlement snapshot (`DEDUCTION` = amount of SD consumed against owed).  
- Refundable balance is calculation; payout is a **separate manual** Owner/Manager action and creates `REFUND`.  
- Refund is remaining security deposit after deductions — **not** a payment reversal.  
- MVP refund methods: `CASH` | `BANK_TRANSFER` (Razorpay payout deferred).

---

## 10. Monthly billing

- Asynchronous worker-driven.  
- **Billing worker** (Billing Trigger SQS): on schedule (23:00 IST on the 1st), fans out idempotent per-stay (or per-property) jobs to the Invoice queue.  
- **Invoice worker** (Invoice SQS): creates/finalizes that stay’s MONTHLY invoice, binds variable charges, snapshots due dates.  
- Due date = billing date + property `payment_due_days` (snapshotted).  
- Finalized invoices immutable.  
- CHECKOUT invoices = new checkout charges only (not unpaid prior invoices).  
- No public manual monthly billing trigger.

---

## 11. Payments & financial persistence

- Generic Payment: `RAZORPAY` | `CASH` | `BANK_TRANSFER`.  
- Confirmed payments immutable; Razorpay verified + idempotent; bank reference required for bank transfer.  
- No partial payments; no client-side invoice allocation.  
- One confirmed payment may settle multiple invoices via server-owned payment/invoice junction.  
- Post-checkout collections use the same payment APIs against remaining unpaid invoices; they do **not** rewrite the settlement snapshot.

---

## 12. Checkout & settlement

- Requires ACTIVE Occupancy.  
- **New checkout charges** = prorated rent + unbilled variable service charges + manager-entered damages/manual charges (+ unbilled fixed `MONTHLY_ARREARS` for the open period if not yet invoiced).  
- Settlement math (snapshotted):  
  - `owedBeforeSD` = unpaid finalized invoice balances + new checkout charges  
  - `depositDeduction` = `min(depositBalanceBefore, owedBeforeSD)` → ledger `DEDUCTION`  
  - `netReceivable` = `owedBeforeSD - depositDeduction`  
  - `refundDue` = `depositBalanceBefore - depositDeduction`  
  - `netReceivable` and `refundDue` are mutually exclusive (at most one > 0).  
- Confirmation: immutable `settlement_snapshot`; CHECKOUT invoice for **new charges only**; close Occupancy/Tenancy stay; end ACTIVE service enrollments.  
- Manager **may** finish checkout with `netReceivable > 0` unpaid — requires explicit ack (`leaveReceivable`); no partial pay at checkout (pay **full** net via normal payment, or leave **all** unpaid).  
- Refund payout is separate and only when `refundDue > 0`.

---

## 13. Expenses, notices, documents & tickets

- Property/organization scoped; must not bypass authorization.  
- Notices: property-wide Markdown.  
- Documents: metadata in DB; binaries in private object storage.  
  **Canonical status:** `PENDING_UPLOAD` ? `UPLOADED` ? `ARCHIVED`.  
- Tickets: property-scoped operational tickets with lifecycle status.  
- These features do not alter Tenant ? Tenancy ? Occupancy.

---

## 14. Key lifecycle examples

| Scenario | Result |
|---|---|
| New move-in | Tenant + new Tenancy + Move-In; after payment ? ACTIVE Occupancy |
| Draft loses bed | Draft remains; activation blocked until available bed selected |
| Transfer | Same Tenancy; Occupancy swap |
| Checkout | Occupancy ends; Tenancy stay ends; settlement finalized |
| Return later | New Tenancy + independent deposit/services |
| Block bed | Availability = BLOCKED with reason |

---

## 15. Explicit MVP boundaries

- No Transfer table  
- No bed/occupancy-tied services  
- No meter/quantity allocation  
- No deposit `ALLOCATION`  
- No transfer-time deposit refund  
- No partial payments / client invoice allocation  
- No separate Complete Move-In  
- No payment-reversal semantics for deposit refunds  
- Razorpay deposit payout deferred (CASH/BANK_TRANSFER refund only)  
- No future bed reservation model  
- No historical bed-block API  
- Viewer role outside MVP  
- Taxes outside MVP  

---

## 16. Persistence & API alignment

- DB Schema v5.0 is the persistence baseline (incl. tickets, bed block columns, `tenant.organization_id`, document statuses, settlement snapshot, payment_invoice).  
- API Contract v5.0 exposes workflow-bound `/tenancies/{id}/…` paths; no free-standing Tenancy/Occupancy CRUD.  
- Service listing is tenancy-scoped.

---

## 17. Version change summary — v4.0 ? v5.0

- Tickets required in persistence as well as API/FR.  
- Bed availability model: AVAILABLE / OCCUPIED / BLOCKED with required reason.  
- Tenant organization scoping made explicit.  
- Document lifecycle made canonical (`PENDING_UPLOAD` / `UPLOADED` / `ARCHIVED`).  
- Tenancy API policy clarified; services listed by tenancy.  
- Locked Owner / Member / Manager authz (no Viewer); owner-only property create/update/delete & membership admin.
- Occupied-bed blocking allowed.
- Managers may manage rooms/beds on assigned properties; property create/delete and membership admin remain Owner-only.
- Controlled document pack advanced to v5.0 across product docs; Infra/Terraform v5.0 alignment-only.
