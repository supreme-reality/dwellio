import type { ReactNode } from "react";

const STEPS = [
  "Tenant",
  "Bed",
  "Details",
  "Services",
  "Upfront",
  "Confirm & Pay",
] as const;

type StepperProps = {
  current: number;
  children?: ReactNode;
};

export function MoveInStepper({ current, children }: StepperProps) {
  return (
    <div className="space-y-4">
      <ol className="flex flex-wrap gap-2 text-xs">
        {STEPS.map((label, index) => {
          const active = index === current;
          const done = index < current;
          return (
            <li
              key={label}
              className={`rounded-md px-2 py-1 ${
                active
                  ? "bg-neutral-900 text-white"
                  : done
                    ? "bg-neutral-200 text-neutral-800"
                    : "bg-neutral-100 text-neutral-500"
              }`}
            >
              {index + 1}. {label}
            </li>
          );
        })}
      </ol>
      {children}
    </div>
  );
}
