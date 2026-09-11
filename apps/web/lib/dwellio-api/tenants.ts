import { apiFetch } from "./client";

export type Tenant = {
  id: string;
  organizationId: string;
  firstName: string;
  lastName: string | null;
  phone: string;
  email: string | null;
  dateOfBirth: string | null;
  gender: string | null;
  address: string | null;
  emergencyContactName: string | null;
  emergencyContactPhone: string | null;
  governmentId: string | null;
  notes: string | null;
  status: string;
  createdAt: string;
  updatedAt: string;
};

export type StayHistoryItem = {
  tenancyId: string;
  propertyId: string;
  status: string;
  createdAt: string;
};

export type TenancyStayContext = {
  id: string;
  tenantId: string;
  propertyId: string;
  status: string;
  currentOccupancy?: {
    occupancyId: string;
    bedId: string;
    status: string;
    startedAt: string;
  } | null;
  createdAt: string;
  updatedAt: string;
};

export type CreateTenantBody = {
  firstName: string;
  lastName?: string;
  phone: string;
  email?: string;
  dateOfBirth?: string;
  gender?: string;
  address?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  governmentId?: string;
  notes?: string;
};

export function listTenants(token: string, propertyId: string) {
  return apiFetch<Tenant[]>(`/api/v1/properties/${propertyId}/tenants`, {
    token,
  });
}

export function createTenant(
  token: string,
  propertyId: string,
  body: CreateTenantBody,
) {
  return apiFetch<Tenant>(`/api/v1/properties/${propertyId}/tenants`, {
    method: "POST",
    token,
    body,
  });
}

export function getTenant(token: string, tenantId: string) {
  return apiFetch<Tenant>(`/api/v1/tenants/${tenantId}`, { token });
}

export function getTenantStayHistory(token: string, tenantId: string) {
  return apiFetch<StayHistoryItem[]>(
    `/api/v1/tenants/${tenantId}/stay-history`,
    { token },
  );
}

export function getTenancy(token: string, tenancyId: string) {
  return apiFetch<TenancyStayContext>(`/api/v1/tenancies/${tenancyId}`, {
    token,
  });
}
