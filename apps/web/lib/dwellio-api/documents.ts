import { apiFetch } from "./client";

export type DocumentItem = {
  id: string;
  organizationId: string;
  propertyId: string;
  tenantId: string | null;
  tenancyId: string | null;
  name: string;
  storageKey: string;
  contentType: string | null;
  sizeBytes: number | null;
  status: string;
  createdAt: string;
  updatedAt: string;
};

export type DocumentUploadIntent = {
  document: DocumentItem;
  uploadUrl: string;
};

export function listDocuments(token: string, propertyId: string) {
  return apiFetch<DocumentItem[]>(
    `/api/v1/properties/${propertyId}/documents`,
    { token },
  );
}

export function createDocumentIntent(
  token: string,
  propertyId: string,
  body: {
    name: string;
    contentType?: string;
    tenantId?: string;
    tenancyId?: string;
  },
) {
  return apiFetch<DocumentUploadIntent>(
    `/api/v1/properties/${propertyId}/documents`,
    { method: "POST", token, body },
  );
}

export function completeDocument(token: string, documentId: string) {
  return apiFetch<DocumentItem>(`/api/v1/documents/${documentId}/complete`, {
    method: "POST",
    token,
  });
}

export function deleteDocument(token: string, documentId: string) {
  return apiFetch<void>(`/api/v1/documents/${documentId}`, {
    method: "DELETE",
    token,
  });
}
