type ExportsPageProps = {
  params: Promise<{ orgId: string; propertyId: string }>;
};

export default async function ExportsPage({ params }: ExportsPageProps) {
  const { propertyId } = await params;
  const types = ["tenants", "expenses", "invoices"] as const;

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-semibold tracking-tight">Exports</h1>
      <p className="text-sm text-neutral-600">Download property-scoped CSV files.</p>
      <ul className="space-y-2">
        {types.map((type) => (
          <li key={type}>
            <a
              href={`/api/exports/${propertyId}/${type}`}
              className="inline-flex rounded-md border border-neutral-300 bg-white px-3 py-1.5 text-sm hover:bg-neutral-50"
            >
              Download {type}.csv
            </a>
          </li>
        ))}
      </ul>
    </div>
  );
}
