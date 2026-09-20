import { apiFetch } from "./client";

export type CheckoutPreview = {
  tenancyId: string;
  occupancyId: string;
  outstandingReceivables: number | string;
  newCheckoutCharges: number | string;
  rentProration: number | string;
  damagesAmount: number | string;
  manualChargesAmount: number | string;
  variableChargesAmount: number | string;
  fixedMonthlyArrearsAmount: number | string;
  depositBalanceBefore: number | string;
  depositDeduction: number | string;
  depositRefundDue: number | string;
  totalReceivable: number | string;
  netReceivable: number | string;
  refundDue: number | string;
  currency: string;
};

export type CheckoutConfirm = {
  settlementId: string;
  tenancyId: string;
  checkoutInvoiceId: string | null;
  outstandingReceivables: number | string;
  newCheckoutCharges: number | string;
  depositBalanceBefore: number | string;
  depositDeduction: number | string;
  depositRefundDue: number | string;
  totalReceivable: number | string;
  netReceivable: number | string;
  refundDue: number | string;
  currency: string;
  razorpay?: {
    keyId: string;
    orderId: string;
    amount: number | string;
    currency: string;
  } | null;
};

export type Settlement = {
  id: string;
  tenancyId: string;
  occupancyId: string;
  checkoutDate: string;
  outstandingReceivable: number | string;
  newCheckoutCharges: number | string;
  depositBalanceBefore: number | string;
  depositDeduction: number | string;
  depositRefundDue: number | string;
  totalReceivable: number | string;
  netReceivable: number | string;
  refundDue: number | string;
  currency: string;
  status: string;
  confirmedAt: string | null;
  createdAt: string;
};

export function previewCheckout(
  token: string,
  body: {
    tenancyId: string;
    damagesAmount?: number;
    manualChargesAmount?: number;
  },
) {
  return apiFetch<CheckoutPreview>("/api/v1/checkout/preview", {
    method: "POST",
    token,
    body,
  });
}

export function confirmCheckout(
  token: string,
  body: {
    tenancyId: string;
    damagesAmount?: number;
    manualChargesAmount?: number;
    leaveReceivable?: boolean;
    paymentMethod?: "CASH" | "BANK_TRANSFER" | "RAZORPAY";
    bankTransferReference?: string;
    idempotencyKey?: string;
  },
) {
  return apiFetch<CheckoutConfirm>("/api/v1/checkout", {
    method: "POST",
    token,
    body,
  });
}

export function getSettlement(token: string, settlementId: string) {
  return apiFetch<Settlement>(`/api/v1/settlements/${settlementId}`, {
    token,
  });
}

export function refundSettlement(
  token: string,
  settlementId: string,
  body: {
    paymentMethod: "CASH" | "BANK_TRANSFER";
    bankTransferReference?: string;
  },
) {
  return apiFetch(`/api/v1/settlements/${settlementId}/refund`, {
    method: "POST",
    token,
    body,
  });
}
