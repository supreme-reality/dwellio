"use server";

import { revalidatePath } from "next/cache";

import { requireAccessToken } from "@/lib/auth";
import {
  createBedRent,
  createPropertyRent,
  createRoomRent,
} from "@/lib/dwellio-api/rent";
import {
  createService,
  createServiceConfig,
  endEnrollment,
  enrollService,
  recordServiceCharge,
} from "@/lib/dwellio-api/services";

function rentPath(orgId: string, propertyId: string) {
  return `/o/${orgId}/p/${propertyId}/rent`;
}

function servicesPath(orgId: string, propertyId: string) {
  return `/o/${orgId}/p/${propertyId}/services`;
}

function tenancyPath(orgId: string, propertyId: string, tenancyId: string) {
  return `/o/${orgId}/p/${propertyId}/tenancies/${tenancyId}`;
}

export async function createRentAction(formData: FormData) {
  const orgId = String(formData.get("orgId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const level = String(formData.get("level") ?? "PROPERTY");
  const roomId = String(formData.get("roomId") ?? "");
  const bedId = String(formData.get("bedId") ?? "");
  const amount = Number(formData.get("amount"));
  const effectiveFrom = String(formData.get("effectiveFrom") ?? "");
  const effectiveToRaw = String(formData.get("effectiveTo") ?? "").trim();
  const body = {
    amount,
    effectiveFrom,
    effectiveTo: effectiveToRaw || null,
  };
  const token = await requireAccessToken();
  if (level === "ROOM" && roomId) {
    await createRoomRent(token, roomId, body);
  } else if (level === "BED" && bedId) {
    await createBedRent(token, bedId, body);
  } else {
    await createPropertyRent(token, propertyId, body);
  }
  revalidatePath(rentPath(orgId, propertyId));
}

export async function createServiceAction(formData: FormData) {
  const orgId = String(formData.get("orgId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const token = await requireAccessToken();
  await createService(token, propertyId, {
    name: String(formData.get("name") ?? "").trim(),
    billingType: String(formData.get("billingType") ?? "FIXED") as
      | "FIXED"
      | "VARIABLE",
    billingTiming: String(formData.get("billingTiming") ?? "MONTHLY_ARREARS") as
      | "UPFRONT"
      | "MONTHLY_ARREARS",
    mandatory: String(formData.get("mandatory") ?? "false") === "true",
    prorationSetting: String(formData.get("prorationSetting") ?? "NONE"),
  });
  revalidatePath(servicesPath(orgId, propertyId));
}

export async function createServiceConfigAction(formData: FormData) {
  const orgId = String(formData.get("orgId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const serviceId = String(formData.get("serviceId") ?? "");
  const token = await requireAccessToken();
  await createServiceConfig(token, serviceId, {
    amount: Number(formData.get("amount")),
    effectiveFrom: String(formData.get("effectiveFrom") ?? ""),
  });
  revalidatePath(servicesPath(orgId, propertyId));
}

export async function enrollServiceAction(formData: FormData) {
  const orgId = String(formData.get("orgId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const tenancyId = String(formData.get("tenancyId") ?? "");
  const token = await requireAccessToken();
  await enrollService(token, tenancyId, {
    serviceId: String(formData.get("serviceId") ?? ""),
    startedAt: String(formData.get("startedAt") ?? ""),
  });
  revalidatePath(tenancyPath(orgId, propertyId, tenancyId));
}

export async function endEnrollmentAction(formData: FormData) {
  const orgId = String(formData.get("orgId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const tenancyId = String(formData.get("tenancyId") ?? "");
  const enrollmentId = String(formData.get("enrollmentId") ?? "");
  const token = await requireAccessToken();
  await endEnrollment(token, enrollmentId, {
    endedAt: String(formData.get("endedAt") ?? ""),
  });
  revalidatePath(tenancyPath(orgId, propertyId, tenancyId));
}

export async function recordChargeAction(formData: FormData) {
  const orgId = String(formData.get("orgId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const tenancyId = String(formData.get("tenancyId") ?? "");
  const enrollmentId = String(formData.get("enrollmentId") ?? "");
  const token = await requireAccessToken();
  await recordServiceCharge(token, enrollmentId, {
    billingPeriod: String(formData.get("billingPeriod") ?? ""),
    amount: Number(formData.get("amount")),
    note: String(formData.get("note") ?? "") || undefined,
  });
  revalidatePath(tenancyPath(orgId, propertyId, tenancyId));
}
