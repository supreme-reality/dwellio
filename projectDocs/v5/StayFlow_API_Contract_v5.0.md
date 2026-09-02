# StayFlow — API Contract v5.0

**Controlled public API baseline**  
**Aligned with:** Project Context v5.0 · Functional Requirements v5.0 · DB Schema v5.0 · Architecture v5.0 · UX Flows v5.0  

Supersedes API Contract v1.9.

---

## 1. Authority & conventions

- Base path: `/api/v1`
- JSON; UUIDs; dates `YYYY-MM-DD`; timestamps ISO-8601 with timezone; money as decimal + ISO currency
- Server derives organization/property access from authenticated membership; clients cannot expand scope
- Owner = organization-wide admin + full access to all properties
- Member = org membership without property access until Owner assigns them
- Manager = property-scoped via explicit Owner assignment (Viewer out of MVP)
- Financial writes support idempotency

### Tenancy resource policy (v5 clarification)

- **No free-standing Tenancy/Occupancy CRUD** (create/update/delete outside defined workflows).
- **Workflow-bound tenancy paths are allowed and preferred** for stay-scoped state: deposit, services, stay read context.
- Occupancy history is exposed via tenancy stay context, not a generic `/occupancies` collection.

---



### Authorization rules (MVP)

| Action | Owner | Manager (assigned property) | Member (no property assignment) |
|---|---|---|---|
| Create org | yes (bootstrap) | no | no |
| Add/remove org members | yes | **no** | no |
| Add/remove/assign property managers | yes | **no** | no |
| Create/update/delete properties | yes | **no** | no |
| Rooms & beds CRUD (within a property) | yes | **yes — assigned properties only** | no |
| Property operational APIs (tenants, move-in, rent, services, billing, checkout, tickets, notices, documents, bed block) | all properties | **assigned properties only** | none |
| See property list | all | assigned only | **empty list** |

- Viewer role is **out of MVP**.  
- Blocking an occupied bed is allowed; availability becomes `BLOCKED` (existing occupancy continues).  
- Managers may block/unblock beds on assigned properties; Owners on any org property.
- Property settings update (`PATCH /properties/{id}`) is **Owner only** (Managers cannot change `payment_due_days` / `default_currency`).

## 2. Organizations, properties, rooms & beds

| Method | Path | Purpose |
|---|---|---|
| POST | `/organizations` | Create organization; creator becomes `OWNER` |
| GET | `/organizations` | Accessible organizations |
| GET | `/organizations/{organizationId}` | Organization context |
| GET | `/organizations/{organizationId}/members` | Organization members (`OWNER` / `MEMBER`) |
| POST | `/organizations/{organizationId}/members` | **Owner only** — add `MEMBER` (or invite existing user) |
| PATCH | `/organizations/{organizationId}/members/{membershipId}` | **Owner only** — deactivate/reactivate member (deactivate clears that member’s `property_membership` rows) |
| GET | `/organizations/{organizationId}/properties` | Properties visible to caller (Owner: all; Manager: assigned only; Member with none: empty list) |
| POST | `/organizations/{organizationId}/properties` | **Owner only** — create property |
| DELETE | `/organizations/{organizationId}/properties/{propertyId}` | **Owner only** — deactivate/remove property (soft) |
| GET | `/properties/{propertyId}/managers` | **Owner only** — list property manager assignments |
| POST | `/properties/{propertyId}/managers` | **Owner only** — assign org `MEMBER` as `MANAGER` |
| DELETE | `/properties/{propertyId}/managers/{propertyMembershipId}` | **Owner only** — remove manager assignment |
| GET | `/properties/{propertyId}` | Property context/settings |
| PATCH | `/properties/{propertyId}` | **Owner only** — update property (incl. `payment_due_days`, `default_currency`) |
| GET | `/properties/{propertyId}/rooms` | List rooms |
| POST | `/properties/{propertyId}/rooms` | Create room |
| PATCH | `/rooms/{roomId}` | Update room |
| GET | `/rooms/{roomId}/beds` | List **ACTIVE** beds in room |
| POST | `/rooms/{roomId}/beds` | Create bed |
| GET | `/properties/{propertyId}/beds` | List **ACTIVE** beds with **derived availability** |
| GET | `/beds/{bedId}` | Bed detail + availability (**404** if catalog `INACTIVE`) |
| PATCH | `/beds/{bedId}` | Update bed catalog fields (not block state); `status=INACTIVE` rejected while bed has ACTIVE occupancy |
| POST | `/beds/{bedId}/block` | **Block bed** — body `{ "reason": "..." }` (required) |
| POST | `/beds/{bedId}/unblock` | **Unblock bed** |

