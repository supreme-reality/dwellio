"use client";

import { useRouter } from "next/navigation";
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
import { openRazorpayCheckout } from "@/lib/razorpay";

type CheckoutFormProps = {
  orgId: string;
  propertyId: string;
  tenancyId: string;
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
    hint: "Collect the full net receivable online.",
  },
  {
    value: "CASH",
    title: "Cash (manual)",
    hint: "Record cash already received for the full net receivable.",
  },
  {
    value: "BANK_TRANSFER",
    title: "Bank transfer (manual)",
    hint: "Record a transfer already received. Reference required.",
  },
];

export function CheckoutForm({
  orgId,
  propertyId,
  tenancyId,
}: CheckoutFormProps) {
  const router = useRouter();
  const [preview, setPreview] = useState<CheckoutPreview | null>(null);
  const [damages, setDamages] = useState("0");
  const [manual, setManual] = useState("0");
  const [leaveReceivable, setLeaveReceivable] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>("RAZORPAY");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const netReceivable = preview ? Number(preview.netReceivable) : 0;
  const needsCollection = netReceivable > 0;

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

  async function onConfirm(formData: FormData) {
    setLoading(true);
    setError(null);
    try {
      const result = await confirmCheckoutAction(formData);
      if (result && "mode" in result && result.mode === "razorpay") {
        await openRazorpayCheckout(
          result.razorpay,
          () => {
            router.push(result.donePath);
            router.refresh();
          },
          "Checkout payment",
        );
      }
    } catch (err) {
      if (
        typeof err === "object" &&
        err !== null &&
        "digest" in err &&
        String((err as { digest?: string }).digest).startsWith("NEXT_REDIRECT")
      ) {
        throw err;
      }
      setError(err instanceof Error ? err.message : "Checkout failed");
    } finally {
      setLoading(false);
    }
  }

  const submitLabel = !needsCollection || leaveReceivable
    ? "Confirm checkout"
    : paymentMethod === "RAZORPAY"
      ? "Pay with Razorpay"
      : paymentMethod === "CASH"
        ? "Record cash and confirm"
        : "Record transfer and confirm";

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

          {needsCollection ? (
            <>
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={leaveReceivable}
                  onChange={(event) => setLeaveReceivable(event.target.checked)}
                />
                Leave receivable unpaid (explicit — no partial payment)
              </label>

              {!leaveReceivable ? (
                <fieldset>
                  <legend className="mb-2 text-sm font-medium text-neutral-800">
                    Pay full net receivable ({formatMoney(preview.netReceivable, preview.currency)})
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
                              name="paymentMethodChoice"
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
              ) : null}
            </>
          ) : null}

          <form action={onConfirm} className="space-y-3">
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
            {needsCollection && !leaveReceivable ? (
              <>
                <input type="hidden" name="paymentMethod" value={paymentMethod} />
                {paymentMethod === "BANK_TRANSFER" ? (
                  <Input
                    label="Bank transfer reference"
                    name="bankTransferReference"
                    placeholder="Required for bank transfer"
                    required
                  />
                ) : null}
              </>
            ) : null}
            <Button type="submit" disabled={loading}>
              {loading ? "Processing…" : submitLabel}
            </Button>
          </form>
        </>
      ) : null}
    </div>
  );
}
