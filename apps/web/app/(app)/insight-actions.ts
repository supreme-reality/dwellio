"use server";

import { revalidatePath } from "next/cache";

import { requireAccessToken } from "@/lib/auth";
import {
  createExpense,
  createExpenseType,
  deleteExpense,
} from "@/lib/dwellio-api/expenses";

export async function createExpenseTypeAction(formData: FormData) {
  const orgId = String(formData.get("orgId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const token = await requireAccessToken();
  await createExpenseType(token, propertyId, {
    name: String(formData.get("name") ?? "").trim(),
  });
  revalidatePath(`/o/${orgId}/p/${propertyId}/expenses`);
}

export async function createExpenseAction(formData: FormData) {
  const orgId = String(formData.get("orgId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const token = await requireAccessToken();
  await createExpense(token, propertyId, {
    expenseTypeId: String(formData.get("expenseTypeId") ?? ""),
    amount: Number(formData.get("amount")),
    incurredOn: String(formData.get("incurredOn") ?? ""),
    notes: String(formData.get("notes") ?? "").trim() || undefined,
  });
  revalidatePath(`/o/${orgId}/p/${propertyId}/expenses`);
}

export async function deleteExpenseAction(formData: FormData) {
  const orgId = String(formData.get("orgId") ?? "");
  const propertyId = String(formData.get("propertyId") ?? "");
  const expenseId = String(formData.get("expenseId") ?? "");
  const token = await requireAccessToken();
  await deleteExpense(token, expenseId);
  revalidatePath(`/o/${orgId}/p/${propertyId}/expenses`);
}
