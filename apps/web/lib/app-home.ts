import { listOrganizations } from "@/lib/dwellio-api/orgs";
import { listProperties } from "@/lib/dwellio-api/properties";

export async function resolveAppHomePath(token: string): Promise<string> {
  const orgs = await listOrganizations(token);
  if (orgs.length === 0) {
    return "/onboarding";
  }

  const org = orgs[0];
  const properties = await listProperties(token, org.id);
  if (properties.length === 0) {
    return `/o/${org.id}`;
  }

  return `/o/${org.id}/p/${properties[0].id}/inventory`;
}
