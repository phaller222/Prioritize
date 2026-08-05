# OpenAPI specification (`openapi.json`)

`openapi.json` is a committed snapshot of the Prioritize REST API (`/api/v1`), generated from the running
app's `/v3/api-docs`. It is the single source of truth for the generated client libraries
(`prioritize-<lang>-client`). Refresh it once per release (drop the `-SNAPSHOT` from the app version first),
the same discipline as `docs/apidocs` (Javadoc) — never hand-edit it.

**Frozen at `1.3.0`** — this snapshot is the released `1.3.0` REST contract. It is the source of truth for
generating the `1.x` client libraries. The next refresh happens at the following release (bump the version
first, then regenerate).

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
schemas at `1.2.0`; `1.3.0` adds `ResourceValueDTO` and `ResourceStatusDTO`, 51 in total).

The only remaining non-DTO response schemas are plain computed records (`ProjectProgress`, `ScanResult`,
`TrackingSummary`, `WorkSession`).

## Known experimental area

`ProjectGoalProperty` / `ProjectGoalPropertyDocument` still appear in the schema list because the goal
**request** body (`GoalRequest.properties`) carries the polymorphic goal-property shape directly. This is
the same evolving, experimental shape as `SkillProperty` (see the skill endpoints) and may still change
within the 1.x line; it is deliberately kept out of the stable **response** payloads (`ProjectGoalDTO`
omits the property collection).
