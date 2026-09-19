import { apiFetch } from "./client";

export type Invoice = {
  id: string;
  tenancyId: string;
  invoiceType: string;
  billingPeriod: string | null;
  billingDate: string | null;
  dueDate: string | null;
  status: string;
  currency: string;
  subtotal: number | string;
  total: number | string;
  finalizedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type DepositSummary = {
  tenancyId: string;
  balance: number | string;
  totalReceipts: number | string;
  totalDeductions: number | string;
  totalRefunds: number | string;
  currency: string;
};

export type DepositLedgerEntry = {
  id: string;
  type: string;
  amount: number | string;
  reference: string | null;
  notes: string | null;
  createdAt: string;
};

export type DepositLedger = {
  tenancyId: string;
  balance: number | string;
  entries: DepositLedgerEntry[];
};

export type Payment = {
  id: string;
  organizationId: string;
  tenancyId: string;
  paymentMethod: string;
  amount: number | string;
  currency: string;
  status: string;
  externalReference: string | null;
  bankTransferReference: string | null;
  idempotencyKey: string | null;
  confirmedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export function listPropertyInvoices(token: string, propertyId: string) {
  return apiFetch<Invoice[]>(`/api/v1/properties/${propertyId}/invoices`, {
    token,
  });
}

export function listTenancyInvoices(token: string, tenancyId: string) {
  return apiFetch<Invoice[]>(`/api/v1/tenancies/${tenancyId}/invoices`, {
    token,
  });
}

export function getInvoice(token: string, invoiceId: string) {
  return apiFetch<Invoice>(`/api/v1/invoices/${invoiceId}`, { token });
}

export function getDepositSummary(token: string, tenancyId: string) {
  return apiFetch<DepositSummary>(`/api/v1/tenancies/${tenancyId}/deposit`, {
    token,
  });
}

export function getDepositLedger(token: string, tenancyId: string) {
  return apiFetch<DepositLedger>(
    `/api/v1/tenancies/${tenancyId}/deposit/ledger`,
    { token },
  );
}

export function getPayment(token: string, paymentId: string) {
  return apiFetch<Payment>(`/api/v1/payments/${paymentId}`, { token });
}

export function confirmPayment(token: string, paymentId: string) {
  return apiFetch<Payment>(`/api/v1/payments/${paymentId}/confirm`, {
    method: "POST",
    token,
  });
}
