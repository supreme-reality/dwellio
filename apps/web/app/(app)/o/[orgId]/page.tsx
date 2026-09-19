import { createPropertyAction } from "@/app/(app)/actions";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { Input } from "@/components/ui/input";
import { requireAccessToken, requireSession } from "@/lib/auth";
import { DwellioApiError } from "@/lib/dwellio-api/client";
import { getOrganization, listOrganizations } from "@/lib/dwellio-api/orgs";
import { listProperties } from "@/lib/dwellio-api/properties";
import Link from "next/link";

export default async function OrganizationHomePage({
  params,
}: {
  params: Promise<{ orgId: string }>;
}) {
  const { orgId } = await params;
  const session = await requireSession();
  const token = await requireAccessToken();

  let error: DwellioApiError | null = null;
  let org = null;
  let organizations: Awaited<ReturnType<typeof listOrganizations>> = [];
  let properties: Awaited<ReturnType<typeof listProperties>> = [];

  try {
    [organizations, org, properties] = await Promise.all([
      listOrganizations(token),
      getOrganization(token, orgId),
      listProperties(token, orgId),
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
      <main className="mx-auto max-w-lg p-8">
        <Alert tone="error" requestId={error.requestId}>
          {error.message}
        </Alert>
      </main>
    );
  }

  const isOwner = org?.role === "OWNER";

  return (
    <div className="min-h-screen bg-neutral-50">
      <header className="border-b border-neutral-200 bg-white">
        <div className="mx-auto flex max-w-3xl items-center justify-between gap-4 px-4 py-3">
          <Link href={`/o/${orgId}`} className="text-xl font-semibold">
            Dwellio
          </Link>
          <div className="flex items-center gap-3 text-sm">
            <span className="text-neutral-600">
              {session.user.email ?? session.user.name ?? "Signed in"}
            </span>
            <a
              href="/auth/logout"
              className="rounded-md border border-neutral-300 px-3 py-1.5 hover:bg-neutral-50"
            >
              Log out
            </a>
          </div>
        </div>
      </header>

      <div className="mx-auto max-w-3xl space-y-6 px-4 py-6">
        {organizations.length > 1 ? (
          <div className="flex flex-wrap gap-2 text-sm">
            {organizations.map((item) => (
              <Link
                key={item.id}
                href={`/o/${item.id}`}
                className={`rounded-md border px-2 py-1 ${
                  item.id === orgId
                    ? "border-neutral-900 bg-neutral-900 text-white"
                    : "border-neutral-300 bg-white"
                }`}
              >
                {item.name}
              </Link>
            ))}
          </div>
        ) : null}

        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{org?.name}</h1>
          <p className="text-sm text-neutral-600">Role: {org?.role}</p>
        </div>

        {properties.length === 0 ? (
          <EmptyState
            title={isOwner ? "No properties yet" : "No properties assigned"}
            description={
              isOwner
                ? "Create a property to start managing inventory and stays."
                : "Ask an organization owner to assign you as a property manager."
            }
          />
        ) : (
          <ul className="space-y-2">
            {properties.map((property) => (
              <li key={property.id}>
                <Link
                  href={`/o/${orgId}/p/${property.id}/inventory`}
                  className="block rounded-md border border-neutral-200 bg-white px-4 py-3 hover:bg-neutral-50"
                >
                  <div className="font-medium">{property.name}</div>
                  <div className="text-sm text-neutral-600">
                    {property.address || "No address"} ·{" "}
                    {property.defaultCurrency}
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}

        {isOwner ? (
          <section className="rounded-lg border border-neutral-200 bg-white p-4">
            <h2 className="mb-3 text-lg font-semibold">Create property</h2>
            <form
              action={createPropertyAction}
              className="grid gap-3 sm:grid-cols-2"
            >
              <input type="hidden" name="organizationId" value={orgId} />
              <Input label="Name" name="name" required />
              <Input label="Address" name="address" />
              <Input
                label="Payment due days"
                name="paymentDueDays"
                type="number"
                min={0}
                max={365}
                defaultValue={5}
                required
              />
              <Input
                label="Default currency"
                name="defaultCurrency"
                defaultValue="INR"
                maxLength={3}
                required
              />
              <div className="sm:col-span-2">
                <Button type="submit">Create property</Button>
              </div>
            </form>
          </section>
        ) : null}
      </div>
    </div>
  );
}
