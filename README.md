# Prioritize

> Open-source framework for organizing companies, employees, devices (IoT), and their tasks — Spring Boot 4 / Java 21.

Prioritize models organizational structures (companies, departments, users, roles), manages documents with versioning, represents skills for people **and** devices, controls IoT resources over MQTT and REST, and organizes work as projects, tasks and goals with NFC-driven time tracking. The project is being migrated from Java EE to Spring Boot and exposes a REST API throughout, against which arbitrary clients can be built.

---

## Status

Active migration from Java EE to Spring Boot 4. The runnable Spring Boot core covers: company/user management, documents, skills, resource control (MQTT/REST), Flowable processes, and the **project subsystem** — projects, blackboards, tasks, goal-driven progress, task time tracking, and NFC tags as physical triggers (including broadcasting scans over MQTT). An admin GUI (Vaadin) is in early development on a feature branch. Some concepts from the original framework (action board, message inbox) are planned but not yet ported.

---

## Technology stack

| Area | Technology |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 4.0.5 (Web, Data JPA, Security, Integration) |
| Persistence | PostgreSQL (production), H2 (local/tests) |
| Authentication | HTTP Basic Auth **or** OAuth2 Resource Server (Keycloak, JWT) |
| IoT transport | MQTT (Spring Integration + Eclipse Paho v3) and REST |
| Process engine | Flowable (BPMN) |
| Document parsing | Apache Tika |
| API docs | springdoc-openapi (Swagger UI) |
| Build | Maven |
| Boilerplate | Lombok |

---

## Architecture

### Layers

The application follows a clear layering with a fixed convention for authorization:

- **Controllers** accept `Authentication`, resolve the `PUser` from it, and pass it **explicitly** to the service layer. Controllers contain no authorization logic.
- **Services** hold all business logic **including authorization**. Permission checks happen exclusively here and are enforced via exceptions (not via return values).
- **Repositories** (Spring Data JPA) encapsulate data access.

Further conventions: constructor injection via Lombok `@RequiredArgsConstructor`; IDs consistently typed as `Long`; `CurrentUserResolver` as the central bridge between Spring Security's `Authentication` and the `PUser` model.

### Resource control (hexagonal)

Control of IoT resources is modeled as a hexagonal port. The rest of the system only knows the `ResourceControlAdapter` interface, not the concrete transport:

- **REST** (`RestResourceControlAdapter`) is the always-active base transport. Any resource with an IP set is controllable via REST (`POST http://<ip>:<port>/command`).
- **MQTT** (`MqttResourceControlAdapter`) is an optional, additional capability. The entire MQTT branch is active only when `prioritize.mqtt.enabled=true` (`@ConditionalOnProperty`); otherwise it stays dormant.

The `ResourceControlService` selects the transport per command following a capability-set strategy with fallback:

1. MQTT capability present **and** online → MQTT
2. MQTT capability present but offline + REST endpoint (IP) set → REST fallback
3. No MQTT capability → REST
4. No reachable transport → `ResourceOfflineException` (HTTP 503)

The inbound and outbound directions are deliberately separated: outbound commands go through the port; inbound device events (status, discovery, telemetry) are handled by a separate inbound path (`InboundResourceEventHandler`). The wire format is JSON (`ResourceCommandMessage` with `command` / `param` / `slot`).

### Slot-bound control via reservations

Resources can have multiple **slots** (`maxSlots`). A control command always addresses a specific slot — this slot is **not supplied by the client** but derived server-side from the calling user's active reservation:

- Exactly one active reservation by the user → its slot is used.
- No active reservation → `SlotNotReservedException` (HTTP 409). A command requires an ongoing reservation.
- Multiple active reservations → slot is ambiguous → `SlotNotReservedException` (HTTP 409).

"Active" means the current point in time lies within the reserved window (`dateFrom <= now < dateUntil`). An expired reservation implicitly releases the slot; a command sent afterwards runs into the 409 case.

