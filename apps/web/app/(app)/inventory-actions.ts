"use server";

import { revalidatePath } from "next/cache";

import { requireAccessToken } from "@/lib/auth";
import {
  blockBed,
  createBed,
  createRoom,
  unblockBed,
  updateBed,
  updateRoom,
} from "@/lib/dwellio-api/inventory";

function inventoryPath(orgId: string, propertyId: string) {
  return `/o/${orgId}/p/${propertyId}/inventory`;
}

export async function createRoomAction(formData: FormData) {
  const orgId = String(formData.get("orgId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const name = String(formData.get("name") ?? "").trim();
  const token = await requireAccessToken();
  await createRoom(token, propertyId, { name });
  revalidatePath(inventoryPath(orgId, propertyId));
}

export async function updateRoomAction(formData: FormData) {
  const orgId = String(formData.get("orgId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const roomId = String(formData.get("roomId") ?? "");
  const name = String(formData.get("name") ?? "").trim();
  const status = String(formData.get("status") ?? "") as "ACTIVE" | "INACTIVE";
  const token = await requireAccessToken();
  await updateRoom(token, roomId, {
    name: name || undefined,
    status: status || undefined,
  });
  revalidatePath(inventoryPath(orgId, propertyId));
}

export async function createBedAction(formData: FormData) {
  const orgId = String(formData.get("orgId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const roomId = String(formData.get("roomId") ?? "");
  const name = String(formData.get("name") ?? "").trim();
  const token = await requireAccessToken();
  await createBed(token, roomId, { name });
  revalidatePath(inventoryPath(orgId, propertyId));
}

export async function updateBedAction(formData: FormData) {
  const orgId = String(formData.get("orgId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const bedId = String(formData.get("bedId") ?? "");
  const name = String(formData.get("name") ?? "").trim();
  const status = String(formData.get("status") ?? "") as "ACTIVE" | "INACTIVE";
  const token = await requireAccessToken();
  await updateBed(token, bedId, {
    name: name || undefined,
    status: status || undefined,
  });
  revalidatePath(inventoryPath(orgId, propertyId));
}

export async function blockBedAction(formData: FormData) {
  const orgId = String(formData.get("orgId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const bedId = String(formData.get("bedId") ?? "");
  const reason = String(formData.get("reason") ?? "").trim();
  if (!reason) {
    throw new Error("Block reason is required");
  }
  const token = await requireAccessToken();
  await blockBed(token, bedId, { reason });
  revalidatePath(inventoryPath(orgId, propertyId));
}

export async function unblockBedAction(formData: FormData) {
  const orgId = String(formData.get("orgId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const bedId = String(formData.get("bedId") ?? "");
  const token = await requireAccessToken();
  await unblockBed(token, bedId);
  revalidatePath(inventoryPath(orgId, propertyId));
}
