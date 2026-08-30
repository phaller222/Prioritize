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

package de.hallerweb.enterprise.prioritize.service.project;

import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.project.Project;
import de.hallerweb.enterprise.prioritize.model.project.Task;
import de.hallerweb.enterprise.prioritize.model.project.TaskStatus;
import de.hallerweb.enterprise.prioritize.model.resource.CostRateUnit;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.resource.ResourceGroup;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.company.DepartmentRepository;
import de.hallerweb.enterprise.prioritize.repository.project.TaskRepository;
import de.hallerweb.enterprise.prioritize.service.project.ProjectService.ProjectData;
import de.hallerweb.enterprise.prioritize.service.project.TaskService.TaskData;
import de.hallerweb.enterprise.prioritize.service.resource.ResourceService;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
class TaskServiceTest {

    @Autowired
    private ProjectService projectService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private ResourceService resourceService;
    @Autowired
    private DepartmentRepository departmentRepository;

    private PUser admin;
    private PUser member;
    private Project project;

    @BeforeEach
    void setUp() {
        admin = userService.findUserByUsername("admin");
        member = userService.createUser(PUser.builder()
                .username("task-member-" + System.nanoTime())
                .name("Member")
                .firstname("Miriam")
                .email("miriam@example.com")
                .password("plaintext123")
                .admin(false)
                .build());
        project = projectService.createProject(
                new ProjectData("Gemini", "Task project", 3, null, null, 50), admin);
    }

    private Task newTask() {
        return taskService.createTask(project.getId(),
                new TaskData("Design", "Design the thing", 2), admin);
    }

    /** The same, but carrying a cost rate — the three cost fields only mean anything together. */
    private Resource ratedResource(String name, String rate, CostRateUnit unit) {
        Resource resource = newResource(name);
        resource.setCostRate(new BigDecimal(rate));
        resource.setCostCurrency("EUR");
        resource.setCostRateUnit(unit);
        return resource;
    }

    /**
     * Books a closed equipment span of the given length, ending now. Clocking in and straight out
     * would measure milliseconds, so the start is moved back afterwards — the same trick the
     * retroactive-stop tests use.
     */
    private void bookEquipment(Task task, Resource device, Duration length) {
        taskService.startEquipmentUsage(task.getId(), device.getId(), admin);
        taskRepository.findById(task.getId()).orElseThrow()
                .activeEquipmentSpanFor(device).setDateFrom(Instant.now().minus(length));
        taskService.stopEquipmentUsage(task.getId(), device.getId(), admin);
    }

    /** A piece of equipment in the default department's own group, ready to be clocked onto a task. */
    private Resource newResource(String name) {
        Department department = departmentRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Kein Department — InitializationService nicht gelaufen?"));
        ResourceGroup group = resourceService.createResourceGroup(
                "Geräte-" + System.nanoTime(), department, admin);
        return resourceService.createResource(
                Resource.builder().name(name).description(name).maxSlots(1).build(),
                group.getId(), admin);
    }

    @Test
    @DisplayName("createTask: Task landet auf dem Blackboard mit Status CREATED")
    void createTask_addsToBlackboardWithStatusCreated() {
        Task task = newTask();

        assertNotNull(task.getId());
        assertEquals(TaskStatus.CREATED, task.getTaskStatus());
        assertTrue(taskRepository.findByBlackboard_Id(project.getBlackboard().getId()).stream()
                .anyMatch(t -> t.getId().equals(task.getId())));
    }

    @Test
    @DisplayName("assignTask: setzt Assignee und hebt Status auf ASSIGNED")
    void assignTask_setsAssigneeAndPromotesStatus() {
        Task task = newTask();
        Task assigned = taskService.assignTask(task.getId(), admin.getId(), admin);

        assertNotNull(assigned.getAssignee());
        assertEquals(admin.getId(), assigned.getAssignee().getId());
        assertEquals(TaskStatus.ASSIGNED, assigned.getTaskStatus());
    }

    @Test
    @DisplayName("changeStatus: Übergang aus einem Terminalzustand (CLOSED) wirft IllegalStateException")
    void changeStatus_fromTerminal_throws() {
        Task task = newTask();
        taskService.changeStatus(task.getId(), TaskStatus.CLOSED, admin);

        assertThrows(IllegalStateException.class,
                () -> taskService.changeStatus(task.getId(), TaskStatus.OPEN, admin));
    }

    @Test
    @DisplayName("getTasksForProject: liefert die Tasks des Projekts")
    void getTasksForProject_returnsTasks() {
        Task task = newTask();
        assertTrue(taskService.getTasksForProject(project.getId(), admin).stream()
                .anyMatch(t -> t.getId().equals(task.getId())));
    }

    @Test
    @DisplayName("deleteTask: entfernt den Task vom Blackboard")
    void deleteTask_removesTask() {
        Task task = newTask();
        taskService.deleteTask(task.getId(), admin);
        assertFalse(taskRepository.existsById(task.getId()));
    }

    // --- Time tracking ---

    @Test
    @DisplayName("startTracking: öffnet einen aktiven TimeSpan und setzt Status STARTED")
    void startTracking_opensActiveSpan() {
        Task task = newTask();
        Task started = taskService.startTracking(task.getId(), admin);

        assertTrue(started.isTracking());
        assertEquals(TaskStatus.STARTED, started.getTaskStatus());
        assertTrue(started.getTimeSpent().isEmpty(), "Der laufende Span zählt noch nicht zur Historie");
    }

    @Test
    @DisplayName("startTracking: erneuter Start bei laufendem Tracking wirft IllegalStateException")
    void startTracking_whenAlreadyRunning_throws() {
        Task task = newTask();
        taskService.startTracking(task.getId(), admin);
        assertThrows(IllegalStateException.class,
                () -> taskService.startTracking(task.getId(), admin));
    }

