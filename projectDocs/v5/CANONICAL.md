# StayFlow — Canonical Document Pack v5.0

**Status:** Implementation baseline  
**Location:** `projectDocs/v5/`

This pack supersedes the prior mixed PDF baselines (Context v4.0, FR v2.0, API v1.9, DB v1.5, Arch v1.7, UX v1.6, UI v1.3, Infra v1.3, Terraform v1.2) for implementation decisions.

Older PDFs under `projectDocs/` are retained for history only — **do not implement from them**.

---

## Canonical set

| Document | File | Notes |
|---|---|---|
| Project Context v5.0 | `StayFlow_Project_Context_v5.0.md` | Product authority |
| Functional Requirements v5.0 | `StayFlow_Functional_Requirements_v5.0.md` | Acceptance |
| API Contract v5.0 | `StayFlow_API_Contract_v5.0.md` | Public HTTP API |
| DB Schema v5.0 | `StayFlow_DB_Schema_v5.0.md` | Persistence |
| Architecture v5.0 | `StayFlow_Architecture_v5.0.md` | Transactions + AWS mapping |
| UX Flows v5.0 | `StayFlow_UX_Flows_v5.0.md` | User journeys |
| UI Style Guide v5.0 | `StayFlow_UI_Style_Guide_v5.0.md` | UI patterns |
| Infrastructure v5.0 | `StayFlow_Infrastructure_v5.0.md` | **Alignment-only** (no AWS change) |
| Terraform v5.0 | `StayFlow_Terraform_v5.0.md` | **Alignment-only** (no IaC change) |

---

## Gaps closed in v5.0

1. **Tickets** — `ticket` table + API/FR/Context/UX alignment  
2. **Bed blocking** — `block_reason` / derived `AVAILABLE|OCCUPIED|BLOCKED` + `POST .../block|unblock`  
3. **Tenant org scope** — `tenant.organization_id` + uniqueness  
4. **Documents** — `PENDING_UPLOAD` → `UPLOADED`; hard `DELETE` (no `ARCHIVED` in MVP)  
5. **Tenancy API** — workflow-bound `/tenancies/{id}` allowed; services listed by tenancy; minimal stay GET  
6. **Version references** — entire pack cites v5.0 peers  

Also included: Owner/Member org membership + Owner-assigned property Manager (no Viewer); occupied-bed blocking; `property.default_currency`; explicit deposit `RECEIPT` on move-in payment; transfer preview services = informational.

**Checkout / workers clarifications (locked for Tasks 23–27):** settlement math (`depositDeduction = min(SD, unpaid + newCharges)`); unpaid finish with `leaveReceivable`; refund manual CASH/BANK_TRANSFER; transfer proration + override; billing worker fans out, invoice worker creates MONTHLY invoices.

**Side features clarifications (locked for Tasks 28–32):**
1. **Expenses** — full CRUD (GET/POST + PATCH/DELETE); hard delete OK (not financial-immutable). Schema: `expense_type` (`id`, `property_id`, `name` unique/property, `active`, timestamps); `expense` (`id`, `property_id`, `expense_type_id`, `amount NUMERIC(12,2)`, `currency` from property `default_currency`, `incurred_on`, `notes`, `created_by_user_id`, timestamps). Flyway: `V10__ops_side.sql` (V7–V9 already used).
2. **Notices** — `DRAFT` / `PUBLISHED` only; `POST …/publish`; **`DELETE /notices/{id}`**. No archive.
3. **Documents** — `PENDING_UPLOAD` → HeadObject → `UPLOADED`; **`DELETE /documents/{id}`** (DB row + best-effort S3 object). No `ARCHIVED` in MVP. Local object store: **MinIO** in docker-compose (S3 API).
4. **Tickets** — `assigned_to_user_id` must be Owner or Manager on that property (else 422); unassign on member deactivate / manager removal for `OPEN`/`IN_PROGRESS`.
5. **Analytics** (read-only; optional `?from=&to=` ISO dates; default = current calendar month in property TZ / UTC if unset):
   - occupancy (**point-in-time now**, ignore range): `{ totalBeds, occupiedBeds, blockedBeds, occupancyRate }` — ACTIVE catalog beds only
   - revenue (range): `{ currency, paidAmount, finalizedInvoiceAmount }` — confirmed payments + finalized invoices in range
   - expenses (range): `{ currency, totalAmount, byType: [{ expenseTypeId, name, totalAmount }] }` — sum by `incurred_on`
6. **Exports** — `GET .../exports/{exportType}` CSV only; `exportType` ∈ `tenants` | `expenses` | `invoices` (property-scoped).

---

## Authz lock (confirmed)

1. No Viewer in MVP.
2. Member with zero property assignments may sign in and see an empty property list.
3. Occupied beds may be blocked (Owner: all properties; Manager: assigned properties).
4. Owner alone **creates / updates / soft-deletes** properties, manages org members, and assigns property managers; Managers cannot.
5. Managers **may** create/update rooms and beds on assigned properties.
6. Deactivating an org member **deletes** their `property_membership` rows (no soft status on that table); reactivate does not restore assignments.
7. Bed catalog `INACTIVE` is soft-delete: omit from inventory list/get; reject while ACTIVE occupancy exists (transfer/checkout first; use block if tenant stays).

## Ready for implementation

Core stay / billing / checkout / payments / deposits / services / rent / tickets / bed block can be implemented from this pack without inventing persistence for the previously missing pieces.

Still intentionally deferred (not blockers): full OpenAPI request/response field catalogs for every endpoint — follow DB columns + API purpose tables; add OpenAPI during implementation if desired.
