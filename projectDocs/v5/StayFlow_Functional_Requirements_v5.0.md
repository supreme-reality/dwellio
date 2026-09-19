# StayFlow — Functional Requirements v5.0

**Controlled acceptance baseline**  
**Aligned with:** Project Context v5.0 · API Contract v5.0 · DB Schema v5.0 · Architecture v5.0 · UX Flows v5.0

Supersedes Functional Requirements v2.0.

---

## 1. Authority

Project Context v5.0 is authoritative for product/domain rules. This document defines acceptance criteria. API v5.0 and DB v5.0 define surface and persistence.

---

## 2. Multi-tenancy & access

- Organization is the strict SaaS boundary.  
- Users may belong to multiple organizations.  
- Owner = organization-wide admin and full access to all properties.  
- Member = org membership; zero property assignments ⇒ empty property list is valid.  
- Manager = org Member assigned to property(ies) by Owner only; cannot create/update/delete properties or assign managers.  
- Manager **may** create/update rooms and beds on assigned properties; Owner may on all properties.  
- Owner alone updates property settings (`payment_due_days`, `default_currency`, name/address).  
- Deactivating a member clears their property manager assignments; reactivate does not restore them.  
- Viewer is out of MVP.  
- Server enforces scope regardless of client input.  
- Cross-organization access via resource IDs must be impossible.  
- **Tenant records are organization-scoped.**

---

## 3. Tenant, tenancy & occupancy

- Tenant = person; Tenancy = one stay; Occupancy = bed assignment.  
- Every new stay/move-in creates a new Tenancy.  
- Closed Tenancy never reopened on return.  
- At most one ACTIVE Occupancy per Tenancy and per Bed.  
- Checkout closes ACTIVE Occupancy and ends Tenancy stay.

---

## 4. Property inventory & availability

- Hierarchy: Organization ? Property ? Room ? Bed.  
- Bed availability: **`AVAILABLE` | `OCCUPIED` | `BLOCKED`** for catalog-`ACTIVE` beds.  
- Blocking requires a non-empty reason; unblock clears block.  
- Occupied beds may be blocked (availability becomes BLOCKED; current occupancy continues).  
- Soft-delete bed (`INACTIVE`) only when no ACTIVE occupancy (transfer/checkout first); inactive beds omitted from list/get.  
- Owner: any org bed; Manager: assigned properties only.  
- Draft/cancelled move-ins do not reserve or block beds.  
- Revalidate availability immediately before move-in activation and transfer execution.  
- Preview never reserves.  
- No historical bed-block API in MVP.

---

## 5. Rent configuration

- Property / Room / Bed overrides; most-specific wins.  
- Effective-dated; no overwrite of history.  
- Schedule next-period changes; current-period = calendar-day proration.  
- Monthly rent upfront; finalized invoices immutable.

---

## 6. Services & utilities

- Property catalog; Tenancy-level enrollment only.  
- Fixed/variable; mandatory/optional; upfront/monthly-arrears.  
- ACTIVE enrollment has no end date; ENDED has end date; restart = new row.  
- Variable: manager-entered amount; missing ? no charge.  
- Transfer: enrollments remain on Tenancy (informational in preview).

---

## 7. Move-in acceptance

- Create move-in ? DRAFT + new Tenancy.  
- Draft savable; retains service selections; no bed reservation; no ACTIVE Occupancy.  
- Upfront includes rent, deposit, upfront services.  
- Generic Payment flow (including move-in payment façade).  
- Success ? revalidate AVAILABLE bed ? ACTIVE Occupancy ? enrollments ? complete.  
- Deposit collection writes ledger `RECEIPT`.  
- Failure ? remain DRAFT.  
- No separate Complete Move-In step.

---

## 8. Transfer acceptance

- Same Tenancy; destination bed on **same property**; must be AVAILABLE at confirmation.  
- Preview: destination bed, destination rent, current tenancy services (informational).  
- Execution revalidates and swaps Occupancies transactionally.  
- Mid-cycle rent: default **proration difference** charge; manager may override with a manual amount.  
- Deposit stays; no transfer refund.  
- No persisted Transfer entity.

---

## 9. Deposit requirements

- Tenancy-owned; RECEIPT / DEDUCTION / REFUND; no ALLOCATION.  
- DEDUCTION on checkout = SD consumed against owed (pending invoices + new charges).  
- REFUND = remaining deposit payout after deductions (not payment reversal); manual Owner/Manager; MVP methods `CASH` | `BANK_TRANSFER`.

---

## 10. Monthly billing

- Billing worker fans out jobs from Billing Trigger SQS; Invoice worker creates/finalizes MONTHLY invoices and binds variable charges.  
- Due date snapshotted from `payment_due_days`.  
- Finalized invoices immutable; CHECKOUT invoices = new charges only.  
- No public manual billing trigger.

---

## 11. Payments & invoice settlement

- RAZORPAY / CASH / BANK_TRANSFER; confirmed immutable.  
- Razorpay verified + idempotent; bank reference required.  
- No partial payments; no client allocation.  
- Server-owned multi-invoice settlement mapping.  
- After checkout, unpaid balances remain collectible via normal payments; settlement snapshot is not rewritten.

