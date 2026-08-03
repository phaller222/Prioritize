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
import de.hallerweb.enterprise.prioritize.model.security.PUser;
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
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @LocalServerPort private int port;
    @Autowired private UserService userService;
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
    // Helpers
    // ==========================================

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
