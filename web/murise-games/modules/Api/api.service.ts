import { env } from "@/config/env";
import { getSupabaseClient } from "@/config/supabase";
import type { CustomApiResponse, RequestOptions } from "./api.type";

function resolveUrl(url: string): string {
  if (/^https?:\/\//i.test(url)) {
    return url;
  }
  const normalizedPath = url.startsWith("/") ? url : `/${url}`;
  return `${env.apiBaseUrl}${normalizedPath}`;
}

function generateRequestId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `req-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`;
}

async function resolveAuthHeader(
  existingHeaders: Record<string, string>,
  skipAuth?: boolean
): Promise<Record<string, string>> {
  if (skipAuth) {
    return {};
  }

  const hasAuthHeader = Object.keys(existingHeaders).some(
    (key) => key.toLowerCase() === "authorization"
  );
  if (hasAuthHeader) {
    return {};
  }

  const supabase = getSupabaseClient();
  const { data, error } = await supabase.auth.getSession();

  if (error || !data.session?.access_token) {
    const unauthorizedError: CustomApiResponse<null> = {
      status: "FAIL",
      message: "Authentication is required to access this resource.",
      code: 401,
      data: null,
      errors: [],
      requestId: null,
    };
    throw unauthorizedError;
  }

  return { Authorization: `Bearer ${data.session.access_token}` };
}

async function apiRequest<T>({
  method = "GET",
  url,
  data,
  headers = {},
  skipAuth = false,
}: RequestOptions): Promise<CustomApiResponse<T>> {
  const fullUrl = resolveUrl(url);
  const authHeader = await resolveAuthHeader(headers, skipAuth);
  const requestId = generateRequestId();

  const requestHeaders: Record<string, string> = {
    Accept: "application/json",
    "X-Request-ID": requestId,
    ...authHeader,
    ...headers,
  };

  const hasBody = method !== "GET" && data !== undefined;
  if (hasBody) {
    requestHeaders["Content-Type"] = "application/json";
  }

  const config: RequestInit = {
    method,
    headers: requestHeaders,
    credentials: "omit",
    body: hasBody ? JSON.stringify(data) : undefined,
  };

  try {
    const response = await fetch(fullUrl, config);

    if (!response.ok) {
      const errorData = (await response
        .json()
        .catch(() => null)) as CustomApiResponse<null> | null;

      if (errorData && errorData.status) {
        throw errorData;
      }

      const fallbackError: CustomApiResponse<null> = {
        status: "ERROR",
        message: errorData?.message || "A network or server error occurred.",
        code: response.status,
        data: null,
        errors: [],
        requestId: response.headers.get("X-Request-ID") ?? requestId,
      };
      throw fallbackError;
    }

    if (
      response.status === 204 ||
      response.headers.get("content-length") === "0"
    ) {
      return {
        status: "SUCCESS",
        message: "No content",
        code: 204,
        data: null,
        errors: [],
        requestId: response.headers.get("X-Request-ID") ?? requestId,
      };
    }

    const responseData = (await response.json()) as CustomApiResponse<T>;
    if (responseData.status !== "SUCCESS") {
      throw responseData;
    }

    return responseData;
  } catch (error: unknown) {
    console.error(`Error in API request to ${fullUrl}:`, error);
    throw error;
  }
}

export function isApiResponseError(
  error: unknown
): error is CustomApiResponse<null> {
  return (
    typeof error === "object" &&
    error !== null &&
    "status" in error &&
    "message" in error &&
    "code" in error
  );
}

export function extractApiErrorMessage(
  error: unknown,
  fallback = "An unexpected error occurred."
): string {
  if (isApiResponseError(error)) {
    const firstFieldError = error.errors?.[0];
    const baseMessage = firstFieldError
      ? `${firstFieldError.field}: ${firstFieldError.message}`
      : error.message;
    return error.requestId
      ? `${baseMessage} (req: ${error.requestId})`
      : baseMessage;
  }

  if (error instanceof Error && error.message) {
    return error.message;
  }

  return fallback;
}

export const ApiService = {
  get: <T>(url: string, headers?: Record<string, string>) =>
    apiRequest<T>({ method: "GET", url, headers }),

  post: <T>(url: string, data?: unknown, headers?: Record<string, string>) =>
    apiRequest<T>({ method: "POST", url, data, headers }),

  put: <T>(url: string, data?: unknown, headers?: Record<string, string>) =>
    apiRequest<T>({ method: "PUT", url, data, headers }),

  delete: <T>(url: string, data?: unknown, headers?: Record<string, string>) =>
    apiRequest<T>({ method: "DELETE", url, data, headers }),

  patch: <T>(url: string, data?: unknown, headers?: Record<string, string>) =>
    apiRequest<T>({ method: "PATCH", url, data, headers }),
};