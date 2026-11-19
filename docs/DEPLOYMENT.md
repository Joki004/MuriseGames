# Deployment

Use one Git repository and deploy `api` and `web/murise-games` independently.
Hosting costs and sleep/idle limits are separate from Supabase's plan.

## Backend

- Docker build context: `api/`; Dockerfile: `api/Dockerfile`.
- Set `SPRING_PROFILES_ACTIVE=prod` and all backend variables listed in `api/.env.example`.
- Both JDBC connections must target production; give them their respective app/migrator credentials.
- Mount the Supabase CA certificate and point both JDBC URLs to it.
- Set exact HTTPS frontend origins in `CORS_ORIGINS`; no wildcard.
- Health probe: `/actuator/health`. This includes database health; details are hidden.
- Start with one API instance and a small Hikari pool. Run migrations before adding multiple replicas.
- For V1, Flyway runs on startup with separate credentials. The backend process therefore holds both
  passwords. A later release job can own migrations and remove the migration credentials from the runtime.

## Frontend

- Configure the host's root directory as `web/murise-games`.
- Build: `npm ci` then `npm run build`; Node.js 22.
- Set the three `NEXT_PUBLIC_*` variables from `.env.example` **before building**.
- Public values are embedded at build time; rebuild when changing the Supabase project or API URL.
- Never provide a database password, Supabase secret key or `service_role` key to the frontend.
- Configure the deployed frontend URL and `/account` redirect URL in the production Auth settings.

## Before rehearsal

Verify CI, migrations on a fresh database, signup confirmation via your SMTP provider, login/logout,
catalogue access, unauthorized HTTP 401 responses, and mobile use. Complete the V1 feature tasks before
using this as the event app. The scaffold itself is not event-ready.
Keep dev and prod keys separate. Test data and test accounts must not be copied to production.
