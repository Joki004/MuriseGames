# MuriseGames

Game nights with a plot. Next.js frontend, Spring Boot API, Supabase Auth and PostgreSQL.

## What this starter includes

- Java 21, Spring Boot 4.1.2 and the Gradle wrapper under `api/`.
- Next.js 16 / React 19 / TypeScript under `web/murise-games/`.
- Supabase email/password sign-up and login in the browser; JWT validation in Spring.
- Read-only game catalogue seeded by Flyway, with nine working game names and rules pending review.
- Separate database application/migration roles, explicit runtime profiles, CORS and health endpoints.
- Environment templates, CI with a disposable PostgreSQL database, and an API Dockerfile.

This is a foundation, not the completed event app. Event creation, invitations, organiser permissions,
scheduling, scoring, leaderboards and password recovery remain to implement. See [V1 tasks](docs/V1-TASKS.md).

## Start here

1. Read [Supabase setup](docs/SUPABASE.md). Create or select a **development** project first.
2. Run `api/db/bootstrap.sql` once as its database administrator, then set the two role passwords.
3. Copy `api/.env.example` to `api/.env` and fill it locally.
4. Copy `web/murise-games/.env.example` to `web/murise-games/.env.local` and fill its public values.
5. Start both processes in separate terminals:

```sh
cd api
./gradlew bootRun --args='--spring.profiles.active=local'
```

On Windows PowerShell, use `./gradlew.bat bootRun --args="--spring.profiles.active=local"`.

```sh
cd web/murise-games
npm ci
npm run dev
```

Frontend: http://localhost:3000. Backend health: http://localhost:8080/actuator/health.
Run the API from `api/`, so the local profile can find its `.env` file.
Do not run the API until database values are configured; it intentionally has no production fallback credentials.
The frontend can be previewed without environment values.

## Verify

```sh
cd api
./gradlew test bootJar
```

```sh
cd web/murise-games
npm run lint
npm run typecheck
npm run build
```

Database integration tests run only with `RUN_DB_TESTS=true` and a disposable database bootstrapped
with the test role passwords from `.github/workflows/ci.yml`. Without that flag they are skipped.
CI enables them. No real Supabase secrets are needed for builds or CI.

## One repository, two deployments

| Service | Root directory | Runtime profile |
| --- | --- | --- |
| Frontend (for example Vercel) | `web/murise-games` | Next.js build environment |
| Backend Docker service | `api` | `SPRING_PROFILES_ACTIVE=prod` |

See [deployment](docs/DEPLOYMENT.md), [data model](docs/DATA-MODEL.md), and [local integration](docs/LOCAL-INTEGRATION.md).
Keep real environment files out of Git. Only `NEXT_PUBLIC_*` public values go in the frontend;
database credentials belong exclusively in the backend host's secret settings.
