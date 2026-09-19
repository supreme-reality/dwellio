import type { ReactNode } from "react";

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
      <header className="border-b border-neutral-200 bg-white">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-4 px-4 py-3">
          <div className="flex flex-wrap items-center gap-4">
            <a href={brandHref} className="text-xl font-semibold tracking-tight">
              Dwellio
            </a>
            {orgSwitcher}
            {propertySwitcher}
          </div>
          <div className="flex items-center gap-3 text-sm">
            <span className="text-neutral-600">{userLabel}</span>
            <a
              href="/auth/logout"
              className="rounded-md border border-neutral-300 px-3 py-1.5 hover:bg-neutral-50"
            >
              Log out
            </a>
          </div>
        </div>
      </header>
      <div className="mx-auto flex max-w-7xl gap-6 px-4 py-6">
        <aside className="w-52 shrink-0">{sidebar}</aside>
        <main className="min-w-0 flex-1">{children}</main>
      </div>
    </div>
  );
}
