import { apiFetch } from "./client";

export type OccupancyAnalytics = {
  totalBeds: number;
  occupiedBeds: number;
  blockedBeds: number;
  occupancyRate: number | string;
};

export type RevenueAnalytics = {
  currency: string;
  paidAmount: number | string;
  finalizedInvoiceAmount: number | string;
};

export type ExpenseAnalytics = {
  currency: string;
  totalAmount: number | string;
  byType: { expenseTypeId: string; name: string; totalAmount: number | string }[];
};

export function getOccupancyAnalytics(token: string, propertyId: string) {
  return apiFetch<OccupancyAnalytics>(
    `/api/v1/properties/${propertyId}/analytics/occupancy`,
    { token },
  );
}

export function getRevenueAnalytics(
  token: string,
  propertyId: string,
  from?: string,
  to?: string,
) {
  const params = new URLSearchParams();
  if (from) params.set("from", from);
  if (to) params.set("to", to);
  const qs = params.toString();
  return apiFetch<RevenueAnalytics>(
    `/api/v1/properties/${propertyId}/analytics/revenue${qs ? `?${qs}` : ""}`,
    { token },
  );
}

export function getExpenseAnalytics(
  token: string,
  propertyId: string,
  from?: string,
  to?: string,
) {
  const params = new URLSearchParams();
  if (from) params.set("from", from);
  if (to) params.set("to", to);
  const qs = params.toString();
  return apiFetch<ExpenseAnalytics>(
    `/api/v1/properties/${propertyId}/analytics/expenses${qs ? `?${qs}` : ""}`,
    { token },
  );
}

export async function downloadExportCsv(
  token: string,
  propertyId: string,
  exportType: "tenants" | "expenses" | "invoices",
): Promise<string> {
  const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL;
  if (!baseUrl) throw new Error("NEXT_PUBLIC_API_BASE_URL is not configured");
  const response = await fetch(
    `${baseUrl}/api/v1/properties/${propertyId}/exports/${exportType}`,
    {
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: "text/csv",
      },
      cache: "no-store",
    },
  );
  if (!response.ok) {
    throw new Error(`Export failed (${response.status})`);
  }
  return response.text();
}