    @Test
    @DisplayName("stopTracking: schließt den Span, hängt ihn an timeSpent und setzt Status STOPPED")
    void stopTracking_closesSpanAndArchives() {
        Task task = newTask();
        taskService.startTracking(task.getId(), admin);
        Task stopped = taskService.stopTracking(task.getId(), admin);

        assertFalse(stopped.isTracking());
        assertEquals(TaskStatus.STOPPED, stopped.getTaskStatus());
        assertEquals(1, stopped.getTimeSpent().size());
        assertNotNull(stopped.getTimeSpent().get(0).getDateFrom());
        assertNotNull(stopped.getTimeSpent().get(0).getDateUntil());
    }

    @Test
    @DisplayName("stopTracking: ohne laufendes Tracking wirft IllegalStateException")
    void stopTracking_whenIdle_throws() {
        Task task = newTask();
        assertThrows(IllegalStateException.class,
                () -> taskService.stopTracking(task.getId(), admin));
    }

    @Test
    @DisplayName("toggleTracking: wechselt zwischen Start und Stop und sammelt die Spans")
    void toggleTracking_alternatesAndAccumulates() {
        Task task = newTask();

        taskService.toggleTracking(task.getId(), admin); // start
        assertTrue(taskService.getTask(task.getId(), admin).isTracking());

        taskService.toggleTracking(task.getId(), admin); // stop
        taskService.toggleTracking(task.getId(), admin); // start again
        Task afterThird = taskService.toggleTracking(task.getId(), admin); // stop again

        assertFalse(afterThird.isTracking());
        assertEquals(2, afterThird.getTimeSpent().size());
    }

    @Test
    @DisplayName("getTrackingSummary: laufendes Tracking meldet tracking=true und einen runningSince")
    void getTrackingSummary_whileRunning() {
        Task task = newTask();
        taskService.startTracking(task.getId(), admin);

        var summary = taskService.getTrackingSummary(task.getId(), admin);

        assertEquals(task.getId(), summary.taskId());
        assertTrue(summary.trackingForMe());
        assertNotNull(summary.runningSince());
        assertTrue(summary.totalSeconds() >= 0);
        assertTrue(summary.totalText().startsWith("PT"), "ISO-8601 duration expected");
    }

    @Test
    @DisplayName("getTrackingSummary: nach Stop tracking=false, kein runningSince, Summe bleibt")
    void getTrackingSummary_afterStop() {
        Task task = newTask();
        taskService.startTracking(task.getId(), admin);
        taskService.stopTracking(task.getId(), admin);

        var summary = taskService.getTrackingSummary(task.getId(), admin);

        assertFalse(summary.trackingForMe());
        assertNull(summary.runningSince());
        assertTrue(summary.totalSeconds() >= 0);
    }

    @Test
    @DisplayName("getWorkSessions: laufendes Tracking hängt die offene Session (until=null, running) zuletzt an")
    void getWorkSessions_whileRunning() {
        Task task = newTask();
        taskService.startTracking(task.getId(), admin); // one completed
        taskService.stopTracking(task.getId(), admin);
        taskService.startTracking(task.getId(), admin); // one running

        var sessions = taskService.getWorkSessions(task.getId(), admin);

        assertEquals(2, sessions.size());
        assertFalse(sessions.get(0).running());
        assertNotNull(sessions.get(0).until());
        var open = sessions.get(1);
        assertTrue(open.running());
        assertNull(open.until(), "Die laufende Session hat noch kein Ende");
        assertNotNull(open.from());
    }

    @Test
    @DisplayName("getWorkSessions: ohne Tracking leere Liste, nach Stop eine abgeschlossene Session")
    void getWorkSessions_idleThenAfterStop() {
        Task task = newTask();
        assertTrue(taskService.getWorkSessions(task.getId(), admin).isEmpty());

        taskService.startTracking(task.getId(), admin);
        taskService.stopTracking(task.getId(), admin);

        var sessions = taskService.getWorkSessions(task.getId(), admin);
        assertEquals(1, sessions.size());
        assertFalse(sessions.get(0).running());
        assertNotNull(sessions.get(0).until());
    }

    // --- Correcting tracked time ---

    /** A completed session on the task, tracked by the given user. */
    private TaskService.WorkSession trackedSession(Task task, PUser user) {
        taskService.startTracking(task.getId(), user);
        taskService.stopTracking(task.getId(), user);
        var sessions = taskService.getWorkSessions(task.getId(), admin);
        return sessions.get(sessions.size() - 1);
    }

    @Test
    @DisplayName("getWorkSessions: eine normal getrackte Session hat eine id und keine Korrektur")
    void getWorkSessions_untouchedSessionHasIdAndNoCorrection() {
        Task task = newTask();
        var session = trackedSession(task, admin);

        assertNotNull(session.id(), "Ohne id ließe sich die Session nicht korrigieren");
        assertNull(session.correction(), "Eine unangetastete Session trägt keinen Korrekturvermerk");
    }

    @Test
    @DisplayName("updateWorkSession: korrigiert die Zeiten, bewahrt den Urzustand und vermerkt Bearbeiter und Grund")
    void updateWorkSession_correctsBoundsAndRecordsAudit() {
        Task task = newTask();
        var session = trackedSession(task, admin);
        Instant originalFrom = session.from();
        Instant originalUntil = session.until();

        Instant from = Instant.now().minus(Duration.ofHours(4));
        Instant until = Instant.now().minus(Duration.ofHours(1));
        var corrected = taskService.updateWorkSession(
                task.getId(), session.id(), from, until, "Ausstechen vergessen", admin);

        assertEquals(from, corrected.from());
        assertEquals(until, corrected.until());
        assertEquals(Duration.ofHours(3).toSeconds(), corrected.seconds());

        var correction = corrected.correction();
        assertNotNull(correction);
        assertEquals(TaskService.CorrectionKind.CORRECTED, correction.kind());
        assertEquals("admin", correction.correctedBy());
        assertEquals("Ausstechen vergessen", correction.reason());
        assertNotNull(correction.correctedAt());
        assertEquals(originalFrom, correction.originalFrom(), "Der Urzustand muss sichtbar bleiben");
        assertEquals(originalUntil, correction.originalUntil());
    }

