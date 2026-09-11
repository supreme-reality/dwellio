"use client";

import { useState } from "react";

import {
  confirmCheckoutAction,
  previewCheckoutAction,
} from "@/app/(app)/transfer-checkout-actions";
import { PreviewPanel } from "@/components/ui/preview-panel";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { CheckoutPreview } from "@/lib/dwellio-api/checkout";
import { formatMoney } from "@/lib/format";

type CheckoutFormProps = {
  orgId: string;
  propertyId: string;
  tenancyId: string;
};

export function CheckoutForm({
  orgId,
  propertyId,
  tenancyId,
}: CheckoutFormProps) {
  const [preview, setPreview] = useState<CheckoutPreview | null>(null);
  const [damages, setDamages] = useState("0");
  const [manual, setManual] = useState("0");
  const [leaveReceivable, setLeaveReceivable] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onPreview() {
    setError(null);
    const formData = new FormData();
    formData.set("tenancyId", tenancyId);
    formData.set("damagesAmount", damages);
    formData.set("manualChargesAmount", manual);
    try {
      setPreview(await previewCheckoutAction(formData));
    } catch (err) {
      setPreview(null);
      setError(err instanceof Error ? err.message : "Preview failed");
    }
  }

  return (
    <div className="space-y-4">
      <div className="grid gap-3 sm:grid-cols-2">
        <Input
          label="Damages amount"
          name="damagesAmount"
          type="number"
          step="0.01"
          min="0"
          value={damages}
          onChange={(event) => setDamages(event.target.value)}
        />
        <Input
          label="Manual charges"
          name="manualChargesAmount"
          type="number"
          step="0.01"
          min="0"
          value={manual}
          onChange={(event) => setManual(event.target.value)}
        />
        <div className="sm:col-span-2">
          <Button type="button" variant="secondary" onClick={onPreview}>
            Preview checkout
          </Button>
        </div>
      </div>

      {error ? <p className="text-sm text-red-700">{error}</p> : null}

      {preview ? (
        <>
          <PreviewPanel title="Checkout settlement">
            <p>
              Unpaid invoices (outstanding):{" "}
              {formatMoney(preview.outstandingReceivables, preview.currency)}
            </p>
            <p>
              New charges:{" "}
              {formatMoney(preview.newCheckoutCharges, preview.currency)}
            </p>
            <p>
              Deposit before:{" "}
              {formatMoney(preview.depositBalanceBefore, preview.currency)}
            </p>
            <p>
              Deposit applied:{" "}
              {formatMoney(preview.depositDeduction, preview.currency)}
            </p>
            <p>
              Net receivable:{" "}
              {formatMoney(preview.netReceivable, preview.currency)}
            </p>
            <p>
              Refund due: {formatMoney(preview.refundDue, preview.currency)}
            </p>
          </PreviewPanel>

          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={leaveReceivable}
              onChange={(event) => setLeaveReceivable(event.target.checked)}
            />
            Leave receivable unpaid (explicit — no partial payment)
          </label>

          <form action={confirmCheckoutAction}>
            <input type="hidden" name="orgId" value={orgId} />
            <input type="hidden" name="propertyId" value={propertyId} />
            <input type="hidden" name="tenancyId" value={tenancyId} />
            <input type="hidden" name="damagesAmount" value={damages} />
            <input type="hidden" name="manualChargesAmount" value={manual} />
            <input
              type="hidden"
              name="leaveReceivable"
              value={leaveReceivable ? "true" : "false"}
            />
            <Button type="submit">Confirm checkout</Button>
          </form>
        </>
      ) : null}
    </div>
  );
}
