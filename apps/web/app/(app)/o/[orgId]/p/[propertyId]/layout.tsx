import { AppShell } from "@/components/shell/app-shell";
import { AuthHeader } from "@/components/shell/auth-header";
import { OrgSwitcher } from "@/components/shell/org-switcher";
import { PropertySwitcher } from "@/components/shell/property-switcher";
import { SidebarNav } from "@/components/shell/sidebar-nav";
import { Alert } from "@/components/ui/alert";
import { requireAccessToken, requireSession } from "@/lib/auth";
import { DwellioApiError } from "@/lib/dwellio-api/client";
import { getOrganization, listOrganizations } from "@/lib/dwellio-api/orgs";
import { getProperty, listProperties } from "@/lib/dwellio-api/properties";

export default async function PropertyLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ orgId: string; propertyId: string }>;
}) {
  const { orgId, propertyId } = await params;
  const session = await requireSession();
  const token = await requireAccessToken();
  const userLabel = session.user.email ?? session.user.name ?? "Signed in";

  try {
    const [organizations, organization, properties, property] =
      await Promise.all([
        listOrganizations(token),
        getOrganization(token, orgId),
        listProperties(token, orgId),
        getProperty(token, propertyId),
      ]);

    if (property.organizationId !== orgId) {
      return (
        <div className="min-h-screen bg-neutral-50">
          <AuthHeader brandHref={`/o/${orgId}`} userLabel={userLabel} />
          <main className="mx-auto max-w-lg p-8">
            <Alert tone="error">
              Property does not belong to this organization.
            </Alert>
          </main>
        </div>
      );
    }

    const isOwner = organization.role === "OWNER";

    return (
      <AppShell
        brandHref={`/o/${orgId}`}
        orgSwitcher={
          <OrgSwitcher organizations={organizations} currentOrgId={orgId} />
        }
        propertySwitcher={
          <PropertySwitcher
            organizationId={orgId}
            properties={properties}
            currentPropertyId={propertyId}
          />
        }
        sidebar={
          <SidebarNav
            orgId={orgId}
            propertyId={propertyId}
            isOwner={isOwner}
          />
        }
        userLabel={userLabel}
      >
        {children}
      </AppShell>
    );
  } catch (error) {
    if (error instanceof DwellioApiError) {
      return (
        <div className="min-h-screen bg-neutral-50">
          <AuthHeader brandHref={`/o/${orgId}`} userLabel={userLabel} />
          <main className="mx-auto max-w-lg p-8">
            <Alert tone="error" requestId={error.requestId}>
              {error.message}
            </Alert>
          </main>
        </div>
      );
    }
    throw error;
  }
}
