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

package de.hallerweb.enterprise.prioritize.controller.project;

import de.hallerweb.enterprise.prioritize.config.CurrentUserResolver;
import de.hallerweb.enterprise.prioritize.model.project.Task;
import de.hallerweb.enterprise.prioritize.model.project.TaskStatus;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.project.TaskService;
import de.hallerweb.enterprise.prioritize.service.project.TaskService.TaskData;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for {@link Task tasks}. Authorization is derived from the owning project's
 * membership (see {@link TaskService}); the acting user is resolved from the authentication.
 *
 * @author peter haller
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final CurrentUserResolver currentUserResolver;

    private PUser getCurrentUser(Authentication auth) {
        return currentUserResolver.resolve(auth);
    }

    /**
     * Creates a task on the given project's blackboard.
     */
    @PostMapping("/projects/{projectId}/tasks")
    public ResponseEntity<Task> createTask(
        @PathVariable Long projectId, @RequestBody TaskRequest request, Authentication auth) {
        Task task = taskService.createTask(projectId, request.toData(), getCurrentUser(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(task);
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<Task> getTask(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(taskService.getTask(id, getCurrentUser(auth)));
    }

    @PatchMapping("/tasks/{id}")
    public ResponseEntity<Task> updateTask(
        @PathVariable Long id, @RequestBody TaskRequest request, Authentication auth) {
        return ResponseEntity.ok(taskService.updateTask(id, request.toData(), getCurrentUser(auth)));
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id, Authentication auth) {
        taskService.deleteTask(id, getCurrentUser(auth));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/tasks/{id}/assignee/{actorId}")
    public ResponseEntity<Task> assignTask(
        @PathVariable Long id, @PathVariable Long actorId, Authentication auth) {
        return ResponseEntity.ok(taskService.assignTask(id, actorId, getCurrentUser(auth)));
    }

    @DeleteMapping("/tasks/{id}/assignee")
    public ResponseEntity<Task> unassignTask(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(taskService.unassignTask(id, getCurrentUser(auth)));
    }

    @PutMapping("/tasks/{id}/status")
    public ResponseEntity<Task> changeStatus(
        @PathVariable Long id, @RequestBody TaskStatusRequest request, Authentication auth) {
        if (request == null || request.status() == null) {
            throw new IllegalArgumentException("status is required.");
        }
        return ResponseEntity.ok(taskService.changeStatus(id, request.status(), getCurrentUser(auth)));
    }

    @PutMapping("/tasks/{id}/goal/{goalId}")
    public ResponseEntity<Task> assignGoal(
        @PathVariable Long id, @PathVariable Long goalId, Authentication auth) {
        return ResponseEntity.ok(taskService.assignGoal(id, goalId, getCurrentUser(auth)));
    }

    @DeleteMapping("/tasks/{id}/goal")
    public ResponseEntity<Task> unassignGoal(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(taskService.unassignGoal(id, getCurrentUser(auth)));
    }

    /**
     * Request body for creating/updating a task. {@code name} is mandatory.
     */
    public record TaskRequest(String name, String description, int priority) {
        TaskData toData() {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name is required.");
            }
            return new TaskData(name, description, priority);
        }
    }

    /**
     * Request body for a task status change.
     */
    public record TaskStatusRequest(TaskStatus status) {
    }
}