Availability values returned: `AVAILABLE` | `OCCUPIED` | `BLOCKED` (derived per DB Schema v5.0; only for catalog-`ACTIVE` beds).  
Managers may create/update rooms and beds on **assigned** properties; Owners may on any property. **Only Owners create/update/delete properties.**  
Catalog soft-delete: set bed `status=INACTIVE` only after transfer or checkout (no ACTIVE occupancy). Use **block** when a tenant remains but new assignments must stop. Inactive beds are omitted from list/get.  
Member deactivate: delete all `property_membership` for that org membership; reactivate does not restore manager assignments. Ticket rows are not deleted; when tickets exist, open/`IN_PROGRESS` tickets assigned to that user on affected properties should be unassigned (`assigned_to_user_id` cleared).  
No historical bed-block API in MVP.

---

## 3. Tenants & stay context

| Method | Path | Purpose |
|---|---|---|
| GET | `/properties/{propertyId}/tenants` | List/search tenants visible for this property (org-scoped records) |
| POST | `/properties/{propertyId}/tenants` | Create tenant in the property’s **organization** |
| GET | `/tenants/{tenantId}` | Tenant profile |
| PATCH | `/tenants/{tenantId}` | Update tenant |
| GET | `/tenants/{tenantId}/stay-history` | Historical tenancies for the tenant |
| GET | `/tenancies/{tenancyId}` | **Minimal stay context read** (status, property, current occupancy summary) |

Tenant is organization-scoped (`organization_id`). Creating under a property sets org from that property.

---

## 4. Move-in

| Method | Path | Purpose |
|---|---|---|
| POST | `/properties/{propertyId}/move-ins` | Create DRAFT move-in + new Tenancy |
| GET | `/move-ins/{moveInId}` | Read/resume |
| PATCH | `/move-ins/{moveInId}` | Update draft |
| POST | `/move-ins/{moveInId}/payment` | Workflow façade: confirm/record required upfront payment via Payment model |
| POST | `/move-ins/{moveInId}/cancel` | Cancel draft |

- Draft does not reserve/block bed and has no ACTIVE Occupancy.  
- Successful payment: revalidate bed `AVAILABLE` ? ACTIVE Occupancy ? selected Tenancy services ? complete move-in.  
- No separate Complete Move-In endpoint.  
- `POST /move-ins/{id}/payment` writes the generic `payment` row (and deposit `RECEIPT` when applicable); it is not a separate payment domain table.

---

## 5. Rent

| Method | Path | Purpose |
|---|---|---|
| GET | `/properties/{propertyId}/rent-config` | List effective-dated rent config |
| POST | `/properties/{propertyId}/rent-config` | Property default |
| POST | `/rooms/{roomId}/rent-config` | Room override |
| POST | `/beds/{bedId}/rent-config` | Bed override |
| GET | `/properties/{propertyId}/rent/resolve?bedId=&date=` | Resolve applicable rent |

Resolution: Bed ? Room ? Property. Current-period changes calendar-day prorated.

---

## 6. Services

| Method | Path | Purpose |
|---|---|---|
| GET | `/properties/{propertyId}/services` | Catalog |
| POST | `/properties/{propertyId}/services` | Create service |
| PATCH | `/services/{serviceId}` | Update service |
| GET | `/services/{serviceId}/config` | Config history |
| POST | `/services/{serviceId}/config` | Create config |
| GET | `/tenancies/{tenancyId}/services` | **List enrollments for this Tenancy** (v5) |
| POST | `/tenancies/{tenancyId}/services` | Enroll |
| POST | `/service-enrollments/{enrollmentId}/end` | End enrollment |
| POST | `/service-enrollments/{enrollmentId}/charges` | Variable period amount |

Removed vs v1.9: `GET /tenants/{tenantId}/services` (ambiguous across stays).

