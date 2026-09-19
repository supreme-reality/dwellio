import { updateTicketStatusAction } from "@/app/(app)/ops-actions";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Select } from "@/components/ui/select";
import { requireAccessToken } from "@/lib/auth";
import { DwellioApiError } from "@/lib/dwellio-api/client";
import { getTicket } from "@/lib/dwellio-api/tickets";
import Link from "next/link";

export default async function TicketDetailPage({
  params,
}: {
  params: Promise<{ orgId: string; propertyId: string; ticketId: string }>;
}) {
  const { orgId, propertyId, ticketId } = await params;
  const token = await requireAccessToken();

  try {
    const ticket = await getTicket(token, ticketId);

    return (
      <div className="space-y-4">
        <Link
          href={`/o/${orgId}/p/${propertyId}/tickets`}
          className="text-sm underline"
        >
          ← Tickets
        </Link>
        <h1 className="text-2xl font-semibold tracking-tight">{ticket.title}</h1>
        <Badge>{ticket.status}</Badge>
        <p className="whitespace-pre-wrap text-sm text-neutral-700">
          {ticket.body || "No body"}
        </p>
        <form
          action={updateTicketStatusAction}
          className="flex flex-wrap items-end gap-3"
        >
          <input type="hidden" name="orgId" value={orgId} />
          <input type="hidden" name="propertyId" value={propertyId} />
          <input type="hidden" name="ticketId" value={ticketId} />
          <Select label="Status" name="status" defaultValue={ticket.status}>
            <option value="OPEN">Open</option>
            <option value="IN_PROGRESS">In progress</option>
            <option value="RESOLVED">Resolved</option>
            <option value="CLOSED">Closed</option>
          </Select>
          <Button type="submit">Update status</Button>
        </form>
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
