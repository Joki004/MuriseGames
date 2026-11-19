# MuriseGames API

Spring Boot backend using Java 21, Supabase PostgreSQL and Flyway.

## Prerequisites

- JDK 21 selected for both the project and Gradle JVM.
- A development Supabase project.
- Its downloaded database CA certificate.
- Schema `murise` and database roles:
  - `murise_migrator`: USAGE and CREATE on the schema.
  - `murise_app`: USAGE on the schema; table grants added by migrations.

The schema is owned by the database administrator. Flyway creates
its objects using the migration role.

## Configuration

Configuration files are in `src/main/resources/`:

- `application.yaml`: shared settings.
- `application-local.yaml`: development overrides.
- `application-prod.yaml`: production overrides.

Set the following in IntelliJ's application Run Configuration →
Environment variables. Replace placeholders with development values.

```dotenv
SPRING_PROFILES_ACTIVE=local
DB_APP_URL=jdbc:postgresql://YOUR_POOLER_HOST:5432/postgres?sslmode=verify-full&sslrootcert=C:/certs/dev-ca.cer
DB_APP_USERNAME=murise_app.YOUR_PROJECT_REF
DB_APP_PASSWORD=YOUR_APP_PASSWORD
DB_MIGRATION_URL=jdbc:postgresql://YOUR_POOLER_HOST:5432/postgres?sslmode=verify-full&sslrootcert=C:/certs/dev-ca.cer
DB_MIGRATOR_USERNAME=murise_migrator.YOUR_PROJECT_REF
DB_MIGRATOR_PASSWORD=YOUR_MIGRATOR_PASSWORD
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
CORS_ORIGINS=http://localhost:3000
PORT=8080
```

Use the exact session-pooler host from Supabase Connect.
The certificate must exist at the configured path.

These are environment-variable examples. The application does not
automatically load a `.env` file.

## Run

Run `ApiApplication` in IntelliJ with the environment above.

Alternatively, from `api/`, after setting the variables in that terminal:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

On macOS/Linux, use `./gradlew`.

## Endpoints

| Endpoint | Access |
| --- | --- |
| `GET /actuator/health` | Public |
| `GET /api/me` | Valid Supabase user JWT required |

JWT validation checks signature, issuer, audience and validity times.
The configured JWKS setup requires an ES256 or RS256 signing key.

Authentication uses bearer headers. Event-level permissions are
separate application features still to implement.

## Database migrations

Location: `src/main/resources/db/migration/`

Naming example: `V1__verify_flyway_connection.sql`

- Use two underscores after the version.
- Add a new version for each database change.
- Do not edit migrations already applied.
- Grant the application role only the table permissions it needs.
- Keep the `murise` schema out of Supabase's exposed Data API schemas.

## Verification

```powershell
curl.exe -i http://localhost:8080/actuator/health
```

Expected: HTTP 200 and status UP.

Restarting should leave Flyway up to date without rerunning applied migrations.

## Build and tests

Build the application JAR:

```powershell
.\gradlew.bat bootJar
```

A credential-independent test configuration and CI are pending.
The current Spring context test needs database configuration;
do not run it against production.