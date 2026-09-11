type FeaturePlaceholderProps = {
  title: string;
  description?: string;
};

export function FeaturePlaceholder({
  title,
  description = "This screen will be implemented in a later Phase 7 task.",
}: FeaturePlaceholderProps) {
  return (
    <div className="space-y-2">
      <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
      <p className="text-sm text-neutral-600">{description}</p>
    </div>
  );
}
