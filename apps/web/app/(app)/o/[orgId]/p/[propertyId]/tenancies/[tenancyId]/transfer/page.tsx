import { TransferForm } from "@/components/transfer/transfer-form";
import { Alert } from "@/components/ui/alert";
import { requireAccessToken } from "@/lib/auth";
import { DwellioApiError } from "@/lib/dwellio-api/client";
import { listBedsForProperty } from "@/lib/dwellio-api/inventory";
import { getTenancy } from "@/lib/dwellio-api/tenants";
import Link from "next/link";

export default async function TransferPage({
  params,
}: {
  params: Promise<{ orgId: string; propertyId: string; tenancyId: string }>;
}) {
  const { orgId, propertyId, tenancyId } = await params;
  const token = await requireAccessToken();

  try {
    const [tenancy, beds] = await Promise.all([
      getTenancy(token, tenancyId),
      listBedsForProperty(token, propertyId),
    ]);

    const available = beds.filter(
      (bed) =>
        bed.availability === "AVAILABLE" &&
        bed.id !== tenancy.currentOccupancy?.bedId,
    );

    return (
      <div className="space-y-4">
        <Link
          href={`/o/${orgId}/p/${propertyId}/tenancies/${tenancyId}`}
          className="text-sm underline"
        >
          ← Tenancy
        </Link>
        <h1 className="text-2xl font-semibold tracking-tight">Transfer</h1>
        <p className="text-sm text-neutral-600">
          Same property only. Deposit stays with the Tenancy. Preview before
          confirm.
        </p>
        <TransferForm
          orgId={orgId}
          propertyId={propertyId}
          tenancyId={tenancyId}
          beds={available.map((bed) => ({ id: bed.id, name: bed.name }))}
        />
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
