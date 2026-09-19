import { createRentAction } from "@/app/(app)/rent-service-actions";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { requireAccessToken } from "@/lib/auth";
import { DwellioApiError } from "@/lib/dwellio-api/client";
import { listBedsForProperty, listRooms } from "@/lib/dwellio-api/inventory";
import { getProperty } from "@/lib/dwellio-api/properties";
import { listRentConfig, type RentConfig } from "@/lib/dwellio-api/rent";
import { formatDate, formatMoney } from "@/lib/format";
import { ResolveRentForm } from "@/components/rent/resolve-rent-form";

function scheduleLabel(config: RentConfig, today: string): string {
  if (config.effectiveFrom > today) return "Scheduled";
  if (config.effectiveTo && config.effectiveTo < today) return "Expired";
  return "Effective";
}

export default async function RentPage({
  params,
}: {
  params: Promise<{ orgId: string; propertyId: string }>;
}) {
  const { orgId, propertyId } = await params;
  const token = await requireAccessToken();
  const today = new Date().toISOString().slice(0, 10);

  try {
    const [property, configs, rooms, beds] = await Promise.all([
      getProperty(token, propertyId),
      listRentConfig(token, propertyId),
      listRooms(token, propertyId),
      listBedsForProperty(token, propertyId),
    ]);

    return (
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Rent</h1>
          <p className="text-sm text-neutral-600">
            Hierarchy: Property → Room → Bed. Bed overrides room overrides
            property.
          </p>
        </div>

        {configs.length === 0 ? (
          <EmptyState
            title="No rent configs"
            description="Create a property-level rent to get started."
          />
        ) : (
          <ul className="divide-y divide-neutral-200 rounded-lg border border-neutral-200 bg-white">
            {configs.map((config) => (
              <li
                key={config.id}
                className="flex flex-wrap items-center justify-between gap-3 px-4 py-3"
              >
                <div>
                  <div className="font-medium">
                    {formatMoney(config.amount, property.defaultCurrency)} ·{" "}
                    {config.level}
                  </div>
                  <div className="text-sm text-neutral-600">
                    {formatDate(config.effectiveFrom)} →{" "}
                    {formatDate(config.effectiveTo)}
                    {config.roomId ? ` · room ${config.roomId.slice(0, 8)}` : ""}
                    {config.bedId ? ` · bed ${config.bedId.slice(0, 8)}` : ""}
                  </div>
                </div>
                <Badge
                  tone={
                    scheduleLabel(config, today) === "Effective"
                      ? "success"
                      : scheduleLabel(config, today) === "Scheduled"
                        ? "info"
                        : "neutral"
                  }
                >
                  {scheduleLabel(config, today)}
                </Badge>
              </li>
            ))}
          </ul>
        )}

        <section className="rounded-lg border border-neutral-200 bg-white p-4">
          <h2 className="mb-3 text-lg font-semibold">Create rent config</h2>
          <form action={createRentAction} className="grid gap-3 sm:grid-cols-2">
            <input type="hidden" name="orgId" value={orgId} />
            <input type="hidden" name="propertyId" value={propertyId} />
            <Select label="Level" name="level" defaultValue="PROPERTY">
              <option value="PROPERTY">Property</option>
              <option value="ROOM">Room</option>
              <option value="BED">Bed</option>
            </Select>
            <Select label="Room (for room/bed level)" name="roomId" defaultValue="">
              <option value="">—</option>
              {rooms.map((room) => (
                <option key={room.id} value={room.id}>
                  {room.name}
                </option>
              ))}
            </Select>
            <Select label="Bed (for bed level)" name="bedId" defaultValue="">
              <option value="">—</option>
              {beds.map((bed) => (
                <option key={bed.id} value={bed.id}>
                  {bed.name}
                </option>
              ))}
            </Select>
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
            <Input label="Effective to (optional)" name="effectiveTo" type="date" />
            <div className="sm:col-span-2">
              <Button type="submit">Create</Button>
            </div>
          </form>
        </section>

        <ResolveRentForm
          orgId={orgId}
          propertyId={propertyId}
          beds={beds.map((bed) => ({ id: bed.id, name: bed.name }))}
          currency={property.defaultCurrency}
          defaultDate={today}
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
