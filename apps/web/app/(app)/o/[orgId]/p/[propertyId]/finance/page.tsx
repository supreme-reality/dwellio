import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/ui/empty-state";
import { requireAccessToken } from "@/lib/auth";
import { DwellioApiError } from "@/lib/dwellio-api/client";
import {
  getDepositLedger,
  getDepositSummary,
  listPropertyInvoices,
  listTenancyInvoices,
  type DepositLedgerEntry,
  type Invoice,
} from "@/lib/dwellio-api/finance";
import { getProperty } from "@/lib/dwellio-api/properties";
import { formatDate, formatMoney } from "@/lib/format";
import Link from "next/link";

function invoiceAmountDueLabel(invoice: Invoice): string {
  if (invoice.status === "PAID") return "Payment Confirmed";
  return "Amount Due";
}

export default async function FinancePage({
  params,
  searchParams,
}: {
  params: Promise<{ orgId: string; propertyId: string }>;
  searchParams: Promise<{ tenancyId?: string }>;
}) {
  const { orgId, propertyId } = await params;
  const { tenancyId } = await searchParams;
  const token = await requireAccessToken();

  try {
    const property = await getProperty(token, propertyId);
    const invoices = tenancyId
      ? await listTenancyInvoices(token, tenancyId)
      : await listPropertyInvoices(token, propertyId);

    let depositSummary = null;
    let ledgerEntries: DepositLedgerEntry[] = [];
    if (tenancyId) {
      depositSummary = await getDepositSummary(token, tenancyId);
      const ledger = await getDepositLedger(token, tenancyId);
      ledgerEntries = ledger.entries.filter((entry) =>
        ["RECEIPT", "DEDUCTION", "REFUND"].includes(entry.type),
      );
    }

    return (
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Finance</h1>
          <p className="text-sm text-neutral-600">
            Invoices, deposit ledger, and payment status labels. No Run Billing
            or partial allocation.
          </p>
          {tenancyId ? (
            <p className="mt-1 text-sm">
              Filtered to tenancy{" "}
              <Link
                href={`/o/${orgId}/p/${propertyId}/tenancies/${tenancyId}`}
                className="underline"
              >
                {tenancyId.slice(0, 8)}…
              </Link>
            </p>
          ) : null}
        </div>

        <section className="space-y-3">
          <h2 className="text-lg font-semibold">Invoices</h2>
          {invoices.length === 0 ? (
            <EmptyState title="No invoices" />
          ) : (
            <ul className="divide-y divide-neutral-200 rounded-lg border border-neutral-200 bg-white">
              {invoices.map((invoice) => (
                <li
                  key={invoice.id}
                  className="flex flex-wrap items-center justify-between gap-3 px-4 py-3"
                >
                  <div>
                    <div className="font-medium">
                      {invoice.invoiceType} · {formatDate(invoice.dueDate)}
                    </div>
                    <div className="text-sm text-neutral-600">
                      {invoiceAmountDueLabel(invoice)}:{" "}
                      {formatMoney(invoice.total, invoice.currency)}
                    </div>
                  </div>
                  <Badge
                    tone={invoice.status === "PAID" ? "success" : "warning"}
                  >
                    {invoice.status === "PAID"
                      ? "Payment Confirmed"
                      : invoice.status}
                  </Badge>
                </li>
              ))}
            </ul>
          )}
        </section>

        {tenancyId && depositSummary ? (
          <section className="space-y-3">
            <h2 className="text-lg font-semibold">Deposit</h2>
            <div className="grid gap-3 sm:grid-cols-2 rounded-lg border border-neutral-200 bg-white p-4 text-sm">
              <p>
                Refundable Balance:{" "}
                {formatMoney(
                  depositSummary.balance,
                  depositSummary.currency || property.defaultCurrency,
                )}
              </p>
              <p>
                Refund Paid (total refunds):{" "}
                {formatMoney(
                  depositSummary.totalRefunds,
                  depositSummary.currency || property.defaultCurrency,
                )}
              </p>
              <p>
                Receipts:{" "}
                {formatMoney(
                  depositSummary.totalReceipts,
                  depositSummary.currency || property.defaultCurrency,
                )}
              </p>
              <p>
                Deductions:{" "}
                {formatMoney(
                  depositSummary.totalDeductions,
                  depositSummary.currency || property.defaultCurrency,
                )}
              </p>
            </div>
            <ul className="divide-y divide-neutral-200 rounded-lg border border-neutral-200 bg-white">
              {ledgerEntries.map((entry) => (
                <li
                  key={entry.id}
                  className="flex flex-wrap items-center justify-between gap-2 px-4 py-2 text-sm"
                >
                  <span>
                    <Badge>{entry.type}</Badge> {entry.notes || entry.reference}
                  </span>
                  <span>
                    {formatMoney(
                      entry.amount,
                      depositSummary.currency || property.defaultCurrency,
                    )}
                  </span>
                </li>
              ))}
              {ledgerEntries.length === 0 ? (
                <li className="px-4 py-3 text-sm text-neutral-600">
                  No ledger entries
                </li>
              ) : null}
            </ul>
          </section>
        ) : (
          <p className="text-sm text-neutral-600">
            Open Finance from a tenancy to see deposit Refundable Balance /
            Refund Paid and ledger (RECEIPT / DEDUCTION / REFUND only).
          </p>
        )}
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
