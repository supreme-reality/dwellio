"use client";

import { useRouter } from "next/navigation";

import type { Organization } from "@/lib/dwellio-api/types";

type OrgSwitcherProps = {
  organizations: Organization[];
  currentOrgId: string;
};

export function OrgSwitcher({
  organizations,
  currentOrgId,
}: OrgSwitcherProps) {
  const router = useRouter();

  return (
    <label className="flex items-center gap-2 text-sm">
      <span className="text-neutral-500">Org</span>
      <select
        className="rounded-md border border-neutral-300 bg-white px-2 py-1"
        value={currentOrgId}
        onChange={(event) => {
          router.push(`/o/${event.target.value}`);
        }}
      >
        {organizations.map((org) => (
          <option key={org.id} value={org.id}>
            {org.name}
          </option>
        ))}
      </select>
    </label>
  );
}
