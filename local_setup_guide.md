# MuriseGames — Local Setup Guide

This guide walks through setting up the **MuriseGames** backend (Spring Boot + Flyway), frontend (Next.js), and database/auth (Supabase) from scratch on a new machine.

---

## Architecture & Current State

- **Frontend (`web/murise-games/`):** Next.js (App Router), React, TypeScript, Tailwind CSS, Supabase Auth
- **Backend (`api/`):** Java 21, Spring Boot, Spring Data JPA, Flyway (`V1`–`V6`)
- **Database & Auth:** Supabase PostgreSQL + Supabase Auth (JWT validated by Spring Boot)

### Local URLs

| Service | URL |
| --- | --- |
| Frontend | `http://localhost:3000` |
| Backend API | `http://localhost:8080` |
| Health Check | `http://localhost:8080/actuator/health` |
| Swagger UI (`local` profile) | `http://localhost:8080/swagger-ui.html` |

---

## Prerequisites

- **Git**
- **JDK 21** (selected for both your IDE Project SDK and Gradle JVM)
- **Node.js 22** and **npm**
- A **Supabase** account (with a dedicated **development** project — never connect local development to production)

---

## Step 1: Set Up Supabase

### 1.1 Download the Database CA Certificate
1. Open your development project in the **Supabase Dashboard**.
2. Go to **Project Settings → Database** (or click **Connect** at the top).
3. Download the **SSL Certificate** (`prod-ca-2021.crt` or `dev-ca.cer`) and save it to a stable local path, for example:
   - Windows: `C:/certs/dev-ca.cer` (use forward slashes `/` in JDBC URLs)
   - macOS/Linux: `/Users/<you>/certs/dev-ca.cer`
4. Note your **Session Pooler** connection host (port `5432`, e.g., `aws-0-eu-west-1.pooler.supabase.com`) and your **Project Ref** (found in your project URL `https://<YOUR_PROJECT_REF>.supabase.co`).

### 1.2 Configure Supabase Auth URLs
1. In the Supabase Dashboard, go to **Authentication → URL Configuration**.
2. Set **Site URL** to:
   ```text
   http://localhost:3000
   ```
3. Add these **Redirect URLs**:
   ```text
   http://localhost:3000/auth/callback
   http://localhost:3000/reset-password
   ```

### 1.3 Keep `murise` Out of PostgREST Exposed Schemas
1. Go to **Project Settings → Data API** (or **API**).
2. Confirm `murise` is **not** listed under **Exposed schemas** (only `public` / `graphql_public` should be there). All domain access goes through Spring Boot, not direct browser queries.

### 1.4 Run the Database Bootstrap SQL (First-Time Setup)
If you are using a brand-new Supabase project (or if the `murise` schema, roles, or `murise.profiles` table do not exist yet), open the **Supabase SQL Editor** and run the script below as `postgres`.

> **Important:** Replace `'CHANGE_ME_APP_PASSWORD'` and `'CHANGE_ME_MIGRATOR_PASSWORD'` with your own strong dev passwords and reuse them in Step 2.

```sql
BEGIN;

-- 1. Create least-privilege runtime and migration roles (idempotent check)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'murise_app') THEN
        CREATE ROLE murise_app
            LOGIN PASSWORD 'CHANGE_ME_APP_PASSWORD'
            NOSUPERUSER NOCREATEDB NOCREATEROLE
            NOINHERIT NOBYPASSRLS;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'murise_migrator') THEN
        CREATE ROLE murise_migrator
            LOGIN PASSWORD 'CHANGE_ME_MIGRATOR_PASSWORD'
            NOSUPERUSER NOCREATEDB NOCREATEROLE
            NOINHERIT NOBYPASSRLS;
    END IF;
END
$$;

GRANT CONNECT ON DATABASE postgres TO murise_app, murise_migrator;

-- 2. Create isolated application schema
CREATE SCHEMA IF NOT EXISTS murise AUTHORIZATION postgres;
REVOKE ALL ON SCHEMA murise FROM PUBLIC, anon, authenticated;

GRANT USAGE, CREATE ON SCHEMA murise TO murise_migrator;
GRANT USAGE ON SCHEMA murise TO murise_app;

-- 3. Provision murise.profiles (references Supabase-managed auth.users)
--    Required before Flyway V2 runs.
CREATE TABLE IF NOT EXISTS murise.profiles (
    id           uuid PRIMARY KEY REFERENCES auth.users (id),
    display_name text NOT NULL CHECK (btrim(display_name) <> ''),
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now()
);

REVOKE ALL ON TABLE murise.profiles FROM PUBLIC, anon, authenticated;
GRANT ALL ON TABLE murise.profiles TO murise_migrator;
GRANT SELECT, INSERT, UPDATE ON TABLE murise.profiles TO murise_app;

COMMIT;
```

---

## Step 2: Configure & Run the Backend (`api/`)

Spring Boot uses `src/main/resources/application.yaml` plus `application-local.yaml` when the `local` profile is active. It does **not** automatically load a `.env` file, so set these as environment variables in your IDE Run Configuration or terminal.

### 2.1 Backend Environment Variables

