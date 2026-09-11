import type { ReactNode } from "react";

type PreviewPanelProps = {
  title: string;
  children: ReactNode;
};

export function PreviewPanel({ title, children }: PreviewPanelProps) {
  return (
    <section className="rounded-lg border-2 border-dashed border-amber-300 bg-amber-50 p-4">
      <h2 className="mb-2 text-sm font-semibold uppercase tracking-wide text-amber-900">
        Preview — not yet committed · {title}
      </h2>
      <div className="space-y-1 text-sm text-neutral-900">{children}</div>
    </section>
  );
}
