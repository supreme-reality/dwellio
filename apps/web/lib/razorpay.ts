"use client";

type RazorpayCheckout = {
  keyId: string;
  orderId: string;
  amount: number | string;
  currency: string;
};

declare global {
  interface Window {
    Razorpay?: new (options: Record<string, unknown>) => {
      open: () => void;
    };
  }
}

function loadRazorpayScript(): Promise<void> {
  if (typeof window === "undefined") return Promise.resolve();
  if (window.Razorpay) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = "https://checkout.razorpay.com/v1/checkout.js";
    script.onload = () => resolve();
    script.onerror = () => reject(new Error("Failed to load Razorpay Checkout"));
    document.body.appendChild(script);
  });
}

export async function openRazorpayCheckout(
  checkout: RazorpayCheckout,
  onSuccess: () => void,
  description = "Payment",
): Promise<void> {
  await loadRazorpayScript();
  if (!window.Razorpay) {
    throw new Error("Razorpay Checkout is unavailable");
  }

  const key =
    checkout.keyId || process.env.NEXT_PUBLIC_RAZORPAY_KEY_ID || "";
  if (!key || key === "rzp_test_local") {
    throw new Error("Razorpay key is not configured");
  }

  const amountPaise =
    typeof checkout.amount === "string"
      ? Math.round(Number(checkout.amount) * 100)
      : Math.round(Number(checkout.amount) * 100);

  const rzp = new window.Razorpay({
    key,
    order_id: checkout.orderId,
    amount: amountPaise,
    currency: checkout.currency,
    name: "Dwellio",
    description,
    handler: () => onSuccess(),
  });
  rzp.open();
}
