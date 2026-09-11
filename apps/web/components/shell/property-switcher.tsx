"use client";

import { useRouter } from "next/navigation";

import type { Property } from "@/lib/dwellio-api/types";

type PropertySwitcherProps = {
  organizationId: string;
  properties: Property[];
  currentPropertyId: string;
};

export function PropertySwitcher({
  organizationId,
  properties,
  currentPropertyId,
}: PropertySwitcherProps) {
  const router = useRouter();

  if (properties.length === 0) {
    return (
      <span className="text-sm text-neutral-500">No properties assigned</span>
    );
  }

  return (
    <label className="flex items-center gap-2 text-sm">
      <span className="text-neutral-500">Property</span>
      <select
        className="rounded-md border border-neutral-300 bg-white px-2 py-1"
        value={currentPropertyId}
        onChange={(event) => {
          router.push(
            `/o/${organizationId}/p/${event.target.value}/inventory`,
          );
        }}
      >
        {properties.map((property) => (
          <option key={property.id} value={property.id}>
            {property.name}
          </option>
        ))}
      </select>
    </label>
  );
}
