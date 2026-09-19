"use client";

import { useState } from "react";

import {
  blockBedAction,
  unblockBedAction,
} from "@/app/(app)/inventory-actions";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Modal } from "@/components/ui/modal";
import { Textarea } from "@/components/ui/textarea";
import type { Bed } from "@/lib/dwellio-api/inventory";

function availabilityTone(availability: Bed["availability"]) {
  if (availability === "AVAILABLE") return "success" as const;
  if (availability === "OCCUPIED") return "info" as const;
  if (availability === "BLOCKED") return "danger" as const;
  return "neutral" as const;
}

type BedRowProps = {
  bed: Bed;
  orgId: string;
  propertyId: string;
};

export function BedRow({ bed, orgId, propertyId }: BedRowProps) {
  const [blockOpen, setBlockOpen] = useState(false);
  const [unblockOpen, setUnblockOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function submitBlock() {
    setError(null);
    if (!reason.trim()) {
      setError("Reason is required");
      return;
    }
    const formData = new FormData();
    formData.set("orgId", orgId);
    formData.set("propertyId", propertyId);
    formData.set("bedId", bed.id);
    formData.set("reason", reason.trim());
    try {
      await blockBedAction(formData);
      setBlockOpen(false);
      setReason("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to block bed");
    }
  }

  async function submitUnblock() {
    const formData = new FormData();
    formData.set("orgId", orgId);
    formData.set("propertyId", propertyId);
    formData.set("bedId", bed.id);
    try {
      await unblockBedAction(formData);
      setUnblockOpen(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to unblock bed");
    }
  }

  return (
    <li className="flex flex-wrap items-start justify-between gap-3 border-t border-neutral-100 py-3 first:border-t-0">
      <div>
        <div className="font-medium">{bed.name}</div>
        <div className="mt-1 flex flex-wrap items-center gap-2">
          {bed.availability ? (
            <Badge tone={availabilityTone(bed.availability)}>
              {bed.availability}
            </Badge>
          ) : null}
          {bed.availability === "BLOCKED" && bed.blockReason ? (
            <span className="text-sm text-neutral-600">
              Reason: {bed.blockReason}
            </span>
          ) : null}
        </div>
        {error ? <p className="mt-1 text-xs text-red-700">{error}</p> : null}
      </div>
      <div className="flex gap-2">
        {bed.availability === "BLOCKED" ? (
          <Button
            type="button"
            variant="secondary"
            onClick={() => setUnblockOpen(true)}
          >
            Unblock
          </Button>
        ) : (
          <Button
            type="button"
            variant="secondary"
            onClick={() => setBlockOpen(true)}
          >
            Block
          </Button>
        )}
      </div>

      {blockOpen ? (
        <Modal title={`Block ${bed.name}`} onClose={() => setBlockOpen(false)}>
          <div className="space-y-3">
            <Textarea
              label="Reason"
              name="reason"
              required
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              placeholder="Maintenance, hold, etc."
            />
            <div className="flex justify-end gap-2">
              <Button
                type="button"
                variant="secondary"
                onClick={() => setBlockOpen(false)}
              >
                Cancel
              </Button>
              <Button type="button" variant="danger" onClick={submitBlock}>
                Block bed
              </Button>
            </div>
          </div>
        </Modal>
      ) : null}

      {unblockOpen ? (
        <ConfirmDialog
          title={`Unblock ${bed.name}?`}
          body="This bed will become available if it is not occupied."
          confirmLabel="Unblock"
          onCancel={() => setUnblockOpen(false)}
          onConfirm={submitUnblock}
        />
      ) : null}
    </li>
  );
}
