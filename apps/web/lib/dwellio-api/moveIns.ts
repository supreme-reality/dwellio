import { apiFetch } from "./client";

export type MoveInServiceSelection = {
  serviceId: string;
  selectedAmount: number | string | null;
};

export type MoveIn = {
  id: string;
  tenancyId: string;
  tenancyStatus: string;
  tenantId: string;
  propertyId: string;
  bedId: string;
  moveInDate: string;
  status: string;
  serviceSelections: MoveInServiceSelection[];
  createdAt: string;
  updatedAt: string;
};

export type MoveInPaymentResult = {
  moveIn: MoveIn;
  payment: {
    id: string;
    status: string;
    paymentMethod: string;
    amount: number | string;
    currency: string;
  };
  razorpay?: {
    keyId: string;
    orderId: string;
    amount: number | string;
    currency: string;
  } | null;
};

export function createMoveIn(
  token: string,
  propertyId: string,
  body: {
    tenantId: string;
    bedId: string;
    moveInDate: string;
    serviceSelections?: { serviceId: string; selectedAmount?: number }[];
  },
) {
  return apiFetch<MoveIn>(`/api/v1/properties/${propertyId}/move-ins`, {
    method: "POST",
    token,
    body,
  });
}

export function getMoveIn(token: string, moveInId: string) {
  return apiFetch<MoveIn>(`/api/v1/move-ins/${moveInId}`, { token });
}

export function updateMoveIn(
  token: string,
  moveInId: string,
  body: {
    bedId?: string;
    moveInDate?: string;
    serviceSelections?: { serviceId: string; selectedAmount?: number }[];
  },
) {
  return apiFetch<MoveIn>(`/api/v1/move-ins/${moveInId}`, {
    method: "PATCH",
    token,
    body,
  });
}

export function cancelMoveIn(token: string, moveInId: string) {
  return apiFetch<MoveIn>(`/api/v1/move-ins/${moveInId}/cancel`, {
    method: "POST",
    token,
  });
}

export function payMoveIn(
  token: string,
  moveInId: string,
  body: {
    paymentMethod: "CASH" | "BANK_TRANSFER" | "RAZORPAY";
    amount: number;
    currency: string;
    depositAmount?: number;
    bankTransferReference?: string;
    externalReference?: string;
    idempotencyKey?: string;
  },
) {
  return apiFetch<MoveInPaymentResult>(`/api/v1/move-ins/${moveInId}/payment`, {
    method: "POST",
    token,
    body,
  });
}
