type MeResponse = {
  id: string;
  email: string;
  name: string;
  status: string;
};

export async function fetchMe(accessToken: string): Promise<MeResponse> {
  const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL;
  if (!baseUrl) {
    throw new Error("NEXT_PUBLIC_API_BASE_URL is not configured");
  }

  const response = await fetch(`${baseUrl}/api/v1/me`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      Accept: "application/json",
    },
    cache: "no-store",
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(`GET /api/v1/me failed (${response.status}): ${body}`);
  }

  return (await response.json()) as MeResponse;
}
