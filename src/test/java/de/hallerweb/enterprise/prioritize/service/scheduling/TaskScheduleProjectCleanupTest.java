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

package de.hallerweb.enterprise.prioritize.service.scheduling;

import de.hallerweb.enterprise.prioritize.dto.scheduling.TaskScheduleRequest;
import de.hallerweb.enterprise.prioritize.model.project.Project;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.scheduling.TaskScheduleRepository;
import de.hallerweb.enterprise.prioritize.service.project.ProjectService;
import de.hallerweb.enterprise.prioritize.service.project.ProjectService.ProjectData;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that deleting a project takes its task schedules with it. A schedule is not reachable from
 * {@code Project}, so nothing cascades to it and the leftover row used to break the delete on the foreign
 * key — surfacing as a misleading 403 rather than a conflict. Runs against the real database because the
 * point of the test is exactly that constraint.
 *
 * @author peter haller
 */
@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
class TaskScheduleProjectCleanupTest {

    @Autowired
    private ProjectService projectService;
    @Autowired
    private TaskScheduleService scheduleService;
    @Autowired
    private TaskScheduleRepository scheduleRepository;
    @Autowired
    private UserService userService;

    private PUser admin;

    @BeforeEach
    void setUp() {
        admin = userService.findUserByUsername("admin");
    }

    private Project newProjectWithSchedule() {
        Project project = projectService.createProject(
                new ProjectData("Cleanup probe", "has a schedule", 1,
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 10),
                admin);
        scheduleService.createSchedule(project.getId(),
                new TaskScheduleRequest("Nightly", "Generate report", "auto", 1,
                        "0 0 8 * * *", "Europe/Berlin", true),
                admin);
        return project;
    }

    @Test
    @DisplayName("deleteProject: entfernt die Task-Schedules des Projekts mit")
    void deleteProject_removesItsSchedules() {
        Project project = newProjectWithSchedule();
        Long projectId = project.getId();
        assertFalse(scheduleRepository.findByProject_Id(projectId).isEmpty(),
                "precondition: the project has a schedule");

        assertDoesNotThrow(() -> projectService.deleteProject(projectId, admin),
                "a project with schedules must be deletable");

        assertTrue(scheduleRepository.findByProject_Id(projectId).isEmpty(),
                "the schedules must be gone with the project");
    }

    @Test
    @DisplayName("deleteProject: lässt die Schedules anderer Projekte unangetastet")
    void deleteProject_keepsOtherProjectsSchedules() {
        Project doomed = newProjectWithSchedule();
        Project survivor = newProjectWithSchedule();

        projectService.deleteProject(doomed.getId(), admin);

        assertTrue(scheduleRepository.findByProject_Id(doomed.getId()).isEmpty());
        assertFalse(scheduleRepository.findByProject_Id(survivor.getId()).isEmpty(),
                "only the deleted project's schedules may be removed");
    }
}
