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

import de.hallerweb.enterprise.prioritize.config.AuthenticatedUser;
import de.hallerweb.enterprise.prioritize.dto.project.TaskDTO;
import de.hallerweb.enterprise.prioritize.model.project.Task;
import de.hallerweb.enterprise.prioritize.model.project.TaskStatus;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.project.TaskService;
import de.hallerweb.enterprise.prioritize.service.project.TaskService.TaskData;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;

/**
 * REST endpoints for {@link Task tasks}. Authorization is derived from the owning project's
 * membership (see {@link TaskService}); the acting user is resolved from the authentication.
 *
 * @author peter haller
 */
@Tag(name = "Tasks", description = "Manage tasks: assignment, status, goals and time tracking.")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    /**
     * Creates a task on the given project's blackboard.
     */
    @Operation(summary = "Creates a task on the given project's blackboard")
    @PostMapping("/projects/{projectId}/tasks")
    public ResponseEntity<TaskDTO> createTask(
        @PathVariable Long projectId, @RequestBody TaskRequest request, @AuthenticatedUser PUser currentUser) {
        Task task = taskService.createTask(projectId, request.toData(), currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(TaskDTO.from(task, currentUser));
    }

    @Operation(summary = "Get task")
    @GetMapping("/tasks/{id}")
    public ResponseEntity<TaskDTO> getTask(@PathVariable Long id, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(TaskDTO.from(taskService.getTask(id, currentUser), currentUser));
    }

    @Operation(summary = "Update task")
    @PatchMapping("/tasks/{id}")
    public ResponseEntity<TaskDTO> updateTask(
        @PathVariable Long id, @RequestBody TaskRequest request, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(TaskDTO.from(taskService.updateTask(id, request.toData(), currentUser), currentUser));
    }

    @Operation(summary = "Delete task")
    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id, @AuthenticatedUser PUser currentUser) {
        taskService.deleteTask(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Assign task")
    @PutMapping("/tasks/{id}/assignee/{actorId}")
    public ResponseEntity<TaskDTO> assignTask(
        @PathVariable Long id, @PathVariable Long actorId, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(TaskDTO.from(taskService.assignTask(id, actorId, currentUser), currentUser));
    }

    @Operation(summary = "Unassign task")
    @DeleteMapping("/tasks/{id}/assignee")
    public ResponseEntity<TaskDTO> unassignTask(@PathVariable Long id, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(TaskDTO.from(taskService.unassignTask(id, currentUser), currentUser));
    }

    @Operation(summary = "Change status")
    @PutMapping("/tasks/{id}/status")
    public ResponseEntity<TaskDTO> changeStatus(
        @PathVariable Long id, @RequestBody TaskStatusRequest request, @AuthenticatedUser PUser currentUser) {
        if (request == null || request.status() == null) {
            throw new IllegalArgumentException("status is required.");
        }
        return ResponseEntity.ok(TaskDTO.from(taskService.changeStatus(id, request.status(), currentUser), currentUser));
    }

    @Operation(summary = "Assign goal")
    @PutMapping("/tasks/{id}/goal/{goalId}")
    public ResponseEntity<TaskDTO> assignGoal(
        @PathVariable Long id, @PathVariable Long goalId, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(TaskDTO.from(taskService.assignGoal(id, goalId, currentUser), currentUser));
    }

    @Operation(summary = "Unassign goal")
    @DeleteMapping("/tasks/{id}/goal")
    public ResponseEntity<TaskDTO> unassignGoal(@PathVariable Long id, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(TaskDTO.from(taskService.unassignGoal(id, currentUser), currentUser));
    }

    @Operation(summary = "Start tracking")
    @PostMapping("/tasks/{id}/tracking/start")
    public ResponseEntity<TaskDTO> startTracking(@PathVariable Long id, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(TaskDTO.from(taskService.startTracking(id, currentUser), currentUser));
    }

    @Operation(summary = "Stop tracking")
    @PostMapping("/tasks/{id}/tracking/stop")
    public ResponseEntity<TaskDTO> stopTracking(@PathVariable Long id, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(TaskDTO.from(taskService.stopTracking(id, currentUser), currentUser));
    }

    @Operation(summary = "Toggle tracking")
    @PostMapping("/tasks/{id}/tracking/toggle")
    public ResponseEntity<TaskDTO> toggleTracking(@PathVariable Long id, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(TaskDTO.from(taskService.toggleTracking(id, currentUser), currentUser));
    }

    /** Returns the total time tracked on the task (completed spans plus the running one, live). */
    @Operation(summary = "Returns the total time tracked on the task (completed spans plus the running one, live)")
    @GetMapping("/tasks/{id}/tracking")
    public ResponseEntity<TaskService.TrackingSummary> getTracking(@PathVariable Long id, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(taskService.getTrackingSummary(id, currentUser));
    }

    /** Returns the individual tracked work sessions of the task (completed spans plus the running one). */
    @Operation(summary = "Returns the individual tracked work sessions of the task (completed spans plus the running one)")
    @GetMapping("/tasks/{id}/tracking/sessions")
    public ResponseEntity<List<TaskService.WorkSession>> getTrackingSessions(@PathVariable Long id, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(taskService.getWorkSessions(id, currentUser));
    }

    /**
     * Stops the running session retroactively, for the "forgot to clock out last night" case.
     */
    @Operation(summary = "Stops the running work session at an earlier point in time")
    @PostMapping("/tasks/{id}/tracking/stop-at")
    public ResponseEntity<TaskDTO> stopTrackingAt(
        @PathVariable Long id, @RequestBody StopAtRequest request, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(TaskDTO.from(
                taskService.stopTrackingAt(id, request.until(), request.reason(), request.userId(), currentUser), currentUser));
    }

    /**
     * Books a work session that was never clocked. Manager or member; booking someone else's time is
     * reserved for the project manager.
     */
    @Operation(summary = "Books a work session by hand that was never clocked")
    @PostMapping("/tasks/{id}/tracking/sessions")
    public ResponseEntity<TaskService.WorkSession> addTrackingSession(
        @PathVariable Long id, @RequestBody WorkSessionRequest request, @AuthenticatedUser PUser currentUser) {
        TaskService.WorkSession session = taskService.addWorkSession(
                id, request.from(), request.until(), request.reason(), request.userId(), currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    /**
     * Corrects the bounds of a completed work session. The project manager may correct any session,
     * everyone else only the sessions they took part in.
     * <p>
     * A correction cannot move a session to a different person, so a {@code userId} in the body is
     * refused rather than dropped: a caller who sends one believes the session changed hands, and
     * accepting the request silently would confirm a belief that is wrong.
     */
    @Operation(summary = "Corrects the start and end of a completed work session")
    @PutMapping("/tasks/{id}/tracking/sessions/{sessionId}")
    public ResponseEntity<TaskService.WorkSession> updateTrackingSession(
        @PathVariable Long id, @PathVariable Long sessionId, @RequestBody WorkSessionRequest request,
        @AuthenticatedUser PUser currentUser) {
        if (request.userId() != null) {
            throw new IllegalArgumentException(
                    "A correction cannot reassign a session — delete it and book a new one for the other person.");
        }
        return ResponseEntity.ok(taskService.updateWorkSession(
                id, sessionId, request.from(), request.until(), request.reason(), currentUser));
    }

    /** Removes a completed work session, for instance a scan of the wrong tag. */
    @Operation(summary = "Removes a completed work session")
    @DeleteMapping("/tasks/{id}/tracking/sessions/{sessionId}")
    public ResponseEntity<Void> deleteTrackingSession(
        @PathVariable Long id, @PathVariable Long sessionId, @AuthenticatedUser PUser currentUser) {
        taskService.deleteWorkSession(id, sessionId, currentUser);
        return ResponseEntity.noContent().build();
    }

    /**
     * Request body for correcting or hand-booking a work session. All of {@code from}, {@code until}
     * and {@code reason} are mandatory. {@code userId} books the session for somebody else, which is
     * a manager's job; on a correction it is rejected, since a correction cannot move a session to a
     * different person.
     */
    public record WorkSessionRequest(Instant from, Instant until, String reason, Long userId) {
    }

    /**
     * Request body for stopping the running session retroactively. {@code until} and {@code reason}
     * are mandatory. {@code userId} names whose clock to close; omit it to close your own. Closing a
     * colleague's is a manager job — the case where somebody left a clock running overnight.
     */
    public record StopAtRequest(Instant until, String reason, Long userId) {
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
