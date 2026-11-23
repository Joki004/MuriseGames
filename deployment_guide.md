# Deployment guide: MuriseGames production

How to put **MuriseGames** online for real players:

| Part | Where | Address you'll get |
|---|---|---|
| Web app (Next.js) | Vercel, free plan | `https://<project>.vercel.app` |
| API (Spring Boot) | Railway, Hobby plan | `https://<service>.up.railway.app` |
| Database, sign-in, pictures | Supabase, **new production project** | `https://<ref>.supabase.co` |
| Sign-up / reset emails | Your SMTP provider (Gmail or a domain sender) | n/a |

Keep your current Supabase project for development. Real players never touch it.

Work through the sections in order. Each section ends with a check.

---

## 0. Before you start

- Merge everything you want live into **`main`** (Railway and Vercel deploy from it).
- Make sure CI is green on `main`.
- Have these open: Supabase dashboard, Railway dashboard, Vercel dashboard, a password manager.

---

## 1. Supabase production project

### 1.1 Create the project

1. Supabase → **New project**. Name: `murisegames-prod`.
2. Region: **Central EU (Frankfurt)** (close to Railway's EU region and to Poland).
3. Save the database password in your password manager.

### 1.2 Roles, schema and profiles table

Open **SQL Editor** and run the script from `local_setup_guide.md`, section "brand-new Supabase project", **with new strong passwords**. Do not reuse the development passwords.

This creates `murise_app`, `murise_migrator`, the `murise` schema and `murise.profiles`. Flyway creates everything else when the API first starts.

### 1.3 Picture storage

**Storage → New bucket**:

- Name: `murise-pictures` (exact; the API uses this name)
- **Public: off** (pictures are served through signed links)

### 1.4 JWT signing keys

The API only accepts **asymmetric** tokens (ES256 or RS256).

**Project Settings → JWT Keys**: the current key must be an ECC (P-256) or RSA key. If the project still shows only a legacy HS256 secret, create/rotate to an asymmetric signing key there before going further.

### 1.5 Auth URLs

You need the Vercel address for this. If you don't have it yet, do section 3 first and come back.

**Authentication → URL Configuration**:

- **Site URL:** `https://<your-vercel-domain>`
- **Redirect URLs:** add
  - `https://<your-vercel-domain>/auth/callback`
  - `https://<your-vercel-domain>/**`

### 1.6 Email sending (required)

Supabase's built-in email service is for testing only: it sends very few emails per hour and only to your Supabase team. Real sign-ups **will fail** without this step.

**Authentication → Emails → SMTP Settings → Enable custom SMTP.**

**Option A: Gmail, no domain needed**

1. On the Gmail account that will send the emails, turn on 2-step verification.
2. Google Account → Security → **App passwords** → create one called "MuriseGames".
3. In Supabase:

| Field | Value |
|---|---|
| Sender email | the Gmail address |
| Sender name | `MuriseGames` |
| Host | `smtp.gmail.com` |
| Port | `587` |
| Username | the Gmail address |
| Password | the 16-character app password |

Gmail allows roughly 500 emails a day, plenty for this event.

**Option B: your own domain.** Use Resend or Brevo, verify the domain with their DNS records, then enter their SMTP details here. Better delivery and less spam-folder risk.

### 1.7 Email rate limit

**Authentication → Rate Limits → emails per hour**: raise it to about `100` before the announcement (the default after enabling custom SMTP is lower, and an announcement brings sign-ups in bursts).

**Check:** sign up with a test address from the Supabase dashboard (Authentication → Users → Invite) and confirm the email arrives from your sender.

### 1.8 Connection details for Railway

**Connect** (top of the project) → **Session pooler**. Note:

- Pooler host, e.g. `aws-0-eu-central-1.pooler.supabase.com` (port **5432**)
- Project ref (the `<ref>` in `https://<ref>.supabase.co`)
- **Project Settings → API Keys**: the publishable key (for the web app) and the secret / service-role key (for the API, **never** for the web app)

---

## 2. API on Railway

The repo already contains `api/Dockerfile` and `api/railway.json`.

### 2.1 Create the service

1. Railway → **New Project → Deploy from GitHub repo** → `Joki004/MuriseGames`.
2. Open the new service → **Settings**:
   - **Source → Root Directory:** `/api`
   - **Config-as-code → Railway Config File:** `/api/railway.json` (Railway needs the full path here even with a root directory set)
   - **Branch:** `main`
   - **Region:** EU (West)
   - **Serverless / App sleeping:** **off** (it must stay awake on game night)

### 2.2 Variables

**Variables → Raw editor**, replace the placeholders:

```env
SPRING_PROFILES_ACTIVE=prod
DB_APP_URL=jdbc:postgresql://<POOLER_HOST>:5432/postgres?sslmode=require
DB_APP_USERNAME=murise_app.<PROJECT_REF>
DB_APP_PASSWORD=<murise_app password from 1.2>
DB_MIGRATION_URL=jdbc:postgresql://<POOLER_HOST>:5432/postgres?sslmode=require
DB_MIGRATOR_USERNAME=murise_migrator.<PROJECT_REF>
DB_MIGRATOR_PASSWORD=<murise_migrator password from 1.2>
SUPABASE_URL=https://<PROJECT_REF>.supabase.co
SUPABASE_SERVICE_ROLE_KEY=<secret key from 1.8>
CORS_ORIGINS=https://<your-vercel-domain>
SWAGGER_ENABLED=false
```

Notes:

- `sslmode=require` encrypts the connection without a local CA file. Locally you use `verify-full` with a certificate file; you can switch production to that later by adding the CA file to the image.
- `CORS_ORIGINS` must be the exact web address, no trailing slash, no `*`. Until Vercel exists, put a placeholder and update it in step 3.4.

### 2.3 Public address

**Settings → Networking → Generate Domain.** Copy the `https://…up.railway.app` address.

**Check:**

- The deploy log shows Flyway applying `V1` … `V18` and then `Started ApiApplication`.
- `https://<railway-domain>/actuator/health` shows `{"status":"UP"}`.

---

## 3. Web app on Vercel

### 3.1 Import

Vercel → **Add New → Project** → import `Joki004/MuriseGames`.

- **Root Directory:** `web/murise-games`
- Framework: Next.js (detected)

### 3.2 Environment variables (Production)

```env
NEXT_PUBLIC_SUPABASE_URL=https://<PROJECT_REF>.supabase.co
NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=<publishable key from 1.8>
NEXT_PUBLIC_API_BASE_URL=https://<railway-domain>
```

Only the **publishable** key goes here, never the secret one.

### 3.3 Deploy

Click **Deploy** and note the address (`https://<project>.vercel.app`, or add your own domain in Settings → Domains).

### 3.4 Connect the pieces

- Railway → Variables → set `CORS_ORIGINS=https://<your-vercel-domain>` (Railway redeploys).
- Supabase → finish step 1.5 with this address.

---

## 4. Smoke test (about 15 minutes)

On your phone and a laptop, with two different email addresses:

1. Sign up → the confirmation email arrives → the link opens the app signed in.
2. Forgot password → the reset email arrives → setting a new password works.
3. Create an event, add teams, upload an event picture.
4. Create an invitation link → the second account opens it, requests to join → approve → assign to a team.
5. Open Player screen, TV display and Host control deck. No errors in the browser console.
6. Railway logs show no errors during all of this.

If all six pass, you can announce.

---

## 5. After the announcement

- **Watch:** Supabase → Authentication → Users (sign-ups arriving), Railway → logs, Gmail/SMTP → bounces.
- **Updating:** pushing to `main` redeploys both API and web. Flyway applies new migrations automatically on API start.
- **Rolling back:** Railway → Deployments → previous deploy → **Redeploy**; Vercel → Deployments → previous → **Promote to production**. Database migrations only go forward, so roll back code, not migrations; fix data with a new migration.
- **Before game night (GM-034):** stop merging features, take a database backup (Supabase → Database → Backups), note the deployed commit as the rollback version.
