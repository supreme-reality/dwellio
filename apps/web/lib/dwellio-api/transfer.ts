import { apiFetch } from "./client";
import type { ServiceEnrollment } from "./services";

export type TransferPreview = {
  tenancyId: string;
  sourceBedId: string;
  destinationBedId: string;
  destinationAvailable: boolean;
  sourceRent: number | string;
  destinationRent: number | string;
  prorationDiff: number | string;
  chargeAmount: number | string;
  currency: string;
  currentEnrollments: ServiceEnrollment[];
};

export function previewTransfer(
  token: string,
  body: {
    tenancyId: string;
    destinationBedId: string;
    prorationOverrideAmount?: number | null;
  },
) {
  return apiFetch<TransferPreview>("/api/v1/transfers/preview", {
    method: "POST",
    token,
    body,
  });
}

export function executeTransfer(
  token: string,
  body: {
    tenancyId: string;
    destinationBedId: string;
    prorationOverrideAmount?: number | null;
  },
) {
  return apiFetch("/api/v1/transfers", {
    method: "POST",
    token,
    body,
  });
}
