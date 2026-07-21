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
import de.hallerweb.enterprise.prioritize.model.project.Project;
import de.hallerweb.enterprise.prioritize.model.project.Task;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.project.ProjectService;
import de.hallerweb.enterprise.prioritize.service.project.ProjectService.ProjectData;
import de.hallerweb.enterprise.prioritize.service.project.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * REST endpoints for {@link Project projects}. Access is membership-based (see
 * {@link ProjectService}); the acting user is resolved from the authentication.
 *
 * @author peter haller
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final TaskService taskService;
    private final CurrentUserResolver currentUserResolver;

    private PUser getCurrentUser(Authentication auth) {
        return currentUserResolver.resolve(auth);
    }

    /**
     * Creates a new project (the caller becomes manager and first member).
     */
    @PostMapping("/projects")
    public ResponseEntity<Project> createProject(@RequestBody ProjectRequest request, Authentication auth) {
        Project project = projectService.createProject(request.toData(), getCurrentUser(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(project);
    }

    /**
     * Returns the projects the caller manages or participates in.
     */
    @GetMapping("/projects")
    public ResponseEntity<List<Project>> getMyProjects(Authentication auth) {
        return ResponseEntity.ok(projectService.getMyProjects(getCurrentUser(auth)));
    }

    @GetMapping("/projects/{id}")
    public ResponseEntity<Project> getProject(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(projectService.getProject(id, getCurrentUser(auth)));
    }

    @PatchMapping("/projects/{id}")
    public ResponseEntity<Project> updateProject(
        @PathVariable Long id, @RequestBody ProjectRequest request, Authentication auth) {
        return ResponseEntity.ok(projectService.updateProject(id, request.toData(), getCurrentUser(auth)));
    }

    @DeleteMapping("/projects/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id, Authentication auth) {
        projectService.deleteProject(id, getCurrentUser(auth));
        return ResponseEntity.noContent().build();
    }

    // --- Team / resources / documents (manager only) ---

    @PostMapping("/projects/{id}/members/{userId}")
    public ResponseEntity<Project> addMember(
        @PathVariable Long id, @PathVariable Long userId, Authentication auth) {
        return ResponseEntity.ok(projectService.addMember(id, userId, getCurrentUser(auth)));
    }

    @DeleteMapping("/projects/{id}/members/{userId}")
    public ResponseEntity<Project> removeMember(
        @PathVariable Long id, @PathVariable Long userId, Authentication auth) {
        return ResponseEntity.ok(projectService.removeMember(id, userId, getCurrentUser(auth)));
    }

    /**
     * Hands the project over to another of its members. Allowed for the current manager and for an
     * administrator; the designated user must already be a member (400 otherwise).
     */
    @PutMapping("/projects/{id}/manager/{userId}")
    public ResponseEntity<Project> transferManager(
        @PathVariable Long id, @PathVariable Long userId, Authentication auth) {
        return ResponseEntity.ok(projectService.transferManager(id, userId, getCurrentUser(auth)));
    }

    @PostMapping("/projects/{id}/resources/{resourceId}")
    public ResponseEntity<Project> addResource(
        @PathVariable Long id, @PathVariable Long resourceId, Authentication auth) {
        return ResponseEntity.ok(projectService.addResource(id, resourceId, getCurrentUser(auth)));
    }

    @DeleteMapping("/projects/{id}/resources/{resourceId}")
    public ResponseEntity<Project> removeResource(
        @PathVariable Long id, @PathVariable Long resourceId, Authentication auth) {
        return ResponseEntity.ok(projectService.removeResource(id, resourceId, getCurrentUser(auth)));
    }

    @PostMapping("/projects/{id}/documents/{documentInfoId}")
    public ResponseEntity<Project> addDocument(
        @PathVariable Long id, @PathVariable Long documentInfoId, Authentication auth) {
        return ResponseEntity.ok(projectService.addDocument(id, documentInfoId, getCurrentUser(auth)));
    }

    @DeleteMapping("/projects/{id}/documents/{documentInfoId}")
    public ResponseEntity<Project> removeDocument(
        @PathVariable Long id, @PathVariable Long documentInfoId, Authentication auth) {
        return ResponseEntity.ok(projectService.removeDocument(id, documentInfoId, getCurrentUser(auth)));
    }

    /**
     * Returns all tasks of a project (caller must be manager or member).
     */
    @GetMapping("/projects/{id}/tasks")
    public ResponseEntity<List<Task>> getTasks(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(taskService.getTasksForProject(id, getCurrentUser(auth)));
    }

    /**
     * Request body for creating/updating a project. All fields are optional except {@code name}.
     */
    public record ProjectRequest(String name, String description, int priority,
                                 LocalDate beginDate, LocalDate dueDate, int maxManDays) {
        ProjectData toData() {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name is required.");
            }
            return new ProjectData(name, description, priority, beginDate, dueDate, maxManDays);
        }
    }
}