```dotenv
SPRING_PROFILES_ACTIVE=local
DB_APP_URL=jdbc:postgresql://YOUR_POOLER_HOST:5432/postgres?sslmode=verify-full&sslrootcert=C:/certs/dev-ca.cer
DB_APP_USERNAME=murise_app.YOUR_PROJECT_REF
DB_APP_PASSWORD=CHANGE_ME_APP_PASSWORD
DB_MIGRATION_URL=jdbc:postgresql://YOUR_POOLER_HOST:5432/postgres?sslmode=verify-full&sslrootcert=C:/certs/dev-ca.cer
DB_MIGRATOR_USERNAME=murise_migrator.YOUR_PROJECT_REF
DB_MIGRATOR_PASSWORD=CHANGE_ME_MIGRATOR_PASSWORD
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
CORS_ORIGINS=http://localhost:3000
PORT=8080
```

> **Note:** When connecting through the Supabase Session Pooler, usernames **must** include the project ref suffix: `murise_app.<YOUR_PROJECT_REF>` and `murise_migrator.<YOUR_PROJECT_REF>`.

### 2.2 Run the Backend
- **IntelliJ IDEA:** Open `api/`, edit the `ApiApplication` Run Configuration, paste the variables above into **Environment variables**, and click **Run**.
- **Terminal (PowerShell from `api/`):** After exporting the variables in your shell:
  ```powershell
  .\gradlew.bat bootRun --args="--spring.profiles.active=local"
  ```
- **Terminal (macOS/Linux from `api/`):**
  ```bash
  ./gradlew bootRun --args="--spring.profiles.active=local"
  ```

On first boot, Flyway automatically applies migrations **`V1` through `V6`** (schema, RLS safety policy, event visibility, invite rate-limit table, and the 9 seeded **FOR THE PLOT** catalog games).

---

## Step 3: Configure & Run the Frontend (`web/murise-games/`)

### 3.1 Create `.env.local`
Create `web/murise-games/.env.local` (next to `package.json` — this file is gitignored):

```dotenv
NEXT_PUBLIC_SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=YOUR_SUPABASE_PUBLISHABLE_OR_ANON_KEY
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

> **Security rule:** `NEXT_PUBLIC_*` values are exposed to the browser. Never put database passwords, `service_role` keys, or secret keys in `.env.local`. Use the **same** development Supabase project as the backend.

### 3.2 Install & Start
From `web/murise-games/`:

```powershell
npm ci
npm run dev
```

Open `http://localhost:3000` (restart `npm run dev` whenever you edit `.env.local`).

---

## Step 4: Verify Everything Works

### 4.1 Check API Health & Swagger
```powershell
curl.exe -i http://localhost:8080/actuator/health
```
Expected: `HTTP/1.1 200` with `{"status":"UP"}`.  
You can also open `http://localhost:8080/swagger-ui.html` to browse all endpoints (Auth, Users, Events, Teams, Invites, Join Requests, Participants, Game Catalog, Event Games, Scoring & Scoreboard).

### 4.2 Verify Flyway & Role Isolation in Supabase SQL Editor
Run these two queries in the **Supabase SQL Editor**:

```sql
-- 1. Confirm V1 through V6 applied cleanly as murise_migrator
SELECT version, description, installed_by, success
FROM murise.flyway_schema_history
ORDER BY installed_rank;

-- 2. Confirm murise_app cannot create tables or alter Flyway history
--    Both columns MUST return false.
SELECT
    has_schema_privilege('murise_app', 'murise', 'CREATE') AS app_can_create_tables,
    has_table_privilege(
        'murise_app',
        'murise.flyway_schema_history',
        'INSERT,UPDATE,DELETE,TRUNCATE'
    ) AS app_can_modify_migration_history;
```

Expected:
- Rows for versions `1` through `6`, all with `success = true`.
- `app_can_create_tables = false` and `app_can_modify_migration_history = false`.

### 4.3 Run Automated Checks
Backend unit tests (runs offline using `application-test.yaml`, no live DB required):
```powershell
cd api
.\gradlew.bat test
```

Frontend lint and build:
```powershell
cd web/murise-games
npm run lint
npm run build
```

### 4.4 Quick UI Smoke Test
1. Open `http://localhost:3000/login` and sign up / sign in.
2. You should land on `/dashboard` with your profile resolved via `GET /api/auth/me`.
3. Create a test event, add two teams, and open **Game Lineup → Add from catalog…** — all 9 seeded games should appear.

---

## Common Troubleshooting

- **SSL / Certificate error on startup:** Double-check that `sslrootcert` points to the exact file path on disk and uses forward slashes (`/`), even on Windows.
- **`password authentication failed` or `Tenant or user not found`:** Make sure `DB_APP_USERNAME` and `DB_MIGRATOR_USERNAME` include `.YOUR_PROJECT_REF` when using the Supabase pooler host.
- **Flyway checksum mismatch:** Never edit an already-applied `V*__*.sql` migration file. Always add a new versioned migration (`V7__...sql`).
- **Signup / confirmation emails not arriving:** Supabase's built-in email provider has strict rate limits. For local testing, you can confirm a test user directly in **Supabase Dashboard → Authentication → Users**.