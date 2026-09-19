"use server";

import { revalidatePath } from "next/cache";

import { requireAccessToken } from "@/lib/auth";
import {
  completeDocument,
  createDocumentIntent,
  deleteDocument,
} from "@/lib/dwellio-api/documents";
import {
  createNotice,
  deleteNotice,
  publishNotice,
} from "@/lib/dwellio-api/notices";
import { createTicket, updateTicket } from "@/lib/dwellio-api/tickets";

function opsPath(orgId: string, propertyId: string, leaf: string) {
  return `/o/${orgId}/p/${propertyId}/${leaf}`;
}

export async function createTicketAction(formData: FormData) {
  const orgId = String(formData.get("orgId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const token = await requireAccessToken();
  await createTicket(token, propertyId, {
    title: String(formData.get("title") ?? "").trim(),
    body: String(formData.get("body") ?? "").trim() || undefined,
    status: "OPEN",
  });
  revalidatePath(opsPath(orgId, propertyId, "tickets"));
}

export async function updateTicketStatusAction(formData: FormData) {
  const orgId = String(formData.get("orgId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const ticketId = String(formData.get("ticketId") ?? "");
  const status = String(formData.get("status") ?? "") as
    | "OPEN"
    | "IN_PROGRESS"
    | "RESOLVED"
    | "CLOSED";
  const token = await requireAccessToken();
  await updateTicket(token, ticketId, { status });
  revalidatePath(opsPath(orgId, propertyId, "tickets"));
  revalidatePath(opsPath(orgId, propertyId, `tickets/${ticketId}`));
}

export async function createNoticeAction(formData: FormData) {
  const orgId = String(formData.get("orgId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const token = await requireAccessToken();
  await createNotice(token, propertyId, {
    title: String(formData.get("title") ?? "").trim(),
    body: String(formData.get("body") ?? ""),
  });
  revalidatePath(opsPath(orgId, propertyId, "notices"));
}

export async function publishNoticeAction(formData: FormData) {
  const orgId = String(formData.get("orgId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const noticeId = String(formData.get("noticeId") ?? "");
  const token = await requireAccessToken();
  await publishNotice(token, noticeId);
  revalidatePath(opsPath(orgId, propertyId, "notices"));
}

export async function deleteNoticeAction(formData: FormData) {
  const orgId = String(formData.get("orgId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const noticeId = String(formData.get("noticeId") ?? "");
  const token = await requireAccessToken();
  await deleteNotice(token, noticeId);
  revalidatePath(opsPath(orgId, propertyId, "notices"));
}

export async function uploadDocumentAction(formData: FormData) {
  const orgId = String(formData.get("orgId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const name = String(formData.get("name") ?? "").trim();
  const file = formData.get("file");
  if (!(file instanceof File) || !name) {
    throw new Error("Name and file are required");
  }
  const token = await requireAccessToken();
  const intent = await createDocumentIntent(token, propertyId, {
    name,
    contentType: file.type || "application/octet-stream",
  });
  const upload = await fetch(intent.uploadUrl, {
    method: "PUT",
    headers: {
      "Content-Type": file.type || "application/octet-stream",
    },
    body: file,
  });
  if (!upload.ok) {
    throw new Error(`Upload failed (${upload.status})`);
  }
  await completeDocument(token, intent.document.id);
  revalidatePath(opsPath(orgId, propertyId, "documents"));
}

export async function deleteDocumentAction(formData: FormData) {
  const orgId = String(formData.get("orgId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const documentId = String(formData.get("documentId") ?? "");
  const token = await requireAccessToken();
  await deleteDocument(token, documentId);
  revalidatePath(opsPath(orgId, propertyId, "documents"));
}
