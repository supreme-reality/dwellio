# StayFlow — UX Flows v5.0

**Controlled UX baseline**  
**Aligned with:** Project Context v5.0 · FR v5.0 · API Contract v5.0 · DB Schema v5.0 · Architecture v5.0 · UI Style Guide v5.0

Supersedes UX Flows v1.6.

---

## 1. Principles

- Organization ? Property context.  
- Tenant / Tenancy / Occupancy distinctions visible.  
- New stay ? new Tenancy; never silently reopen.  
- Financial previews visually distinct from executed transactions.  
- Preview before commit for transfer, checkout, and consequential money actions.

---

## 2. Move-in

**Flow:** Start ? Tenant ? New Tenancy ? Available Bed ? Details ? Services ? Upfront ? Confirm & Pay / Record Payment ? Activation

- Draft: no reservation, no ACTIVE Occupancy.  
- Services are Tenancy-scoped.  
- Success auto-completes; no second Complete Move-In.  
- Payment failure keeps Draft.  
- Pre-activation recheck; if bed blocked/occupied, require new selection.

---

## 3. Tenancy & occupancy views

- Tenant detail: historical Tenancies; active stay distinguished.  
- Tenancy detail: Occupancies, **services**, deposit, invoices for that stay.  
- Historical Occupancies = transfer history, not current presence.

---

## 4. Bed availability & blocking

- Show `AVAILABLE` / `OCCUPIED` / `BLOCKED`.  
- Blocked beds show reason.  
- Owner or assigned Manager can Block (reason required) / Unblock, including occupied beds.  
- Draft selection must not look reserved.  
- Conflicts require explicit re-selection (never silent substitute).  
- No historical block UI in MVP.

---

## 5. Rent & services

- Rent hierarchy Property ? Room ? Bed; show effective vs scheduled.  
- Services: property catalog; enroll/end/variable charge in **Tenancy** context.  
- Transfer preview: destination rent + list of **current** tenancy services (unchanged by transfer).

---

## 6. Transfer

Active Occupancy → same-property Destination Bed → Preview rent (proration diff) & current services → optional manager override → Confirm → New ACTIVE Occupancy  
Deposit stays with Tenancy; no transfer refund UI.

---

## 7. Deposit, billing, checkout, payments

- Deposit in Tenancy context; RECEIPT/DEDUCTION/REFUND; no ALLOCATION.  
- Distinguish Refundable Balance vs Refund Paid.  
- Invoices filterable by property/tenant/tenancy/period; no Run Billing.  
- Checkout from specific Tenancy; settlement preview breakdown; pay **full** netReceivable **or** explicit leave unpaid (`leaveReceivable`); no partial.  
- Checked-out tenancy still shows open receivables for later collection.  
- Separate refund action when refundDue > 0 (CASH / bank transfer).  
- One payment UX for Razorpay/Cash/Bank Transfer.

---

## 8. Documents, notices, tickets

- Documents: uploading → uploaded; delete removes file (labels match API; no Archived).  
- Notices: property Markdown; draft/publish/delete; no targeting/groups/receipts.  
- Tickets: property list/detail; statuses Open / In progress / Resolved / Closed.

---

## 9. State & errors

- Labels: Draft, Active, Ended, Cancelled, Paid, Pending, Blocked, Available, Occupied.  
- Actionable conflict copy for bed unavailable/blocked.

---

## 10. MVP UX exclusions

Transfer history resource UI; bed-tied services; meter UI; ALLOCATION; transfer refund; Complete Move-In; partial pay; manual allocation; Run Billing; historical block history; notice targeting; notice/document archive; taxes; Razorpay deposit payout.

---

## 11. Version change — v1.6 ? v5.0

- Bed block/unblock UX + availability labels.  
- Tenancy-scoped services views.  
- Document upload status labels.  
- Notice/document delete (no archive).  
- Tickets journeys.  
- Org-scoped tenant identity called out.  
- Pack alignment to v5.0.