Services are Tenancy-level only. Transfer preview may show **unchanged current enrollments** as informational context plus destination rent — enrollments are not re-bound to the destination bed.

---

## 7. Transfers

| Method | Path | Purpose |
|---|---|---|
| POST | `/transfers/preview` | Preview destination bed availability, destination rent (incl. proration diff), and current tenancy service enrollments (informational) |
| POST | `/transfers` | Execute transfer |

Body includes source tenancy (or active occupancy context) + destination `bedId`. Destination must be on the **same property** and AVAILABLE.  
Optional manager **manual override** amount for mid-cycle rent difference (otherwise server proration diff).  
No persisted Transfer resource. No transfer-time deposit refund.

---

## 8. Deposits

| Method | Path | Purpose |
|---|---|---|
| GET | `/tenancies/{tenancyId}/deposit` | Balance/summary |
| GET | `/tenancies/{tenancyId}/deposit/ledger` | Append-only ledger |

Types: `RECEIPT` | `DEDUCTION` | `REFUND`. No `ALLOCATION`.

---

## 9. Payments & invoices

| Method | Path | Purpose |
|---|---|---|
| POST | `/payments` | Create/record generic payment |
| GET | `/payments/{paymentId}` | Read payment |
| POST | `/payments/{paymentId}/confirm` | Confirm manual payment where applicable |
| POST | `/webhooks/razorpay` | Verify/idempotent Razorpay events |
| GET | `/properties/{propertyId}/invoices` | List/filter |
| GET | `/tenants/{tenantId}/invoices` | Tenant invoices |
| GET | `/tenancies/{tenancyId}/invoices` | Tenancy invoices |
| GET | `/invoices/{invoiceId}` | Invoice + lines |

Methods: `RAZORPAY` | `CASH` | `BANK_TRANSFER`. Server owns multi-invoice settlement. No partial payment; no client allocation. No public manual billing-run endpoint.  
Post-checkout collections use these payment endpoints against remaining unpaid invoices; they do not rewrite `settlement_snapshot`.

---

## 10. Checkout & settlement

| Method | Path | Purpose |
|---|---|---|
| POST | `/checkout/preview` | Body **must** include `tenancyId`; optional damages/manual charge inputs |
| POST | `/checkout` | Confirm checkout; body **must** include `tenancyId`; when `netReceivable > 0` and unpaid, **must** set `leaveReceivable: true` |
| GET | `/settlements/{settlementId}` | Read immutable snapshot |
| POST | `/settlements/{settlementId}/refund` | Manual remaining-deposit payout → `REFUND` ledger (`CASH` \| `BANK_TRANSFER`) |

Requires ACTIVE Occupancy. Confirmation persists `settlement_snapshot`, writes deposit `DEDUCTION` when SD is consumed, creates CHECKOUT invoice for **new charges only**, closes occupancy/tenancy stay, ends ACTIVE enrollments.

**New checkout charges:** prorated rent + unbilled variable service charges + manager damages/manual charges (+ unbilled fixed `MONTHLY_ARREARS` for the open period if not yet invoiced).

**Settlement math:**

- `owedBeforeSD` = unpaid finalized invoice balances + new checkout charges  
- `depositDeduction` = `min(depositBalanceBefore, owedBeforeSD)`  
- `netReceivable` = `owedBeforeSD − depositDeduction`  
- `refundDue` (= `depositRefundDue`) = `depositBalanceBefore − depositDeduction`  
- At most one of `netReceivable` / `refundDue` is > 0.

**Unpaid finish:** manager may confirm with `netReceivable > 0` only with explicit `leaveReceivable: true`. Alternatively collect **full** `netReceivable` via payment before/with confirm. No partial payment at checkout.

**Refund:** only when `refundDue > 0`; separate Owner/Manager action; MVP methods `CASH` | `BANK_TRANSFER` (Razorpay payout deferred).

Illustrative preview (SD covers all owed; refund due):

```json
{
  "tenancyId": "uuid",
  "outstandingReceivables": 12000.00,
  "newCheckoutCharges": 3500.00,
  "depositBalanceBefore": 25000.00,
  "depositDeduction": 15500.00,
  "depositRefundDue": 9500.00,
  "totalReceivable": 15500.00,
  "netReceivable": 0.00,
  "refundDue": 9500.00,
  "currency": "INR"
}
```

