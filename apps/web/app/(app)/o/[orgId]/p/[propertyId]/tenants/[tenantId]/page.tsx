import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { requireAccessToken } from "@/lib/auth";
import { DwellioApiError } from "@/lib/dwellio-api/client";
import {
  getTenant,
  getTenantStayHistory,
} from "@/lib/dwellio-api/tenants";
import Link from "next/link";

export default async function TenantDetailPage({
  params,
}: {
  params: Promise<{ orgId: string; propertyId: string; tenantId: string }>;
}) {
  const { orgId, propertyId, tenantId } = await params;
  const token = await requireAccessToken();

  try {
    const [tenant, history] = await Promise.all([
      getTenant(token, tenantId),
      getTenantStayHistory(token, tenantId),
    ]);

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
            {tenant.firstName} {tenant.lastName}
          </h1>
          <p className="text-sm text-neutral-600">
            {tenant.phone}
            {tenant.email ? ` · ${tenant.email}` : ""}
          </p>
        </div>

        <div>
          <Link
            href={`/o/${orgId}/p/${propertyId}/move-ins/new?tenantId=${tenant.id}`}
            className="rounded-md bg-neutral-900 px-3 py-1.5 text-sm font-medium text-white"
          >
            Start new stay (new Tenancy)
          </Link>
        </div>

        <section>
          <h2 className="mb-2 text-lg font-semibold">Tenancies</h2>
          <ul className="divide-y divide-neutral-200 rounded-lg border border-neutral-200 bg-white">
            {history.map((item) => (
              <li key={item.tenancyId} className="px-4 py-3">
                <Link
                  href={`/o/${orgId}/p/${propertyId}/tenancies/${item.tenancyId}`}
                  className="flex flex-wrap items-center justify-between gap-2 hover:underline"
                >
                  <span className="text-sm">
                    Tenancy {item.tenancyId.slice(0, 8)}…
                  </span>
                  <Badge
                    tone={item.status === "ACTIVE" ? "success" : "neutral"}
                  >
                    {item.status === "ACTIVE" ? "Active stay" : item.status}
                  </Badge>
                </Link>
              </li>
            ))}
            {history.length === 0 ? (
              <li className="px-4 py-3 text-sm text-neutral-600">
                No tenancies yet.
              </li>
            ) : null}
          </ul>
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
