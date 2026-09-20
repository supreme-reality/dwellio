import type { ReactNode } from "react";

type AuthHeaderProps = {
  brandHref?: string;
  userLabel: string;
  children?: ReactNode;
};

export function AuthHeader({
  brandHref = "/",
  userLabel,
  children,
}: AuthHeaderProps) {
  return (
    <header className="border-b border-neutral-200 bg-white">
      <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-4 px-4 py-3">
        <div className="flex flex-wrap items-center gap-4">
          <a href={brandHref} className="text-xl font-semibold tracking-tight">
            Dwellio
          </a>
          {children}
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
  );
}
