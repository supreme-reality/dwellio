"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import { payMoveInAction } from "@/app/(app)/move-in-actions";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { openRazorpayCheckout } from "@/lib/razorpay";

type MoveInPaymentFormProps = {
  orgId: string;
  propertyId: string;
  moveInId: string;
  currency: string;
};

type PaymentMethod = "RAZORPAY" | "CASH" | "BANK_TRANSFER";

const METHODS: {
  value: PaymentMethod;
  title: string;
  hint: string;
}[] = [
  {
    value: "RAZORPAY",
    title: "Razorpay",
    hint: "Online checkout. Stay stays draft until payment is captured.",
  },
  {
    value: "CASH",
    title: "Cash (manual)",
    hint: "Record money already received. Activates the tenancy immediately.",
  },
  {
    value: "BANK_TRANSFER",
    title: "Bank transfer (manual)",
    hint: "Record a transfer already received. Reference required.",
  },
];

export function MoveInPaymentForm({
  orgId,
  propertyId,
  moveInId,
  currency,
}: MoveInPaymentFormProps) {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>("RAZORPAY");

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

  const submitLabel =
    paymentMethod === "RAZORPAY"
      ? "Pay with Razorpay"
      : paymentMethod === "CASH"
        ? "Record cash payment"
        : "Record bank transfer";

  return (
    <form action={onSubmit} className="grid gap-3 sm:grid-cols-2">
      <input type="hidden" name="orgId" value={orgId} />
      <input type="hidden" name="propertyId" value={propertyId} />
      <input type="hidden" name="moveInId" value={moveInId} />
      <input type="hidden" name="currency" value={currency} />
      <fieldset className="sm:col-span-2">
        <legend className="mb-2 text-sm font-medium text-neutral-800">
          Payment method
        </legend>
        <div className="grid gap-2 sm:grid-cols-3">
          {METHODS.map((method) => {
            const selected = paymentMethod === method.value;
            return (
              <label
                key={method.value}
                className={`flex cursor-pointer flex-col gap-1 rounded-md border px-3 py-2 text-sm ${
                  selected
                    ? "border-neutral-900 bg-neutral-50"
                    : "border-neutral-300 bg-white"
                }`}
              >
                <span className="flex items-center gap-2 font-medium">
                  <input
                    type="radio"
                    name="paymentMethod"
                    value={method.value}
                    checked={selected}
                    onChange={() => setPaymentMethod(method.value)}
                    className="accent-neutral-900"
                  />
                  {method.title}
                </span>
                <span className="text-xs text-neutral-600">{method.hint}</span>
              </label>
            );
          })}
        </div>
      </fieldset>
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
      {paymentMethod === "BANK_TRANSFER" ? (
        <Input
          label="Bank transfer reference"
          name="bankTransferReference"
          placeholder="Required for bank transfer"
          required
        />
      ) : null}
      {error ? (
        <p className="sm:col-span-2 text-sm text-red-700">{error}</p>
      ) : null}
      <div className="sm:col-span-2">
        <Button type="submit" disabled={loading}>
          {loading ? "Processing…" : submitLabel}
        </Button>
      </div>
    </form>
  );
}
