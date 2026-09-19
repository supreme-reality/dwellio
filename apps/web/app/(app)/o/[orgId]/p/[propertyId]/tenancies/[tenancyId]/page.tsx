import {
  endEnrollmentAction,
  enrollServiceAction,
  recordChargeAction,
} from "@/app/(app)/rent-service-actions";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { requireAccessToken } from "@/lib/auth";
import { DwellioApiError } from "@/lib/dwellio-api/client";
import { listServices, listTenancyServices } from "@/lib/dwellio-api/services";
import { getTenancy } from "@/lib/dwellio-api/tenants";
import { formatDate } from "@/lib/format";
import Link from "next/link";

export default async function TenancyPage({
  params,
}: {
  params: Promise<{ orgId: string; propertyId: string; tenancyId: string }>;
}) {
  const { orgId, propertyId, tenancyId } = await params;
  const token = await requireAccessToken();
  const today = new Date().toISOString().slice(0, 10);

  try {
    const [tenancy, enrollments, catalog] = await Promise.all([
      getTenancy(token, tenancyId),
      listTenancyServices(token, tenancyId),
      listServices(token, propertyId),
    ]);

    if (tenancy.propertyId !== propertyId) {
      return (
        <Alert tone="error">Tenancy does not belong to this property.</Alert>
      );
    }

    return (
      <div className="space-y-6">
        <div>
          <Link
            href={`/o/${orgId}/p/${propertyId}/tenants`}
            className="text-sm text-neutral-600 underline"
          >
            ← Tenants
          </Link>
          <h1 className="mt-2 text-2xl font-semibold tracking-tight">
            Tenancy stay
          </h1>
          <div className="mt-2 flex flex-wrap gap-2">
            <Badge
              tone={tenancy.status === "ACTIVE" ? "success" : "neutral"}
            >
              {tenancy.status}
            </Badge>
            {tenancy.currentOccupancy ? (
              <Badge tone="info">
                Occupancy {tenancy.currentOccupancy.status} · bed{" "}
                {tenancy.currentOccupancy.bedId.slice(0, 8)}
              </Badge>
            ) : (
              <Badge>No active occupancy</Badge>
            )}
          </div>
          {tenancy.status === "ACTIVE" && tenancy.currentOccupancy ? (
            <div className="mt-3 flex flex-wrap gap-2">
              <Link
                href={`/o/${orgId}/p/${propertyId}/tenancies/${tenancyId}/transfer`}
                className="rounded-md border border-neutral-300 px-3 py-1.5 text-sm hover:bg-neutral-50"
              >
                Transfer
              </Link>
              <Link
                href={`/o/${orgId}/p/${propertyId}/tenancies/${tenancyId}/checkout`}
                className="rounded-md border border-neutral-300 px-3 py-1.5 text-sm hover:bg-neutral-50"
              >
                Checkout
              </Link>
              <Link
                href={`/o/${orgId}/p/${propertyId}/finance?tenancyId=${tenancyId}`}
                className="rounded-md border border-neutral-300 px-3 py-1.5 text-sm hover:bg-neutral-50"
              >
                Finance
              </Link>
            </div>
          ) : null}
        </div>

        <section className="space-y-3">
          <h2 className="text-lg font-semibold">Service enrollments</h2>
          {enrollments.length === 0 ? (
            <EmptyState
              title="No enrollments"
              description="Enroll a catalog service for this tenancy."
            />
          ) : (
            <ul className="divide-y divide-neutral-200 rounded-lg border border-neutral-200 bg-white">
              {enrollments.map((enrollment) => {
                const service = catalog.find((item) => item.id === enrollment.serviceId);
                return (
                  <li key={enrollment.id} className="space-y-3 px-4 py-3">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div>
                        <div className="font-medium">
                          {service?.name ?? enrollment.serviceId.slice(0, 8)}
                        </div>
                        <div className="text-sm text-neutral-600">
                          {formatDate(enrollment.startedAt)} →{" "}
                          {formatDate(enrollment.endedAt)}
                        </div>
                      </div>
                      <Badge>{enrollment.status}</Badge>
                    </div>
                    {enrollment.status !== "ENDED" ? (
                      <div className="flex flex-wrap gap-4">
                        <form
                          action={endEnrollmentAction}
                          className="flex flex-wrap items-end gap-2"
                        >
                          <input type="hidden" name="orgId" value={orgId} />
                          <input
                            type="hidden"
                            name="propertyId"
                            value={propertyId}
                          />
                          <input
                            type="hidden"
                            name="tenancyId"
                            value={tenancyId}
                          />
                          <input
                            type="hidden"
                            name="enrollmentId"
                            value={enrollment.id}
                          />
                          <Input
                            label="End date"
                            name="endedAt"
                            type="date"
                            defaultValue={today}
                            required
                          />
                          <Button type="submit" variant="secondary">
                            End enrollment
                          </Button>
                        </form>
                        {service?.billingType === "VARIABLE" ? (
                          <form
                            action={recordChargeAction}
                            className="flex flex-wrap items-end gap-2"
                          >
                            <input type="hidden" name="orgId" value={orgId} />
                            <input
                              type="hidden"
                              name="propertyId"
                              value={propertyId}
                            />
                            <input
                              type="hidden"
                              name="tenancyId"
                              value={tenancyId}
                            />
                            <input
                              type="hidden"
                              name="enrollmentId"
                              value={enrollment.id}
                            />
                            <Input
                              label="Billing period"
                              name="billingPeriod"
                              type="date"
                              defaultValue={today}
                              required
                            />
                            <Input
                              label="Amount"
                              name="amount"
                              type="number"
                              step="0.01"
                              min="0"
                              required
                            />
                            <Input label="Note" name="note" />
                            <Button type="submit" variant="secondary">
                              Record charge
                            </Button>
                          </form>
                        ) : null}
                      </div>
                    ) : null}
                  </li>
                );
              })}
            </ul>
          )}
        </section>

        <section className="rounded-lg border border-neutral-200 bg-white p-4">
          <h2 className="mb-3 text-lg font-semibold">Enroll service</h2>
          <form
            action={enrollServiceAction}
            className="flex flex-wrap items-end gap-3"
          >
            <input type="hidden" name="orgId" value={orgId} />
            <input type="hidden" name="propertyId" value={propertyId} />
            <input type="hidden" name="tenancyId" value={tenancyId} />
            <Select label="Service" name="serviceId" required defaultValue="">
              <option value="" disabled>
                Select service
              </option>
              {catalog
                .filter((service) => service.status === "ACTIVE")
                .map((service) => (
                  <option key={service.id} value={service.id}>
                    {service.name}
                  </option>
                ))}
            </Select>
            <Input
              label="Started at"
              name="startedAt"
              type="date"
              defaultValue={today}
              required
            />
            <Button type="submit">Enroll</Button>
          </form>
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
