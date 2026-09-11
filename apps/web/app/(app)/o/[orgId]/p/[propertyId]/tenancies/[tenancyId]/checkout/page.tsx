import { CheckoutForm } from "@/components/checkout/checkout-form";
import { Alert } from "@/components/ui/alert";
import Link from "next/link";

export default async function CheckoutPage({
  params,
}: {
  params: Promise<{ orgId: string; propertyId: string; tenancyId: string }>;
}) {
  const { orgId, propertyId, tenancyId } = await params;

  return (
    <div className="space-y-4">
      <Link
        href={`/o/${orgId}/p/${propertyId}/tenancies/${tenancyId}`}
        className="text-sm underline"
      >
        ← Tenancy
      </Link>
      <h1 className="text-2xl font-semibold tracking-tight">Checkout</h1>
      <p className="text-sm text-neutral-600">
        Pay full net receivable or explicitly leave receivable. No partial
        payments.
      </p>
      <Alert tone="info">
        Preview amounts are calculated only — confirm commits the settlement.
      </Alert>
      <CheckoutForm
        orgId={orgId}
        propertyId={propertyId}
        tenancyId={tenancyId}
      />
    </div>
  );
}
