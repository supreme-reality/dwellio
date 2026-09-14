# StayFlow — Architecture v5.0

**Controlled application architecture**  
**Aligned with:** Project Context v5.0 · FR v5.0 · API Contract v5.0 · DB Schema v5.0 · UX Flows v5.0

Supersedes Architecture v1.7.  
**AWS runtime baseline is unchanged** from Infrastructure v5.0 / prior v1.3 (alignment-only bump).

---

## 1. Principles

- Organization is the SaaS boundary; authorize server-side before domain ops.  
- Tenant ? Tenancy ? Occupancy ? Bed.  
- Financial facts immutable after confirmation/finalization.  
- Multi-state business ops are transactional where domain requires atomicity.  
- Periodic billing is asynchronous (workers), not synchronous request work.

---

## 2. Logical domain

- Tenant may have many Tenancies; Tenancy never reopened after checkout.  
- At most one ACTIVE Occupancy per Tenancy/Bed.  
- Transfer changes Occupancy, preserves Tenancy.  
- Tenant profiles are organization-scoped.

---

## 3. Authorization

- Org membership roles: **OWNER** / **MEMBER**.  
- Property membership role: **MANAGER** only (Viewer out of MVP).  
- Owner has implicit access to all properties; Members need explicit property assignment.  
- Only Owner may create/**update**/delete properties and manage memberships/assignments.  
- Managers may create/update rooms and beds on assigned properties.  
- Deactivating an org member deletes that member’s `property_membership` rows in the same transaction.  
- Repositories apply scope from auth context, not trusted client scope fields.

---

## 4. Inventory & availability

- Bed catalog status (`ACTIVE`/`INACTIVE`) is independent of derived availability.  
- Derived availability (catalog-`ACTIVE` beds only): `BLOCKED` (block_reason set) → else `OCCUPIED` (ACTIVE occupancy) → else `AVAILABLE`.  
- Soft-delete (`INACTIVE`): omit from list/get; reject if ACTIVE occupancy exists (transfer or checkout first). Block when tenant remains but new assignments must stop.  
- Draft move-ins do not reserve.  
- Activation/transfer revalidate; failure does not silently substitute beds.  
- Block/unblock via API; occupied beds may be blocked; no historical block subsystem in MVP.

---

## 5. Rent & services

- Effective-dated rent_config; resolve Bed ? Room ? Property.  
- service ? service_config ? service_enrollment(tenancy).  
- Variable charges manager-entered; no meter subsystem.

---

## 6. Move-in transaction boundary

- DRAFT: Tenancy + MoveIn; no ACTIVE Occupancy; no reservation.  
- Payment façade ? generic Payment (+ deposit RECEIPT).  
- Success transaction: revalidate AVAILABLE ? Occupancy ? enrollments ? complete MoveIn.  
- No Complete Move-In endpoint.

---

## 7. Transfer architecture

- `POST /transfers/preview`, `POST /transfers`.  
- Preview: destination availability + rent (proration diff) + informational current enrollments.  
- Confirm: close source + create destination Occupancy under same Tenancy; destination same property + AVAILABLE.  
- Optional manager override for proration difference amount.  
- No deposit refund on transfer.

---

## 8. Deposit, payment, checkout

- Deposit ledger RECEIPT/DEDUCTION/REFUND on Tenancy.  
- Payment + payment_invoice junction for multi-invoice settlement.  
- Checkout → immutable settlement_snapshot using:  
  `depositDeduction = min(SD, unpaidInvoices + newCharges)`; shortfall → `netReceivable`; surplus → `refundDue`.  
- Unpaid finish allowed with explicit `leaveReceivable`; no partial at checkout.  
- Refund separate, manual, only when `refundDue > 0`; MVP `CASH` | `BANK_TRANSFER`.  
- REFUND ≠ payment reversal.

---

## 9. Monthly billing

