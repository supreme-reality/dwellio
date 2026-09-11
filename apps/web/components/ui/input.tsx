import type { InputHTMLAttributes } from "react";

type InputProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  error?: string;
};

export function Input({ label, error, id, className = "", ...props }: InputProps) {
  const inputId = id ?? props.name ?? label.replace(/\s+/g, "-").toLowerCase();

  return (
    <label className="flex flex-col gap-1 text-sm" htmlFor={inputId}>
      <span className="font-medium text-neutral-800">{label}</span>
      <input
        id={inputId}
        className={`rounded-md border border-neutral-300 px-3 py-2 text-neutral-900 outline-none focus:border-neutral-500 ${className}`}
        {...props}
      />
      {error ? <span className="text-xs text-red-700">{error}</span> : null}
    </label>
  );
}
