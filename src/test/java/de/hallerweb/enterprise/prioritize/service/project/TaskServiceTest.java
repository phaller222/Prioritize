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

import de.hallerweb.enterprise.prioritize.model.project.Project;
import de.hallerweb.enterprise.prioritize.model.project.Task;
import de.hallerweb.enterprise.prioritize.model.project.TaskStatus;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.project.TaskRepository;
import de.hallerweb.enterprise.prioritize.service.project.ProjectService.ProjectData;
import de.hallerweb.enterprise.prioritize.service.project.TaskService.TaskData;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

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
        assertTrue(summary.tracking());
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

        assertFalse(summary.tracking());
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
                .getActiveTimeSpan().setDateFrom(Instant.now().minus(Duration.ofHours(14)));
        Instant until = Instant.now().minus(Duration.ofHours(6));

        Task stopped = taskService.stopTrackingAt(
                task.getId(), until, "gestern Abend vergessen", admin);

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
                task.getId(), Instant.now().plus(Duration.ofHours(1)), "Zukunft", admin));
        assertThrows(IllegalArgumentException.class, () -> taskService.stopTrackingAt(
                task.getId(), Instant.now().minus(Duration.ofHours(1)), "vor dem Start", admin));
        assertThrows(IllegalArgumentException.class, () -> taskService.stopTrackingAt(
                task.getId(), Instant.now(), "  ", admin));
    }

    @Test
    @DisplayName("stopTrackingAt: ohne laufendes Tracking wirft IllegalStateException")
    void stopTrackingAt_whenIdle_throws() {
        Task task = newTask();
        assertThrows(IllegalStateException.class, () -> taskService.stopTrackingAt(
                task.getId(), Instant.now(), "nichts läuft", admin));
    }
}
