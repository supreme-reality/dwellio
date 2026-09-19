import { apiFetch } from "./client";
import type { Property, PropertyManager } from "./types";

export function listProperties(token: string, organizationId: string) {
  return apiFetch<Property[]>(
    `/api/v1/organizations/${organizationId}/properties`,
    { token },
  );
}

export function createProperty(
  token: string,
  organizationId: string,
  body: {
    name: string;
    address?: string;
    paymentDueDays: number;
    defaultCurrency: string;
  },
) {
  return apiFetch<Property>(
    `/api/v1/organizations/${organizationId}/properties`,
    { method: "POST", token, body },
  );
}

export function getProperty(token: string, propertyId: string) {
  return apiFetch<Property>(`/api/v1/properties/${propertyId}`, { token });
}

export function updateProperty(
  token: string,
  propertyId: string,
  body: {
    name?: string;
    address?: string;
    paymentDueDays?: number;
    defaultCurrency?: string;
  },
) {
  return apiFetch<Property>(`/api/v1/properties/${propertyId}`, {
    method: "PATCH",
    token,
    body,
  });
}

export function listPropertyManagers(token: string, propertyId: string) {
  return apiFetch<PropertyManager[]>(
    `/api/v1/properties/${propertyId}/managers`,
    { token },
  );
}

export function assignPropertyManager(
  token: string,
  propertyId: string,
  body: { email: string },
) {
  return apiFetch<PropertyManager>(
    `/api/v1/properties/${propertyId}/managers`,
    { method: "POST", token, body },
  );
}

export function removePropertyManager(
  token: string,
  propertyId: string,
  propertyMembershipId: string,
) {
  return apiFetch<void>(
    `/api/v1/properties/${propertyId}/managers/${propertyMembershipId}`,
    { method: "DELETE", token },
  );
}