### Projects, tasks and goal-driven progress

Projects own a **blackboard** carrying **tasks**. Authorization in this subsystem is **membership-based** (project manager or member), orthogonal to the role/permission system used elsewhere; a task's assignee/manager is a `PActor` (either a `PUser` or a `Resource`). Progress is **goal-driven, not task-driven**: a `Task` carries no percentage, only an optional link to a `ProjectGoal`. A goal's completion is the share of its non-cancelled tasks that reached a done status; a project's progress is the average over its counting goals, and `null` (n/a) when undefined. Progress is always **computed, never stored**.

### Task time tracking and NFC

Time tracking lives on the `Task` (a running span plus a history of completed spans), so it works **with or without** NFC. `GET /tasks/{id}/tracking` returns the aggregated total (the running span counted live up to now); `GET /tasks/{id}/tracking/sessions` lists the individual work sessions.

An `NfcUnit` is a physical NFC tag mounted on a resource (a resource may carry several tags of different types: `COUNTER`, `CHECKPOINT`, `TIMETRACKER`, `INFOPOINT`, `OTHER`). Scanning a tag resolves it by UUID and triggers a type-specific action — a `TIMETRACKER` tag toggles the time tracking of the single task it is bound to. When the MQTT profile is active, each scan is additionally **broadcast** on the topic `nfc/scan/<tag-uuid>` so devices and dashboards can observe it live; without MQTT, scanning works unchanged.

---

## Configuration (profiles)

Behavior is controlled via Spring profiles. The default profile is `postgres` (see `application.yaml`).

| Profile | Purpose |
|---|---|
| `postgres` | PostgreSQL data source (production/NAS setup). **Default.** |
| `h2` | Local H2 file database including H2 console at `/h2-console`. |
| `keycloak` | Switches security from Basic Auth to OAuth2 Resource Server (JWT). |
| `mqtt` | Enables the MQTT transport (`prioritize.mqtt.enabled=true`). |

Profiles are combinable, e.g. `spring.profiles.active=postgres,keycloak,mqtt`.

### Authentication

The security configuration is profile-dependent and mutually exclusive:

- **Without** the `keycloak` profile, `SecurityConfig` (`@Profile("!keycloak")`) applies with **HTTP Basic Auth**.
- **With** the `keycloak` profile, `KeycloakSecurityConfig` (`@Profile("keycloak")`) applies as an **OAuth2 Resource Server**. The `issuer-uri` is set in `application-keycloak.yaml`; it must exactly match the `iss` claim of the tokens.

A default administrator (`admin` / `p@ssword`) is seeded on first start by `InitializationService` (BCrypt-hashed in the database). Change it before any non-local deployment.

### Database credentials

The PostgreSQL data source reads its password from the `DB_PASSWORD` environment variable, defaulting to `prioritize` for local development (see `application-postgres.yaml`). Set `DB_PASSWORD` in any shared or production environment.

### MQTT

Bound to the prefix `prioritize.mqtt` (`MqttProperties`):

```yaml
prioritize:
  mqtt:
    enabled: true
    broker-url: tcp://memoryalpha:1883   # TLS later: ssl://...:8883
    client-id: prioritize-backend
    # username: prioritize
    # password: ${MQTT_PASSWORD:}
    qos: 1
    subscribe-topics:
      - DISCOVERY
      - "+/status"
```

---

## Quickstart (local)

Prerequisites: JDK 21, Maven, a reachable database (or use the `h2` profile).

```bash
# Local with H2 (no external DB needed)
mvn spring-boot:run -Dspring-boot.run.profiles=h2

# Against PostgreSQL (default) with Basic Auth
mvn spring-boot:run

# With Keycloak and MQTT
mvn spring-boot:run -Dspring-boot.run.profiles=postgres,keycloak,mqtt
```

Tests:

```bash
mvn test
```

With the `h2` profile, the H2 console is available at `http://localhost:8080/h2-console`.

