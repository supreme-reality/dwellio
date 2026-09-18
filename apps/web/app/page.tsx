import { auth0 } from "@/lib/auth0";
import { fetchMe } from "@/lib/dwellio-api";

export const dynamic = "force-dynamic";

export default async function Home() {
  const session = await auth0.getSession();

  if (!session) {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center gap-6 p-8">
        <h1 className="text-4xl font-semibold tracking-tight">Dwellio</h1>
        <p className="max-w-md text-center text-neutral-600">
          PG and co-living management for organizations.
        </p>
        <div className="flex gap-4">
          <a
            href="/auth/login?screen_hint=signup"
            className="rounded-md border border-neutral-300 px-4 py-2 text-sm font-medium hover:bg-neutral-50"
          >
            Sign up
          </a>
          <a
            href="/auth/login"
            className="rounded-md bg-neutral-900 px-4 py-2 text-sm font-medium text-white hover:bg-neutral-800"
          >
            Log in
          </a>
        </div>
      </main>
    );
  }

  let meError: string | null = null;
  let me: Awaited<ReturnType<typeof fetchMe>> | null = null;

  try {
    const { token } = await auth0.getAccessToken();
    me = await fetchMe(token);
  } catch (error) {
    meError = error instanceof Error ? error.message : "Failed to load profile";
  }

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-2xl flex-col gap-6 p-8">
      <header className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-semibold tracking-tight">Dwellio</h1>
          <p className="mt-1 text-sm text-neutral-600">
            Signed in as {session.user.email ?? session.user.name ?? "user"}
          </p>
        </div>
        <a
          href="/auth/logout"
          className="rounded-md border border-neutral-300 px-3 py-1.5 text-sm hover:bg-neutral-50"
        >
          Log out
        </a>
      </header>

      <section className="rounded-lg border border-neutral-200 p-4">
        <h2 className="text-sm font-medium text-neutral-500">Auth0 session</h2>
        <pre className="mt-2 overflow-x-auto text-xs text-neutral-800">
          {JSON.stringify(session.user, null, 2)}
        </pre>
      </section>

      <section className="rounded-lg border border-neutral-200 p-4">
        <h2 className="text-sm font-medium text-neutral-500">Dwellio /api/v1/me</h2>
        {meError ? (
          <p className="mt-2 text-sm text-red-700">{meError}</p>
        ) : (
          <pre className="mt-2 overflow-x-auto text-xs text-neutral-800">
            {JSON.stringify(me, null, 2)}
          </pre>
        )}
      </section>
    </main>
  );
}
