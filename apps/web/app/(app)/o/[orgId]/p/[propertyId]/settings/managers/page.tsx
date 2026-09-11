import {
  assignManagerAction,
  removeManagerAction,
} from "@/app/(app)/actions";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { Input } from "@/components/ui/input";
import { requireAccessToken } from "@/lib/auth";
import { DwellioApiError } from "@/lib/dwellio-api/client";
import { getOrganization } from "@/lib/dwellio-api/orgs";
import { listPropertyManagers } from "@/lib/dwellio-api/properties";

export default async function ManagersSettingsPage({
  params,
}: {
  params: Promise<{ orgId: string; propertyId: string }>;
}) {
  const { orgId, propertyId } = await params;
  const token = await requireAccessToken();

  try {
    const org = await getOrganization(token, orgId);
    if (org.role !== "OWNER") {
      return <Alert tone="error">Only owners can manage property managers.</Alert>;
    }

    const managers = await listPropertyManagers(token, propertyId);

    return (
      <div className="space-y-6">
        <h1 className="text-2xl font-semibold tracking-tight">Managers</h1>

        {managers.length === 0 ? (
          <EmptyState
            title="No managers assigned"
            description="Assign an organization member by email to manage this property."
          />
        ) : (
          <ul className="divide-y divide-neutral-200 rounded-lg border border-neutral-200 bg-white">
            {managers.map((manager) => (
              <li
                key={manager.propertyMembershipId}
                className="flex flex-wrap items-center justify-between gap-3 px-4 py-3"
              >
                <div>
                  <div className="font-medium">
                    {manager.name || manager.email}
                  </div>
                  <div className="text-sm text-neutral-600">{manager.email}</div>
                </div>
                <form action={removeManagerAction}>
                  <input type="hidden" name="organizationId" value={orgId} />
                  <input type="hidden" name="propertyId" value={propertyId} />
                  <input
                    type="hidden"
                    name="propertyMembershipId"
                    value={manager.propertyMembershipId}
                  />
                  <Button type="submit" variant="danger">
                    Remove
                  </Button>
                </form>
              </li>
            ))}
          </ul>
        )}

        <section className="rounded-lg border border-neutral-200 bg-white p-4">
          <h2 className="mb-3 text-lg font-semibold">Assign manager</h2>
          <form
            action={assignManagerAction}
            className="flex flex-wrap items-end gap-3"
          >
            <input type="hidden" name="organizationId" value={orgId} />
            <input type="hidden" name="propertyId" value={propertyId} />
            <Input label="Member email" name="email" type="email" required />
            <Button type="submit">Assign</Button>
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
