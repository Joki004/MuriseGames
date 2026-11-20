export type ApiResponseStatus = "SUCCESS" | "FAIL" | "ERROR";

export type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

export interface ApiValidationError {
  field: string;
  message: string;
}

export interface CustomApiResponse<T> {
  status: ApiResponseStatus;
  message: string;
  code: number;
  data: T | null;
  errors?: ApiValidationError[];
  requestId?: string | null;
}

export interface RequestOptions {
  method?: HttpMethod;
  url: string;
  data?: unknown;
  headers?: Record<string, string>;
  skipAuth?: boolean;
}