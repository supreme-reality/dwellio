import { startMoveInAction } from "@/app/(app)/move-in-actions";
import { MoveInStepper } from "@/components/move-in/move-in-stepper";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { requireAccessToken } from "@/lib/auth";
import { DwellioApiError } from "@/lib/dwellio-api/client";
import { listBedsForProperty } from "@/lib/dwellio-api/inventory";
import { listTenants } from "@/lib/dwellio-api/tenants";

export default async function NewMoveInPage({
  params,
  searchParams,
}: {
  params: Promise<{ orgId: string; propertyId: string }>;
  searchParams: Promise<{ tenantId?: string }>;
}) {
  const { orgId, propertyId } = await params;
  const { tenantId: presetTenantId } = await searchParams;
  const token = await requireAccessToken();
  const today = new Date().toISOString().slice(0, 10);

  try {
    const [tenants, beds] = await Promise.all([
      listTenants(token, propertyId),
      listBedsForProperty(token, propertyId),
    ]);
    const availableBeds = beds.filter(
      (bed) => bed.availability === "AVAILABLE",
    );

    return (
      <div className="space-y-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Move-in</h1>
          <p className="text-sm text-neutral-600">
            Selecting a tenant for a new stay creates a <strong>new Tenancy</strong>.
            A draft does <strong>not</strong> reserve the bed.
          </p>
        </div>

        <MoveInStepper current={0}>
          <form
            action={startMoveInAction}
            className="grid gap-3 rounded-lg border border-neutral-200 bg-white p-4 sm:grid-cols-2"
          >
            <input type="hidden" name="orgId" value={orgId} />
            <input type="hidden" name="propertyId" value={propertyId} />
            <Select
              label="Tenant"
              name="tenantId"
              required
              defaultValue={presetTenantId ?? ""}
            >
              <option value="" disabled>
                Select tenant
              </option>
              {tenants.map((tenant) => (
                <option key={tenant.id} value={tenant.id}>
                  {tenant.firstName} {tenant.lastName} ({tenant.phone})
                </option>
              ))}
            </Select>
            <Select label="Available bed" name="bedId" required defaultValue="">
              <option value="" disabled>
                Select bed
              </option>
              {availableBeds.map((bed) => (
                <option key={bed.id} value={bed.id}>
                  {bed.name} · AVAILABLE
                </option>
              ))}
            </Select>
            <Input
              label="Move-in date"
              name="moveInDate"
              type="date"
              defaultValue={today}
              required
            />
            <div className="sm:col-span-2 space-y-2">
              <p className="text-xs text-neutral-500">
                Steps covered: Tenant → Bed → Details. Continue to save a draft
                and configure services / payment.
              </p>
              <Button type="submit">Save draft & continue</Button>
            </div>
          </form>
        </MoveInStepper>
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
