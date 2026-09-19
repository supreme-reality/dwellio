import { apiFetch } from "./client";

export type ExpenseType = {
  id: string;
  propertyId: string;
  name: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type Expense = {
  id: string;
  propertyId: string;
  expenseTypeId: string;
  amount: number | string;
  currency: string;
  incurredOn: string;
  notes: string | null;
  createdByUserId: string;
  createdAt: string;
  updatedAt: string;
};

export function listExpenseTypes(token: string, propertyId: string) {
  return apiFetch<ExpenseType[]>(
    `/api/v1/properties/${propertyId}/expense-types`,
    { token },
  );
}

export function createExpenseType(
  token: string,
  propertyId: string,
  body: { name: string },
) {
  return apiFetch<ExpenseType>(
    `/api/v1/properties/${propertyId}/expense-types`,
    { method: "POST", token, body },
  );
}

export function listExpenses(token: string, propertyId: string) {
  return apiFetch<Expense[]>(`/api/v1/properties/${propertyId}/expenses`, {
    token,
  });
}

export function createExpense(
  token: string,
  propertyId: string,
  body: {
    expenseTypeId: string;
    amount: number;
    incurredOn: string;
    notes?: string;
  },
) {
  return apiFetch<Expense>(`/api/v1/properties/${propertyId}/expenses`, {
    method: "POST",
    token,
    body,
  });
}

export function deleteExpense(token: string, expenseId: string) {
  return apiFetch<void>(`/api/v1/expenses/${expenseId}`, {
    method: "DELETE",
    token,
  });
}
