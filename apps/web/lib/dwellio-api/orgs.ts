import { apiFetch } from "./client";
import type { Organization, OrganizationMember } from "./types";

export function listOrganizations(token: string) {
  return apiFetch<Organization[]>("/api/v1/organizations", { token });
}

export function createOrganization(token: string, body: { name: string }) {
  return apiFetch<Organization>("/api/v1/organizations", {
    method: "POST",
    token,
    body,
  });
}

export function getOrganization(token: string, organizationId: string) {
  return apiFetch<Organization>(`/api/v1/organizations/${organizationId}`, {
    token,
  });
}

export function listOrganizationMembers(token: string, organizationId: string) {
  return apiFetch<OrganizationMember[]>(
    `/api/v1/organizations/${organizationId}/members`,
    { token },
  );
}

export function addOrganizationMember(
  token: string,
  organizationId: string,
  body: { email: string },
) {
  return apiFetch<OrganizationMember>(
    `/api/v1/organizations/${organizationId}/members`,
    { method: "POST", token, body },
  );
}

export function updateOrganizationMember(
  token: string,
  organizationId: string,
  membershipId: string,
  body: { status: "ACTIVE" | "INACTIVE" },
) {
  return apiFetch<OrganizationMember>(
    `/api/v1/organizations/${organizationId}/members/${membershipId}`,
    { method: "PATCH", token, body },
  );
}
