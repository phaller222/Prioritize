# Migrating an existing database

Prioritize has no schema migration tool. Both profiles run Hibernate with `ddl-auto: update`, which
**adds** tables and columns but never drops one. A fresh installation therefore needs nothing from
this document — Hibernate creates the current schema. An installation that has been running since an
earlier release needs the statements below, applied once, with the application stopped.

**Check the current state before running anything.** Do not assume a statement still applies:
`ddl-auto: update` turns out to widen some column types on its own, at least on PostgreSQL — when
1.4.0 was migrated, `date_of_birth` had already become a `date` there while the same column on H2 was
still a `timestamp`. The two engines had drifted apart without anybody touching them. Re-running a
statement that no longer applies is an error on both.

Reading the columns is one query:

```sql
SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'puser';
```

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
