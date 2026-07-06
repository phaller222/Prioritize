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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

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
    private Project project;

    @BeforeEach
    void setUp() {
        admin = userService.findUserByUsername("admin");
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
}