Illustrative preview (SD insufficient; leave receivable allowed):

```json
{
  "tenancyId": "uuid",
  "outstandingReceivables": 12000.00,
  "newCheckoutCharges": 3500.00,
  "depositBalanceBefore": 10000.00,
  "depositDeduction": 10000.00,
  "depositRefundDue": 0.00,
  "totalReceivable": 15500.00,
  "netReceivable": 5500.00,
  "refundDue": 0.00,
  "currency": "INR"
}
```
---

## 11. Expenses, notices, documents, tickets

| Method | Path | Purpose |
|---|---|---|
| GET/POST | `/properties/{propertyId}/expense-types` | Categories |
| GET/POST | `/properties/{propertyId}/expenses` | Expenses |
| GET/POST | `/properties/{propertyId}/notices` | Notices |
| PATCH | `/notices/{noticeId}` | Edit |
| POST | `/notices/{noticeId}/publish` | Publish |
| GET/POST | `/properties/{propertyId}/documents` | List / create upload intent (`PENDING_UPLOAD`) |
| POST | `/documents/{documentId}/complete` | HeadObject verify ? **`UPLOADED`** |
| GET | `/documents/{documentId}` | Metadata |
| GET/POST | `/properties/{propertyId}/tickets` | List / create tickets |
| GET | `/tickets/{ticketId}` | Ticket detail |
| PATCH | `/tickets/{ticketId}` | Update ticket/status |

Document statuses: `PENDING_UPLOAD` ? `UPLOADED` ? `ARCHIVED`.  
Notice body is Markdown; sanitize/disable raw HTML on render.

---

## 12. Analytics & exports

| Method | Path | Purpose |
|---|---|---|
| GET | `/properties/{propertyId}/analytics/occupancy` | Occupancy metrics |
| GET | `/properties/{propertyId}/analytics/revenue` | Revenue summary |
| GET | `/properties/{propertyId}/analytics/expenses` | Expense summary |
| GET | `/properties/{propertyId}/exports/{exportType}` | Operational export |

Read-only / derived; do not create financial truth.

---

## 13. Errors

```json
{
  "error": {
    "code": "BED_UNAVAILABLE",
    "message": "The selected bed is no longer available.",
    "details": { "bedId": "uuid" },
    "requestId": "uuid"
  }
}
```

| HTTP | Meaning |
|---|---|
| 400 | Malformed request |
| 401 | Unauthenticated |
| 403 | Access denied |
| 404 | Not found in accessible scope |
| 409 | Lifecycle/concurrency (bed unavailable/blocked, no active occupancy, idempotency conflict) |
| 422 | Business validation |
| 500 | Unexpected; include requestId |

---

## 14. MVP exclusions

- Persisted Transfer resource  
- Free-standing Tenancy/Occupancy CRUD outside workflows  
- Bed/Occupancy-scoped service enrollment  
- Meter/quantity allocation  
- Deposit `ALLOCATION`  
- Transfer-time deposit refund  
- Partial payments / client invoice allocation  
- Separate Complete Move-In endpoint  
- Public manual monthly billing  
- Historical bed-block API  
- Razorpay deposit **payout** (refund methods: CASH / BANK_TRANSFER only in MVP)  
- Taxes  

---

## 15. Version change summary — v1.9 ? v5.0

- Added bed `block` / `unblock` endpoints and derived availability.  
- Added ticket detail GET; tickets backed by DB.  
- Tenant create/list clarified as organization-scoped.  
- Document lifecycle: `PENDING_UPLOAD` / `UPLOADED` / `ARCHIVED`.  
- Replaced tenant-scoped service list with tenancy-scoped list; added minimal `GET /tenancies/{id}`.  
- Clarified tenancy resource policy and move-in payment as Payment façade.  
- Checkout requires `tenancyId` in body; settlement math + `leaveReceivable` unpaid finish; refund methods CASH/BANK_TRANSFER.  
- Transfer: same-property destination; proration diff with optional manager override; preview services = informational.  
- Billing worker fans out; Invoice worker creates MONTHLY invoices.  
- Baseline references updated to the v5.0 pack.
