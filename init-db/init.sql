-- Runs once on first start of a fresh PostgreSQL data volume (docker-entrypoint-initdb.d).
--
-- No manual DDL is required: both the application (JPA, ddl-auto=update) and the Flowable
-- engine (database-schema-update) create their tables in the default `public` schema on
-- first boot. Verified empirically — the ACT_* engine tables land in `public`; the
-- `flowable.table-prefix` line in application-postgres.yaml is not honoured by the Flowable
-- Spring Boot starter, so no separate schema is needed here.
--
-- This file is kept as the mount point for any future seed/migration SQL.
SELECT 1;
