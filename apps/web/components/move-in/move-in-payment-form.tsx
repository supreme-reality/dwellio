"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import { payMoveInAction } from "@/app/(app)/move-in-actions";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { openRazorpayCheckout } from "@/lib/razorpay";

type MoveInPaymentFormProps = {
  orgId: string;
  propertyId: string;
  moveInId: string;
  currency: string;
};

export function MoveInPaymentForm({
  orgId,
  propertyId,
  moveInId,
  currency,
}: MoveInPaymentFormProps) {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onSubmit(formData: FormData) {
    setLoading(true);
    setError(null);
    try {
      const result = await payMoveInAction(formData);
      if (result && "mode" in result && result.mode === "razorpay") {
        const checkout = result.result.razorpay;
        if (!checkout) {
          throw new Error("Razorpay checkout payload missing");
        }
        await openRazorpayCheckout(checkout, () => {
          router.push(result.tenancyPath);
          router.refresh();
        });
        return;
      }
    } catch (err) {
      // redirect() throws NEXT_REDIRECT — ignore
      if (
        typeof err === "object" &&
        err !== null &&
        "digest" in err &&
        String((err as { digest?: string }).digest).startsWith("NEXT_REDIRECT")
      ) {
        throw err;
      }
      setError(err instanceof Error ? err.message : "Payment failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <form action={onSubmit} className="grid gap-3 sm:grid-cols-2">
      <input type="hidden" name="orgId" value={orgId} />
      <input type="hidden" name="propertyId" value={propertyId} />
      <input type="hidden" name="moveInId" value={moveInId} />
      <input type="hidden" name="currency" value={currency} />
      <Select label="Payment method" name="paymentMethod" defaultValue="CASH">
        <option value="CASH">CASH</option>
        <option value="BANK_TRANSFER">BANK_TRANSFER</option>
        <option value="RAZORPAY">RAZORPAY</option>
      </Select>
      <Input
        label="Amount"
        name="amount"
        type="number"
        step="0.01"
        min="0.01"
        required
      />
      <Input
        label="Deposit amount (optional)"
        name="depositAmount"
        type="number"
        step="0.01"
        min="0"
      />
      <Input
        label="Bank transfer reference"
        name="bankTransferReference"
        placeholder="Required for BANK_TRANSFER"
      />
      {error ? (
        <p className="sm:col-span-2 text-sm text-red-700">{error}</p>
      ) : null}
      <div className="sm:col-span-2">
        <Button type="submit" disabled={loading}>
          {loading ? "Processing…" : "Confirm & Pay / Record Payment"}
        </Button>
      </div>
    </form>
  );
}
