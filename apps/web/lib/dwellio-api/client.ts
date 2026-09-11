export class DwellioApiError extends Error {
  constructor(
    message: string,
    public status: number,
    public code?: string,
    public requestId?: string,
  ) {
    super(message);
    this.name = "DwellioApiError";
  }
}

type ApiFetchOptions = {
  method?: string;
  body?: unknown;
  token: string;
};

export async function apiFetch<T>(
  path: string,
  options: ApiFetchOptions,
): Promise<T> {
  const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL;
  if (!baseUrl) {
    throw new Error("NEXT_PUBLIC_API_BASE_URL is not configured");
  }

  const response = await fetch(`${baseUrl}${path}`, {
    method: options.method ?? "GET",
    headers: {
      Authorization: `Bearer ${options.token}`,
      Accept: "application/json",
      ...(options.body !== undefined
        ? { "Content-Type": "application/json" }
        : {}),
    },
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    cache: "no-store",
  });

  if (!response.ok) {
    let code: string | undefined;
    let message = `Request failed (${response.status})`;
    let requestId: string | undefined;
    try {
      const json = (await response.json()) as {
        error?: { code?: string; message?: string; requestId?: string };
      };
      code = json.error?.code;
      message = json.error?.message ?? message;
      requestId = json.error?.requestId;
    } catch {
      /* ignore non-JSON error bodies */
    }
    throw new DwellioApiError(message, response.status, code, requestId);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}
