/**
 * Server-side backend origin used by SSR data fetches and Next route handlers.
 * Browser requests intentionally use same-origin `/api/...` routes instead.
 */
export const backendBaseUrl =
  process.env.KRAFT_BACKEND_INTERNAL_URL ?? "http://backend:8080";
