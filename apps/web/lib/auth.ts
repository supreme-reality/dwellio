import { redirect } from "next/navigation";

import { auth0 } from "@/lib/auth0";

export async function requireSession() {
  const session = await auth0.getSession();
  if (!session) {
    redirect("/auth/login");
  }
  return session;
}

export async function requireAccessToken(): Promise<string> {
  await requireSession();
  const { token } = await auth0.getAccessToken();
  if (!token) {
    redirect("/auth/login");
  }
  return token;
}
