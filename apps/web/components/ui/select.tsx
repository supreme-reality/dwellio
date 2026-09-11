import type { SelectHTMLAttributes, ReactNode } from "react";

type SelectProps = SelectHTMLAttributes<HTMLSelectElement> & {
  label: string;
  error?: string;
  children: ReactNode;
};

export function Select({
  label,
  error,
  id,
  className = "",
  children,
  ...props
}: SelectProps) {
  const selectId = id ?? props.name ?? label.replace(/\s+/g, "-").toLowerCase();

  return (
    <label className="flex flex-col gap-1 text-sm" htmlFor={selectId}>
      <span className="font-medium text-neutral-800">{label}</span>
      <select
        id={selectId}
        className={`rounded-md border border-neutral-300 bg-white px-3 py-2 text-neutral-900 outline-none focus:border-neutral-500 ${className}`}
        {...props}
      >
        {children}
      </select>
      {error ? <span className="text-xs text-red-700">{error}</span> : null}
    </label>
  );
}
