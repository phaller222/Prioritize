# Migrating an existing database

Prioritize has no schema migration tool. Both profiles run Hibernate with `ddl-auto: update`, which
**adds** tables and columns but never drops one and never changes an existing column's type. A fresh
installation therefore needs nothing from this document — Hibernate creates the current schema. An
installation that has been running since an earlier release needs the statements below, applied once,
with the application stopped.

Check first whether a statement applies at all: a database created after the release in question
already has the target state, and re-running these is either a no-op or an error depending on the
engine.

## 1.4.0

### `puser.last_login` — drop

`lastLogin` promised a timestamp the code never wrote: nothing in the application ever set it, so it
was `null` for every account since the column was introduced. It is replaced by `lastSeen`, which is
stamped on authentication and throttled. Because the old column was never populated, there is nothing
to migrate — the data is not lost, it never existed.

```sql
ALTER TABLE puser DROP COLUMN last_login;
```

Leaving the column in place is harmless: Hibernate ignores columns it does not map. Drop it so the
next person reading the schema does not go looking for what fills it.

### `puser.date_of_birth` — timestamp to date

A birthday is a calendar date, not a point in time. Stored as a timestamp and put on the wire as an
instant, it renders a day early or late whenever the reader's time zone differs from the server's.
The column becomes a `date` and the REST field changes from `format: date-time` to `format: date`.

PostgreSQL:

```sql
ALTER TABLE puser ALTER COLUMN date_of_birth TYPE date USING date_of_birth::date;
```

H2:

```sql
ALTER TABLE puser ALTER COLUMN date_of_birth DATE;
```

The cast keeps the day and discards the time component. That is lossless in practice — the time part
was midnight, or whatever offset a client's timestamp happened to carry, and never meant anything.

**This changes the published API contract**, so clients generated against 1.3.x must be regenerated
against the 1.4.0 specification. `dateOfBirth` becomes `LocalDate`/`date` in every generated client
instead of `OffsetDateTime`.
