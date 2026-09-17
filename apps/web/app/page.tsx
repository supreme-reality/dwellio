import { auth0 } from "@/lib/auth0";
import { resolveAppHomePath } from "@/lib/app-home";
import { DwellioApiError, fetchMe } from "@/lib/dwellio-api";
import { redirect } from "next/navigation";

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

  try {
    const { token } = await auth0.getAccessToken();
    // Warm /me upsert so org APIs see the user
    await fetchMe(token);
    redirect(await resolveAppHomePath(token));
  } catch (error) {
    // redirect() throws; rethrow Next.js redirects
    if (
      typeof error === "object" &&
      error !== null &&
      "digest" in error &&
      String((error as { digest?: string }).digest).startsWith("NEXT_REDIRECT")
    ) {
      throw error;
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
        <p className="text-sm text-red-700">
          {error instanceof DwellioApiError
            ? `${error.message} (HTTP ${error.status}${error.code ? `, ${error.code}` : ""})`
            : error instanceof Error
              ? error.message
              : "Failed to load application home"}
        </p>
        <a href="/onboarding" className="text-sm underline">
          Go to onboarding
        </a>
      </main>
    );
  }
}
