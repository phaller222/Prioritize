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
import de.hallerweb.enterprise.prioritize.model.project.goal.ProjectGoal;
import de.hallerweb.enterprise.prioritize.model.project.goal.ProjectGoalProperty;
import de.hallerweb.enterprise.prioritize.model.project.goal.ProjectGoalPropertyNumeric;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.project.TaskRepository;
import de.hallerweb.enterprise.prioritize.service.project.ProjectGoalService.GoalData;
import de.hallerweb.enterprise.prioritize.service.project.ProjectGoalService.ProjectProgress;
import de.hallerweb.enterprise.prioritize.service.project.ProjectService.ProjectData;
import de.hallerweb.enterprise.prioritize.service.project.TaskService.TaskData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
class ProjectGoalServiceTest {

    @Autowired
    private ProjectGoalService projectGoalService;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private de.hallerweb.enterprise.prioritize.service.security.UserService userService;

    private PUser admin;
    private PUser outsider;
    private Project project;

    @BeforeEach
    void setUp() {
        admin = userService.findUserByUsername("admin");
        outsider = userService.createUser(PUser.builder()
                .username("goal-outsider-" + System.nanoTime())
                .name("Outsider")
                .firstname("Olga")
                .email("olga@example.com")
                .password("plaintext123")
                .admin(false)
                .build());
        project = projectService.createProject(
                new ProjectData("Apollo", "Moon project", 5,
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 100),
                admin);
    }

    private ProjectGoal newGoal(String name) {
        return projectGoalService.createGoal(project.getId(),
                new GoalData(name, "desc", null), admin);
    }

    private Task newTaskForGoal(ProjectGoal goal) {
        Task task = taskService.createTask(project.getId(),
                new TaskData("t-" + System.nanoTime(), "d", 1), admin);
        taskService.assignGoal(task.getId(), goal.getId(), admin);
        return task;
    }

    @Test
    @DisplayName("createGoal: legt Ziel mit Property an (nur Manager)")
    void createGoal_withProperty_persists() {
        ProjectGoalPropertyNumeric prop = new ProjectGoalPropertyNumeric();
        prop.setName("temperature");
        prop.setMin(0);
        prop.setMax(10);
        List<ProjectGoalProperty> props = List.of(prop);

        ProjectGoal goal = projectGoalService.createGoal(project.getId(),
                new GoalData("Cool it", "keep cool", props), admin);

        assertNotNull(goal.getId());
        assertEquals(1, goal.getProperties().size());
        assertEquals("temperature", goal.getProperties().get(0).getName());

        assertTrue(projectGoalService.getGoals(project.getId(), admin).stream()
                .anyMatch(g -> g.getId().equals(goal.getId())));
    }

    @Test
    @DisplayName("createGoal: Nicht-Manager erhält AccessDeniedException")
    void createGoal_asNonManager_throwsAccessDenied() {
        assertThrows(AccessDeniedException.class, () -> projectGoalService.createGoal(
                project.getId(), new GoalData("x", "y", null), outsider));
    }

    @Test
    @DisplayName("updateGoal: ersetzt die Properties")
    void updateGoal_replacesProperties() {
        ProjectGoalPropertyNumeric prop = new ProjectGoalPropertyNumeric();
        prop.setName("old");
        ProjectGoal goal = projectGoalService.createGoal(project.getId(),
                new GoalData("g", "d", List.of(prop)), admin);

        ProjectGoalPropertyNumeric replacement = new ProjectGoalPropertyNumeric();
        replacement.setName("new");
        ProjectGoal updated = projectGoalService.updateGoal(project.getId(), goal.getId(),
                new GoalData("g2", "d2", List.of(replacement)), admin);

        assertEquals("g2", updated.getName());
        assertEquals(1, updated.getProperties().size());
        assertEquals("new", updated.getProperties().get(0).getName());
    }

    @Test
    @DisplayName("deleteGoal: löst zugeordnete Tasks vom Ziel (kein FK-Bruch)")
    void deleteGoal_detachesTasks() {
        ProjectGoal goal = newGoal("g");
        Task task = newTaskForGoal(goal);

        projectGoalService.deleteGoal(project.getId(), goal.getId(), admin);

        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertNull(reloaded.getGoal(), "Task must be detached from the deleted goal");
        assertTrue(projectGoalService.getGoals(project.getId(), admin).stream()
                .noneMatch(g -> g.getId().equals(goal.getId())));
    }

    @Test
    @DisplayName("computeProgress: ohne Ziele ist der Fortschritt n/a (null)")
    void computeProgress_noGoals_isNull() {
        ProjectProgress progress = projectGoalService.computeProgress(project.getId(), admin);
        assertNull(progress.overallPercentage());
        assertTrue(progress.goals().isEmpty());
    }

    @Test
    @DisplayName("computeProgress: Ziel ohne (zählende) Tasks ist n/a")
    void computeProgress_goalWithoutTasks_isNull() {
        ProjectGoal goal = newGoal("g");
        ProjectProgress progress = projectGoalService.computeProgress(project.getId(), admin);
        assertEquals(1, progress.goals().size());
        assertNull(progress.goals().get(0).percentage());
        assertNull(progress.overallPercentage(), "no goal has counting tasks");
    }

    @Test
    @DisplayName("computeProgress: erledigte/offene Tasks ergeben Prozent, CANCELLED zählt nicht")
    void computeProgress_derivesFromTaskStatus() {
        ProjectGoal goal = newGoal("g");
        Task done = newTaskForGoal(goal);
        Task open = newTaskForGoal(goal);
        Task cancelled = newTaskForGoal(goal);

        taskService.changeStatus(done.getId(), TaskStatus.FINISHED, admin);
        // 'open' stays in its initial (non-terminal) status
        taskService.changeStatus(cancelled.getId(), TaskStatus.CANCELLED, admin);

        ProjectProgress progress = projectGoalService.computeProgress(project.getId(), admin);
        // counting tasks = {done, open}; cancelled excluded -> 1 of 2 done = 50%
        assertEquals(50, progress.goals().get(0).percentage());
        assertEquals(50, progress.overallPercentage());
    }

    @Test
    @DisplayName("computeProgress: Task ohne Ziel beeinflusst den Fortschritt nicht")
    void computeProgress_taskWithoutGoal_ignored() {
        ProjectGoal goal = newGoal("g");
        Task done = newTaskForGoal(goal);
        taskService.changeStatus(done.getId(), TaskStatus.FINISHED, admin);
        // a second task without any goal
        taskService.createTask(project.getId(), new TaskData("free", "d", 1), admin);

        ProjectProgress progress = projectGoalService.computeProgress(project.getId(), admin);
        assertEquals(100, progress.goals().get(0).percentage());
        assertEquals(100, progress.overallPercentage());
    }

    @Test
    @DisplayName("assignGoal: fremdes Ziel (anderes Projekt) wird abgelehnt")
    void assignGoal_foreignGoal_rejected() {
        ProjectGoal goal = newGoal("g");
        Project other = projectService.createProject(
                new ProjectData("Gemini", "d", 1, null, null, 10), admin);
        Task task = taskService.createTask(other.getId(), new TaskData("t", "d", 1), admin);

        assertThrows(java.util.NoSuchElementException.class,
                () -> taskService.assignGoal(task.getId(), goal.getId(), admin));
    }
}
