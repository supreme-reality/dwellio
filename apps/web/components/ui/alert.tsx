import type { ReactNode } from "react";

type Tone = "error" | "info";

const toneClass: Record<Tone, string> = {
  error: "border-red-200 bg-red-50 text-red-900",
  info: "border-sky-200 bg-sky-50 text-sky-900",
};

type AlertProps = {
  tone?: Tone;
  children: ReactNode;
  requestId?: string;
};

export function Alert({ tone = "info", children, requestId }: AlertProps) {
  return (
    <div className={`rounded-md border px-3 py-2 text-sm ${toneClass[tone]}`}>
      <div>{children}</div>
      {requestId ? (
        <div className="mt-1 text-xs opacity-80">Request ID: {requestId}</div>
      ) : null}
    </div>
  );
}