---

## 12. Checkout & settlement

- Requires ACTIVE Occupancy else lifecycle conflict.  
- Preview distinguishes unpaid invoices, new charges (prorated rent, unbilled variable charges, damages/manual), deposit application, `netReceivable`, `refundDue`.  
- Formula: `depositDeduction = min(SD, unpaidInvoices + newCharges)`; shortfall → `netReceivable`; surplus SD → `refundDue`.  
- Confirmation → immutable settlement snapshot; CHECKOUT invoice for new charges only; close Occupancy/Tenancy; end ACTIVE enrollments.  
- Unpaid finish allowed: explicit `leaveReceivable` when `netReceivable > 0`; otherwise optional full payment of net before/with confirm — no partial at checkout.  
- Separate refund when `refundDue > 0` creates REFUND ledger row.

---

## 13. Expenses

- Property expense categories and expenses; org/property isolated; reporting read-only derived.

---

## 14. Notices

- Create/edit/publish/archive; Markdown body; sanitize/disable raw HTML; property scope.

---

## 15. Documents

- Metadata in DB; binary in private object storage.  
- May associate Property / Tenant / Tenancy.  
- **Status lifecycle:** `PENDING_UPLOAD` ? `UPLOADED` (after backend object verify) ? `ARCHIVED`.  
- Browser claim alone is insufficient for UPLOADED.

---

## 16. Tickets

- Property-scoped operational tickets.  
- Status lifecycle: `OPEN` / `IN_PROGRESS` / `RESOLVED` / `CLOSED`.  
- Accessible only within authorized organization/property scope.  
- Persisted per DB Schema v5.0.  
- On member deactivate or manager removal: do not delete tickets; clear `assigned_to_user_id` for `OPEN` / `IN_PROGRESS` tickets assigned to that user on affected properties.

---

## 17. Minimal analytics & exports

- Read-only derived occupancy/revenue/expense metrics and supported exports.  
- No invented future reservations; no new financial truth.

---

## 18. Financial immutability & audit

- Finalized invoices/lines, confirmed payments, deposit ledger, settlement snapshots immutable.  
- Financial writes idempotent where retries possible.  
- Do not recompute historical amounts from current config.

---

## 19. Cross-entity transactions

| Operation | Atomic behavior |
|---|---|
| Move-in completion | Payment ? bed AVAILABLE revalidate ? Occupancy ? enrollments ? complete (+ RECEIPT) |
| Transfer | Revalidate same-property destination → close source Occupancy → create destination (+ proration/override charge as applicable) |
| Checkout | Validate ACTIVE Occupancy → snapshot + DEDUCTION → CHECKOUT invoice → close stay (+ optional full net payment or leaveReceivable) |
| Deposit refund | Manual payout → REFUND ledger (only if refundDue > 0) |
| Payment settlement | Confirm Payment ? server payment_invoice rows |
| Bed block/unblock | Persist/clear block_reason transactionally |

---

## 20. Acceptance scenarios

| Scenario | Condition |
|---|---|
| New stay | New Tenancy; payment ? ACTIVE Occupancy |
| Draft move-in | No ACTIVE Occupancy; no reservation |
| Bed blocked | Availability BLOCKED; cannot activate onto it |
| Org tenant | Tenant belongs to organization; visible under property APIs |
| Member no properties | Can sign in; property list empty |
| Manager limits | Cannot create properties or assign managers |
| Rooms/beds | Manager may create/update on assigned properties |
| Block occupied | Allowed; bed shows BLOCKED |
| Document upload | complete verify ? UPLOADED |
| Services list | Listed by tenancyId, not tenantId |
| Tickets | Create/list/update within property scope |
| Transfer services | Enrollments remain; no bed rebind |
| Transfer rent | Proration diff default; manager override allowed |
| Checkout | Snapshot + closure; unpaid finish with leaveReceivable OK |
| Checkout unpaid | netReceivable left on invoices; collect later via payments |
| Deposit refund | Remaining deposit, not payment reversal; CASH/BANK_TRANSFER MVP |

---

## 21. MVP exclusions

- Transfer persistence  
- Bed/occupancy service enrollment  
- Meter/quantity allocation  
- Deposit ALLOCATION  
- Transfer-time deposit refund  
- Partial payments / client allocation  
- Separate move-in payment table  
- Separate checkout deposit-application table  
- Manual public billing trigger  
- Razorpay deposit payout (CASH/BANK_TRANSFER refund only)  
- Future bed reservations / historical block API  
- Viewer role  
- Taxes  

---

## 22. Version change summary — v2.0 ? v5.0

- Bed AVAILABLE/OCCUPIED/BLOCKED + reason acceptance.  
- Tenant organization scoping.  
- Document PENDING_UPLOAD/UPLOADED/ARCHIVED.  
- Tickets persistence acceptance.  
- Tenancy-scoped service listing; tenancy API policy.  
- Baseline pack ? v5.0 (Infra/Terraform alignment-only).
