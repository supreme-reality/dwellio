import {
  addMemberAction,
  setMemberStatusAction,
} from "@/app/(app)/actions";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { requireAccessToken } from "@/lib/auth";
import { DwellioApiError } from "@/lib/dwellio-api/client";
import { getOrganization, listOrganizationMembers } from "@/lib/dwellio-api/orgs";

export default async function MembersSettingsPage({
  params,
}: {
  params: Promise<{ orgId: string; propertyId: string }>;
}) {
  const { orgId, propertyId } = await params;
  const token = await requireAccessToken();

  try {
    const org = await getOrganization(token, orgId);
    if (org.role !== "OWNER") {
      return <Alert tone="error">Only owners can manage members.</Alert>;
    }

    const members = await listOrganizationMembers(token, orgId);

    return (
      <div className="space-y-6">
        <h1 className="text-2xl font-semibold tracking-tight">Members</h1>

        <ul className="divide-y divide-neutral-200 rounded-lg border border-neutral-200 bg-white">
          {members.map((member) => (
            <li
              key={member.membershipId}
              className="flex flex-wrap items-center justify-between gap-3 px-4 py-3"
            >
              <div>
                <div className="font-medium">{member.name || member.email}</div>
                <div className="text-sm text-neutral-600">{member.email}</div>
              </div>
              <div className="flex items-center gap-2">
                <Badge>{member.role}</Badge>
                <Badge
                  tone={member.status === "ACTIVE" ? "success" : "neutral"}
                >
                  {member.status}
                </Badge>
                {member.role !== "OWNER" ? (
                  <form action={setMemberStatusAction}>
                    <input type="hidden" name="organizationId" value={orgId} />
                    <input type="hidden" name="propertyId" value={propertyId} />
                    <input
                      type="hidden"
                      name="membershipId"
                      value={member.membershipId}
                    />
                    <input
                      type="hidden"
                      name="status"
                      value={
                        member.status === "ACTIVE" ? "INACTIVE" : "ACTIVE"
                      }
                    />
                    <Button type="submit" variant="secondary">
                      {member.status === "ACTIVE" ? "Deactivate" : "Reactivate"}
                    </Button>
                  </form>
                ) : null}
              </div>
            </li>
          ))}
        </ul>

        <section className="rounded-lg border border-neutral-200 bg-white p-4">
          <h2 className="mb-3 text-lg font-semibold">Add member</h2>
          <form action={addMemberAction} className="flex flex-wrap items-end gap-3">
            <input type="hidden" name="organizationId" value={orgId} />
            <input type="hidden" name="propertyId" value={propertyId} />
            <Input label="Email" name="email" type="email" required />
            <Button type="submit">Add</Button>
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
