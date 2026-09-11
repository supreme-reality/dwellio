import { createServiceAction, createServiceConfigAction } from "@/app/(app)/rent-service-actions";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { requireAccessToken } from "@/lib/auth";
import { DwellioApiError } from "@/lib/dwellio-api/client";
import { getProperty } from "@/lib/dwellio-api/properties";
import {
  listServiceConfig,
  listServices,
  type ServiceConfig,
  type ServiceItem,
} from "@/lib/dwellio-api/services";
import { formatDate, formatMoney } from "@/lib/format";

export default async function ServicesPage({
  params,
}: {
  params: Promise<{ orgId: string; propertyId: string }>;
}) {
  const { orgId, propertyId } = await params;
  const token = await requireAccessToken();
  const today = new Date().toISOString().slice(0, 10);

  try {
    const [property, services] = await Promise.all([
      getProperty(token, propertyId),
      listServices(token, propertyId),
    ]);

    const configsByService = new Map<string, ServiceConfig[]>();
    await Promise.all(
      services.map(async (service) => {
        const configs = await listServiceConfig(token, service.id);
        configsByService.set(service.id, configs);
      }),
    );

    return (
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Services</h1>
          <p className="text-sm text-neutral-600">
            Property catalog. Enroll services on a tenancy stay context.
          </p>
        </div>

        {services.length === 0 ? (
          <EmptyState
            title="No services"
            description="Create a service in the property catalog."
          />
        ) : (
          <div className="space-y-4">
            {services.map((service: ServiceItem) => {
              const configs = configsByService.get(service.id) ?? [];
              return (
                <section
                  key={service.id}
                  className="rounded-lg border border-neutral-200 bg-white p-4"
                >
                  <div className="mb-2 flex flex-wrap items-center gap-2">
                    <h2 className="text-lg font-semibold">{service.name}</h2>
                    <Badge>{service.billingType}</Badge>
                    <Badge tone="info">{service.billingTiming}</Badge>
                    <Badge
                      tone={service.status === "ACTIVE" ? "success" : "neutral"}
                    >
                      {service.status}
                    </Badge>
                    {service.mandatory ? <Badge tone="warning">Mandatory</Badge> : null}
                  </div>
                  <ul className="mb-3 space-y-1 text-sm text-neutral-700">
                    {configs.map((config) => (
                      <li key={config.id}>
                        {formatMoney(config.amount, property.defaultCurrency)}{" "}
                        from {formatDate(config.effectiveFrom)}
                        {config.effectiveTo
                          ? ` → ${formatDate(config.effectiveTo)}`
                          : ""}
                      </li>
                    ))}
                  </ul>
                  <form
                    action={createServiceConfigAction}
                    className="flex flex-wrap items-end gap-2 border-t border-neutral-100 pt-3"
                  >
                    <input type="hidden" name="orgId" value={orgId} />
                    <input type="hidden" name="propertyId" value={propertyId} />
                    <input type="hidden" name="serviceId" value={service.id} />
                    <Input
                      label="Amount"
                      name="amount"
                      type="number"
                      step="0.01"
                      min="0"
                      required
                    />
                    <Input
                      label="Effective from"
                      name="effectiveFrom"
                      type="date"
                      defaultValue={today}
                      required
                    />
                    <Button type="submit" variant="secondary">
                      Add config
                    </Button>
                  </form>
                </section>
              );
            })}
          </div>
        )}

        <section className="rounded-lg border border-neutral-200 bg-white p-4">
          <h2 className="mb-3 text-lg font-semibold">Create service</h2>
          <form
            action={createServiceAction}
            className="grid gap-3 sm:grid-cols-2"
          >
            <input type="hidden" name="orgId" value={orgId} />
            <input type="hidden" name="propertyId" value={propertyId} />
            <Input label="Name" name="name" required />
            <Select label="Billing type" name="billingType" defaultValue="FIXED">
              <option value="FIXED">FIXED</option>
              <option value="VARIABLE">VARIABLE</option>
            </Select>
            <Select
              label="Billing timing"
              name="billingTiming"
              defaultValue="MONTHLY_ARREARS"
            >
              <option value="UPFRONT">UPFRONT</option>
              <option value="MONTHLY_ARREARS">MONTHLY_ARREARS</option>
            </Select>
            <Select label="Mandatory" name="mandatory" defaultValue="false">
              <option value="false">No</option>
              <option value="true">Yes</option>
            </Select>
            <Input
              label="Proration setting"
              name="prorationSetting"
              defaultValue="NONE"
              required
            />
            <div className="sm:col-span-2">
              <Button type="submit">Create service</Button>
            </div>
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
