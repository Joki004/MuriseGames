# Supabase setup for MuriseGames

## Environments and cost

Recommended for V1: two projects in the `Murise-Games` organization, `murise-dev` and `murise-prod`.
The Free plan currently allows two active free projects across organizations where you are an
owner or administrator; a new organization does not reset that limit. Paused projects do not count.
The organization screenshot's project count does not show which projects are active.

Check the existing `MJ org` projects before creating two more. Keep useful existing projects running;
do not pause or delete them just to follow this guide. If only one slot is available, use hosted
`murise-prod` and local Supabase for development. If no slots are free, develop locally first.
No staging project is required for this event. Pricing and plan terms can change.

Free currently includes a 500 MB database and 1 GB file storage; inactive projects can pause after
one week. Check that the production project is running before rehearsal and the event. Hosting the
Spring and Next.js services and sending email are separate services with their own limits/costs.

## 1. Create the development project

1. Open **Murise-Games** in the Supabase dashboard, confirm it is on **Free**, and choose **New project**.
2. Name it `murise-dev`. Choose a nearby European region appropriate for your attendees/backend.
3. Generate a strong database administrator password and save it in your password manager.
4. Wait until the database is ready. Repeat for `murise-prod` later if a free slot is available.
5. Keep test users/data in dev. Production uses separate keys, passwords and real event data.

## 2. Prepare the database roles

In the development project's SQL Editor, run the contents of `api/db/bootstrap.sql` once as
the default database administrator (`postgres`). It creates:

| Role / schema | Purpose |
| --- | --- |
| `murise_migrator` | Owns `murise`; Flyway creates and alters application tables |
| `murise_app` | API runtime; only explicitly granted application-table access |
| `murise` | Private application schema, not exposed through the Supabase Data API |

The script intentionally fails if the roles/schema exist. Inspect existing objects instead of
blindly dropping them. It assumes the database is named `postgres` and the administrator role is `postgres`.

Set different strong passwords for both new roles. Prefer `psql`'s interactive password prompts
over committing SQL containing passwords:

```text
\password murise_app
\password murise_migrator
```

Connect `psql` as the database administrator using the connection details from **Connect**.
If using the SQL Editor to set passwords instead, enter your own `ALTER ROLE ... PASSWORD ...`
statements there and keep that credential-bearing text out of the repository.

Do not add `murise` to the exposed Data API schemas. Next.js calls Spring for application data;
it uses the Supabase browser client only for authentication. There are deliberately no grants
to `anon`, `authenticated` or `service_role` on the application tables.
If direct browser database access is introduced later, design RLS and grants explicitly first.

## 3. Configure JDBC connections

Open **Connect** and choose the session pooler connection on **port 5432** for environments needing IPv4.
Use the exact pooler hostname shown for your project. Do not use the transaction pooler on 6543 for
this persistent Spring connection/Flyway setup.

| Variable | Value |
| --- | --- |
| `DB_APP_URL` | JDBC URL with database `postgres`, TLS and certificate verification |
| `DB_APP_USERNAME` | `murise_app.PROJECT_REF` through shared session pooler |
| `DB_APP_PASSWORD` | The app role password you just set |
| `DB_MIGRATION_URL` | Usually the same host/database URL, with separate credentials below |
| `DB_MIGRATOR_USERNAME` | `murise_migrator.PROJECT_REF` through shared session pooler |
| `DB_MIGRATOR_PASSWORD` | The migrator role password |

For a direct connection (where IPv6 is available), use the direct host from Connect and the bare role
names, without `.PROJECT_REF`. Do not put a password inside a JDBC URL. Download the project CA certificate
from database settings and configure `sslmode=verify-full&sslrootcert=/absolute/path/to/certificate`.
Mount the certificate into the deployed backend at the path you configure.
On Windows use forward slashes in certificate paths, such as `C:/Users/you/certs/supabase.cer`.

The `local` profile imports `api/.env` as **Java properties**, not shell syntax. Do not surround values
with quotes or write `export`. Escape literal backslashes with a second backslash. IDE environment
variables are an alternative. Always launch from `api/`. Hosted profiles use host environment settings.

## 4. Configure authentication

1. In Authentication, enable email/password sign-in and use a password minimum of at least 8 characters.
2. Use an asymmetric JWT signing key (ES256 or RS256). The backend validates the issuer, expiry,
   signature and `authenticated` audience using the project's JWKS endpoint. A legacy HS256-only
   project needs signing-key migration before this scaffold can accept its tokens.
3. Set development Site URL to `http://localhost:3000` and allow redirect URL
   `http://localhost:3000/account`. Production uses your exact HTTPS frontend URL and `/account` URL.
4. Configure custom SMTP before inviting guests with email confirmation. Supabase's default email
   service only delivers to project team members and has a very low sending limit. Verify with a test
   guest account before rehearsal. Do not disable production confirmation simply to work around SMTP.
5. Copy the project URL and **publishable key** to `web/murise-games/.env.local`.
6. Set backend `SUPABASE_URL` to the same project URL. No publishable key or service-role key is needed
   by Spring for JWT verification. `CORS_ORIGINS` is a comma-separated list of exact frontend origins.

The current frontend intentionally uses browser authentication, not server-rendered account pages.
It passes the session access token to Spring. Client-side session state is never an authorization decision.
Password recovery, event membership authorization and global administration remain separate V1 tasks.

## 5. Run and smoke-test

Start the API with the local profile. Flyway creates and seeds `murise.games` using the migration role;
Hibernate checks the schema without modifying it. Start Next.js and visit `/account`.

- Create a test account, follow email confirmation, sign in, then choose **View games**.
- Confirm nine entries are shown; their rules are explicitly pending review.
- Sign out. A request to `/api/games` without a token must receive HTTP 401.
- Change to production only after repeating setup with production credentials and deployment URLs.

## Official references (checked 19 November 2026)

- https://supabase.com/docs/guides/platform/billing-on-supabase
- https://supabase.com/pricing
- https://supabase.com/docs/guides/database/connecting-to-postgres
- https://supabase.com/docs/guides/platform/ssl-enforcement
- https://supabase.com/docs/guides/auth/signing-keys
- https://supabase.com/docs/guides/auth/auth-smtp
- https://supabase.com/docs/guides/local-development