    @Test
    @DisplayName("updateWorkSession: eine zweite Korrektur überschreibt den Urzustand nicht")
    void updateWorkSession_secondCorrectionKeepsFirstOriginal() {
        Task task = newTask();
        var session = trackedSession(task, admin);
        Instant originalFrom = session.from();

        taskService.updateWorkSession(task.getId(), session.id(),
                Instant.now().minus(Duration.ofHours(4)), Instant.now().minus(Duration.ofHours(1)),
                "erste Korrektur", admin);
        var twice = taskService.updateWorkSession(task.getId(), session.id(),
                Instant.now().minus(Duration.ofHours(5)), Instant.now().minus(Duration.ofHours(2)),
                "zweite Korrektur", admin);

        assertEquals(originalFrom, twice.correction().originalFrom(),
                "originalFrom bleibt der ursprünglich gestochene Wert");
        assertEquals("zweite Korrektur", twice.correction().reason());
    }

    @Test
    @DisplayName("updateWorkSession: ohne Grund, mit verdrehten oder zukünftigen Zeiten wirft IllegalArgumentException")
    void updateWorkSession_rejectsImplausibleInput() {
        Task task = newTask();
        var session = trackedSession(task, admin);
        Instant from = Instant.now().minus(Duration.ofHours(4));
        Instant until = Instant.now().minus(Duration.ofHours(1));

        assertThrows(IllegalArgumentException.class, () -> taskService.updateWorkSession(
                task.getId(), session.id(), from, until, "  ", admin), "Grund ist Pflicht");
        assertThrows(IllegalArgumentException.class, () -> taskService.updateWorkSession(
                task.getId(), session.id(), until, from, "verdreht", admin), "Ende vor Beginn");
        assertThrows(IllegalArgumentException.class, () -> taskService.updateWorkSession(
                task.getId(), session.id(), from, Instant.now().plus(Duration.ofHours(1)),
                "Zukunft", admin), "Ende in der Zukunft");
    }

    @Test
    @DisplayName("updateWorkSession: die laufende Session ist nicht direkt korrigierbar")
    void updateWorkSession_runningSessionRejected() {
        Task task = newTask();
        taskService.startTracking(task.getId(), admin);
        Long runningId = taskService.getWorkSessions(task.getId(), admin).get(0).id();

        assertThrows(IllegalStateException.class, () -> taskService.updateWorkSession(
                task.getId(), runningId, Instant.now().minus(Duration.ofHours(2)),
                Instant.now().minus(Duration.ofHours(1)), "geht nicht", admin));
    }

    @Test
    @DisplayName("addWorkSession: trägt eine nie gestochene Session nach und markiert sie als MANUAL_ENTRY")
    void addWorkSession_bookedByHand() {
        Task task = newTask();
        Instant from = Instant.now().minus(Duration.ofHours(6));
        Instant until = Instant.now().minus(Duration.ofHours(4));

        var added = taskService.addWorkSession(
                task.getId(), from, until, "Einstechen vergessen", null, admin);

        assertNotNull(added.id());
        assertEquals(Duration.ofHours(2).toSeconds(), added.seconds());
        assertFalse(added.running());
        assertEquals(TaskService.CorrectionKind.MANUAL_ENTRY, added.correction().kind());
        assertNull(added.correction().originalFrom(),
                "Es gab keinen aufgezeichneten Zustand, der zu bewahren wäre");
        assertEquals(1, taskService.getWorkSessions(task.getId(), admin).size());
    }

    @Test
    @DisplayName("addWorkSession: fremde Zeit nachtragen darf nur der Projektmanager")
    void addWorkSession_forSomeoneElseIsManagerOnly() {
        Task task = newTask();
        projectService.addMember(project.getId(), member.getId(), admin);
        Instant from = Instant.now().minus(Duration.ofHours(6));
        Instant until = Instant.now().minus(Duration.ofHours(4));

        assertThrows(AccessDeniedException.class, () -> taskService.addWorkSession(
                task.getId(), from, until, "für den Kollegen", admin.getId(), member));

        var forMember = taskService.addWorkSession(
                task.getId(), from, until, "für den Gesellen", member.getId(), admin);
        assertNotNull(forMember.id());
    }

    @Test
    @DisplayName("deleteWorkSession: entfernt die Fehlbuchung aus der Historie")
    void deleteWorkSession_removesSession() {
        Task task = newTask();
        var session = trackedSession(task, admin);

        taskService.deleteWorkSession(task.getId(), session.id(), admin);

        assertTrue(taskService.getWorkSessions(task.getId(), admin).isEmpty());
    }

