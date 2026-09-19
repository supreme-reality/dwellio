import { createOrganizationAction } from "@/app/(app)/actions";
import { AuthHeader } from "@/components/shell/auth-header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { requireSession } from "@/lib/auth";

export default async function OnboardingPage() {
  const session = await requireSession();
  const userLabel = session.user.email ?? session.user.name ?? "Signed in";

  return (
    <div className="flex min-h-screen flex-col bg-neutral-50">
      <AuthHeader brandHref="/onboarding" userLabel={userLabel} />
      <main className="mx-auto flex w-full max-w-lg flex-1 flex-col justify-center gap-6 p-8">
        <div>
          <h1 className="text-3xl font-semibold tracking-tight">
            Create your organization
          </h1>
          <p className="mt-2 text-sm text-neutral-600">
            Choose a name to get started.
          </p>
        </div>
        <form action={createOrganizationAction} className="space-y-4">
          <Input
            label="Organization name"
            name="name"
            required
            placeholder="Acme PG"
          />
          <Button type="submit">Create organization</Button>
        </form>
      </main>
    </div>
  );
}
