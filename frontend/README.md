# Frontend

React 18 and strict TypeScript application. The dev server and production build generate API contracts from the backend's OpenAPI document before starting/compiling.

1. Start the backend so `http://localhost:8080/v3/api-docs` is available.
2. Copy `.env.example` to `.env.local` if the API base URL, version, or schema URL differs from the defaults.
3. Run `npm install`, then `npm run dev`.

`npm run build`, `npm run lint`, and `npm test` run the production compiler, lint checks, and Vitest suite. `npm run test:e2e` runs the Playwright browser checks; the Playwright web server starts Vite directly and does not require a live backend for the mocked authentication flow.
