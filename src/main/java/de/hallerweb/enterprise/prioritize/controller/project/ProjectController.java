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
import de.hallerweb.enterprise.prioritize.dto.project.ProjectDTO;
import de.hallerweb.enterprise.prioritize.dto.project.TaskDTO;
import de.hallerweb.enterprise.prioritize.model.project.Project;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.project.ProjectService;
import de.hallerweb.enterprise.prioritize.service.project.ProjectService.ProjectData;
import de.hallerweb.enterprise.prioritize.service.project.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

/**
 * REST endpoints for {@link Project projects}. Access is membership-based (see
 * {@link ProjectService}); the acting user is resolved from the authentication.
 *
 * @author peter haller
 */
@Tag(name = "Projects", description = "Manage projects, their manager, team members, resources and documents.")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final TaskService taskService;

    /**
     * Creates a new project (the caller becomes manager and first member).
     */
    @Operation(summary = "Creates a new project (the caller becomes manager and first member)")
    @PostMapping("/projects")
    public ResponseEntity<ProjectDTO> createProject(@RequestBody ProjectRequest request, @AuthenticatedUser PUser currentUser) {
        Project project = projectService.createProject(request.toData(), currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectDTO.from(project));
    }

    /**
     * Returns the projects the caller manages or participates in.
     */
    @Operation(summary = "Returns the projects the caller manages or participates in")
    @GetMapping("/projects")
    public ResponseEntity<List<ProjectDTO>> getMyProjects(@AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(projectService.getMyProjects(currentUser)
                .stream().map(ProjectDTO::from).toList());
    }

    @Operation(summary = "Get project")
    @GetMapping("/projects/{id}")
    public ResponseEntity<ProjectDTO> getProject(@PathVariable Long id, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(ProjectDTO.from(projectService.getProject(id, currentUser)));
    }

    @Operation(summary = "Update project")
    @PatchMapping("/projects/{id}")
    public ResponseEntity<ProjectDTO> updateProject(
        @PathVariable Long id, @RequestBody ProjectRequest request, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(ProjectDTO.from(projectService.updateProject(id, request.toData(), currentUser)));
    }

    @Operation(summary = "Delete project")
    @DeleteMapping("/projects/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id, @AuthenticatedUser PUser currentUser) {
        projectService.deleteProject(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    // --- Team / resources / documents (manager only) ---

    @Operation(summary = "Add member")
    @PostMapping("/projects/{id}/members/{userId}")
    public ResponseEntity<ProjectDTO> addMember(
        @PathVariable Long id, @PathVariable Long userId, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(ProjectDTO.from(projectService.addMember(id, userId, currentUser)));
    }

    @Operation(summary = "Remove member")
    @DeleteMapping("/projects/{id}/members/{userId}")
    public ResponseEntity<ProjectDTO> removeMember(
        @PathVariable Long id, @PathVariable Long userId, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(ProjectDTO.from(projectService.removeMember(id, userId, currentUser)));
    }

    /**
     * Hands the project over to another of its members. Allowed for the current manager and for an
     * administrator; the designated user must already be a member (400 otherwise).
     */
    @Operation(summary = "Hands the project over to another of its members")
    @PutMapping("/projects/{id}/manager/{userId}")
    public ResponseEntity<ProjectDTO> transferManager(
        @PathVariable Long id, @PathVariable Long userId, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(ProjectDTO.from(projectService.transferManager(id, userId, currentUser)));
    }

    @Operation(summary = "Add resource")
    @PostMapping("/projects/{id}/resources/{resourceId}")
    public ResponseEntity<ProjectDTO> addResource(
        @PathVariable Long id, @PathVariable Long resourceId, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(ProjectDTO.from(projectService.addResource(id, resourceId, currentUser)));
    }

    @Operation(summary = "Remove resource")
    @DeleteMapping("/projects/{id}/resources/{resourceId}")
    public ResponseEntity<ProjectDTO> removeResource(
        @PathVariable Long id, @PathVariable Long resourceId, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(ProjectDTO.from(projectService.removeResource(id, resourceId, currentUser)));
    }

    @Operation(summary = "Add document")
    @PostMapping("/projects/{id}/documents/{documentInfoId}")
    public ResponseEntity<ProjectDTO> addDocument(
        @PathVariable Long id, @PathVariable Long documentInfoId, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(ProjectDTO.from(projectService.addDocument(id, documentInfoId, currentUser)));
    }

    @Operation(summary = "Remove document")
    @DeleteMapping("/projects/{id}/documents/{documentInfoId}")
    public ResponseEntity<ProjectDTO> removeDocument(
        @PathVariable Long id, @PathVariable Long documentInfoId, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(ProjectDTO.from(projectService.removeDocument(id, documentInfoId, currentUser)));
    }

    /**
     * Returns all tasks of a project (caller must be manager or member).
     */
    @Operation(summary = "Returns all tasks of a project (caller must be manager or member)")
    @GetMapping("/projects/{id}/tasks")
    public ResponseEntity<List<TaskDTO>> getTasks(@PathVariable Long id, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(taskService.getTasksForProject(id, currentUser)
                .stream().map(task -> TaskDTO.from(task, currentUser)).toList());
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
