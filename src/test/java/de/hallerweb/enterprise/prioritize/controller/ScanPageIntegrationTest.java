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

import de.hallerweb.enterprise.prioritize.model.nfc.NfcUnit;
import de.hallerweb.enterprise.prioritize.model.project.Project;
import de.hallerweb.enterprise.prioritize.model.project.Task;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.nfc.NfcUnitRepository;
import de.hallerweb.enterprise.prioritize.service.project.ProjectService;
import de.hallerweb.enterprise.prioritize.service.project.TaskService;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests for the NFC scan page — the surface a phone reaches after tapping a sticker.
 * <p>
 * Two things here are worth more than the assertions themselves. First, that the page is reachable
 * at all: Vaadin's servlet is root-mapped in this application, and whether a plain Spring MVC
 * controller still gets its request was an open question rather than a certainty. Second, that a
 * repeated GET does not toggle the clock — the entire reason the page exists instead of pointing the
 * tag straight at the REST endpoint.
 * <p>
 * Drives the real form login over HTTP with a cookie jar, because {@code /scan/**} deliberately sits
 * in the session-based chain rather than the stateless Basic-auth one under {@code /api}.
 *
 * @author peter haller
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:scan-page-it;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@ActiveProfiles("h2")
class ScanPageIntegrationTest {

    private static final String ADMIN = "admin";
    private static final String ADMIN_PASSWORD = "p@ssword";

    @LocalServerPort private int port;
    @Autowired private UserService userService;
    @Autowired private ProjectService projectService;
    @Autowired private TaskService taskService;
    @Autowired private NfcUnitRepository nfcUnitRepository;

    /** One cookie jar for both clients, so the session survives when a test stops at a redirect. */
    private final CookieManager cookies = new CookieManager();

    /** Follows redirects and keeps cookies — a browser, in other words. */
    private final HttpClient browser = HttpClient.newBuilder()
            .cookieHandler(cookies)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Same session, but stops at the redirect so its status code and target can be asserted. */
    private final HttpClient noRedirect = HttpClient.newBuilder()
            .cookieHandler(cookies)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private String trackerUuid;
    private Long taskId;

    @BeforeEach
    void setUp() {
        PUser admin = userService.findUserByUsername(ADMIN);
        Project project = projectService.createProject(new ProjectService.ProjectData(
                "Baustelle Kita " + System.nanoTime(), "created by ScanPageIntegrationTest", 1,
                java.time.LocalDate.now(), java.time.LocalDate.now().plusDays(7), 10), admin);
        Task task = taskService.createTask(project.getId(),
                new TaskService.TaskData("Schlitze klopfen", "created by test", 1), admin);
        taskId = task.getId();

        trackerUuid = "it-tracker-" + System.nanoTime();
        nfcUnitRepository.save(NfcUnit.builder()
                .uuid(trackerUuid)
                .name("Baustellencontainer")
                .type(NfcUnit.NfcUnitType.TIMETRACKER)
                .task(task)
                .build());
    }

    /**
     * The routing proof. A 404 here would mean Vaadin swallowed the path; the redirect means the
     * request reached the security chain on its way to a controller that exists.
     */
    @Test
    @DisplayName("An anonymous scan is sent to the login, not to a 404")
    void anonymousScanIsRedirectedToLogin() throws Exception {
        HttpResponse<String> response = noRedirect.send(
                HttpRequest.newBuilder(uri("/scan/" + trackerUuid)).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertNotEquals(404, response.statusCode(),
                "a 404 would mean the scan path never reaches a controller");
        assertEquals(302, response.statusCode(), "an anonymous visitor belongs on the login page");
        assertTrue(response.headers().firstValue("Location").orElse("").contains("login"),
                "and that redirect must point at the login: "
                        + response.headers().firstValue("Location").orElse("<none>"));
    }

    @Test
    @DisplayName("The scan page names the task and offers to clock in")
    void scanPageShowsTheBoundTask() throws Exception {
        login();
        String body = get("/scan/" + trackerUuid);

        assertTrue(body.contains("Schlitze klopfen"), "the page must say what the sticker points at");
        assertTrue(body.contains("Einstechen"), "an idle task offers to start: " + body);
        assertFalse(taskService.getTrackingSummary(taskId, admin()).tracking(),
                "merely looking at the page must not have started anything");
    }

    /**
     * The point of the whole design. A browser repeats GETs on its own — prefetch, reload, the back
     * button — and the underlying scan <em>toggles</em>. If rendering the page were the scan, the
     * second view would silently stop a running clock and the day's hours would be wrong.
     */
    @Test
    @DisplayName("Reloading the page never toggles the clock")
    void repeatedGetDoesNotToggleTracking() throws Exception {
        login();

        get("/scan/" + trackerUuid);
        get("/scan/" + trackerUuid);
        get("/scan/" + trackerUuid);
        assertFalse(taskService.getTrackingSummary(taskId, admin()).tracking(),
                "three views must leave the clock exactly as they found it");

        post("/scan/" + trackerUuid);
        assertTrue(taskService.getTrackingSummary(taskId, admin()).tracking(),
                "the confirming POST is what starts the clock");

        get("/scan/" + trackerUuid);
        get("/scan/" + trackerUuid);
        assertTrue(taskService.getTrackingSummary(taskId, admin()).tracking(),
                "and reloading afterwards must not stop it again");
    }

    @Test
    @DisplayName("The button turns into Ausstechen once the clock runs")
    void buttonReflectsTheCurrentState() throws Exception {
        login();

        assertTrue(post("/scan/" + trackerUuid).contains("Ausstechen"),
                "after clocking in the page must offer the opposite action");
        assertTrue(get("/scan/" + trackerUuid).contains("Ausstechen"),
                "and still offer it on a fresh view");

        post("/scan/" + trackerUuid);
        assertTrue(get("/scan/" + trackerUuid).contains("Einstechen"),
                "clocking out returns the page to its starting state");
    }

    /**
     * Post/Redirect/Get, pinned. Answering the POST with the page itself would leave the browser on
     * the POST, and a pull-to-refresh — the most natural gesture on a phone — would re-submit it and
     * toggle the clock a second time. The 303 moves the address bar onto the GET, which is exactly
     * the request {@link #repeatedGetDoesNotToggleTracking} proves harmless.
     */
    @Test
    @DisplayName("Clocking in answers with a redirect, so a refresh cannot book twice")
    void postRedirectsToTheReadablePage() throws Exception {
        login();
        String form = csrfField(get("/scan/" + trackerUuid));

        HttpResponse<String> response = noRedirect.send(HttpRequest.newBuilder(uri("/scan/" + trackerUuid))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(303, response.statusCode(),
                "the POST must redirect rather than render: " + response.body());
        String location = response.headers().firstValue("Location").orElse("");
        assertTrue(location.startsWith("/scan/" + trackerUuid),
                "and send the browser back to the page it came from, got: " + location);
        assertTrue(taskService.getTrackingSummary(taskId, admin()).tracking(),
                "the scan itself must still have happened");
    }

    @Test
    @DisplayName("An unknown sticker explains itself instead of erroring out")
    void unknownTagIsExplained() throws Exception {
        login();
        String body = get("/scan/does-not-exist");

        assertTrue(body.contains("Unbekannter Aufkleber"),
                "someone standing at a container needs a sentence, not a stack trace: " + body);
    }

    /**
     * A time tracker with nothing bound throws deep in the service. On a phone that has to arrive as
     * a readable sentence, so the page catches it — this pins that it does.
     */
    @Test
    @DisplayName("A tracker without a task says so rather than throwing")
    void trackerWithoutTaskIsExplained() throws Exception {
        String orphan = "it-orphan-" + System.nanoTime();
        nfcUnitRepository.save(NfcUnit.builder()
                .uuid(orphan).name("Loser Aufkleber")
                .type(NfcUnit.NfcUnitType.TIMETRACKER)
                .build());

        login();
        assertTrue(get("/scan/" + orphan).contains("ohne Aufgabe"),
                "the missing binding must be named as the problem");
    }

    // ==========================================
    // Plumbing
    // ==========================================

    private PUser admin() {
        return userService.findUserByUsername(ADMIN);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    /** Signs in through Spring Security's form login, leaving the session cookie in the jar. */
    private void login() throws Exception {
        browser.send(HttpRequest.newBuilder(uri("/login")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        String form = "username=" + URLEncoder.encode(ADMIN, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(ADMIN_PASSWORD, StandardCharsets.UTF_8);

        HttpResponse<String> response = browser.send(HttpRequest.newBuilder(uri("/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                HttpResponse.BodyHandlers.ofString());

        assertTrue(response.statusCode() < 400, "form login failed: " + response.statusCode());
        assertFalse(response.uri().toString().contains("error"),
                "form login was rejected: " + response.uri());
    }

    private String get(String path) throws Exception {
        HttpResponse<String> response = browser.send(
                HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "GET " + path + " -> " + response.body());
        return response.body();
    }

    /**
     * Submits the page's own form, token and all — the way the phone does it. Posting without the
     * token would be refused with 403, so reading it out of the rendered page also pins that the
     * page really ships a usable one.
     */
    private String post(String path) throws Exception {
        String form = csrfField(get(path));

        HttpResponse<String> response = browser.send(HttpRequest.newBuilder(uri(path))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "POST " + path + " -> " + response.body());
        return response.body();
    }

    /** Pulls the hidden CSRF input out of the rendered form as an encoded form body. */
    private String csrfField(String html) {
        Matcher matcher = Pattern
                .compile("<input type=\"hidden\" name=\"([^\"]+)\" value=\"([^\"]+)\">")
                .matcher(html);
        assertTrue(matcher.find(), "the page must carry a CSRF token in its form: " + html);
        return URLEncoder.encode(matcher.group(1), StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(matcher.group(2), StandardCharsets.UTF_8);
    }
}
