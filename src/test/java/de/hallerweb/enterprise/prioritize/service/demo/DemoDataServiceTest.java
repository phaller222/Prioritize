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

package de.hallerweb.enterprise.prioritize.service.demo;

import de.hallerweb.enterprise.prioritize.model.nfc.NfcUnit;
import de.hallerweb.enterprise.prioritize.model.nfc.NfcUnit.NfcUnitType;
import de.hallerweb.enterprise.prioritize.model.project.Project;
import de.hallerweb.enterprise.prioritize.model.project.Task;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.company.CompanyService;
import de.hallerweb.enterprise.prioritize.service.nfc.NfcUnitService;
import de.hallerweb.enterprise.prioritize.service.project.ProjectService;
import de.hallerweb.enterprise.prioritize.service.project.TaskService;
import de.hallerweb.enterprise.prioritize.service.resource.ResourceService;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the demo data set, which {@link DemoDataRunner} seeds during startup under the
 * {@code demo} profile — so by the time this test runs, {@link DemoDataService#seed()} has already
 * happened and the assertions read the result. That also makes this test the proof that the runner
 * fires at all, and in the right order: without the platform's own initialization having run first
 * there would be no admin account to seed as.
 * <p>
 * Runs against a private in-memory H2, never the shared PostgreSQL or the dev database at
 * {@code ~/prioritize.mv.db}: this data set is deliberately large and would be a mess to remove
 * from a real installation again.
 * <p>
 * What is worth pinning here is not every record — it is the handful of properties the demo relies
 * on and that break silently: that a second start does not duplicate anything, that the stickers
 * are bound to tasks (an unbound tracker renders an error page instead of a clock), that hours are
 * already on the board, and that the dates stay relative to today.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:demo-data-it;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@ActiveProfiles({"h2", "demo"})
class DemoDataServiceTest {

    @Autowired private DemoDataService demoDataService;
    @Autowired private CompanyService companyService;
    @Autowired private UserService userService;
    @Autowired private ResourceService resourceService;
    @Autowired private NfcUnitService nfcUnitService;
    @Autowired private ProjectService projectService;
    @Autowired private TaskService taskService;

    private PUser admin() {
        return userService.findUserByUsername("admin");
    }

    @Test
    @DisplayName("Der Betrieb steht: Company, Abteilungen und die vier Leute")
    void seedsTheBusiness() {
        assertEquals(1, companyService.findAll().stream()
                .filter(c -> "Elektro Musterbetrieb GmbH".equals(c.getName())).count());

        for (String username : List.of("meister", "geselle1", "geselle2", "azubi")) {
            assertNotNull(userService.findUserByUsername(username), username + " fehlt");
        }
    }

    @Test
    @DisplayName("Ein zweiter Start legt nichts doppelt an")
    void seedIsIdempotent() {
        long companiesBefore = companyService.findAll().size();
        long resourcesBefore = resourceService.getAllResources(admin()).size();

        demoDataService.seed();

        assertEquals(companiesBefore, companyService.findAll().size(),
                "ein Neustart des Containers darf keinen zweiten Betrieb anlegen");
        assertEquals(resourcesBefore, resourceService.getAllResources(admin()).size());
    }

    @Test
    @DisplayName("Die überfällige Leiterprüfung ist da — der wichtigste Eintrag der Demo")
    void containsTheOverdueLadder() {
        Resource ladder = resourceService.getAllResources(admin()).stream()
                .filter(r -> r.getName().startsWith("Anlegeleiter"))
                .findFirst().orElseThrow();
        assertTrue(ladder.getDescription().contains("ÜBERFÄLLIG"),
                "ein Datensatz, in dem alles in Ordnung ist, zeigt nicht wofür man das Werkzeug braucht");
    }

    @Test
    @DisplayName("Jeder TIMETRACKER-Aufkleber hängt an einer Aufgabe")
    void everyTrackerTagIsBound() {
        List<NfcUnit> trackers = resourceService.getAllResources(admin()).stream()
                .flatMap(r -> nfcUnitService.getNfcUnitsForResource(r.getId(), admin()).stream())
                .filter(u -> u.getType() == NfcUnitType.TIMETRACKER)
                .toList();

        assertEquals(3, trackers.size(), "Lagertor, Kita-Container und PV-Halle");
        trackers.forEach(tracker -> assertNotNull(tracker.getBoundTaskId(),
                "ein ungebundener Tracker zeigt am Handy eine Fehlerseite statt einer Uhr: "
                        + tracker.getUuid()));
    }

    @Test
    @DisplayName("Auf der Kita-Baustelle sind schon Stunden gebucht, verteilt auf mehrere Personen")
    void hoursAreAlreadyOnTheBoard() {
        Project kita = projectService.getMyProjects(admin()).stream()
                .filter(p -> p.getName().startsWith("Baustelle Kita"))
                .findFirst().orElseThrow();
        Long taskId = taskService.getTasksForProject(kita.getId(), admin()).stream()
                .filter(t -> t.getName().startsWith("Rohinstallation"))
                .findFirst().orElseThrow().getId();

        TaskService.TrackingSummary summary = taskService.getTrackingSummary(taskId, admin());
        assertTrue(summary.totalSeconds() > 0,
                "„Stunden auf dieser Baustelle\" muss sofort etwas zeigen, nicht erst nach einem Tag");
        assertFalse(summary.trackingForMe(), "beim Start läuft noch keine Uhr");

        assertTrue(taskService.getWorkSessions(taskId, admin()).size() >= 4,
                "zwei Tage mit Vormittag und Nachmittag, für mehr als eine Person");
    }

    /**
     * The per-person split, checked where it actually lives. {@code WorkSession} carries no people —
     * they hang off the {@code TimeSpan} — so this reads the entity, and needs a transaction for the
     * lazy set. It matters because booking someone else's time is the manager-only path the demo
     * relies on: without it every hour would land on the admin account.
     */
    @Test
    @Transactional
    @DisplayName("Die Stunden sind auf verschiedene Personen gebucht, nicht alle auf den Admin")
    void hoursAreBookedPerPerson() {
        Project kita = projectService.getMyProjects(admin()).stream()
                .filter(p -> p.getName().startsWith("Baustelle Kita"))
                .findFirst().orElseThrow();
        Task task = taskService.getTasksForProject(kita.getId(), admin()).stream()
                .filter(t -> t.getName().startsWith("Rohinstallation"))
                .findFirst().orElseThrow();

        Set<String> workers = taskService.getTask(task.getId(), admin()).getTimeSpent().stream()
                .flatMap(span -> span.getInvolvedUsers().stream())
                .map(PUser::getUsername)
                .collect(Collectors.toSet());

        assertTrue(workers.size() >= 2, "erwartet mehrere Gewerke-Beteiligte, gefunden: " + workers);
        assertFalse(workers.contains("admin"),
                "die Zeit gehört den Leuten auf der Baustelle, nicht dem Konto, das sie eingetragen hat");
    }

    @Test
    @DisplayName("Alle Termine liegen relativ zu heute, nicht auf einem festen Datum")
    void datesAreRelativeToToday() {
        // The trap this guards: a hard-coded date makes the whole set read as long overdue within
        // months. Booked sessions must sit in the recent past, never in the future.
        Instant now = Instant.now();
        Instant twoWeeksAgo = now.minus(java.time.Duration.ofDays(14));

        List<TaskService.WorkSession> sessions = projectService.getMyProjects(admin()).stream()
                .flatMap(p -> taskService.getTasksForProject(p.getId(), admin()).stream())
                .flatMap(t -> taskService.getWorkSessions(t.getId(), admin()).stream())
                .toList();

        assertFalse(sessions.isEmpty(), "ohne gebuchte Sessions sagt dieser Test nichts");
        sessions.forEach(session -> {
            assertTrue(session.from().isAfter(twoWeeksAgo),
                    "Session liegt zu weit in der Vergangenheit — feste Datumsangabe? " + session.from());
            assertTrue(session.until().isBefore(now),
                    "eine gebuchte Session darf nicht in der Zukunft enden: " + session.until());
        });
    }
}
