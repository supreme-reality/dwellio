import { apiFetch } from "./client";

export type RentConfig = {
  id: string;
  propertyId: string;
  roomId: string | null;
  bedId: string | null;
  amount: number | string;
  effectiveFrom: string;
  effectiveTo: string | null;
  level: string;
};

export type ResolvedRent = {
  amount: number | string;
  level: string;
  rentConfigId: string;
  effectiveFrom: string;
  effectiveTo: string | null;
  currency: string;
};

export type CreateRentBody = {
  amount: number;
  effectiveFrom: string;
  effectiveTo?: string | null;
};

export function listRentConfig(token: string, propertyId: string) {
  return apiFetch<RentConfig[]>(
    `/api/v1/properties/${propertyId}/rent-config`,
    { token },
  );
}

export function createPropertyRent(
  token: string,
  propertyId: string,
  body: CreateRentBody,
) {
  return apiFetch<RentConfig>(
    `/api/v1/properties/${propertyId}/rent-config`,
    { method: "POST", token, body },
  );
}

export function createRoomRent(
  token: string,
  roomId: string,
  body: CreateRentBody,
) {
  return apiFetch<RentConfig>(`/api/v1/rooms/${roomId}/rent-config`, {
    method: "POST",
    token,
    body,
  });
}

export function createBedRent(
  token: string,
  bedId: string,
  body: CreateRentBody,
) {
  return apiFetch<RentConfig>(`/api/v1/beds/${bedId}/rent-config`, {
    method: "POST",
    token,
    body,
  });
}

export function resolveRent(
  token: string,
  propertyId: string,
  bedId: string,
  date: string,
) {
  const params = new URLSearchParams({ bedId, date });
  return apiFetch<ResolvedRent>(
    `/api/v1/properties/${propertyId}/rent/resolve?${params}`,
    { token },
  );
}
