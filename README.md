# MuriseGames

An application for organising themed game nights.

Planned V1 features include events, teams, game selection, schedules,
score entry and leaderboards.

## Stack

- Frontend: Next.js, React and TypeScript
- Backend: Java 21 and Spring Boot
- Database: Supabase PostgreSQL
- Authentication: Supabase Auth
- Database migrations: Flyway

## Repository

- `api/` — Spring Boot backend
- `web/murise-games/` — Next.js frontend

## Getting started

Run the backend and frontend in separate terminals.

- [Backend setup](api/README.md)
- [Frontend setup](web/murise-games/README.md)

Local addresses:

- Frontend: http://localhost:3000
- Backend: http://localhost:8080
- Health: http://localhost:8080/actuator/health

## Environments

- `local`: development Supabase project
- `prod`: separate production configuration; deployment pending

Keep development and production data and credentials separate.
Never commit passwords, access tokens or populated environment files.

## Verification status

Verified locally:

- Backend and frontend start.
- Health endpoint returns HTTP 200.
- Missing and malformed bearer tokens receive HTTP 401.
- CORS accepts localhost:3000 and rejects an untrusted origin.
- Flyway migration succeeds using the migration role.
- Application role cannot create tables in the application schema
  or modify Flyway migration history.

Pending:

- Real Supabase user-token verification during login implementation.
- Browser-to-backend integration.
- CI and tests independent of personal development credentials.
- Production deployment and smoke tests.