### API documentation

While the application is running, interactive OpenAPI documentation is served via springdoc (Swagger UI, typically at `/swagger-ui.html`). The `basicAuth` security scheme is registered, so endpoints can be tested authenticated directly from the UI.

---

## REST API (overview)

All core endpoints live under `/api/v1`. The table below is an overview, not a complete reference — the authoritative, always-current description is provided by the OpenAPI docs.

### Resources & control

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/resourcegroups/{groupId}/resources` | Resources of a group |
| `POST` | `/api/v1/resourcegroups/{groupId}/resources` | Create resource |
| `GET` | `/api/v1/resources/{id}` | Get resource |
| `PATCH` | `/api/v1/resources/{id}` | Partial update (null = unchanged) |
| `DELETE` | `/api/v1/resources/{id}` | Delete resource |
| `POST` | `/api/v1/resources/{id}/command` | Send control command (slot derived from reservation) |
| `POST` | `/api/v1/resources/{id}/reserve` | Reserve resource for a time window |
| `GET` | `/api/v1/resources/{id}/reservations` | All reservations of the resource |
| `GET` | `/api/v1/resources/{id}/reservations/mine` | Own active reservations (slot preview) |
| `DELETE` | `/api/v1/reservations/{reservationId}` | Cancel reservation / release slot |
| `POST` | `/api/v1/resources/{id}/values` | Ingest a telemetry reading |
| `GET` | `/api/v1/resources/{resourceId}/skills` | Skills of a resource |
| `POST` | `/api/v1/resources/{resourceId}/skills` | Assign skill |

### Projects, tasks & goals

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/projects` | Create project |
| `GET` | `/api/v1/projects/mine` | Projects I manage or am a member of |
| `GET` / `PUT` / `DELETE` | `/api/v1/projects/{id}` | Get / update / delete project |
| `POST` / `DELETE` | `/api/v1/projects/{id}/members` | Add / remove member |
| `GET` | `/api/v1/projects/{id}/tasks` | Tasks on the project's blackboard |
| `GET` / `POST` | `/api/v1/projects/{id}/goals` | List / create goals |
| `GET` | `/api/v1/projects/{id}/progress` | Computed goal-driven progress |
| `POST` | `/api/v1/projects/{projectId}/tasks` | Create task |
| `GET` / `PUT` / `DELETE` | `/api/v1/tasks/{id}` | Get / update / delete task |
| `POST` | `/api/v1/tasks/{id}/assign` | Assign a `PActor` |
| `PUT` | `/api/v1/tasks/{id}/status` | Change task status |
| `PUT` / `DELETE` | `/api/v1/tasks/{id}/goal` | Assign / unassign a goal |

### Time tracking & NFC

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/tasks/{id}/tracking/{start\|stop\|toggle}` | Start / stop / toggle time tracking |
| `GET` | `/api/v1/tasks/{id}/tracking` | Aggregated tracked total (running span live) |
| `GET` | `/api/v1/tasks/{id}/tracking/sessions` | Individual tracked work sessions |
| `GET` / `POST` | `/api/v1/resources/{id}/nfc-units` | List / register NFC tags on a resource |
| `PUT` / `DELETE` | `/api/v1/nfc-units/{id}/task/{taskId}` | Bind / unbind a `TIMETRACKER` tag to a task |
| `POST` | `/api/v1/nfc/scan/{uuid}` | Process a tag scan (type-specific action) |

### Companies & departments

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/companies/{id}` | Get company |
| `POST` | `/api/v1/companies/filter` | Filter companies |
| `PUT` | `/api/v1/companies/{id}` | Update company |
| `DELETE` | `/api/v1/companies/{id}` | Delete company |
| `POST` | `/api/v1/companies/{companyId}/departments` | Create department |
| `GET` | `/api/v1/companies/{companyId}/departments` | Departments of a company |
| `GET` | `/api/v1/departments/{id}` | Get department |
| `PUT` | `/api/v1/departments/{id}` | Update department |
| `DELETE` | `/api/v1/departments/{id}` | Delete department |

