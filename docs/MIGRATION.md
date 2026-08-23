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

### `task.active_time_span_id` — the running clock moves to the span

Time tracking used to hang off the task: one `active_time_span_id` column, so exactly one clock could
run per task. That was wrong as soon as two people worked the same job — the second person to scan
the sticker stopped the first one's clock, and the **task total itself** came out wrong (two hours
booked where four were worked), quite apart from any per-person reporting. Clocks are now per person:
a task holds as many open spans as there are people on it, and the link lives on the span
(`time_span.active_task_id`).

Hibernate adds the new column on its own at the next start; it does not move the data and does not
drop the old column. So: start the application once (or add the column by hand), then carry over any
clock that was running, then drop the old column.

```sql
UPDATE time_span ts SET active_task_id =
    (SELECT t.id FROM task t WHERE t.active_time_span_id = ts.id)
  WHERE EXISTS (SELECT 1 FROM task t WHERE t.active_time_span_id = ts.id);

ALTER TABLE task DROP COLUMN active_time_span_id;
```

The `UPDATE` is a no-op on an installation where nobody was clocked in at the moment of the upgrade —
check before you worry:

```sql
SELECT COUNT(*) FROM task WHERE active_time_span_id IS NOT NULL;
```

Closed sessions are not affected: they always lived in `time_span.task_id` and stay there.

**This changes the published API contract.** The field `tracking` is renamed to `trackingForMe` in
the task DTO, in the tracking summary and in an NFC scan result, and it now answers "is *my* clock
running" rather than "is anything running on this task" — a task-wide flag can no longer say whose
clock it means. The tracking summary also gains `runningCount` (how many people are on the task right
now), and the MQTT scan broadcast renames the same field to `trackingForScanner`, since a broadcast
is read by somebody other than the scanner. The rename is deliberate: keeping the old name would have
left every generated client compiling happily against a silently changed meaning. `POST
/tasks/{id}/tracking/stop-at` additionally accepts an optional `userId`, naming whose clock to close
— without it a manager could no longer close a clock a crew member left running overnight.

The task DTO gains `runningCount` alongside `trackingForMe`, and every work session now names its
owner (`userId`, `username`). Both follow from the same change: a task holds the sessions of
everybody who worked on it, so a list of bare intervals says neither how many people are on the job
nor whose time it is, and the correction endpoints decide by exactly that.

## 1.4.0 — changes with no database work

These need no SQL. They are listed because they change the published contract, and a client
generated against 1.3.x will not see them until it is regenerated.

### Reading a department now needs permission

`GET /departments/{id}` used to answer for any authenticated caller, without checking whether that
caller may see the department at all. It is now gated on `READ` like every other department access,
so a caller outside the department receives `403` where it previously received the department. This
is a fix, not a restriction — but an integration that read departments it had no rights to will stop
working, and that is worth knowing before the upgrade rather than after.

Alongside it, `GET /departments` lists the departments the caller may read across all companies.
Until now the only way in was `GET /companies/{companyId}/departments`, and a fresh installation has
no company to start from, so the department tree was unreachable over REST.

### Work sessions can be corrected

`GET /tasks/{id}/tracking/sessions` gains an `id` per session and a `correction` block — who changed
a session, when, why, and what the bounds were before. Sessions that were recorded normally and never
touched carry `correction: null`. Four endpoints go with it: `POST /tasks/{id}/tracking/stop-at` for
the forgotten clock-out, and `POST`, `PUT` and `DELETE` on
`/tasks/{id}/tracking/sessions[/{sessionId}]` for booking, correcting and removing one by hand. A
reason is mandatory on all of them.

### A resource can state what it costs

`Resource` gains `costRate`, `costCurrency` and `costRateUnit` (`HOUR`, `DAY`, `USAGE`), readable and
writable over REST. The three travel together: either all are set or none, the currency must be an
ISO 4217 code, and a rate of `0` means "free of charge", which is a different statement from "not
recorded". The columns are additive, so Hibernate adds them at the next start.
