# OpenAPI specification (`openapi.json`)

`openapi.json` is a committed snapshot of the Prioritize REST API (`/api/v1`), generated from the running
app's `/v3/api-docs`. It is the single source of truth for the generated client libraries
(`prioritize-<lang>-client`). Refresh it once per release (drop the `-SNAPSHOT` from the app version first),
the same discipline as `docs/apidocs` (Javadoc) — never hand-edit it.

**Frozen at `1.5.0`** — this snapshot is the released `1.5.0` REST contract. It is the source of truth for
generating the `1.x` client libraries. The next refresh happens at the following release (bump the version
first, then regenerate).

Two things about the snapshot itself, both of which have gone wrong before: it is generated on the default
port, because `servers[0].url` is baked into every generated client, and it is written with the repository's
CRLF line endings, or the diff is six thousand lines of nothing.

## What `1.5.0` added

Costing a job, and the equipment side of time tracking. All of it is additive — no path, schema, operation
id, tag, request body, response or parameter of `1.4.1` changed, so a `1.4.x` client keeps working against
this release:

- `POST|GET /qualification-levels`, `GET|PUT|DELETE /qualification-levels/{levelId}` and
  `PUT /users/{userId}/qualification-level` — what an hour of work costs, held by the qualification level
  rather than by the person. New tag, so a new `*Api` class appears in every generated client.
- `POST /tasks/{id}/equipment/{resourceId}/start|stop|toggle|stop-at` and
  `GET /tasks/{id}/equipment`, `GET /tasks/{id}/equipment/sessions` — machine hours, deliberately on their
  own paths and in their own responses: work hours and machine hours must never end up in one sum.
- `POST /tasks/{id}/equipment/{resourceId}/sessions`, `PUT|DELETE /tasks/{id}/equipment/sessions/{sessionId}`
  — the same corrections work sessions have had since `1.4.0`.
- `GET /tasks/{id}/equipment/cost`, `GET /tasks/{id}/labour/cost` and `GET /tasks/{id}/cost` — duration
  times the rate a booking was stamped with. The money is summed per currency; the hours never are.
- `UserDTO` gained `qualificationLevelId`/`qualificationLevelName`, `TaskDTO` gained
  `equipmentRunningCount`.

Two changes that are additive in the document but can still be felt by an old client:
`NfcUnitDTO.type`, `NfcUnitRequest.type` and `ScanResult.type` gained the constant `EQUIPMENT`, which a
strictly generated `1.4.x` enum does not know; and `ResourceRequest.maxSlots` now carries `minimum: 1`, so
a slot count that could never be booked is refused at the door instead of surfacing later as a misleading
`409 All slots (0) occupied.` See `MIGRATION.md`.

## What `1.3.0` added

The read side a REST consumer needs to reach resources at all — previously it could not get there, because
the only way in was a per-group endpoint whose group ids nothing handed out:

- `GET /resources` — flat list of every readable resource.
- `GET /resources/{id}/values/latest` — newest reading per telemetry data point.
- `GET /resources/status` — the same picture as `/resources` plus, per resource, its latest values and its
  monitoring rules, in **one** call instead of `1 + 2N`. Meant for status views that poll.
- `GET /departments/{deptId}/resourcegroups` and `PUT /resourcegroups/{groupId}` — resource groups could be
  created and deleted but never listed or renamed, so no consumer could learn a group id.

The document sizes also changed shape: the spec is now emitted as OpenAPI **3.0.1** rather than 3.1.0. The
contract uses no 3.1-only construct, and OpenAPI Generator cannot resolve a 3.1.0 header — so the client
harnesses no longer need to rewrite it.

## Status

Both sides are now DTO-based. No endpoint binds a JPA entity as `@RequestBody`, and no endpoint returns a
raw JPA entity anymore: the five controllers that used to (`ProjectController`→`Project`,
`TaskController`→`Task`, `NfcUnitController`→`NfcUnit`, `DocumentRestController`→`DocumentInfo`,
`ProjectGoalController`→`ProjectGoal`) now answer flat DTOs, so the heavy entity graph
(`PUser`, `Company`, `Resource`, `Document`, `Blackboard`, …) is gone from the schema list (71 → 49
schemas at `1.2.0`; `1.3.0` adds `ResourceValueDTO` and `ResourceStatusDTO`, 51 in total; `1.4.x` reaches
54, and `1.5.0` adds the twelve cost and equipment records, 66 in total).

The only remaining non-DTO response schemas are plain computed records (`ProjectProgress`, `ScanResult`,
`TrackingSummary`, `WorkSession`).

## Known experimental area

`ProjectGoalProperty` / `ProjectGoalPropertyDocument` still appear in the schema list because the goal
**request** body (`GoalRequest.properties`) carries the polymorphic goal-property shape directly. This is
the same evolving, experimental shape as `SkillProperty` (see the skill endpoints) and may still change
within the 1.x line; it is deliberately kept out of the stable **response** payloads (`ProjectGoalDTO`
omits the property collection).