    @Test
    @DisplayName("Korrekturrechte: ein Mitglied korrigiert die eigene Session, aber keine fremde")
    void correctionRights_ownSessionYesForeignNo() {
        Task task = newTask();
        projectService.addMember(project.getId(), member.getId(), admin);

        var adminSession = trackedSession(task, admin);
        var memberSession = trackedSession(task, member);
        Instant from = Instant.now().minus(Duration.ofHours(4));
        Instant until = Instant.now().minus(Duration.ofHours(1));

        var corrected = taskService.updateWorkSession(
                task.getId(), memberSession.id(), from, until, "selbst korrigiert", member);
        assertEquals(member.getUsername(), corrected.correction().correctedBy());
        assertEquals(from, corrected.from());

        assertThrows(AccessDeniedException.class, () -> taskService.updateWorkSession(
                task.getId(), adminSession.id(), from, until, "fremde Zeit", member));
        assertThrows(AccessDeniedException.class,
                () -> taskService.deleteWorkSession(task.getId(), adminSession.id(), member));
    }

    @Test
    @DisplayName("stopTrackingAt: beendet die laufende Session rückwirkend und vermerkt BACKDATED_STOP")
    void stopTrackingAt_closesRunningSessionRetroactively() {
        Task task = newTask();
        taskService.startTracking(task.getId(), admin);
        // Pretend the clock-in happened yesterday evening and nobody clocked out.
        taskRepository.findById(task.getId()).orElseThrow()
                .activeTimeSpanFor(admin).setDateFrom(Instant.now().minus(Duration.ofHours(14)));
        Instant until = Instant.now().minus(Duration.ofHours(6));

        Task stopped = taskService.stopTrackingAt(
                task.getId(), until, "gestern Abend vergessen", null, admin);

        assertFalse(stopped.isTracking());
        assertEquals(TaskStatus.STOPPED, stopped.getTaskStatus());
        var sessions = taskService.getWorkSessions(task.getId(), admin);
        assertEquals(1, sessions.size());
        var session = sessions.get(0);
        assertEquals(until, session.until());
        assertEquals(TaskService.CorrectionKind.BACKDATED_STOP, session.correction().kind());
        assertNotNull(session.correction().originalFrom());
        assertNull(session.correction().originalUntil(),
                "Die Session war offen — es gab kein Ende, das zu bewahren wäre");
        assertEquals("gestern Abend vergessen", session.correction().reason());
    }

    @Test
    @DisplayName("stopTrackingAt: Ende in der Zukunft oder vor dem Beginn wirft IllegalArgumentException")
    void stopTrackingAt_rejectsImplausibleEnd() {
        Task task = newTask();
        taskService.startTracking(task.getId(), admin);

        assertThrows(IllegalArgumentException.class, () -> taskService.stopTrackingAt(
                task.getId(), Instant.now().plus(Duration.ofHours(1)), "Zukunft", null, admin));
        assertThrows(IllegalArgumentException.class, () -> taskService.stopTrackingAt(
                task.getId(), Instant.now().minus(Duration.ofHours(1)), "vor dem Start", null, admin));
        assertThrows(IllegalArgumentException.class, () -> taskService.stopTrackingAt(
                task.getId(), Instant.now(), "  ", null, admin));
    }

    @Test
    @DisplayName("stopTrackingAt: ohne laufendes Tracking wirft IllegalStateException")
    void stopTrackingAt_whenIdle_throws() {
        Task task = newTask();
        assertThrows(IllegalStateException.class, () -> taskService.stopTrackingAt(
                task.getId(), Instant.now(), "nichts läuft", null, admin));
    }
    // --- Parallel clocks: several people on one task ---

    @Test
    @DisplayName("Zwei Personen am selben Task: die zweite Uhr stoppt die erste nicht")
    void parallelClocks_secondPersonDoesNotStopTheFirst() {
        projectService.addMember(project.getId(), member.getId(), admin);
        Task task = newTask();

        taskService.startTracking(task.getId(), admin);
        taskService.startTracking(task.getId(), member);

        Task both = taskRepository.findById(task.getId()).orElseThrow();
        assertTrue(both.isTrackingFor(admin), "die Uhr des Ersten läuft weiter");
        assertTrue(both.isTrackingFor(member), "der Zweite bekommt eine eigene Uhr");
        assertEquals(2, taskService.getTrackingSummary(task.getId(), admin).runningCount());
    }

    @Test
    @DisplayName("Der Aufwand pro Task summiert alle parallelen Uhren, nicht nur eine")
    void parallelClocks_taskTotalSumsEveryOpenSpan() {
        projectService.addMember(project.getId(), member.getId(), admin);
        Task task = newTask();
        taskService.startTracking(task.getId(), admin);
        taskService.startTracking(task.getId(), member);

        // Beide sind seit zwei Stunden eingestochen.
        Instant twoHoursAgo = Instant.now().minus(Duration.ofHours(2));
        Task persisted = taskRepository.findById(task.getId()).orElseThrow();
        persisted.activeTimeSpanFor(admin).setDateFrom(twoHoursAgo);
        persisted.activeTimeSpanFor(member).setDateFrom(twoHoursAgo);

        long total = taskService.getTrackingSummary(task.getId(), admin).totalSeconds();

        // Genau der Fehler, der parallele Uhren erzwungen hat: mit einer einzigen Uhr stünden hier
        // 2 h, obwohl 4 h gearbeitet wurden — der Aufwand pro Task wäre falsch, ganz ohne dass
        // irgendjemand eine Auswertung pro Person wollte.
        assertEquals(4 * 3600, total, 60, "zwei Leute mal zwei Stunden sind vier Stunden am Task");
    }

