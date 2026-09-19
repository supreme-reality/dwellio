"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";

import { requireAccessToken } from "@/lib/auth";
import {
  confirmCheckout,
  previewCheckout,
  refundSettlement,
} from "@/lib/dwellio-api/checkout";
import {
  executeTransfer,
  previewTransfer,
} from "@/lib/dwellio-api/transfer";

export async function previewTransferAction(formData: FormData) {
  const tenancyId = String(formData.get("tenancyId") ?? "");
  const destinationBedId = String(formData.get("destinationBedId") ?? "");
  const overrideRaw = String(formData.get("prorationOverrideAmount") ?? "").trim();
  const token = await requireAccessToken();
  return previewTransfer(token, {
    tenancyId,
    destinationBedId,
    prorationOverrideAmount: overrideRaw ? Number(overrideRaw) : null,
  });
}

export async function executeTransferAction(formData: FormData) {
  const orgId = String(formData.get("orgId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const tenancyId = String(formData.get("tenancyId") ?? "");
  const destinationBedId = String(formData.get("destinationBedId") ?? "");
  const overrideRaw = String(formData.get("prorationOverrideAmount") ?? "").trim();
  const token = await requireAccessToken();
  await executeTransfer(token, {
    tenancyId,
    destinationBedId,
    prorationOverrideAmount: overrideRaw ? Number(overrideRaw) : null,
  });
  revalidatePath(`/o/${orgId}/p/${propertyId}/tenancies/${tenancyId}`);
  redirect(`/o/${orgId}/p/${propertyId}/tenancies/${tenancyId}`);
}

export async function previewCheckoutAction(formData: FormData) {
  const tenancyId = String(formData.get("tenancyId") ?? "");
  const damagesAmount = Number(formData.get("damagesAmount") ?? 0);
  const manualChargesAmount = Number(formData.get("manualChargesAmount") ?? 0);
  const token = await requireAccessToken();
  return previewCheckout(token, {
    tenancyId,
    damagesAmount,
    manualChargesAmount,
  });
}

export async function confirmCheckoutAction(formData: FormData) {
  const orgId = String(formData.get("orgId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const tenancyId = String(formData.get("tenancyId") ?? "");
  const damagesAmount = Number(formData.get("damagesAmount") ?? 0);
  const manualChargesAmount = Number(formData.get("manualChargesAmount") ?? 0);
  const leaveReceivable = String(formData.get("leaveReceivable") ?? "") === "true";
  const token = await requireAccessToken();
  const result = await confirmCheckout(token, {
    tenancyId,
    damagesAmount,
    manualChargesAmount,
    leaveReceivable,
  });
  redirect(
    `/o/${orgId}/p/${propertyId}/tenancies/${tenancyId}/checkout/done?settlementId=${result.settlementId}`,
  );
}

export async function refundSettlementAction(formData: FormData) {
  const orgId = String(formData.get("orgId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const tenancyId = String(formData.get("tenancyId") ?? "");
  const settlementId = String(formData.get("settlementId") ?? "");
  const paymentMethod = String(formData.get("paymentMethod") ?? "") as
    | "CASH"
    | "BANK_TRANSFER";
  const bankTransferReference = String(
    formData.get("bankTransferReference") ?? "",
  ).trim();
  const token = await requireAccessToken();
  await refundSettlement(token, settlementId, {
    paymentMethod,
    bankTransferReference: bankTransferReference || undefined,
  });
  revalidatePath(
    `/o/${orgId}/p/${propertyId}/tenancies/${tenancyId}/checkout/done`,
  );
}
