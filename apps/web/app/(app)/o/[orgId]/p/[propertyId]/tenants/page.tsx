import { createTenantAction } from "@/app/(app)/move-in-actions";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { Input } from "@/components/ui/input";
import { requireAccessToken } from "@/lib/auth";
import { DwellioApiError } from "@/lib/dwellio-api/client";
import { listTenants } from "@/lib/dwellio-api/tenants";
import Link from "next/link";

export default async function TenantsPage({
  params,
}: {
  params: Promise<{ orgId: string; propertyId: string }>;
}) {
  const { orgId, propertyId } = await params;
  const token = await requireAccessToken();

  try {
    const tenants = await listTenants(token, propertyId);

    return (
      <div className="space-y-6">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">Tenants</h1>
            <p className="text-sm text-neutral-600">
              Organization-scoped profiles. A new stay creates a new Tenancy.
            </p>
          </div>
          <Link
            href={`/o/${orgId}/p/${propertyId}/move-ins/new`}
            className="rounded-md bg-neutral-900 px-3 py-1.5 text-sm font-medium text-white"
          >
            Start move-in
          </Link>
        </div>

        {tenants.length === 0 ? (
          <EmptyState
            title="No tenants"
            description="Create a tenant profile, then start a move-in."
          />
        ) : (
          <ul className="divide-y divide-neutral-200 rounded-lg border border-neutral-200 bg-white">
            {tenants.map((tenant) => (
              <li key={tenant.id}>
                <Link
                  href={`/o/${orgId}/p/${propertyId}/tenants/${tenant.id}`}
                  className="block px-4 py-3 hover:bg-neutral-50"
                >
                  <div className="font-medium">
                    {tenant.firstName} {tenant.lastName}
                  </div>
                  <div className="text-sm text-neutral-600">
                    {tenant.phone}
                    {tenant.email ? ` · ${tenant.email}` : ""}
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}

        <section className="rounded-lg border border-neutral-200 bg-white p-4">
          <h2 className="mb-3 text-lg font-semibold">Create tenant</h2>
          <form
            action={createTenantAction}
            className="grid gap-3 sm:grid-cols-2"
          >
            <input type="hidden" name="orgId" value={orgId} />
            <input type="hidden" name="propertyId" value={propertyId} />
            <Input label="First name" name="firstName" required />
            <Input label="Last name" name="lastName" />
            <Input label="Phone" name="phone" required />
            <Input label="Email" name="email" type="email" />
            <Input label="Notes" name="notes" className="sm:col-span-2" />
            <div className="sm:col-span-2">
              <Button type="submit">Create tenant</Button>
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
