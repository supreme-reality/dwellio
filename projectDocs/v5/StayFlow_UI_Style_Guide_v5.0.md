# StayFlow — UI Style Guide v5.0

**Controlled implementation-level UI guidance**  
**Aligned with:** Project Context v5.0 · UX Flows v5.0 · FR v5.0 · API Contract v5.0 · DB Schema v5.0 · Architecture v5.0

Supersedes UI Style Guide v1.3.

---

## 1. Principles

- Clarity over decoration; lifecycle and money immediately understandable.  
- Preview before commitment.  
- Separate calculated amounts from executed transactions.  
- Keep organization/property context visible.

---

## 2. Context & navigation

- Organization ? Property.  
- Tenant may show many Tenancies; active stay distinguished.  
- Tenancy is the stay context for occupancy, services, deposit, invoices.

---

## 3. Move-in UI

**Stepper:** Tenant ? Bed ? Details ? Services ? Upfront ? Confirm & Pay / Record Payment  

- Selecting tenant for a new stay creates a **new Tenancy** (state that explicitly).  
- Draft does not reserve; no ACTIVE Occupancy implied.  
- Tenancy-level services only.  
- No second Complete Move-In.  
- Bed conflict ? require new selection.

---

## 4. Bed availability & blocking

- Badges: `AVAILABLE` / `OCCUPIED` / `BLOCKED`.  
- Blocked shows reason.  
- Block action requires reason field; Unblock is explicit confirm.  
- Do not imply draft selection is a hold.

---

## 5. Rent, services, transfer

- Rent hierarchy + effective vs scheduled.  
- Services in Tenancy context; restart = new enrollment.  
- Transfer: within Tenancy / same property; preview destination rent (proration diff) + current services; optional override; deposit stays; no transfer refund controls.

---

## 6. Financial UI

- Labels: Amount Due, Payment Confirmed, Refundable Balance, Refund Paid.  
- Deposit history RECEIPT/DEDUCTION/REFUND only.  
- Checkout preview separates unpaid invoices / new charges / deposit application / netReceivable / refundDue.  
- Checkout confirm: collect full net **or** explicit “leave receivable” — no partial.  
- After checkout, show open receivables on the ended stay.  
- Refund only when refundDue > 0; CASH / bank transfer controls.  
- No partial payment or manual invoice allocation controls.  
- No Run Billing.

---

## 7. Documents, notices, tickets

- Document status labels: Pending upload → Uploaded (delete action; no Archived).  
- Notices: Markdown; draft/publish/delete; no raw HTML editing.  
- Tickets: status badges Open / In progress / Resolved / Closed.

---

## 8. Accessibility & consistency

- Accessible labels, keyboard focus, sufficient contrast.  
- Consistent date/currency/status formatting.  
- Do not communicate critical state by color alone.

---

## 9. MVP UI exclusions

Same as UX v5.0 exclusions.

---

## 10. Version change — v1.3 ? v5.0

- Bed block UI patterns.  
- Document status vocabulary (Pending upload / Uploaded; delete).  
- Tickets UI.  
- Explicit new-Tenancy move-in copy.  
- Aligned to v5.0 controlled pack.
