import { apiFetch } from "./client";

export type ServiceItem = {
  id: string;
  propertyId: string;
  name: string;
  billingType: string;
  billingTiming: string;
  mandatory: boolean;
  prorationSetting: string;
  status: string;
  createdAt: string;
  updatedAt: string;
};

export type ServiceConfig = {
  id: string;
  serviceId: string;
  amount: number | string;
  effectiveFrom: string;
  effectiveTo: string | null;
  createdAt: string;
  updatedAt: string;
};

export type ServiceEnrollment = {
  id: string;
  tenancyId: string;
  serviceId: string;
  status: string;
  startedAt: string;
  endedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export function listServices(token: string, propertyId: string) {
  return apiFetch<ServiceItem[]>(`/api/v1/properties/${propertyId}/services`, {
    token,
  });
}

export function createService(
  token: string,
  propertyId: string,
  body: {
    name: string;
    billingType: "FIXED" | "VARIABLE";
    billingTiming: "UPFRONT" | "MONTHLY_ARREARS";
    mandatory: boolean;
    prorationSetting: string;
  },
) {
  return apiFetch<ServiceItem>(`/api/v1/properties/${propertyId}/services`, {
    method: "POST",
    token,
    body,
  });
}

export function listServiceConfig(token: string, serviceId: string) {
  return apiFetch<ServiceConfig[]>(`/api/v1/services/${serviceId}/config`, {
    token,
  });
}

export function createServiceConfig(
  token: string,
  serviceId: string,
  body: { amount: number; effectiveFrom: string },
) {
  return apiFetch<ServiceConfig>(`/api/v1/services/${serviceId}/config`, {
    method: "POST",
    token,
    body,
  });
}

export function listTenancyServices(token: string, tenancyId: string) {
  return apiFetch<ServiceEnrollment[]>(
    `/api/v1/tenancies/${tenancyId}/services`,
    { token },
  );
}

export function enrollService(
  token: string,
  tenancyId: string,
  body: { serviceId: string; startedAt: string },
) {
  return apiFetch<ServiceEnrollment>(
    `/api/v1/tenancies/${tenancyId}/services`,
    { method: "POST", token, body },
  );
}

export function endEnrollment(
  token: string,
  enrollmentId: string,
  body: { endedAt: string },
) {
  return apiFetch<ServiceEnrollment>(
    `/api/v1/service-enrollments/${enrollmentId}/end`,
    { method: "POST", token, body },
  );
}

export function recordServiceCharge(
  token: string,
  enrollmentId: string,
  body: { billingPeriod: string; amount: number; note?: string },
) {
  return apiFetch(`/api/v1/service-enrollments/${enrollmentId}/charges`, {
    method: "POST",
    token,
    body,
  });
}
