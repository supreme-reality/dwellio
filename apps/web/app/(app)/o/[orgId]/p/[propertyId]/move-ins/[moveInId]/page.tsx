import {
  cancelMoveInAction,
  updateMoveInAction,
} from "@/app/(app)/move-in-actions";
import { MoveInPaymentForm } from "@/components/move-in/move-in-payment-form";
import { MoveInStepper } from "@/components/move-in/move-in-stepper";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { requireAccessToken } from "@/lib/auth";
import { DwellioApiError } from "@/lib/dwellio-api/client";
import { listBedsForProperty } from "@/lib/dwellio-api/inventory";
import { getMoveIn } from "@/lib/dwellio-api/moveIns";
import { getProperty } from "@/lib/dwellio-api/properties";
import { listServices } from "@/lib/dwellio-api/services";
import { getTenant, getTenancy } from "@/lib/dwellio-api/tenants";
import Link from "next/link";

export default async function MoveInDetailPage({
  params,
}: {
  params: Promise<{ orgId: string; propertyId: string; moveInId: string }>;
}) {
  const { orgId, propertyId, moveInId } = await params;
  const token = await requireAccessToken();

  try {
    const [moveIn, property, services, beds] = await Promise.all([
      getMoveIn(token, moveInId),
      getProperty(token, propertyId),
      listServices(token, propertyId),
      listBedsForProperty(token, propertyId),
    ]);

    if (moveIn.propertyId !== propertyId) {
      return <Alert tone="error">Move-in does not belong to this property.</Alert>;
    }

    const [tenant, tenancy] = await Promise.all([
      getTenant(token, moveIn.tenantId),
      getTenancy(token, moveIn.tenancyId),
    ]);

    const selected = new Set(
      moveIn.serviceSelections.map((item) => item.serviceId),
    );
    const currentBed = beds.find((bed) => bed.id === moveIn.bedId);
    const isDraft = moveIn.status === "DRAFT";
    const step =
      moveIn.status === "COMPLETED" || tenancy.status === "ACTIVE" ? 5 : 3;

    return (
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">
            Move-in draft
          </h1>
          <p className="text-sm text-neutral-600">
            Draft does not reserve a bed. New stay = new Tenancy (
            {moveIn.tenancyId.slice(0, 8)}…).
          </p>
          <div className="mt-2 flex flex-wrap gap-2">
            <Badge>{moveIn.status}</Badge>
            <Badge tone={tenancy.status === "ACTIVE" ? "success" : "neutral"}>
              Tenancy {tenancy.status}
            </Badge>
          </div>
        </div>

        <MoveInStepper current={step}>
          <div className="space-y-4 rounded-lg border border-neutral-200 bg-white p-4">
            <p className="text-sm">
              Tenant:{" "}
              <Link
                href={`/o/${orgId}/p/${propertyId}/tenants/${tenant.id}`}
                className="underline"
              >
                {tenant.firstName} {tenant.lastName}
              </Link>
            </p>
            <p className="text-sm">
              Bed: {currentBed?.name ?? moveIn.bedId.slice(0, 8)}{" "}
              {currentBed?.availability ? (
                <Badge
                  tone={
                    currentBed.availability === "AVAILABLE"
                      ? "success"
                      : "danger"
                  }
                >
                  {currentBed.availability}
                </Badge>
              ) : null}
            </p>
            {currentBed &&
            currentBed.availability !== "AVAILABLE" &&
            isDraft ? (
              <Alert tone="error">
                Selected bed is no longer available. Choose another bed below
                before paying.
              </Alert>
            ) : null}

            {isDraft ? (
              <>
                <form
                  action={updateMoveInAction}
                  className="grid gap-3 border-t border-neutral-100 pt-4 sm:grid-cols-2"
                >
                  <input type="hidden" name="orgId" value={orgId} />
                  <input type="hidden" name="propertyId" value={propertyId} />
                  <input type="hidden" name="moveInId" value={moveInId} />
                  <label className="flex flex-col gap-1 text-sm sm:col-span-2">
                    <span className="font-medium">Bed (reselect if conflict)</span>
                    <select
                      name="bedId"
                      defaultValue={moveIn.bedId}
                      className="rounded-md border border-neutral-300 bg-white px-3 py-2"
                    >
                      {beds
                        .filter(
                          (bed) =>
                            bed.availability === "AVAILABLE" ||
                            bed.id === moveIn.bedId,
                        )
                        .map((bed) => (
                          <option key={bed.id} value={bed.id}>
                            {bed.name} · {bed.availability}
                          </option>
                        ))}
                    </select>
                  </label>
                  <Input
                    label="Move-in date"
                    name="moveInDate"
                    type="date"
                    defaultValue={moveIn.moveInDate}
                  />
                  <fieldset className="sm:col-span-2">
                    <legend className="mb-2 text-sm font-medium">
                      Services
                    </legend>
                    <div className="space-y-2">
                      {services
                        .filter((service) => service.status === "ACTIVE")
                        .map((service) => (
                          <label
                            key={service.id}
                            className="flex items-center gap-2 text-sm"
                          >
                            <input
                              type="checkbox"
                              name="serviceId"
                              value={service.id}
                              defaultChecked={
                                selected.has(service.id) || service.mandatory
                              }
                            />
                            {service.name} ({service.billingTiming})
                          </label>
                        ))}
                    </div>
                  </fieldset>
                  <div className="sm:col-span-2">
                    <Button type="submit" variant="secondary">
                      Save draft details
                    </Button>
                  </div>
                </form>

                <div className="border-t border-neutral-100 pt-4">
                  <h2 className="mb-3 text-lg font-semibold">
                    Upfront · Confirm & Pay
                  </h2>
                  <MoveInPaymentForm
                    orgId={orgId}
                    propertyId={propertyId}
                    moveInId={moveInId}
                    currency={property.defaultCurrency}
                  />
                </div>

                <form action={cancelMoveInAction}>
                  <input type="hidden" name="orgId" value={orgId} />
                  <input type="hidden" name="propertyId" value={propertyId} />
                  <input type="hidden" name="moveInId" value={moveInId} />
                  <Button type="submit" variant="danger">
                    Cancel draft
                  </Button>
                </form>
              </>
            ) : (
              <p className="text-sm">
                Move-in {moveIn.status}.{" "}
                <Link
                  href={`/o/${orgId}/p/${propertyId}/tenancies/${moveIn.tenancyId}`}
                  className="underline"
                >
                  Open tenancy
                </Link>
              </p>
            )}
          </div>
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
