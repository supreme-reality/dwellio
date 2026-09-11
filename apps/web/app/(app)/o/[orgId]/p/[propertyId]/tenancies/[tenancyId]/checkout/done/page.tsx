import { refundSettlementAction } from "@/app/(app)/transfer-checkout-actions";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { requireAccessToken } from "@/lib/auth";
import { DwellioApiError } from "@/lib/dwellio-api/client";
import { getSettlement } from "@/lib/dwellio-api/checkout";
import { listTenancyInvoices } from "@/lib/dwellio-api/finance";
import { formatMoney } from "@/lib/format";
import Link from "next/link";

export default async function CheckoutDonePage({
  params,
  searchParams,
}: {
  params: Promise<{ orgId: string; propertyId: string; tenancyId: string }>;
  searchParams: Promise<{ settlementId?: string }>;
}) {
  const { orgId, propertyId, tenancyId } = await params;
  const { settlementId } = await searchParams;
  const token = await requireAccessToken();

  if (!settlementId) {
    return <Alert tone="error">Missing settlementId</Alert>;
  }

  try {
    const [settlement, invoices] = await Promise.all([
      getSettlement(token, settlementId),
      listTenancyInvoices(token, tenancyId).catch(() => []),
    ]);

    const openReceivables = invoices.filter(
      (invoice) =>
        invoice.status !== "PAID" && invoice.status !== "CANCELLED",
    );
    const refundDue = Number(settlement.refundDue);

    return (
      <div className="space-y-6">
        <Link
          href={`/o/${orgId}/p/${propertyId}/tenancies/${tenancyId}`}
          className="text-sm underline"
        >
          ← Tenancy
        </Link>
        <h1 className="text-2xl font-semibold tracking-tight">
          Checkout complete
        </h1>
        <div className="rounded-lg border border-neutral-200 bg-white p-4 text-sm space-y-1">
          <p>
            Net receivable:{" "}
            {formatMoney(settlement.netReceivable, settlement.currency)}
          </p>
          <p>
            Refund due: {formatMoney(settlement.refundDue, settlement.currency)}
          </p>
          <p>Status: {settlement.status}</p>
        </div>

        <section>
          <h2 className="mb-2 text-lg font-semibold">Open receivables</h2>
          {openReceivables.length === 0 ? (
            <p className="text-sm text-neutral-600">None</p>
          ) : (
            <ul className="divide-y divide-neutral-200 rounded-lg border border-neutral-200 bg-white">
              {openReceivables.map((invoice) => (
                <li key={invoice.id} className="px-4 py-2 text-sm">
                  {invoice.id.slice(0, 8)}… · {invoice.status} ·{" "}
                  {formatMoney(invoice.total, settlement.currency)}
                </li>
              ))}
            </ul>
          )}
        </section>

        {refundDue > 0 ? (
          <section className="rounded-lg border border-neutral-200 bg-white p-4">
            <h2 className="mb-3 text-lg font-semibold">Pay refund</h2>
            <form
              action={refundSettlementAction}
              className="flex flex-wrap items-end gap-3"
            >
              <input type="hidden" name="orgId" value={orgId} />
              <input type="hidden" name="propertyId" value={propertyId} />
              <input type="hidden" name="tenancyId" value={tenancyId} />
              <input type="hidden" name="settlementId" value={settlementId} />
              <Select label="Method" name="paymentMethod" defaultValue="CASH">
                <option value="CASH">CASH</option>
                <option value="BANK_TRANSFER">BANK_TRANSFER</option>
              </Select>
              <Input label="Bank reference" name="bankTransferReference" />
              <Button type="submit">Record Refund Paid</Button>
            </form>
          </section>
        ) : null}
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
