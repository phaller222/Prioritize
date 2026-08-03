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
import de.hallerweb.enterprise.prioritize.dto.project.ProjectGoalDTO;
import de.hallerweb.enterprise.prioritize.model.project.goal.ProjectGoal;
import de.hallerweb.enterprise.prioritize.model.project.goal.ProjectGoalProperty;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.project.ProjectGoalService;
import de.hallerweb.enterprise.prioritize.service.project.ProjectGoalService.GoalData;
import de.hallerweb.enterprise.prioritize.service.project.ProjectGoalService.ProjectProgress;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

/**
 * REST endpoints for a project's {@link ProjectGoal goals} and derived progress. Goals are
 * project-scoped; authorization is membership-based (see {@link ProjectGoalService}).
 *
 * @author peter haller
 */
@Tag(name = "Project Goals", description = "Manage a project's goals and read computed progress.")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProjectGoalController {

    private final ProjectGoalService projectGoalService;

    @Operation(summary = "Create goal")
    @PostMapping("/projects/{projectId}/goals")
    public ResponseEntity<ProjectGoalDTO> createGoal(
        @PathVariable Long projectId, @RequestBody GoalRequest request, @AuthenticatedUser PUser currentUser) {
        ProjectGoal goal = projectGoalService.createGoal(projectId, request.toData(), currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectGoalDTO.from(goal));
    }

    @Operation(summary = "Get goals")
    @GetMapping("/projects/{projectId}/goals")
    public ResponseEntity<List<ProjectGoalDTO>> getGoals(@PathVariable Long projectId, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(projectGoalService.getGoals(projectId, currentUser)
                .stream().map(ProjectGoalDTO::from).toList());
    }

    @Operation(summary = "Get goal")
    @GetMapping("/projects/{projectId}/goals/{goalId}")
    public ResponseEntity<ProjectGoalDTO> getGoal(
        @PathVariable Long projectId, @PathVariable Long goalId, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(ProjectGoalDTO.from(projectGoalService.getGoal(projectId, goalId, currentUser)));
    }

    @Operation(summary = "Update goal")
    @PatchMapping("/projects/{projectId}/goals/{goalId}")
    public ResponseEntity<ProjectGoalDTO> updateGoal(
        @PathVariable Long projectId, @PathVariable Long goalId,
        @RequestBody GoalRequest request, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(ProjectGoalDTO.from(
            projectGoalService.updateGoal(projectId, goalId, request.toData(), currentUser)));
    }

    @Operation(summary = "Delete goal")
    @DeleteMapping("/projects/{projectId}/goals/{goalId}")
    public ResponseEntity<Void> deleteGoal(
        @PathVariable Long projectId, @PathVariable Long goalId, @AuthenticatedUser PUser currentUser) {
        projectGoalService.deleteGoal(projectId, goalId, currentUser);
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns the project's progress derived from its goals and their tasks.
     */
    @Operation(summary = "Returns the project's progress derived from its goals and their tasks")
    @GetMapping("/projects/{projectId}/progress")
    public ResponseEntity<ProjectProgress> getProgress(@PathVariable Long projectId, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(projectGoalService.computeProgress(projectId, currentUser));
    }

    /**
     * Request body for creating/updating a goal. {@code name} is mandatory; {@code properties}
     * are polymorphic ({@code numeric}/{@code document}, see {@link ProjectGoalProperty}).
     */
    public record GoalRequest(String name, String description, List<ProjectGoalProperty> properties) {
        GoalData toData() {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name is required.");
            }
            return new GoalData(name, description, properties);
        }
    }
}
