# MuriseGames Web

Next.js frontend using React and TypeScript.

## Prerequisites

- Node.js 22 and npm.

## Run locally

From `web/murise-games/`:

```powershell
npm ci
npm run dev
```

Open http://localhost:3000.

## Environment configuration

When implementing authentication and backend calls, create
`.env.local` beside `package.json`:

```dotenv
NEXT_PUBLIC_SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=YOUR_PUBLISHABLE_KEY
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

The starter does not yet consume these variables.

`NEXT_PUBLIC_*` values are visible in the browser. Never put database
passwords, Supabase secret keys or service-role keys in them.

Use the same development Supabase project as the backend.
Restart Next.js after changing environment values.

## Checks

```powershell
npm run lint
npm run build
```

Run a completed production build locally:

```powershell
npm run start
```

## Integration

The frontend will send the signed-in user's access token to Spring:

`Authorization: Bearer <access_token>`

Spring validates the token and enforces application permissions.

Browser-to-backend integration and production deployment are pending.