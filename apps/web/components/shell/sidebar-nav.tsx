import Link from "next/link";

type NavItem = { href: string; label: string };
type NavGroup = { title: string; items: NavItem[] };

type SidebarNavProps = {
  orgId: string;
  propertyId: string;
  isOwner: boolean;
  pathname?: string;
};

export function SidebarNav({
  orgId,
  propertyId,
  isOwner,
}: SidebarNavProps) {
  const base = `/o/${orgId}/p/${propertyId}`;

  const groups: NavGroup[] = [
    {
      title: "Stay",
      items: [
        { href: `${base}/inventory`, label: "Inventory" },
        { href: `${base}/tenants`, label: "Tenants" },
        { href: `${base}/move-ins/new`, label: "Move-in" },
      ],
    },
    {
      title: "Money",
      items: [
        { href: `${base}/rent`, label: "Rent" },
        { href: `${base}/services`, label: "Services" },
        { href: `${base}/finance`, label: "Finance" },
        { href: `${base}/expenses`, label: "Expenses" },
      ],
    },
    {
      title: "Ops",
      items: [
        { href: `${base}/tickets`, label: "Tickets" },
        { href: `${base}/notices`, label: "Notices" },
        { href: `${base}/documents`, label: "Documents" },
      ],
    },
    {
      title: "Insights",
      items: [
        { href: `${base}/analytics`, label: "Analytics" },
        { href: `${base}/exports`, label: "Exports" },
      ],
    },
  ];

  if (isOwner) {
    groups.push({
      title: "Admin",
      items: [
        { href: `${base}/settings/members`, label: "Members" },
        { href: `${base}/settings/managers`, label: "Managers" },
        { href: `${base}/settings/property`, label: "Property settings" },
      ],
    });
  }

  return (
    <nav className="space-y-4 text-sm">
      {groups.map((group) => (
        <div key={group.title}>
          <div className="mb-1 text-xs font-semibold uppercase tracking-wide text-neutral-500">
            {group.title}
          </div>
          <ul className="space-y-1">
            {group.items.map((item) => (
              <li key={item.href}>
                <Link
                  href={item.href}
                  className="block rounded-md px-2 py-1.5 text-neutral-800 hover:bg-neutral-100"
                >
                  {item.label}
                </Link>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </nav>
  );
}