    @Test
    @DisplayName("Ausstechen beendet nur die eigene Uhr; der Task bleibt STARTED solange jemand arbeitet")
    void parallelClocks_stoppingOneLeavesTheOthersRunning() {
        projectService.addMember(project.getId(), member.getId(), admin);
        Task task = newTask();
        taskService.startTracking(task.getId(), admin);
        taskService.startTracking(task.getId(), member);

        taskService.stopTracking(task.getId(), member);

        Task after = taskRepository.findById(task.getId()).orElseThrow();
        assertTrue(after.isTrackingFor(admin), "der Kollege arbeitet weiter");
        assertFalse(after.isTrackingFor(member), "die eigene Uhr steht");
        assertEquals(TaskStatus.STARTED, after.getTaskStatus(),
                "der Task ist erst STOPPED, wenn die letzte Uhr aus ist");

        taskService.stopTracking(task.getId(), admin);
        Task idle = taskRepository.findById(task.getId()).orElseThrow();
        assertFalse(idle.isTracking());
        assertEquals(TaskStatus.STOPPED, idle.getTaskStatus());
        assertEquals(2, taskService.getWorkSessions(task.getId(), admin).size(),
                "beide Sitzungen bleiben getrennt erhalten");
    }

    @Test
    @DisplayName("Einstechen scheitert nur an der eigenen laufenden Uhr, nicht an der des Kollegen")
    void parallelClocks_ownClockBlocksStartButColleaguesDoNot() {
        projectService.addMember(project.getId(), member.getId(), admin);
        Task task = newTask();
        taskService.startTracking(task.getId(), admin);

        assertDoesNotThrow(() -> taskService.startTracking(task.getId(), member));
        assertThrows(IllegalStateException.class, () -> taskService.startTracking(task.getId(), admin));
    }

    /**
     * Seit die Uhren pro Person laufen, liegen in einem Task die Sitzungen mehrerer Leute nebeneinander.
     * Eine Liste von Intervallen, die nicht sagt, wem sie gehören, kann niemand anzeigen und niemand
     * korrigieren — die Korrektur-Endpunkte entscheiden ohnehin nach Beteiligung, also muss der Aufrufer
     * dieselbe Auskunft bekommen.
     */
    @Test
    @DisplayName("Jede Sitzung nennt die Person, zu der sie gehört")
    void workSessions_nameTheirOwner() {
        projectService.addMember(project.getId(), member.getId(), admin);
        Task task = newTask();
        taskService.startTracking(task.getId(), admin);
        taskService.startTracking(task.getId(), member);
        taskService.stopTracking(task.getId(), member);

        var sessions = taskService.getWorkSessions(task.getId(), admin);
        assertEquals(2, sessions.size());

        var mine = sessions.stream().filter(s -> admin.getId().equals(s.userId())).findFirst().orElseThrow(
                () -> new AssertionError("die eigene Sitzung muss über die userId auffindbar sein"));
        assertEquals(admin.getUsername(), mine.username());
        assertTrue(mine.running(), "die eigene Uhr läuft noch");

        var his = sessions.stream().filter(s -> member.getId().equals(s.userId())).findFirst().orElseThrow(
                () -> new AssertionError("die Sitzung des Kollegen muss ebenso zuzuordnen sein"));
        assertEquals(member.getUsername(), his.username());
        assertFalse(his.running(), "der Kollege hat ausgestochen");
    }

    /** Eine nachgetragene fremde Sitzung gehört dem Beschäftigten, nicht dem, der sie eingebucht hat. */
    @Test
    @DisplayName("Eine nachgetragene Sitzung gehört dem, für den gebucht wurde")
    void workSessions_bookedForSomeoneElseBelongToThem() {
        projectService.addMember(project.getId(), member.getId(), admin);
        Task task = newTask();
        Instant from = Instant.now().minus(Duration.ofHours(3));
        Instant until = Instant.now().minus(Duration.ofHours(1));

        taskService.addWorkSession(task.getId(), from, until, "Zettel vom Montag", member.getId(), admin);

        var session = taskService.getWorkSessions(task.getId(), admin).get(0);
        assertEquals(member.getId(), session.userId(), "gearbeitet hat der Geselle");
        assertEquals(member.getUsername(), session.username());
        assertEquals(admin.getId(), session.correction().correctedById(), "gebucht hat der Meister");
        assertEquals(admin.getUsername(), session.correction().correctedBy());
    }

    // --- Equipment usage: Gerätezeit ---

    @Test
    @DisplayName("startEquipmentUsage: öffnet eine Gerätebuchung, ohne die Arbeitszeit zu berühren")
    void startEquipmentUsage_opensBookingWithoutTouchingWorkTime() {
        Task task = newTask();
        Resource lift = newResource("Hubarbeitsbühne");

        Task booked = taskService.startEquipmentUsage(task.getId(), lift.getId(), admin);

        assertTrue(booked.isEquipmentRunningFor(lift));
        assertEquals(1, booked.getEquipmentRunningCount());
        assertEquals(0, booked.getRunningCount(), "niemand steht am Task, nur das Gerät");
        assertFalse(booked.isTracking());
        assertTrue(booked.getTimeSpent().isEmpty());
    }

    @Test
    @DisplayName("Ein eingestochenes Gerät setzt den Task NICHT auf STARTED — Maschinen arbeiten nicht")
    void startEquipmentUsage_leavesTaskStatusAlone() {
        Task task = newTask();
        Resource dryer = newResource("Bautrockner");

        Task booked = taskService.startEquipmentUsage(task.getId(), dryer.getId(), admin);

        assertEquals(TaskStatus.CREATED, booked.getTaskStatus());
    }

    @Test
    @DisplayName("startEquipmentUsage: dasselbe Gerät zweimal am selben Task wirft IllegalStateException")
    void startEquipmentUsage_twiceOnSameTask_throws() {
        Task task = newTask();
        Resource lift = newResource("Hubarbeitsbühne");
        taskService.startEquipmentUsage(task.getId(), lift.getId(), admin);

        assertThrows(IllegalStateException.class,
                () -> taskService.startEquipmentUsage(task.getId(), lift.getId(), admin));
    }

