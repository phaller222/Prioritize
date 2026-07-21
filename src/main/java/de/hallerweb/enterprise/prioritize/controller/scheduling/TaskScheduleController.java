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

package de.hallerweb.enterprise.prioritize.controller.scheduling;

import de.hallerweb.enterprise.prioritize.config.CurrentUserResolver;
import de.hallerweb.enterprise.prioritize.dto.scheduling.TaskScheduleDTO;
import de.hallerweb.enterprise.prioritize.dto.scheduling.TaskScheduleRequest;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.scheduling.TaskScheduleService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for recurring task schedules ({@link TaskScheduleService}). A schedule belongs to a project
 * (it generates tasks on its blackboard), so creation and listing are nested under the project; a
 * schedule is then addressed by its own id. Authorization is enforced in the service against the
 * target project (member or manager to read, manager to mutate); {@code GlobalExceptionHandler} maps
 * the thrown exceptions to status codes (404/403/400).
 *
 * @author peter haller
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TaskScheduleController {

    private final TaskScheduleService taskScheduleService;
    private final CurrentUserResolver currentUserResolver;

    private PUser getCurrentUser(Authentication auth) {
        return currentUserResolver.resolve(auth);
    }

    /**
     * Creates a recurring task schedule on a project.
     *
     * @return 201 Created with the new schedule
     */
    @PostMapping("/projects/{projectId}/task-schedules")
    public ResponseEntity<TaskScheduleDTO> createSchedule(
        @PathVariable Long projectId,
        @RequestBody TaskScheduleRequest request,
        Authentication auth) {

        TaskScheduleDTO created =
                taskScheduleService.createSchedule(projectId, request, getCurrentUser(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Lists all schedules of a project (enabled or not).
     *
     * @return 200 OK with the schedules
     */
    @GetMapping("/projects/{projectId}/task-schedules")
    public ResponseEntity<List<TaskScheduleDTO>> getSchedules(
        @PathVariable Long projectId,
        Authentication auth) {

        return ResponseEntity.ok(taskScheduleService.getSchedules(projectId, getCurrentUser(auth)));
    }

    /**
     * Returns a single schedule by id.
     *
     * @return 200 OK with the schedule
     */
    @GetMapping("/task-schedules/{id}")
    public ResponseEntity<TaskScheduleDTO> getSchedule(
        @PathVariable Long id,
        Authentication auth) {

        return ResponseEntity.ok(taskScheduleService.getSchedule(id, getCurrentUser(auth)));
    }

    /**
     * Partially updates a schedule (only the fields present in the body are changed).
     *
     * @return 200 OK with the updated schedule
     */
    @PatchMapping("/task-schedules/{id}")
    public ResponseEntity<TaskScheduleDTO> updateSchedule(
        @PathVariable Long id,
        @RequestBody TaskScheduleRequest patch,
        Authentication auth) {

        return ResponseEntity.ok(taskScheduleService.updateSchedule(id, patch, getCurrentUser(auth)));
    }

    /**
     * Deletes a schedule. Already generated tasks are kept.
     *
     * @return 204 No Content
     */
    @DeleteMapping("/task-schedules/{id}")
    public ResponseEntity<Void> deleteSchedule(
        @PathVariable Long id,
        Authentication auth) {

        taskScheduleService.deleteSchedule(id, getCurrentUser(auth));
        return ResponseEntity.noContent().build();
    }
}
