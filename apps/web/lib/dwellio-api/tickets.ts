import { apiFetch } from "./client";

export type Ticket = {
  id: string;
  propertyId: string;
  title: string;
  body: string | null;
  status: string;
  createdByUserId: string;
  assignedToUserId: string | null;
  createdAt: string;
  updatedAt: string;
};

export function listTickets(token: string, propertyId: string) {
  return apiFetch<Ticket[]>(`/api/v1/properties/${propertyId}/tickets`, {
    token,
  });
}

export function createTicket(
  token: string,
  propertyId: string,
  body: {
    title: string;
    body?: string;
    assignedToUserId?: string;
    status?: "OPEN" | "IN_PROGRESS";
  },
) {
  return apiFetch<Ticket>(`/api/v1/properties/${propertyId}/tickets`, {
    method: "POST",
    token,
    body,
  });
}

export function getTicket(token: string, ticketId: string) {
  return apiFetch<Ticket>(`/api/v1/tickets/${ticketId}`, { token });
}

export function updateTicket(
  token: string,
  ticketId: string,
  body: {
    title?: string;
    body?: string;
    status?: "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CLOSED";
    assignedToUserId?: string;
    clearAssignee?: boolean;
  },
) {
  return apiFetch<Ticket>(`/api/v1/tickets/${ticketId}`, {
    method: "PATCH",
    token,
    body,
  });
}