    /**
     * Der Kern der Exklusivität: ein Gerät steht an einem Ort. Läuft die Uhr noch auf der letzten
     * Baustelle, hat jemand das Ausstechen vergessen — das muss auffallen, nicht still umbuchen.
     */
    @Test
    @DisplayName("Ein Gerät kann nicht auf zwei Tasks gleichzeitig laufen und die Meldung nennt den anderen Task")
    void startEquipmentUsage_whileRunningElsewhere_throwsAndNamesOtherTask() {
        Task kita = newTask();
        Task pv = newTask();
        Resource lift = newResource("Hubarbeitsbühne");
        taskService.startEquipmentUsage(kita.getId(), lift.getId(), admin);

        var boom = assertThrows(IllegalStateException.class,
                () -> taskService.startEquipmentUsage(pv.getId(), lift.getId(), admin));

        assertTrue(boom.getMessage().contains(kita.getId().toString()),
                "die Meldung muss sagen, wo das Gerät noch eingestochen ist: " + boom.getMessage());
        assertTrue(taskRepository.findById(kita.getId()).orElseThrow().isEquipmentRunningFor(lift),
                "die erste Buchung bleibt unangetastet");
    }

    @Test
    @DisplayName("stopEquipmentUsage: schließt die Buchung und schreibt sie in die Gerätehistorie")
    void stopEquipmentUsage_closesAndArchives() {
        Task task = newTask();
        Resource lift = newResource("Hubarbeitsbühne");
        taskService.startEquipmentUsage(task.getId(), lift.getId(), admin);

        Task stopped = taskService.stopEquipmentUsage(task.getId(), lift.getId(), admin);

        assertFalse(stopped.isEquipmentRunningFor(lift));
        assertEquals(0, stopped.getEquipmentRunningCount());
        assertEquals(1, stopped.getEquipmentUsage().size());
        assertNotNull(stopped.getEquipmentUsage().get(0).getDateUntil());
    }

    @Test
    @DisplayName("stopEquipmentUsage: ohne laufende Buchung wirft IllegalStateException")
    void stopEquipmentUsage_whenNotRunning_throws() {
        Task task = newTask();
        Resource lift = newResource("Hubarbeitsbühne");

        assertThrows(IllegalStateException.class,
                () -> taskService.stopEquipmentUsage(task.getId(), lift.getId(), admin));
    }

    @Test
    @DisplayName("toggleEquipmentUsage: sticht abwechselnd ein und aus — der Scan am Aufkleber")
    void toggleEquipmentUsage_alternates() {
        Task task = newTask();
        Resource lift = newResource("Hubarbeitsbühne");

        assertTrue(taskService.toggleEquipmentUsage(task.getId(), lift.getId(), admin)
                .isEquipmentRunningFor(lift));
        assertFalse(taskService.toggleEquipmentUsage(task.getId(), lift.getId(), admin)
                .isEquipmentRunningFor(lift));
        assertTrue(taskService.toggleEquipmentUsage(task.getId(), lift.getId(), admin)
                .isEquipmentRunningFor(lift));
    }

    /**
     * Der eigentliche Designpunkt dieses Slices: 4 h Arbeit und 120 h Trockner sind nicht 124 h
     * Aufwand. Die Gerätezeit darf in keiner Arbeitszeit-Auswertung auftauchen.
     */
    @Test
    @DisplayName("Gerätezeit zählt NICHT zur Arbeitszeit — weder in der Summe noch in den Sessions")
    void equipmentTime_staysOutOfWorkTime() {
        Task task = newTask();
        Resource dryer = newResource("Bautrockner");
        taskService.startTracking(task.getId(), admin);
        taskService.startEquipmentUsage(task.getId(), dryer.getId(), admin);
        // Das Gerät läuft seit gestern, gearbeitet wurde eben erst.
        taskRepository.findById(task.getId()).orElseThrow()
                .activeEquipmentSpanFor(dryer).setDateFrom(Instant.now().minus(Duration.ofHours(24)));

        var work = taskService.getTrackingSummary(task.getId(), admin);
        assertTrue(work.totalSeconds() < Duration.ofHours(1).getSeconds(),
                "die 24 Gerätestunden dürfen nicht in der Arbeitszeit landen: " + work.totalSeconds());
        assertEquals(1, work.runningCount(), "das Gerät ist keine Person");
        assertEquals(1, taskService.getWorkSessions(task.getId(), admin).size(),
                "die Gerätebuchung ist keine Arbeitssitzung");
        assertEquals(1, taskService.getEquipmentSessions(task.getId(), admin).size());
    }

    /**
     * Die Mannschaft geht heim, der Trockner läuft weiter: der Task ist fertig für heute, das Gerät
     * hält ihn nicht offen.
     */
    @Test
    @DisplayName("Der Task geht auf STOPPED, während das Gerät weiterläuft")
    void taskStopsWhileEquipmentKeepsRunning() {
        Task task = newTask();
        Resource dryer = newResource("Bautrockner");
        taskService.startTracking(task.getId(), admin);
        taskService.startEquipmentUsage(task.getId(), dryer.getId(), admin);

        Task afterKnockOff = taskService.stopTracking(task.getId(), admin);

        assertEquals(TaskStatus.STOPPED, afterKnockOff.getTaskStatus());
        assertTrue(afterKnockOff.isEquipmentRunningFor(dryer), "der Trockner läuft weiter");
    }

