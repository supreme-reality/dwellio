"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";

import { requireAccessToken } from "@/lib/auth";
import {
  addOrganizationMember,
  createOrganization,
  updateOrganizationMember,
} from "@/lib/dwellio-api/orgs";
import {
  assignPropertyManager,
  createProperty,
  removePropertyManager,
  updateProperty,
} from "@/lib/dwellio-api/properties";

export async function createOrganizationAction(formData: FormData) {
  const name = String(formData.get("name") ?? "").trim();
  if (!name) {
    throw new Error("Organization name is required");
  }
  const token = await requireAccessToken();
  const org = await createOrganization(token, { name });
  redirect(`/o/${org.id}`);
}

export async function createPropertyAction(formData: FormData) {
  const organizationId = String(formData.get("organizationId") ?? "");
  const name = String(formData.get("name") ?? "").trim();
  const address = String(formData.get("address") ?? "").trim();
  const paymentDueDays = Number(formData.get("paymentDueDays") ?? 5);
  const defaultCurrency = String(formData.get("defaultCurrency") ?? "INR")
    .trim()
    .toUpperCase();

  if (!organizationId || !name) {
    throw new Error("Property name is required");
  }

  const token = await requireAccessToken();
  const property = await createProperty(token, organizationId, {
    name,
    address: address || undefined,
    paymentDueDays,
    defaultCurrency,
  });
  redirect(`/o/${organizationId}/p/${property.id}/inventory`);
}

export async function updatePropertyAction(formData: FormData) {
  const organizationId = String(formData.get("organizationId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const name = String(formData.get("name") ?? "").trim();
  const address = String(formData.get("address") ?? "").trim();
  const paymentDueDays = Number(formData.get("paymentDueDays") ?? 5);
  const defaultCurrency = String(formData.get("defaultCurrency") ?? "INR")
    .trim()
    .toUpperCase();

  const token = await requireAccessToken();
  await updateProperty(token, propertyId, {
    name,
    address,
    paymentDueDays,
    defaultCurrency,
  });
  revalidatePath(`/o/${organizationId}/p/${propertyId}/settings/property`);
}

export async function addMemberAction(formData: FormData) {
  const organizationId = String(formData.get("organizationId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const email = String(formData.get("email") ?? "").trim();
  const token = await requireAccessToken();
  await addOrganizationMember(token, organizationId, { email });
  revalidatePath(`/o/${organizationId}/p/${propertyId}/settings/members`);
}

export async function setMemberStatusAction(formData: FormData) {
  const organizationId = String(formData.get("organizationId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const membershipId = String(formData.get("membershipId") ?? "");
  const status = String(formData.get("status") ?? "") as "ACTIVE" | "INACTIVE";
  const token = await requireAccessToken();
  await updateOrganizationMember(token, organizationId, membershipId, {
    status,
  });
  revalidatePath(`/o/${organizationId}/p/${propertyId}/settings/members`);
}

export async function assignManagerAction(formData: FormData) {
  const organizationId = String(formData.get("organizationId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const email = String(formData.get("email") ?? "").trim();
  const token = await requireAccessToken();
  await assignPropertyManager(token, propertyId, { email });
  revalidatePath(`/o/${organizationId}/p/${propertyId}/settings/managers`);
}

export async function removeManagerAction(formData: FormData) {
  const organizationId = String(formData.get("organizationId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const propertyMembershipId = String(
    formData.get("propertyMembershipId") ?? "",
  );
  const token = await requireAccessToken();
  await removePropertyManager(token, propertyId, propertyMembershipId);
  revalidatePath(`/o/${organizationId}/p/${propertyId}/settings/managers`);
}
