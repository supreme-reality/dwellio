import { createTicketAction } from "@/app/(app)/ops-actions";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { requireAccessToken } from "@/lib/auth";
import { DwellioApiError } from "@/lib/dwellio-api/client";
import { listTickets } from "@/lib/dwellio-api/tickets";
import Link from "next/link";

function ticketTone(status: string) {
  if (status === "OPEN") return "warning" as const;
  if (status === "IN_PROGRESS") return "info" as const;
  if (status === "RESOLVED") return "success" as const;
  return "neutral" as const;
}

function ticketLabel(status: string) {
  if (status === "IN_PROGRESS") return "In progress";
  if (status === "OPEN") return "Open";
  if (status === "RESOLVED") return "Resolved";
  if (status === "CLOSED") return "Closed";
  return status;
}

export default async function TicketsPage({
  params,
}: {
  params: Promise<{ orgId: string; propertyId: string }>;
}) {
  const { orgId, propertyId } = await params;
  const token = await requireAccessToken();

  try {
    const tickets = await listTickets(token, propertyId);

    return (
      <div className="space-y-6">
        <h1 className="text-2xl font-semibold tracking-tight">Tickets</h1>

        {tickets.length === 0 ? (
          <EmptyState title="No tickets" />
        ) : (
          <ul className="divide-y divide-neutral-200 rounded-lg border border-neutral-200 bg-white">
            {tickets.map((ticket) => (
              <li key={ticket.id}>
                <Link
                  href={`/o/${orgId}/p/${propertyId}/tickets/${ticket.id}`}
                  className="flex flex-wrap items-center justify-between gap-2 px-4 py-3 hover:bg-neutral-50"
                >
                  <span className="font-medium">{ticket.title}</span>
                  <Badge tone={ticketTone(ticket.status)}>
                    {ticketLabel(ticket.status)}
                  </Badge>
                </Link>
              </li>
            ))}
          </ul>
        )}

        <section className="rounded-lg border border-neutral-200 bg-white p-4">
          <h2 className="mb-3 text-lg font-semibold">Create ticket</h2>
          <form action={createTicketAction} className="space-y-3">
            <input type="hidden" name="orgId" value={orgId} />
            <input type="hidden" name="propertyId" value={propertyId} />
            <Input label="Title" name="title" required />
            <Textarea label="Body" name="body" />
            <Button type="submit">Create</Button>
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