    @Test
    @DisplayName("getEquipmentUsage: summiert je Gerät und wirft verschiedene Geräte nicht zusammen")
    void getEquipmentUsage_sumsPerResource() {
        Task task = newTask();
        Resource lift = newResource("Hubarbeitsbühne");
        Resource dryer = newResource("Bautrockner");
        // Die Bühne war zweimal da, der Trockner läuft noch.
        taskService.startEquipmentUsage(task.getId(), lift.getId(), admin);
        taskService.stopEquipmentUsage(task.getId(), lift.getId(), admin);
        taskService.startEquipmentUsage(task.getId(), lift.getId(), admin);
        taskService.stopEquipmentUsage(task.getId(), lift.getId(), admin);
        taskService.startEquipmentUsage(task.getId(), dryer.getId(), admin);

        var usage = taskService.getEquipmentUsage(task.getId(), admin);

        assertEquals(2, usage.size(), "zwei Geräte, zwei Zeilen — nicht drei Buchungen");
        var liftUsage = usage.stream()
                .filter(u -> u.resourceId().equals(lift.getId())).findFirst().orElseThrow();
        var dryerUsage = usage.stream()
                .filter(u -> u.resourceId().equals(dryer.getId())).findFirst().orElseThrow();
        assertFalse(liftUsage.running(), "die Bühne ist weg");
        assertNull(liftUsage.runningSince());
        assertTrue(dryerUsage.running(), "der Trockner steht noch da");
        assertNotNull(dryerUsage.runningSince());
        assertEquals("Bautrockner", dryerUsage.resourceName());
    }

    @Test
    @DisplayName("stopEquipmentUsageAt: sticht rückwirkend aus und vermerkt BACKDATED_STOP")
    void stopEquipmentUsageAt_closesRetroactively() {
        Task task = newTask();
        Resource lift = newResource("Hubarbeitsbühne");
        taskService.startEquipmentUsage(task.getId(), lift.getId(), admin);
        // Abgeholt wurde die Bühne gestern, gescannt hat sie niemand mehr.
        taskRepository.findById(task.getId()).orElseThrow()
                .activeEquipmentSpanFor(lift).setDateFrom(Instant.now().minus(Duration.ofHours(30)));
        Instant until = Instant.now().minus(Duration.ofHours(6));

        Task stopped = taskService.stopEquipmentUsageAt(
                task.getId(), lift.getId(), until, "Abholung nicht gescannt", admin);

        assertFalse(stopped.isEquipmentRunningFor(lift));
        var booking = taskService.getEquipmentSessions(task.getId(), admin).get(0);
        assertEquals(until, booking.until());
        assertEquals(TaskService.CorrectionKind.BACKDATED_STOP, booking.correction().kind());
        assertEquals("Abholung nicht gescannt", booking.correction().reason());
        assertEquals(admin.getId(), booking.correction().correctedById());
    }

    @Test
    @DisplayName("stopEquipmentUsageAt: unplausibles Ende oder fehlender Grund wirft IllegalArgumentException")
    void stopEquipmentUsageAt_rejectsImplausibleInput() {
        Task task = newTask();
        Resource lift = newResource("Hubarbeitsbühne");
        taskService.startEquipmentUsage(task.getId(), lift.getId(), admin);

        assertThrows(IllegalArgumentException.class, () -> taskService.stopEquipmentUsageAt(
                task.getId(), lift.getId(), Instant.now().plus(Duration.ofHours(1)), "Zukunft", admin));
        assertThrows(IllegalArgumentException.class, () -> taskService.stopEquipmentUsageAt(
                task.getId(), lift.getId(), Instant.now().minus(Duration.ofHours(1)), "vor dem Start", admin));
        assertThrows(IllegalArgumentException.class, () -> taskService.stopEquipmentUsageAt(
                task.getId(), lift.getId(), Instant.now(), "  ", admin));
    }

    // --- Dauer × Satz ---

    /** Mietgeräte werden nach angefangenem Tag abgerechnet — 30 h sind zwei Tage, nicht 1,25. */
    @Test
    @DisplayName("Kosten DAY: 30 Stunden sind zwei angefangene Tage")
    void equipmentCost_dayRateBillsStartedDays() {
        Task task = newTask();
        Resource lift = ratedResource("Hubarbeitsbühne", "89.00", CostRateUnit.DAY);
        bookEquipment(task, lift, Duration.ofHours(30));

        var line = taskService.getEquipmentCost(task.getId(), admin).lines().get(0);

        assertEquals(0, new BigDecimal("2").compareTo(line.billedUnits()), "zwei angefangene Tage");
        assertEquals(0, new BigDecimal("178.00").compareTo(line.amount()));
        assertEquals("EUR", line.currency());
    }

    @Test
    @DisplayName("Kosten DAY: auch ein kurzer Einsatz kostet einen angefangenen Tag, nie null")
    void equipmentCost_dayRateNeverBillsZeroDays() {
        Task task = newTask();
        Resource lift = ratedResource("Hubarbeitsbühne", "89.00", CostRateUnit.DAY);
        bookEquipment(task, lift, Duration.ofMinutes(20));

        var line = taskService.getEquipmentCost(task.getId(), admin).lines().get(0);

        assertEquals(0, new BigDecimal("1").compareTo(line.billedUnits()));
        assertEquals(0, new BigDecimal("89.00").compareTo(line.amount()));
    }

    /** Ein Stundensatz misst Nutzung und wird anteilig berechnet — 90 min zu 12,00 sind 18,00. */
    @Test
    @DisplayName("Kosten HOUR: anteilig, nicht auf volle Stunden aufgerundet")
    void equipmentCost_hourRateIsFractional() {
        Task task = newTask();
        Resource drill = ratedResource("Kernbohrmaschine", "12.00", CostRateUnit.HOUR);
        bookEquipment(task, drill, Duration.ofMinutes(90));

        var line = taskService.getEquipmentCost(task.getId(), admin).lines().get(0);

        assertEquals(0, new BigDecimal("18.00").compareTo(line.amount()));
        assertEquals(1, line.bookings());
    }

