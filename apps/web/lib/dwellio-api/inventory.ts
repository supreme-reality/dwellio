import { apiFetch } from "./client";

export type Room = {
  id: string;
  propertyId: string;
  name: string;
  status: string;
};

export type Bed = {
  id: string;
  roomId: string;
  name: string;
  status: string;
  availability: "AVAILABLE" | "OCCUPIED" | "BLOCKED" | null;
  blockReason: string | null;
};

export function listRooms(token: string, propertyId: string) {
  return apiFetch<Room[]>(`/api/v1/properties/${propertyId}/rooms`, { token });
}

export function createRoom(
  token: string,
  propertyId: string,
  body: { name: string },
) {
  return apiFetch<Room>(`/api/v1/properties/${propertyId}/rooms`, {
    method: "POST",
    token,
    body,
  });
}

export function updateRoom(
  token: string,
  roomId: string,
  body: { name?: string; status?: "ACTIVE" | "INACTIVE" },
) {
  return apiFetch<Room>(`/api/v1/rooms/${roomId}`, {
    method: "PATCH",
    token,
    body,
  });
}

export function listBedsForProperty(token: string, propertyId: string) {
  return apiFetch<Bed[]>(`/api/v1/properties/${propertyId}/beds`, { token });
}

export function listBedsForRoom(token: string, roomId: string) {
  return apiFetch<Bed[]>(`/api/v1/rooms/${roomId}/beds`, { token });
}

export function createBed(
  token: string,
  roomId: string,
  body: { name: string },
) {
  return apiFetch<Bed>(`/api/v1/rooms/${roomId}/beds`, {
    method: "POST",
    token,
    body,
  });
}

export function updateBed(
  token: string,
  bedId: string,
  body: { name?: string; status?: "ACTIVE" | "INACTIVE" },
) {
  return apiFetch<Bed>(`/api/v1/beds/${bedId}`, {
    method: "PATCH",
    token,
    body,
  });
}

export function blockBed(token: string, bedId: string, body: { reason: string }) {
  return apiFetch<Bed>(`/api/v1/beds/${bedId}/block`, {
    method: "POST",
    token,
    body,
  });
}

export function unblockBed(token: string, bedId: string) {
  return apiFetch<Bed>(`/api/v1/beds/${bedId}/unblock`, {
    method: "POST",
    token,
  });
}
