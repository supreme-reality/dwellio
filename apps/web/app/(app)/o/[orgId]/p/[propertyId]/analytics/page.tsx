import { Alert } from "@/components/ui/alert";
import { requireAccessToken } from "@/lib/auth";
import { DwellioApiError } from "@/lib/dwellio-api/client";
import {
  getExpenseAnalytics,
  getOccupancyAnalytics,
  getRevenueAnalytics,
} from "@/lib/dwellio-api/analytics";
import { formatMoney } from "@/lib/format";

export default async function AnalyticsPage({
  params,
  searchParams,
}: {
  params: Promise<{ orgId: string; propertyId: string }>;
  searchParams: Promise<{ from?: string; to?: string }>;
}) {
  const { propertyId } = await params;
  const { from, to } = await searchParams;
  const token = await requireAccessToken();

  try {
    const [occupancy, revenue, expenses] = await Promise.all([
      getOccupancyAnalytics(token, propertyId),
      getRevenueAnalytics(token, propertyId, from, to),
      getExpenseAnalytics(token, propertyId, from, to),
    ]);

    return (
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Analytics</h1>
          <form className="mt-3 flex flex-wrap items-end gap-3 text-sm">
            <label className="flex flex-col gap-1">
              From
              <input
                type="date"
                name="from"
                defaultValue={from}
                className="rounded-md border border-neutral-300 px-2 py-1"
              />
            </label>
            <label className="flex flex-col gap-1">
              To
              <input
                type="date"
                name="to"
                defaultValue={to}
                className="rounded-md border border-neutral-300 px-2 py-1"
              />
            </label>
            <button
              type="submit"
              className="rounded-md bg-neutral-900 px-3 py-1.5 text-white"
            >
              Apply range
            </button>
          </form>
        </div>

        <section className="rounded-lg border border-neutral-200 bg-white p-4">
          <h2 className="mb-2 font-semibold">Occupancy (now)</h2>
          <p className="text-sm">
            {occupancy.occupiedBeds}/{occupancy.totalBeds} occupied ·{" "}
            {occupancy.blockedBeds} blocked · rate {String(occupancy.occupancyRate)}
          </p>
        </section>

        <section className="rounded-lg border border-neutral-200 bg-white p-4">
          <h2 className="mb-2 font-semibold">Revenue</h2>
          <p className="text-sm">
            Paid: {formatMoney(revenue.paidAmount, revenue.currency)} ·
            Finalized invoices:{" "}
            {formatMoney(revenue.finalizedInvoiceAmount, revenue.currency)}
          </p>
        </section>

        <section className="rounded-lg border border-neutral-200 bg-white p-4">
          <h2 className="mb-2 font-semibold">Expenses</h2>
          <p className="mb-2 text-sm">
            Total: {formatMoney(expenses.totalAmount, expenses.currency)}
          </p>
          <ul className="space-y-1 text-sm">
            {expenses.byType.map((row) => (
              <li key={row.expenseTypeId}>
                {row.name}: {formatMoney(row.totalAmount, expenses.currency)}
              </li>
            ))}
          </ul>
        </section>
      </div>
    );
  } catch (error) {
    if (error instanceof DwellioApiError) {
      return (
        <Alert tone="error" requestId={error.requestId}>
          {error.message}
        </Alert>
      );
    }
    throw error;
  }
}
