import {
  createBedAction,
  createRoomAction,
} from "@/app/(app)/inventory-actions";
import { BedRow } from "@/components/inventory/bed-row";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { Input } from "@/components/ui/input";
import { requireAccessToken } from "@/lib/auth";
import { DwellioApiError } from "@/lib/dwellio-api/client";
import {
  listBedsForProperty,
  listRooms,
  type Bed,
  type Room,
} from "@/lib/dwellio-api/inventory";

export default async function InventoryPage({
  params,
}: {
  params: Promise<{ orgId: string; propertyId: string }>;
}) {
  const { orgId, propertyId } = await params;
  const token = await requireAccessToken();

  let rooms: Room[] = [];
  let beds: Bed[] = [];
  let error: DwellioApiError | null = null;

  try {
    [rooms, beds] = await Promise.all([
      listRooms(token, propertyId),
      listBedsForProperty(token, propertyId),
    ]);
  } catch (err) {
    if (err instanceof DwellioApiError) {
      error = err;
    } else {
      throw err;
    }
  }

  if (error) {
    return (
      <Alert tone="error" requestId={error.requestId}>
        {error.message}
      </Alert>
    );
  }

  const bedsByRoom = new Map<string, Bed[]>();
  for (const bed of beds) {
    const list = bedsByRoom.get(bed.roomId) ?? [];
    list.push(bed);
    bedsByRoom.set(bed.roomId, list);
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Inventory</h1>
        <p className="text-sm text-neutral-600">
          Rooms and beds with live availability. Draft move-in selections do not
          reserve beds.
        </p>
      </div>

      {rooms.length === 0 ? (
        <EmptyState
          title="No rooms yet"
          description="Create a room, then add beds."
        />
      ) : (
        <div className="space-y-4">
          {rooms.map((room) => {
            const roomBeds = bedsByRoom.get(room.id) ?? [];
            return (
              <section
                key={room.id}
                className="rounded-lg border border-neutral-200 bg-white p-4"
              >
                <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                  <h2 className="text-lg font-semibold">{room.name}</h2>
                  <span className="text-xs text-neutral-500">{room.status}</span>
                </div>

                {roomBeds.length === 0 ? (
                  <p className="mb-3 text-sm text-neutral-600">No beds in this room.</p>
                ) : (
                  <ul className="mb-3">
                    {roomBeds.map((bed) => (
                      <BedRow
                        key={bed.id}
                        bed={bed}
                        orgId={orgId}
                        propertyId={propertyId}
                      />
                    ))}
                  </ul>
                )}

                <form
                  action={createBedAction}
                  className="flex flex-wrap items-end gap-2 border-t border-neutral-100 pt-3"
                >
                  <input type="hidden" name="orgId" value={orgId} />
                  <input type="hidden" name="propertyId" value={propertyId} />
                  <input type="hidden" name="roomId" value={room.id} />
                  <Input label="New bed name" name="name" required />
                  <Button type="submit" variant="secondary">
                    Add bed
                  </Button>
                </form>
              </section>
            );
          })}
        </div>
      )}

      <section className="rounded-lg border border-neutral-200 bg-white p-4">
        <h2 className="mb-3 text-lg font-semibold">Create room</h2>
        <form
          action={createRoomAction}
          className="flex flex-wrap items-end gap-3"
        >
          <input type="hidden" name="orgId" value={orgId} />
          <input type="hidden" name="propertyId" value={propertyId} />
          <Input label="Room name" name="name" required />
          <Button type="submit">Create room</Button>
        </form>
      </section>
    </div>
  );
}
