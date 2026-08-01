# OpenAPI specification (`openapi.json`)

`openapi.json` is a committed snapshot of the Prioritize REST API (`/api/v1`), generated from the running
app's `/v3/api-docs`. It is the single source of truth for the generated client libraries
(`prioritize-<lang>-client`). Refresh it once per release (drop the `-SNAPSHOT` from the app version first),
the same discipline as `docs/apidocs` (Javadoc) — never hand-edit it.

**Current snapshot: `1.2.0-SNAPSHOT`** (develop), taken after the request/response DTO cleanup.

## Known gap (working snapshot, not the final 1.2.0 freeze)

The **request** side is fully DTO-based (no controller binds a JPA entity as `@RequestBody`). On the
**response** side, three controllers still return raw JPA entities, which transitively pull the whole
entity graph (`PUser`, `Company`, `Resource`, …) into the schema list:

- `ProjectController` → returns `Project`
- `TaskController` → returns `Task`
- `NfcUnitController` → returns `NfcUnit`

Response DTOs for these three are planned before the real 1.2.0 release freeze. Until then this file is a
**working reference**, not the frozen release contract.
