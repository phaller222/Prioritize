/*
 * Copyright 2026 Peter Michael Haller and contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.hallerweb.enterprise.prioritize.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.hallerweb.enterprise.prioritize.model.project.Project;
import de.hallerweb.enterprise.prioritize.model.project.Task;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.resource.ResourceGroup;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.company.DepartmentRepository;
import de.hallerweb.enterprise.prioritize.repository.resource.ResourceGroupRepository;
import de.hallerweb.enterprise.prioritize.repository.resource.ResourceRepository;
import de.hallerweb.enterprise.prioritize.service.project.ProjectService;
import de.hallerweb.enterprise.prioritize.service.project.TaskService;
import de.hallerweb.enterprise.prioritize.service.resource.ResourceService;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManagerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * End-to-end tests over real HTTP against a running servlet container — the layer the rest of the
 * suite does not reach. Service tests call services directly and the controller tests are plain
 * Mockito, so nothing else exercises the actual request path: the Spring Security filter chain, the
 * open-in-view {@code EntityManager}, {@link de.hallerweb.enterprise.prioritize.config.AuthenticatedUserArgumentResolver}
 * and only then the controller. Regressions in exactly that chain are invisible to a green suite.
 * <p>
 * Uses the JDK {@link HttpClient} rather than {@code TestRestTemplate}: in Boot 4 the latter lives in
 * a separate module that is not on this build's classpath, and adding a dependency is not an option
 * for an offline build. The JDK client needs none and makes the wire format explicit.
 * <p>
 * Runs against a private in-memory H2, so it neither needs the shared PostgreSQL nor touches the
 * persistent dev database at {@code ~/prioritize.mv.db}. The admin account is the one
 * {@code InitializationService} seeds.
 *
 * @author peter haller
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:rest-api-it;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@ActiveProfiles("h2")
class RestApiIntegrationTest {

    private static final String ADMIN = "admin";
    private static final String ADMIN_PASSWORD = "p@ssword";
    private static final String PASSWORD = "secret123";

    /** An ISO date-time with no trailing {@code Z} and no numeric offset — what a LocalDateTime becomes. */
    private static final Pattern OFFSET_LESS_TIMESTAMP =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?");

    @LocalServerPort private int port;
    @Autowired private UserService userService;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private ResourceRepository resourceRepository;
    @Autowired private ResourceGroupRepository resourceGroupRepository;
    @Autowired private ResourceService resourceService;
    @Autowired private ProjectService projectService;
    @Autowired private TaskService taskService;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    // ==========================================
    // Authentication
    // ==========================================

    @Test
    @DisplayName("An anonymous request is rejected with 401")
    void anonymousRequestIsRejected() throws Exception {
        assertEquals(401, send(request("/api/v1/resources").GET()).statusCode());
    }

    @Test
    @DisplayName("Basic auth with the seeded admin is accepted")
    void basicAuthIsAccepted() throws Exception {
        assertEquals(200, send(authorized("/api/v1/resources", ADMIN, ADMIN_PASSWORD).GET()).statusCode());
    }

    @Test
    @DisplayName("A wrong password is rejected with 401")
    void wrongPasswordIsRejected() throws Exception {
        assertEquals(401, send(authorized("/api/v1/resources", ADMIN, "nope").GET()).statusCode());
    }

    @Test
    @DisplayName("A deactivated account stops authenticating immediately")
    void deactivatedUserCannotAuthenticate() throws Exception {
        PUser user = createUser("it-deactivated", false);

        assertEquals(200, send(authorized("/api/v1/resources", user.getUsername(), PASSWORD).GET()).statusCode(),
                "the active account should authenticate");

        userService.deactivateUser(user.getId());

        assertEquals(401, send(authorized("/api/v1/resources", user.getUsername(), PASSWORD).GET()).statusCode(),
                "a deactivated account must be locked out at once — no credential caching in between");
    }

    // ==========================================
    // The injected user is the calling user
    // ==========================================

    /**
     * Guards {@code @AuthenticatedUser}: the parameter must carry the user who sent the request, not
     * merely some authenticated user. A plain account without permissions has to be refused where the
     * admin gets through, which can only differ if the identity actually reaches the service.
     */
    @Test
    @DisplayName("Authorization follows the calling user, not just the fact that someone is logged in")
    void injectedUserDrivesAuthorization() throws Exception {
        long companyId = createCompanyAsAdmin();
        PUser plain = createUser("it-plain", false);

        assertEquals(200, send(authorized("/api/v1/companies/" + companyId, ADMIN, ADMIN_PASSWORD).GET()).statusCode(),
                "the admin may read the company");

        int plainStatus = send(authorized("/api/v1/companies/" + companyId, plain.getUsername(), PASSWORD)
                .GET()).statusCode();
        assertNotEquals(200, plainStatus,
                "an account without permissions must not read the company — got " + plainStatus);
        assertEquals(403, plainStatus);
    }

    // ==========================================
    // The combined status endpoint
    // ==========================================

    /**
     * {@code /resources/status} must not be swallowed by the {@code /resources/{id}} template that sits
     * next to it, and it has to describe exactly the resources {@code /resources} lists — same set,
     * same order — just with the values and rules already attached.
     */
    @Test
    @DisplayName("The combined status endpoint answers on its own path and matches the plain resource list")
    void resourceStatusMatchesTheResourceList() throws Exception {
        HttpResponse<String> status = send(authorized("/api/v1/resources/status", ADMIN, ADMIN_PASSWORD).GET());
        assertEquals(200, status.statusCode(), status.body());

        HttpResponse<String> plain = send(authorized("/api/v1/resources", ADMIN, ADMIN_PASSWORD).GET());
        JsonNode combined = json.readTree(status.body());
        JsonNode list = json.readTree(plain.body());

        assertEquals(list.size(), combined.size(), "the status view must cover the same resources");
        for (int i = 0; i < list.size(); i++) {
            JsonNode entry = combined.get(i);
            assertEquals(list.get(i).path("id").asLong(), entry.path("resource").path("id").asLong());
            assertTrue(entry.has("latestValues"), "each entry carries its latest values");
            assertTrue(entry.has("telemetryRules"), "each entry carries its monitoring rules");
        }
    }

    @Test
    @DisplayName("The combined status endpoint is not readable anonymously either")
    void resourceStatusRequiresAuthentication() throws Exception {
        assertEquals(401, send(request("/api/v1/resources/status").GET()).statusCode());
    }

    /**
     * The step that used to be missing on the way to a group-scoped read: resource groups could be
     * created and deleted over REST but never listed, so no consumer could learn a group id and every
     * {@code /resourcegroups/{groupId}/...} endpoint stayed unreachable in practice. Once the listing
     * answers, the id it hands out has to work on the group-scoped read — that is the pair this pins.
     * <p>
     * Walks the whole chain over REST, starting at the flat department listing: until 1.4.0 the
     * department id had to be taken from the repository here, because the seeded structure has no
     * company and departments were only listed underneath one. That this test now needs no repository
     * access is the point of {@code GET /departments}.
     */
    @Test
    @DisplayName("Listing a department's resource groups yields an id that works on the group's resources")
    void resourceGroupsAreDiscoverable() throws Exception {
        long deptId = firstDepartmentIdOverRest();

        HttpResponse<String> groupsResponse = send(
                authorized("/api/v1/departments/" + deptId + "/resourcegroups", ADMIN, ADMIN_PASSWORD).GET());
        assertEquals(200, groupsResponse.statusCode(), groupsResponse.body());
        JsonNode groups = json.readTree(groupsResponse.body());
        assertTrue(groups.size() > 0, "the default resource group must be listed");
        JsonNode group = groups.get(0);
        assertEquals(deptId, group.path("departmentId").asLong(), "groups are scoped to the department");

        assertEquals(200, send(authorized(
                "/api/v1/resourcegroups/" + group.path("id").asLong() + "/resources",
                ADMIN, ADMIN_PASSWORD).GET()).statusCode());
    }

    @Test
    @DisplayName("The resource group listing is not readable anonymously")
    void resourceGroupsRequireAuthentication() throws Exception {
        assertEquals(401, send(request("/api/v1/departments/1/resourcegroups").GET()).statusCode());
    }

    // ==========================================
    // Departments
    // ==========================================

    /**
     * The entry point a client without a company id needs. {@code InitializationService} seeds a
     * department but no company, so before this endpoint existed the seeded department — the one every
     * fresh installation actually has — could not be reached through the API at all.
     */
    @Test
    @DisplayName("The flat department listing exposes the seeded department to an admin")
    void departmentsAreListedFlat() throws Exception {
        HttpResponse<String> response = send(authorized("/api/v1/departments", ADMIN, ADMIN_PASSWORD).GET());
        assertEquals(200, response.statusCode(), response.body());

        JsonNode departments = json.readTree(response.body());
        assertTrue(departments.isArray() && departments.size() > 0,
                "the seeded default department must be listed");

        long seeded = departmentRepository.findAll().stream().findFirst().orElseThrow().getId();
        assertTrue(json.readTree(response.body()).findValuesAsText("id").contains(String.valueOf(seeded)),
                "the listing must contain the department that actually exists, not some other id");
    }

    /**
     * The listing filters on READ per department instead of handing out the whole installation. Without
     * that filter this test would see the same list the admin sees, so it is what keeps
     * {@code getReadableDepartments} from being an unauthorized dump.
     */
    @Test
    @DisplayName("A user without permissions sees no departments in the flat listing")
    void departmentListingIsFilteredByPermission() throws Exception {
        PUser stranger = createUser("it-dept-stranger", false);

        HttpResponse<String> response = send(
                authorized("/api/v1/departments", stranger.getUsername(), PASSWORD).GET());
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(0, json.readTree(response.body()).size(),
                "a user with no READ permission must not learn which departments exist");
    }

    @Test
    @DisplayName("The flat department listing is not readable anonymously")
    void departmentListingRequiresAuthentication() throws Exception {
        assertEquals(401, send(request("/api/v1/departments").GET()).statusCode());
    }

    /**
     * The single read is gated the same way the listing is. It used to be ungated while the listing
     * underneath a company was not, so filtering the listing alone would have been decoration: a caller
     * denied the list could still walk the sequential ids and read every department individually.
     */
    @Test
    @DisplayName("Reading a single department requires READ permission on it")
    void singleDepartmentReadIsAuthorized() throws Exception {
        long deptId = firstDepartmentIdOverRest();
        PUser stranger = createUser("it-dept-single", false);

        assertEquals(403, send(authorized("/api/v1/departments/" + deptId,
                stranger.getUsername(), PASSWORD).GET()).statusCode(),
                "a user without READ must not reach the department by id either");

        assertEquals(200, send(authorized("/api/v1/departments/" + deptId, ADMIN, ADMIN_PASSWORD).GET())
                .statusCode(), "the admin must still be able to read it — otherwise the check is too tight");
    }

    // ==========================================
    // Timestamp wire format
    // ==========================================

    /**
     * OpenAPI's {@code format: date-time} is RFC 3339, which requires a UTC offset — every generated,
     * statically typed client maps it to {@code OffsetDateTime}. The entities store server-zone
     * {@link java.time.LocalDateTime}, and Jackson writes those <em>without</em> an offset, so before
     * {@link de.hallerweb.enterprise.prioritize.dto.WireTime} the affected endpoints were unreadable for
     * the official Java client while the {@code Instant}-backed ones on the same API worked. This walks
     * the whole response and fails on any timestamp that lost its offset again — including inside the
     * nested resource/rule structures of the combined status endpoint, where a future DTO would slip
     * through a field-by-field assertion.
     */
    @Test
    @DisplayName("No endpoint emits a timestamp without a UTC offset")
    void timestampsAlwaysCarryAnOffset() throws Exception {
        assertTrue(OFFSET_LESS_TIMESTAMP.matcher("2026-08-02T21:40:20.81039").matches(),
                "the guard must still recognize the offset-less form this API used to emit — "
                        + "without this the walk below would pass vacuously");

        Resource resource = createMqttResourceWithPing();

        for (String path : new String[]{"/api/v1/resources", "/api/v1/resources/status", "/api/v1/users"}) {
            HttpResponse<String> response = send(authorized(path, ADMIN, ADMIN_PASSWORD).GET());
            assertEquals(200, response.statusCode(), response.body());
            assertOffsetOnEveryTimestamp(json.readTree(response.body()), path);
        }

        JsonNode listed = json.readTree(send(authorized("/api/v1/resources", ADMIN, ADMIN_PASSWORD).GET()).body());
        JsonNode ping = listed.get(0).path("mqttLastPing");
        assertTrue(ping.isTextual(), "the resource under test must actually carry a ping timestamp");
        assertEquals(resource.getMqttLastPing().atZone(ZoneId.systemDefault()).toInstant(),
                OffsetDateTime.parse(ping.asText()).toInstant(),
                "the emitted instant must denote the stored server-zone wall clock");
    }

    /**
     * The write direction of the same defect: the client sends what its {@code OffsetDateTime} produces —
     * a real numeric offset — and the server used to reject that with 400 while silently discarding a
     * {@code Z}. Both spellings must be accepted and mean the same instant, so a value does not change
     * meaning by travelling through the API.
     * <p>
     * Rides on a hand-booked work session because {@code WorkSessionRequest} is the only request body
     * left carrying an {@link Instant} — {@code dateOfBirth}, which used to carry this test, is a
     * {@link LocalDate} since 1.4.0. That is the better subject anyway: a session boundary is a real
     * point in time where an hour lost to a dropped offset is an hour missing from an invoice.
     */
    @Test
    @DisplayName("A timestamp survives the round trip with its offset, whether Z or numeric")
    void offsetTimestampsRoundTripUnchanged() throws Exception {
        long taskId = createTaskAsAdmin();
        Instant from = OffsetDateTime.parse("2026-03-02T08:00:00Z").toInstant();
        Instant until = OffsetDateTime.parse("2026-03-02T12:00:00Z").toInstant();

        String[][] spellings = {
                {"2026-03-02T08:00:00Z", "2026-03-02T12:00:00Z"},
                {"2026-03-02T10:00:00+02:00", "2026-03-02T14:00:00+02:00"}
        };

        for (String[] spelling : spellings) {
            String body = json.writeValueAsString(json.createObjectNode()
                    .put("from", spelling[0])
                    .put("until", spelling[1])
                    .put("reason", "offset round trip"));

            HttpResponse<String> created = send(
                    authorized("/api/v1/tasks/" + taskId + "/tracking/sessions", ADMIN, ADMIN_PASSWORD)
                            .POST(HttpRequest.BodyPublishers.ofString(body)));
            assertEquals(201, created.statusCode(),
                    "a client-shaped offset timestamp must be accepted — " + spelling[0] + ": " + created.body());

            JsonNode session = json.readTree(created.body());
            assertEquals(from, OffsetDateTime.parse(session.path("from").asText()).toInstant(),
                    "the offset in " + spelling[0] + " must be honoured, not dropped");
            assertEquals(until, OffsetDateTime.parse(session.path("until").asText()).toInstant(),
                    "the offset in " + spelling[1] + " must be honoured, not dropped");
        }
    }

    /**
     * A birthday is a calendar date, not an instant. Sent and echoed as a plain {@code yyyy-MM-dd}, it
     * denotes the same day regardless of the reader's zone; as an instant it used to render a day early
     * or late whenever client and server zones differed, which is why the field moved to
     * {@link LocalDate} in 1.4.0.
     * <p>
     * The zone offset in the assertion is deliberate: {@code 1980-05-15} must come back as that day even
     * though the server interprets it in a zone that is <em>not</em> UTC, and a timestamp spelling must
     * be rejected outright rather than silently truncated to some neighbouring day.
     */
    @Test
    @DisplayName("A date of birth is a plain calendar date, not a timestamp")
    void dateOfBirthIsACalendarDate() throws Exception {
        String body = json.writeValueAsString(json.createObjectNode()
                .put("username", "it-dob-" + System.nanoTime())
                .put("name", "Birth").put("firstname", "Date")
                .put("dateOfBirth", "1980-05-15"));

        HttpResponse<String> created = send(authorized("/api/v1/users", ADMIN, ADMIN_PASSWORD)
                .POST(HttpRequest.BodyPublishers.ofString(body)));
        assertEquals(201, created.statusCode(), created.body());
        assertEquals("1980-05-15", json.readTree(created.body()).path("dateOfBirth").asText(),
                "the day must survive unchanged, without a time or a zone shifting it");

        String withTimestamp = json.writeValueAsString(json.createObjectNode()
                .put("username", "it-dob-ts-" + System.nanoTime())
                .put("name", "Birth").put("firstname", "Stamp")
                .put("dateOfBirth", "1980-05-15T23:00:00Z"));

        HttpResponse<String> legacy = send(authorized("/api/v1/users", ADMIN, ADMIN_PASSWORD)
                .POST(HttpRequest.BodyPublishers.ofString(withTimestamp)));
        assertEquals(201, legacy.statusCode(), legacy.body());
        assertEquals("1980-05-15", json.readTree(legacy.body()).path("dateOfBirth").asText(),
                "a client still sending a 1.3.x timestamp must keep the day it wrote — the late hour "
                        + "here would tip over into the next day if the value were shifted into the "
                        + "server zone first, which is the very defect this field was moved to fix");
    }

    // ==========================================
    // lastSeen
    // ==========================================

    /**
     * The point of the throttle: a burst of authenticated requests must cost <b>one</b> write, not one
     * per request. Without it every REST call would write to {@code puser} — this API is stateless, so
     * each call authenticates anew — and a polling client would keep the table permanently busy for a
     * field nothing reads during the request.
     * <p>
     * Asserted on the stored value rather than on Hibernate's statistics, which do not book the bulk
     * update the tracker issues. An unthrottled second write could not go unnoticed: it would carry a
     * later {@code LocalDateTime.now()}. The second account at the end proves exactly that — its own
     * first stamp lands after the first account's, so timestamps written moments apart <em>are</em>
     * distinguishable and the "unchanged" assertion above cannot pass for the wrong reason.
     */
    @Test
    @DisplayName("A burst of authenticated requests stamps lastSeen once, not once per request")
    void lastSeenIsThrottled() throws Exception {
        PUser user = createUser("it-last-seen", false);

        assertEquals(200, send(authorized("/api/v1/resources", user.getUsername(), PASSWORD).GET())
                .statusCode());
        Instant firstStamp = lastSeenOf(user.getId());

        for (int i = 0; i < 5; i++) {
            assertEquals(200, send(authorized("/api/v1/resources", user.getUsername(), PASSWORD).GET())
                    .statusCode());
        }
        assertEquals(firstStamp, lastSeenOf(user.getId()),
                "within the throttle interval no further write may happen");

        PUser other = createUser("it-last-seen-other", false);
        assertEquals(200, send(authorized("/api/v1/resources", other.getUsername(), PASSWORD).GET())
                .statusCode());
        assertTrue(lastSeenOf(other.getId()).isAfter(firstStamp),
                "a stamp written later must be visibly later — otherwise the assertion above is vacuous");
    }

    /**
     * The other half of the guarantee: the stamp does happen, is readable through the API, and lands in
     * the present. {@code lastSeen} is null until the account is first seen, which is what makes the
     * "never set" state of the old {@code lastLogin} field distinguishable from a real timestamp.
     */
    @Test
    @DisplayName("lastSeen is null before the first authentication and set afterwards")
    void lastSeenIsRecordedOnFirstAuthentication() throws Exception {
        Instant before = Instant.now().minusSeconds(5);
        PUser user = createUser("it-seen-once", false);

        JsonNode fresh = readUserAsAdmin(user.getId());
        assertTrue(fresh.path("lastSeen").isNull(), "a user who never authenticated has no lastSeen");

        assertEquals(200, send(authorized("/api/v1/resources", user.getUsername(), PASSWORD).GET())
                .statusCode());

        JsonNode seen = readUserAsAdmin(user.getId());
        assertTrue(seen.path("lastSeen").isTextual(), "authenticating must stamp the account");
        Instant stamped = OffsetDateTime.parse(seen.path("lastSeen").asText()).toInstant();
        assertTrue(stamped.isAfter(before), "the stamp must be current, got " + stamped);
    }

    // ==========================================
    // Persistence through the full request path
    // ==========================================

    /**
     * The regression guard for any future attempt to share one persistence context between the
     * authentication filter and the request. {@code UserService.updateUser} mutates the managed entity
     * and relies on dirty checking; if the caller's own {@code PUser} were loaded read-only during
     * authentication and then handed out again from the same context, this update would be dropped
     * <b>silently</b> — no error, no log. Updating oneself is the case that would break.
     */
    @Test
    @DisplayName("A user updating their own profile actually persists the change")
    void selfUpdatePersists() throws Exception {
        PUser self = createUser("it-self-update", true);
        String newName = "Renamed-" + System.nanoTime();

        String body = json.writeValueAsString(json.createObjectNode()
                .put("username", self.getUsername())
                .put("name", newName)
                .put("firstname", "Self")
                .put("email", "self@example.com")
                .put("admin", true)
                .put("active", true));

        HttpResponse<String> update = send(authorized("/api/v1/users/" + self.getId(), self.getUsername(), PASSWORD)
                .PUT(HttpRequest.BodyPublishers.ofString(body)));
        assertEquals(200, update.statusCode(), update.body());

        HttpResponse<String> reread = send(
                authorized("/api/v1/users/" + self.getId(), self.getUsername(), PASSWORD).GET());
        assertEquals(200, reread.statusCode());
        assertEquals(newName, json.readTree(reread.body()).path("name").asText(),
                "the self-update must be visible on a fresh read, not silently dropped");
    }

    /**
     * Pins what one authenticated REST call currently costs in {@code PUser} loads, so the number is a
     * test result instead of a console observation. It is <b>2</b> today: Spring Security's
     * authentication filter loads the user in its own transaction, and {@code CurrentUserResolver}
     * loads it again because the open-in-view {@code EntityManager} only exists from handler mapping
     * on — two different persistence contexts. Sharing one context would make this <b>1</b>; when that
     * lands, this test goes red and the expectation moves to 1. That is the point of it.
     */
    @Test
    @DisplayName("One authenticated request costs exactly two PUser loads (pins the known duplicate)")
    void currentUserLoadsPerRequestArePinned() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        assertEquals(200, send(authorized("/api/v1/resources", ADMIN, ADMIN_PASSWORD).GET()).statusCode());

        long loads = statistics.getEntityStatistics(PUser.class.getName()).getLoadCount();
        assertEquals(2, loads,
                "expected the documented duplicate load; a shared persistence context would make it 1");
    }

    // ==========================================
    // The published contract
    // ==========================================

    /**
     * {@code docs/openapi.json} is the single source for all four generated client libraries, so
     * anything the document lists becomes a published API class. The scan page is a browser page that
     * hands back HTML, but it carries {@code @ResponseBody}, which is all springdoc needs to document
     * it — it appeared in the document under the generated tag {@code scan-page-controller} and would
     * have shipped as an API returning {@code String}, removable afterwards only by breaking the
     * contract a second time.
     * <p>
     * Asserting the general rule rather than the one path: the REST contract lives under
     * {@code /api/v1}, so any handler outside it that reaches the document is the same mistake with a
     * different name.
     */
    @Test
    @DisplayName("The OpenAPI document describes the REST API only, nothing outside /api/v1")
    void openApiDocumentCoversTheRestApiOnly() throws Exception {
        HttpResponse<String> response = send(authorized("/v3/api-docs", ADMIN, ADMIN_PASSWORD).GET());
        assertEquals(200, response.statusCode(), response.body());

        JsonNode paths = json.readTree(response.body()).path("paths");
        assertTrue(paths.size() > 50, "the document must actually describe the API, not be empty");

        paths.fieldNames().forEachRemaining(path -> assertTrue(path.startsWith("/api/v1/"),
                "the generated clients would get an API class for " + path
                        + " — annotate the handler with @Hidden"));
    }

    // ==========================================
    // Helpers
    // ==========================================

    /**
     * A resource whose {@code mqttLastPing} is actually set. Created through the service (a REST-created
     * resource leaves the field null — {@code ResourceRequest.toResource} deliberately bypasses the
     * Lombok builder defaults), then stamped like an MQTT ping would.
     */
    private Resource createMqttResourceWithPing() {
        PUser admin = userService.findUserByUsername(ADMIN);
        ResourceGroup group = resourceGroupRepository.findAll().stream().findFirst().orElseThrow();

        Resource resource = new Resource();
        resource.setName("it-ping-" + System.nanoTime());
        resource.setDescription("created by RestApiIntegrationTest");
        resource.setMqttResource(true);
        resource.setMqttOnline(true);
        resource.setMaxSlots(1);
        resource.setStationary(true);
        resource.setRemote(false);
        resource.setAgent(false);

        Resource created = resourceService.createResource(resource, group.getId(), admin);
        // millisecond precision: the column rounds sub-microsecond digits away, and comparing the
        // in-memory value against the persisted one would then fail on the rounding, not on the mapping
        created.setMqttLastPing(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        return resourceRepository.save(created);
    }

    /**
     * Fails on any JSON string that looks like an ISO timestamp but carries no offset — the exact shape
     * a {@code LocalDateTime} serializes to. Recurses, so nested DTOs are covered too.
     */
    private void assertOffsetOnEveryTimestamp(JsonNode node, String path) {
        if (node.isObject()) {
            node.properties().forEach(entry -> assertOffsetOnEveryTimestamp(entry.getValue(), path));
        } else if (node.isArray()) {
            node.forEach(child -> assertOffsetOnEveryTimestamp(child, path));
        } else if (node.isTextual() && OFFSET_LESS_TIMESTAMP.matcher(node.asText()).matches()) {
            fail(path + " emits \"" + node.asText() + "\" — a date-time without a UTC offset, "
                    + "which no generated client can parse");
        }
    }

    /** Reads a user through the API as admin, so the assertion sees the wire format, not the entity. */
    private JsonNode readUserAsAdmin(Long id) throws Exception {
        HttpResponse<String> response = send(authorized("/api/v1/users/" + id, ADMIN, ADMIN_PASSWORD).GET());
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    /** The account's stored {@code lastSeen}, read through the API. Fails if it was never stamped. */
    private Instant lastSeenOf(Long userId) throws Exception {
        JsonNode value = readUserAsAdmin(userId).path("lastSeen");
        assertTrue(value.isTextual(), "expected a lastSeen timestamp, got " + value);
        return OffsetDateTime.parse(value.asText()).toInstant();
    }

    /** The first department's id, discovered the way a client has to discover it: over the API. */
    private long firstDepartmentIdOverRest() throws Exception {
        HttpResponse<String> response = send(authorized("/api/v1/departments", ADMIN, ADMIN_PASSWORD).GET());
        assertEquals(200, response.statusCode(), response.body());
        JsonNode departments = json.readTree(response.body());
        assertTrue(departments.size() > 0, "the seeded default department must be listed");
        return departments.get(0).path("id").asLong();
    }

    /**
     * Creates a project with a single task through the services, owned by the seeded admin, and returns
     * the task id. Goes through the services rather than REST because the endpoints under test are the
     * tracking ones; the admin is manager of the project and may therefore book sessions on the task.
     */
    private long createTaskAsAdmin() {
        PUser admin = userService.findUserByUsername(ADMIN);
        Project project = projectService.createProject(new ProjectService.ProjectData(
                "IT Project " + System.nanoTime(), "created by RestApiIntegrationTest", 1,
                LocalDate.now(), LocalDate.now().plusDays(7), 10), admin);
        Task task = taskService.createTask(project.getId(),
                new TaskService.TaskData("IT Task", "created by RestApiIntegrationTest", 1), admin);
        return task.getId();
    }

    private PUser createUser(String prefix, boolean admin) {
        return userService.createUser(PUser.builder()
                .username(prefix + "-" + System.nanoTime())
                .name("Integration").firstname("Test")
                .email(prefix + "@example.com")
                .password(PASSWORD)
                .admin(admin).active(true)
                .build());
    }

    private long createCompanyAsAdmin() throws Exception {
        String body = json.writeValueAsString(json.createObjectNode()
                .put("name", "IT Company " + System.nanoTime())
                .put("description", "created by RestApiIntegrationTest"));

        HttpResponse<String> response = send(authorized("/api/v1/companies", ADMIN, ADMIN_PASSWORD)
                .POST(HttpRequest.BodyPublishers.ofString(body)));
        assertEquals(201, response.statusCode(), response.body());

        JsonNode created = json.readTree(response.body());
        return created.path("id").asLong();
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
    }

    private HttpRequest.Builder authorized(String path, String username, String password) {
        String credentials = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        return request(path).header("Authorization", "Basic " + credentials);
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
