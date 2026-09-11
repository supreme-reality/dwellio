import { updatePropertyAction } from "@/app/(app)/actions";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { requireAccessToken } from "@/lib/auth";
import { DwellioApiError } from "@/lib/dwellio-api/client";
import { getOrganization } from "@/lib/dwellio-api/orgs";
import { getProperty } from "@/lib/dwellio-api/properties";

export default async function PropertySettingsPage({
  params,
}: {
  params: Promise<{ orgId: string; propertyId: string }>;
}) {
  const { orgId, propertyId } = await params;
  const token = await requireAccessToken();

  try {
    const org = await getOrganization(token, orgId);
    if (org.role !== "OWNER") {
      return <Alert tone="error">Only owners can edit property settings.</Alert>;
    }

    const property = await getProperty(token, propertyId);

    return (
      <div className="space-y-6">
        <h1 className="text-2xl font-semibold tracking-tight">
          Property settings
        </h1>
        <form
          action={updatePropertyAction}
          className="grid max-w-xl gap-3 rounded-lg border border-neutral-200 bg-white p-4 sm:grid-cols-2"
        >
          <input type="hidden" name="organizationId" value={orgId} />
          <input type="hidden" name="propertyId" value={propertyId} />
          <Input label="Name" name="name" defaultValue={property.name} required />
          <Input
            label="Address"
            name="address"
            defaultValue={property.address ?? ""}
          />
          <Input
            label="Payment due days"
            name="paymentDueDays"
            type="number"
            min={0}
            max={365}
            defaultValue={property.paymentDueDays}
            required
          />
          <Input
            label="Default currency"
            name="defaultCurrency"
            defaultValue={property.defaultCurrency}
            maxLength={3}
            required
          />
          <div className="sm:col-span-2">
            <Button type="submit">Save</Button>
          </div>
        </form>
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
