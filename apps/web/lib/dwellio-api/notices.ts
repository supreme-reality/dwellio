import { apiFetch } from "./client";

export type Notice = {
  id: string;
  propertyId: string;
  title: string;
  body: string;
  status: string;
  publishedAt: string | null;
  expiresAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export function listNotices(token: string, propertyId: string) {
  return apiFetch<Notice[]>(`/api/v1/properties/${propertyId}/notices`, {
    token,
  });
}

export function createNotice(
  token: string,
  propertyId: string,
  body: { title: string; body: string; expiresAt?: string },
) {
  return apiFetch<Notice>(`/api/v1/properties/${propertyId}/notices`, {
    method: "POST",
    token,
    body,
  });
}

export function updateNotice(
  token: string,
  noticeId: string,
  body: { title?: string; body?: string; expiresAt?: string },
) {
  return apiFetch<Notice>(`/api/v1/notices/${noticeId}`, {
    method: "PATCH",
    token,
    body,
  });
}

export function publishNotice(token: string, noticeId: string) {
  return apiFetch<Notice>(`/api/v1/notices/${noticeId}/publish`, {
    method: "POST",
    token,
  });
}

export function deleteNotice(token: string, noticeId: string) {
  return apiFetch<void>(`/api/v1/notices/${noticeId}`, {
    method: "DELETE",
    token,
  });
}
