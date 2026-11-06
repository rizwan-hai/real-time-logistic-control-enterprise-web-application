export interface ProblemDetails {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  code?: string;
  fieldErrors?: Array<{ field: string; message: string }>;
}

export class ApiClientError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: ProblemDetails['fieldErrors'];

  constructor(problem: ProblemDetails, status: number) {
    super(problem.detail ?? problem.title ?? 'The request could not be completed.');
    this.name = 'ApiClientError';
    this.status = status;
    this.code = problem.code ?? 'REQUEST_FAILED';
    this.fieldErrors = problem.fieldErrors;
  }
}

export function mapApiError(error: unknown): ApiClientError {
  const candidate = error as { response?: { status?: number; data?: ProblemDetails } };
  const status = candidate.response?.status ?? 0;
  return new ApiClientError(candidate.response?.data ?? {}, status);
}
