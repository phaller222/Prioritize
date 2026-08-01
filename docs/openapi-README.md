# OpenAPI specification (`openapi.json`)

`openapi.json` is a committed snapshot of the Prioritize REST API (`/api/v1`), generated from the running
app's `/v3/api-docs`. It is the single source of truth for the generated client libraries
(`prioritize-<lang>-client`). Refresh it once per release (drop the `-SNAPSHOT` from the app version first),
the same discipline as `docs/apidocs` (Javadoc) — never hand-edit it.

**Frozen at `1.2.0`** — this snapshot is the released `1.2.0` REST contract, taken after the full
request+response DTO cleanup. It is the source of truth for generating the `1.x` client libraries. The next
refresh happens at the following release (bump the version first, then regenerate).

## Status

Both sides are now DTO-based. No endpoint binds a JPA entity as `@RequestBody`, and no endpoint returns a
raw JPA entity anymore: the five controllers that used to (`ProjectController`→`Project`,
`TaskController`→`Task`, `NfcUnitController`→`NfcUnit`, `DocumentRestController`→`DocumentInfo`,
`ProjectGoalController`→`ProjectGoal`) now answer flat DTOs, so the heavy entity graph
(`PUser`, `Company`, `Resource`, `Document`, `Blackboard`, …) is gone from the schema list (71 → 49
schemas).

The only remaining non-DTO response schemas are plain computed records (`ProjectProgress`, `ScanResult`,
`TrackingSummary`, `WorkSession`).

## Known experimental area

`ProjectGoalProperty` / `ProjectGoalPropertyDocument` still appear in the schema list because the goal
**request** body (`GoalRequest.properties`) carries the polymorphic goal-property shape directly. This is
the same evolving, experimental shape as `SkillProperty` (see the skill endpoints) and may still change
within the 1.x line; it is deliberately kept out of the stable **response** payloads (`ProjectGoalDTO`
omits the property collection).
