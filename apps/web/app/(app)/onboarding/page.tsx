import { createOrganizationAction } from "@/app/(app)/actions";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

export default function OnboardingPage() {
  return (
    <main className="mx-auto flex min-h-screen max-w-lg flex-col justify-center gap-6 p-8">
      <div>
        <h1 className="text-3xl font-semibold tracking-tight">Dwellio</h1>
        <p className="mt-2 text-sm text-neutral-600">
          Create your organization to get started.
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
  );
}