### Users

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/users/{id}` | Get user |
| `PUT` | `/api/v1/users/{id}` | Update user |
| `PATCH` | `/api/v1/users/{id}` | Partial update |
| `DELETE` | `/api/v1/users/{id}` | Delete user |
| `GET` | `/api/v1/users/{userId}/skills` | Skills of a user |
| `POST` | `/api/v1/users/{userId}/skills` | Assign skill |

### Skills

| Method | Path | Purpose |
|---|---|---|
| `GET` / `POST` | `/api/v1/skills` | List / create skills |
| `GET` / `PUT` / `DELETE` | `/api/v1/skills/{skillId}` | Get / update / delete skill |
| `GET` / `POST` | `/api/v1/skills/categories` | List / create categories |
| `GET` / `PUT` / `DELETE` | `/api/v1/skills/categories/{categoryId}` | Get / update / delete category |

### Documents

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/documents/download/{documentInfoId}` | Download document |
| `GET` | `/api/v1/documents/{id}/version/{versionNumber}` | Get a specific version |
| `GET` | `/api/v1/documents/{id}/history` | Version history |
| `POST` | `/api/v1/documents/{id}/check-out` | Check out (lock) |
| `POST` | `/api/v1/documents/{id}/check-in` | Check in (new version) |
| `POST` | `/api/v1/documents/{id}/cancel-check-out` | Cancel check-out |
| `GET` | `/api/v1/documents/search` | Full-text / metadata search |
| `GET` | `/api/v1/documents/recent` | Recently changed documents |
| `DELETE` | `/api/v1/documents/{id}` | Delete document |
| `GET` | `/api/v1/document-groups/{groupId}/documents` | Documents of a group |
| `DELETE` | `/api/v1/document-groups/{groupId}` | Delete group |

### Processes

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/run` | Start a Flowable process |

---

## Error semantics (HTTP status)

Centralized in `GlobalExceptionHandler`:

| Status | Trigger |
|---|---|
| `400 Bad Request` | `IllegalArgumentException` (e.g. invalid date format, end before start date) |
| `403 Forbidden` | `AccessDeniedException` (missing permission) |
| `404 Not Found` | `NoSuchElementException` / `EntityNotFoundException` |
| `409 Conflict` | `IllegalStateException` (e.g. all slots occupied) and `SlotNotReservedException` (no / ambiguous active reservation) |
| `502 Bad Gateway` | `ResourceCommandFailedException` (device rejected the command) |
| `503 Service Unavailable` | `ResourceOfflineException` (no reachable control channel) |

---

## Domain model (brief overview)

- **Company / Department** — organizational base structure. The hierarchy provides a foundation for tenant separation, but enforced multi-tenant isolation is not yet implemented (projects are membership-scoped, `admin` is a global superuser).
- **PUser / Role / PermissionRecord** — users, roles, and the fine-grained permission model.
- **Resource** — a device / resource; can represent an IoT device and communicate externally (REST/MQTT). Has slots and reservations.
- **ResourceReservation** — time-bound occupancy of a resource slot by a user.
- **Document / DocumentInfo / DocumentGroup** — documents with versioning and check-in/check-out.
- **Skill / SkillCategory / SkillRecord** — capabilities; assignable to users **and** resources.
- **Project / Blackboard / Task / ProjectGoal** — projects own a blackboard of tasks; goals drive computed progress; a task's assignee/manager is a `PActor`.
- **NfcUnit** — a physical NFC tag mounted on a resource; a scan triggers a type-specific action (e.g. toggling a task's time tracking).
- **Address** — embedded value object, managed exclusively through its owners (Company, Department, PUser).

---

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss the direction.

## License

Apache License 2.0 — see [LICENSE](LICENSE). Source files carry the corresponding Apache 2.0 headers.
