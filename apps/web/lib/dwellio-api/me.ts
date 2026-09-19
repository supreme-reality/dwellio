import { apiFetch } from "./client";
import type { Me } from "./types";

export function fetchMe(token: string): Promise<Me> {
  return apiFetch<Me>("/api/v1/me", { token });
}
