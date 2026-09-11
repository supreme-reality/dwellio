"use client";

import { useState } from "react";

import {
  executeTransferAction,
  previewTransferAction,
} from "@/app/(app)/transfer-checkout-actions";
import { PreviewPanel } from "@/components/ui/preview-panel";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import type { TransferPreview } from "@/lib/dwellio-api/transfer";
import { formatMoney } from "@/lib/format";

type BedOption = { id: string; name: string };

type TransferFormProps = {
  orgId: string;
  propertyId: string;
  tenancyId: string;
  beds: BedOption[];
};

export function TransferForm({
  orgId,
  propertyId,
  tenancyId,
  beds,
}: TransferFormProps) {
  const [preview, setPreview] = useState<TransferPreview | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function onPreview(formData: FormData) {
    setError(null);
    try {
      const result = await previewTransferAction(formData);
      setPreview(result);
    } catch (err) {
      setPreview(null);
      setError(err instanceof Error ? err.message : "Preview failed");
    }
  }

  return (
    <div className="space-y-4">
      <form action={onPreview} className="grid gap-3 sm:grid-cols-2">
        <input type="hidden" name="tenancyId" value={tenancyId} />
        <Select label="Destination bed" name="destinationBedId" required defaultValue="">
          <option value="" disabled>
            Select AVAILABLE bed
          </option>
          {beds.map((bed) => (
            <option key={bed.id} value={bed.id}>
              {bed.name}
            </option>
          ))}
        </Select>
        <Input
          label="Proration override (optional)"
          name="prorationOverrideAmount"
          type="number"
          step="0.01"
          min="0"
        />
        <div className="sm:col-span-2">
          <Button type="submit" variant="secondary">
            Preview transfer
          </Button>
        </div>
      </form>

      {error ? <p className="text-sm text-red-700">{error}</p> : null}

      {preview ? (
        <>
          <PreviewPanel title="Transfer">
            <p>Destination available: {String(preview.destinationAvailable)}</p>
            <p>
              Source rent: {formatMoney(preview.sourceRent, preview.currency)}
            </p>
            <p>
              Destination rent:{" "}
              {formatMoney(preview.destinationRent, preview.currency)}
            </p>
            <p>
              Proration diff:{" "}
              {formatMoney(preview.prorationDiff, preview.currency)}
            </p>
            <p>
              Charge amount:{" "}
              {formatMoney(preview.chargeAmount, preview.currency)}
            </p>
            <p className="pt-2 font-medium">Current tenancy services (unchanged)</p>
            <ul className="list-disc pl-5">
              {preview.currentEnrollments.map((enrollment) => (
                <li key={enrollment.id}>
                  {enrollment.serviceId.slice(0, 8)}… · {enrollment.status}
                </li>
              ))}
              {preview.currentEnrollments.length === 0 ? (
                <li>None</li>
              ) : null}
            </ul>
          </PreviewPanel>

          <form action={executeTransferAction} className="space-y-2">
            <input type="hidden" name="orgId" value={orgId} />
            <input type="hidden" name="propertyId" value={propertyId} />
            <input type="hidden" name="tenancyId" value={tenancyId} />
            <input
              type="hidden"
              name="destinationBedId"
              value={preview.destinationBedId}
            />
            <Button type="submit">Confirm transfer</Button>
          </form>
        </>
      ) : null}
    </div>
  );
}
