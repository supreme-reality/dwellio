import type { TextareaHTMLAttributes } from "react";

type TextareaProps = TextareaHTMLAttributes<HTMLTextAreaElement> & {
  label: string;
  error?: string;
};

export function Textarea({
  label,
  error,
  id,
  className = "",
  ...props
}: TextareaProps) {
  const textareaId =
    id ?? props.name ?? label.replace(/\s+/g, "-").toLowerCase();

  return (
    <label className="flex flex-col gap-1 text-sm" htmlFor={textareaId}>
      <span className="font-medium text-neutral-800">{label}</span>
      <textarea
        id={textareaId}
        className={`min-h-24 rounded-md border border-neutral-300 px-3 py-2 text-neutral-900 outline-none focus:border-neutral-500 ${className}`}
        {...props}
      />
      {error ? <span className="text-xs text-red-700">{error}</span> : null}
    </label>
  );
}