    @Test
    @DisplayName("Kosten USAGE: zählt Einsätze, nicht Stunden")
    void equipmentCost_usageRateCountsBookings() {
        Task task = newTask();
        Resource tester = ratedResource("Isolationsmessgerät", "7.50", CostRateUnit.USAGE);
        bookEquipment(task, tester, Duration.ofHours(3));
        bookEquipment(task, tester, Duration.ofHours(9));

        var line = taskService.getEquipmentCost(task.getId(), admin).lines().get(0);

        assertEquals(2, line.bookings());
        assertEquals(0, new BigDecimal("15.00").compareTo(line.amount()),
                "zwei Einsätze zu 7,50 — die 12 Stunden sind egal");
    }

    /**
     * Ohne Satz ist der Betrag {@code null} und die Summe ausdrücklich unvollständig. {@code 0} wäre
     * die Behauptung, das Gerät sei kostenlos — eine andere Aussage als „nicht erfasst".
     */
    @Test
    @DisplayName("Kosten: Gerät ohne Satz liefert null statt 0 und markiert die Summe als unvollständig")
    void equipmentCost_unratedDeviceIsNullNotZero() {
        Task task = newTask();
        Resource lift = ratedResource("Hubarbeitsbühne", "89.00", CostRateUnit.DAY);
        Resource ladder = newResource("Anlegeleiter");
        bookEquipment(task, lift, Duration.ofHours(2));
        bookEquipment(task, ladder, Duration.ofHours(2));

        var report = taskService.getEquipmentCost(task.getId(), admin);

        assertTrue(report.ratesMissing(), "die Leiter hat keinen Satz");
        var ladderLine = report.lines().stream()
                .filter(l -> l.resourceId().equals(ladder.getId())).findFirst().orElseThrow();
        assertNull(ladderLine.amount(), "kein Satz heißt nicht kostenlos");
        assertEquals(Duration.ofHours(2).getSeconds(), ladderLine.totalSeconds(),
                "die Zeit ist trotzdem erfasst");
        assertEquals(1, report.totals().size());
        assertEquals(0, new BigDecimal("89.00").compareTo(report.totals().get(0).amount()));
    }

    /** Euro und Franken zu addieren ergäbe eine Zahl, die in beiden Währungen falsch ist. */
    @Test
    @DisplayName("Kosten: zwei Währungen ergeben zwei Summen, keine Gesamtsumme")
    void equipmentCost_neverSumsAcrossCurrencies() {
        Task task = newTask();
        Resource lift = ratedResource("Hubarbeitsbühne", "89.00", CostRateUnit.DAY);
        Resource crane = ratedResource("Kran", "100.00", CostRateUnit.DAY);
        crane.setCostCurrency("CHF");
        bookEquipment(task, lift, Duration.ofHours(2));
        bookEquipment(task, crane, Duration.ofHours(2));

        var totals = taskService.getEquipmentCost(task.getId(), admin).totals();

        assertEquals(2, totals.size());
        assertEquals(0, new BigDecimal("89.00").compareTo(totals.stream()
                .filter(t -> t.currency().equals("EUR")).findFirst().orElseThrow().amount()));
        assertEquals(0, new BigDecimal("100.00").compareTo(totals.stream()
                .filter(t -> t.currency().equals("CHF")).findFirst().orElseThrow().amount()));
    }

    /**
     * Mehrere Einsätze eines Geräts sind zusammen zu betrachten: 3 × 8 h an einem Tagessatz ist ein
     * Tag, nicht drei — sonst kostet Zwischendurch-Abholen mehr als Dastehenlassen.
     */
    @Test
    @DisplayName("Kosten DAY: mehrere Einsätze eines Geräts werden zusammengezählt, nicht je Einsatz gerundet")
    void equipmentCost_severalBookingsOfOneDeviceAreFoldedFirst() {
        Task task = newTask();
        Resource lift = ratedResource("Hubarbeitsbühne", "89.00", CostRateUnit.DAY);
        // Bewusst 3 × 7 h statt 3 × 8 h: bei exakt 24 h läge der Test auf der Rundungsgrenze und die
        // Millisekunden zwischen Ein- und Ausstechen würden über ein oder zwei Tage entscheiden.
        bookEquipment(task, lift, Duration.ofHours(7));
        bookEquipment(task, lift, Duration.ofHours(7));
        bookEquipment(task, lift, Duration.ofHours(7));

        var line = taskService.getEquipmentCost(task.getId(), admin).lines().get(0);

        assertEquals(3, line.bookings());
        assertEquals(0, new BigDecimal("1").compareTo(line.billedUnits()), "21 h sind ein Tag");
        assertEquals(0, new BigDecimal("89.00").compareTo(line.amount()));
    }

    @Test
    @DisplayName("Kosten: ohne Gerätebuchung ist der Bericht leer, nicht null")
    void equipmentCost_emptyWhenNothingWasBooked() {
        Task task = newTask();
        var report = taskService.getEquipmentCost(task.getId(), admin);

        assertTrue(report.lines().isEmpty());
        assertTrue(report.totals().isEmpty());
        assertFalse(report.ratesMissing());
    }

    @Test
    @DisplayName("getEquipmentSessions: jede Buchung nennt ihr Gerät")
    void getEquipmentSessions_nameTheirDevice() {
        Task task = newTask();
        Resource lift = newResource("Hubarbeitsbühne");
        taskService.startEquipmentUsage(task.getId(), lift.getId(), admin);

        var booking = taskService.getEquipmentSessions(task.getId(), admin).get(0);

        assertEquals(lift.getId(), booking.resourceId());
        assertEquals("Hubarbeitsbühne", booking.resourceName());
        assertTrue(booking.running());
        assertNull(booking.until(), "die laufende Buchung hat noch kein Ende");
    }
}
