import {
  createNoticeAction,
  deleteNoticeAction,
  publishNoticeAction,
} from "@/app/(app)/ops-actions";
import { MarkdownEditor } from "@/components/markdown/markdown-editor";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { requireAccessToken } from "@/lib/auth";
import { DwellioApiError } from "@/lib/dwellio-api/client";
import { listNotices } from "@/lib/dwellio-api/notices";

export default async function NoticesPage({
  params,
}: {
  params: Promise<{ orgId: string; propertyId: string }>;
}) {
  const { orgId, propertyId } = await params;
  const token = await requireAccessToken();

  try {
    const notices = await listNotices(token, propertyId);

    return (
      <div className="space-y-6">
        <h1 className="text-2xl font-semibold tracking-tight">Notices</h1>
        <p className="text-sm text-neutral-600">
          Markdown only. Draft / publish / delete (no archive).
        </p>

        {notices.length === 0 ? (
          <EmptyState title="No notices" />
        ) : (
          <ul className="space-y-3">
            {notices.map((notice) => (
              <li
                key={notice.id}
                className="rounded-lg border border-neutral-200 bg-white p-4"
              >
                <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                  <h2 className="font-semibold">{notice.title}</h2>
                  <Badge
                    tone={notice.status === "PUBLISHED" ? "success" : "neutral"}
                  >
                    {notice.status}
                  </Badge>
                </div>
                <pre className="mb-3 whitespace-pre-wrap text-sm text-neutral-700">
                  {notice.body}
                </pre>
                <div className="flex flex-wrap gap-2">
                  {notice.status === "DRAFT" ? (
                    <form action={publishNoticeAction}>
                      <input type="hidden" name="orgId" value={orgId} />
                      <input type="hidden" name="propertyId" value={propertyId} />
                      <input type="hidden" name="noticeId" value={notice.id} />
                      <Button type="submit" variant="secondary">
                        Publish
                      </Button>
                    </form>
                  ) : null}
                  <form action={deleteNoticeAction}>
                    <input type="hidden" name="orgId" value={orgId} />
                    <input type="hidden" name="propertyId" value={propertyId} />
                    <input type="hidden" name="noticeId" value={notice.id} />
                    <Button type="submit" variant="danger">
                      Delete
                    </Button>
                  </form>
                </div>
              </li>
            ))}
          </ul>
        )}

        <section className="rounded-lg border border-neutral-200 bg-white p-4">
          <h2 className="mb-3 text-lg font-semibold">New notice</h2>
          <form action={createNoticeAction}>
            <input type="hidden" name="orgId" value={orgId} />
            <input type="hidden" name="propertyId" value={propertyId} />
            <MarkdownEditor />
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
