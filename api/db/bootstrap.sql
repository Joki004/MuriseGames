-- Run once as the project database administrator, BEFORE starting the API.
-- Use a fresh dev project first. This intentionally fails if roles already exist.
-- No passwords are stored in source control. Set them separately (see docs).
create role murise_migrator login nosuperuser nocreatedb nocreaterole noinherit;
create role murise_app login nosuperuser nocreatedb nocreaterole noinherit;
grant connect on database postgres to murise_migrator, murise_app;
grant murise_migrator to postgres;
create schema murise authorization murise_migrator;
revoke murise_migrator from postgres;
revoke all on schema murise from public;
grant usage on schema murise to murise_app;
-- No anon/authenticated/service_role grants: application data is API-only.
-- Do not expose murise in Supabase Data API settings.
