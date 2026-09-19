import type { ReactNode } from "react";

import { AuthHeader } from "@/components/shell/auth-header";

type AppShellProps = {
  brandHref: string;
  orgSwitcher: ReactNode;
  propertySwitcher: ReactNode;
  sidebar: ReactNode;
  userLabel: string;
  children: ReactNode;
};

export function AppShell({
  brandHref,
  orgSwitcher,
  propertySwitcher,
  sidebar,
  userLabel,
  children,
}: AppShellProps) {
  return (
    <div className="min-h-screen bg-neutral-50 text-neutral-900">
      <AuthHeader brandHref={brandHref} userLabel={userLabel}>
        {orgSwitcher}
        {propertySwitcher}
      </AuthHeader>
      <div className="mx-auto flex max-w-7xl gap-6 px-4 py-6">
        <aside className="w-52 shrink-0">{sidebar}</aside>
        <main className="min-w-0 flex-1">{children}</main>
      </div>
    </div>
  );
}
