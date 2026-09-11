import {
  deleteDocumentAction,
  uploadDocumentAction,
} from "@/app/(app)/ops-actions";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { Input } from "@/components/ui/input";
import { requireAccessToken } from "@/lib/auth";
import { DwellioApiError } from "@/lib/dwellio-api/client";
import { listDocuments } from "@/lib/dwellio-api/documents";

function documentLabel(status: string) {
  if (status === "PENDING_UPLOAD") return "Pending upload";
  if (status === "UPLOADED") return "Uploaded";
  return status;
}

export default async function DocumentsPage({
  params,
}: {
  params: Promise<{ orgId: string; propertyId: string }>;
}) {
  const { orgId, propertyId } = await params;
  const token = await requireAccessToken();

  try {
    const documents = await listDocuments(token, propertyId);

    return (
      <div className="space-y-6">
        <h1 className="text-2xl font-semibold tracking-tight">Documents</h1>
        <p className="text-sm text-neutral-600">
          Pending upload → Uploaded. Delete removes the file. No archive.
        </p>

        {documents.length === 0 ? (
          <EmptyState title="No documents" />
        ) : (
          <ul className="divide-y divide-neutral-200 rounded-lg border border-neutral-200 bg-white">
            {documents.map((doc) => (
              <li
                key={doc.id}
                className="flex flex-wrap items-center justify-between gap-3 px-4 py-3"
              >
                <div>
                  <div className="font-medium">{doc.name}</div>
                  <Badge
                    tone={doc.status === "UPLOADED" ? "success" : "warning"}
                  >
                    {documentLabel(doc.status)}
                  </Badge>
                </div>
                <form action={deleteDocumentAction}>
                  <input type="hidden" name="orgId" value={orgId} />
                  <input type="hidden" name="propertyId" value={propertyId} />
                  <input type="hidden" name="documentId" value={doc.id} />
                  <Button type="submit" variant="danger">
                    Delete
                  </Button>
                </form>
              </li>
            ))}
          </ul>
        )}

        <section className="rounded-lg border border-neutral-200 bg-white p-4">
          <h2 className="mb-3 text-lg font-semibold">Upload document</h2>
          <form
            action={uploadDocumentAction}
            className="flex flex-wrap items-end gap-3"
          >
            <input type="hidden" name="orgId" value={orgId} />
            <input type="hidden" name="propertyId" value={propertyId} />
            <Input label="Display name" name="name" required />
            <label className="flex flex-col gap-1 text-sm">
              <span className="font-medium text-neutral-800">File</span>
              <input
                type="file"
                name="file"
                required
                className="text-sm"
              />
            </label>
            <Button type="submit">Upload</Button>
          </form>
        </section>
      </div>
    );
  } catch (error) {
    if (error instanceof DwellioApiError) {
      return (
        <Alert tone="error" requestId={error.requestId}>
          {error.message}
        </Alert>
      );
    }
    throw error;
  }
}
