"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";

import { requireAccessToken } from "@/lib/auth";
import {
  cancelMoveIn,
  createMoveIn,
  payMoveIn,
  updateMoveIn,
} from "@/lib/dwellio-api/moveIns";
import { createTenant } from "@/lib/dwellio-api/tenants";

export async function createTenantAction(formData: FormData) {
  const orgId = String(formData.get("orgId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const token = await requireAccessToken();
  await createTenant(token, propertyId, {
    firstName: String(formData.get("firstName") ?? "").trim(),
    lastName: String(formData.get("lastName") ?? "").trim() || undefined,
    phone: String(formData.get("phone") ?? "").trim(),
    email: String(formData.get("email") ?? "").trim() || undefined,
    notes: String(formData.get("notes") ?? "").trim() || undefined,
  });
  revalidatePath(`/o/${orgId}/p/${propertyId}/tenants`);
}

export async function startMoveInAction(formData: FormData) {
  const orgId = String(formData.get("orgId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const tenantId = String(formData.get("tenantId") ?? "");
  const bedId = String(formData.get("bedId") ?? "");
  const moveInDate = String(formData.get("moveInDate") ?? "");
  const token = await requireAccessToken();
  const moveIn = await createMoveIn(token, propertyId, {
    tenantId,
    bedId,
    moveInDate,
    serviceSelections: [],
  });
  redirect(`/o/${orgId}/p/${propertyId}/move-ins/${moveIn.id}`);
}

export async function updateMoveInAction(formData: FormData) {
  const orgId = String(formData.get("orgId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const moveInId = String(formData.get("moveInId") ?? "");
  const bedId = String(formData.get("bedId") ?? "").trim();
  const moveInDate = String(formData.get("moveInDate") ?? "").trim();
  const serviceIds = formData.getAll("serviceId").map(String);
  const token = await requireAccessToken();
  await updateMoveIn(token, moveInId, {
    bedId: bedId || undefined,
    moveInDate: moveInDate || undefined,
    serviceSelections: serviceIds.map((serviceId) => ({ serviceId })),
  });
  revalidatePath(`/o/${orgId}/p/${propertyId}/move-ins/${moveInId}`);
}

export async function cancelMoveInAction(formData: FormData) {
  const orgId = String(formData.get("orgId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const moveInId = String(formData.get("moveInId") ?? "");
  const token = await requireAccessToken();
  await cancelMoveIn(token, moveInId);
  redirect(`/o/${orgId}/p/${propertyId}/tenants`);
}

export async function payMoveInAction(formData: FormData) {
  const orgId = String(formData.get("orgId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const moveInId = String(formData.get("moveInId") ?? "");
  const paymentMethod = String(formData.get("paymentMethod") ?? "") as
    | "CASH"
    | "BANK_TRANSFER"
    | "RAZORPAY";
  const amount = Number(formData.get("amount"));
  const currency = String(formData.get("currency") ?? "INR");
  const depositAmountRaw = String(formData.get("depositAmount") ?? "").trim();
  const bankTransferReference = String(
    formData.get("bankTransferReference") ?? "",
  ).trim();
  const token = await requireAccessToken();
  const result = await payMoveIn(token, moveInId, {
    paymentMethod,
    amount,
    currency,
    depositAmount: depositAmountRaw ? Number(depositAmountRaw) : undefined,
    bankTransferReference: bankTransferReference || undefined,
    idempotencyKey: crypto.randomUUID(),
  });

  if (paymentMethod === "RAZORPAY") {
    if (!result.razorpay?.orderId || !result.razorpay?.keyId) {
      throw new Error("Razorpay checkout was not returned. Payment was not recorded.");
    }
    if (result.razorpay.keyId === "rzp_test_local") {
      throw new Error(
        "Razorpay is not configured on the API. Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET.",
      );
    }
    return {
      mode: "razorpay" as const,
      result,
      tenancyPath: `/o/${orgId}/p/${propertyId}/tenancies/${result.moveIn.tenancyId}`,
    };
  }

  revalidatePath(`/o/${orgId}/p/${propertyId}/move-ins/${moveInId}`);
  redirect(
    `/o/${orgId}/p/${propertyId}/tenancies/${result.moveIn.tenancyId}`,
  );
}
