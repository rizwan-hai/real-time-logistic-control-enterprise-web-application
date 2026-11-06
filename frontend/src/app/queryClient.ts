import { QueryClient } from '@tanstack/react-query';
import { ApiClientError } from '@/services/apiErrors';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: (count, error) => !(error instanceof ApiClientError && error.status === 401) && count < 2,
      refetchOnWindowFocus: false,
    },
    mutations: { retry: false },
  },
});
