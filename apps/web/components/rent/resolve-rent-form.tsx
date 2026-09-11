"use client";

import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { formatMoney } from "@/lib/format";

type BedOption = { id: string; name: string };

type ResolveRentFormProps = {
  orgId: string;
  propertyId: string;
  beds: BedOption[];
  currency: string;
  defaultDate: string;
};

type ResolveResult = {
  amount: number | string;
  level: string;
  effectiveFrom: string;
  effectiveTo: string | null;
  currency: string;
};

export function ResolveRentForm({
  propertyId,
  beds,
  currency,
  defaultDate,
}: ResolveRentFormProps) {
  const [bedId, setBedId] = useState(beds[0]?.id ?? "");
  const [date, setDate] = useState(defaultDate);
  const [result, setResult] = useState<ResolveResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onResolve() {
    setLoading(true);
    setError(null);
    setResult(null);
    try {
      const response = await fetch(
        `/api/rent/resolve?propertyId=${encodeURIComponent(propertyId)}&bedId=${encodeURIComponent(bedId)}&date=${encodeURIComponent(date)}`,
      );
      const json = await response.json();
      if (!response.ok) {
        throw new Error(json.message ?? "Resolve failed");
      }
      setResult(json as ResolveResult);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Resolve failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="rounded-lg border border-neutral-200 bg-white p-4">
      <h2 className="mb-3 text-lg font-semibold">Resolve rent</h2>
      <div className="grid gap-3 sm:grid-cols-3">
        <Select
          label="Bed"
          name="bedId"
          value={bedId}
          onChange={(event) => setBedId(event.target.value)}
        >
          {beds.map((bed) => (
            <option key={bed.id} value={bed.id}>
              {bed.name}
            </option>
          ))}
        </Select>
        <Input
          label="Date"
          name="date"
          type="date"
          value={date}
          onChange={(event) => setDate(event.target.value)}
        />
        <div className="flex items-end">
          <Button type="button" onClick={onResolve} disabled={loading || !bedId}>
            {loading ? "Resolving…" : "Resolve"}
          </Button>
        </div>
      </div>
      {error ? <p className="mt-2 text-sm text-red-700">{error}</p> : null}
      {result ? (
        <p className="mt-3 text-sm text-neutral-800">
          {formatMoney(result.amount, result.currency || currency)} at{" "}
          {result.level} level (effective {result.effectiveFrom}
          {result.effectiveTo ? ` → ${result.effectiveTo}` : ""})
        </p>
      ) : null}
    </section>
  );
}