- EventBridge/Scheduler → Billing Trigger SQS → **billing worker** at **23:00 IST on the 1st**.  
- Billing worker: idempotent run; **fans out** one Invoice-queue message per active stay (or per property batch — implementer choice, stay-level idempotency key required).  
- **Invoice worker** consumes Invoice SQS: creates/finalizes MONTHLY invoice, binds variable charges, snapshots due dates (`billing_date + payment_due_days`).  
- Delete message only after success; uniqueness/idempotency protect money; txn timeout ≤ 5 min.  
- No public manual billing endpoint. Local/test-only internal trigger allowed; must not ship to deployed environments.

---

## 10. Application components

| Component | Responsibility |
|---|---|
| API / App service | AuthZ, validation, sync domain ops, transactions |
| Aurora PostgreSQL | Relational persistence (incl. tickets, bed block columns, org-scoped tenants, document statuses) |
| Billing worker | Monthly billing **orchestration / fan-out** from Billing Trigger queue |
| Invoice worker | Per-stay MONTHLY invoice create/finalize via Invoice queue |
| SQS | Billing Trigger + Invoice queues + DLQs |
| S3 | Private document binaries; app verifies HeadObject before UPLOADED |

Billing and invoice workers are **separate ECS services** for independent scale, from one shared worker container image selected by `WORKER_ROLE=billing|invoice` (repo: `apps/worker`).

---

## 11. AWS runtime (unchanged capacity)

- DEV VPC `10.10.0.0/16`; PROD `10.20.0.0/16`; 2 AZs  
- ALB ? ECS API; workers private  
- API DEV 0/1/2 @ 0.25 vCPU/512 MB; PROD 2/2/4 @ 0.5 vCPU/1 GB  
- Billing worker 0/0/1 both envs; Invoice worker DEV 0/0/1, PROD 1/1/5 (same image, different `WORKER_ROLE`)  
- Aurora Serverless v2 DEV 0–2 ACU; PROD 0.5–4 ACU  
- Secrets Manager, OIDC, CloudWatch, SSM port-forward for temp DB access  

No new AWS service for Tenancy/Transfer/Deposit/Checkout/Tickets/Bed block.

---

## 12. Documents & notices

- Document statuses: `PENDING_UPLOAD` → `UPLOADED`; hard DELETE (DB + best-effort S3). No `ARCHIVED` in MVP.  
- Notices: Markdown; sanitize/disable raw HTML; `DRAFT` / `PUBLISHED`; hard DELETE; no targeting/groups/receipts in MVP.
- Expenses: property-scoped CRUD including hard delete.

---

## 13. Tickets

- Property-scoped ticket entity; lifecycle statuses per DB/API.  
- Same authorization boundary as other property resources.  
- Assignee (optional) must be Owner or Manager on that property.

---

## 13a. Analytics & exports

- Derived read-only; no new financial truth.  
- Occupancy point-in-time; revenue/expenses by date range; CSV exports `tenants`|`expenses`|`invoices`.  
- Local document store for DEV: MinIO (S3-compatible).

---

## 14. Consistency rules

- Move-in payment + activation atomic.  
- Transfer occupancy swap atomic.  
- Checkout snapshot + closure atomic.  
- At-most-one ACTIVE occupancy enforced in DB + transactions.  
- At-least-once workers; idempotency + uniqueness protect money.

---

## 15. MVP architecture exclusions

Same as Context/FR/API v5.0 (no Transfer table, no partial payments, no manual billing trigger, no historical block API, etc.).

---

## 16. Version change summary — v1.7 ? v5.0

- Synced to v5.0 pack.  
- Bed blocking + derived availability.  
- Org-scoped tenants; OWNER/MEMBER + property MANAGER (no Viewer).  
- Document UPLOADED lifecycle + hard DELETE (no ARCHIVED in MVP).  
- Tickets in logical + persistence architecture.  
- AWS capacity unchanged (Infra/Terraform v5.0 alignment-only).  
- Billing fan-out vs Invoice create split; checkout settlement math + leaveReceivable.
