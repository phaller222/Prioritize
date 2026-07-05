package de.hallerweb.enterprise.prioritize.controller.project;

import de.hallerweb.enterprise.prioritize.config.CurrentUserResolver;
import de.hallerweb.enterprise.prioritize.model.project.goal.ProjectGoal;
import de.hallerweb.enterprise.prioritize.model.project.goal.ProjectGoalProperty;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.project.ProjectGoalService;
import de.hallerweb.enterprise.prioritize.service.project.ProjectGoalService.GoalData;
import de.hallerweb.enterprise.prioritize.service.project.ProjectGoalService.ProjectProgress;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for a project's {@link ProjectGoal goals} and derived progress. Goals are
 * project-scoped; authorization is membership-based (see {@link ProjectGoalService}).
 *
 * @author peter haller
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProjectGoalController {

    private final ProjectGoalService projectGoalService;
    private final CurrentUserResolver currentUserResolver;

    private PUser getCurrentUser(Authentication auth) {
        return currentUserResolver.resolve(auth);
    }

    @PostMapping("/projects/{projectId}/goals")
    public ResponseEntity<ProjectGoal> createGoal(
        @PathVariable Long projectId, @RequestBody GoalRequest request, Authentication auth) {
        ProjectGoal goal = projectGoalService.createGoal(projectId, request.toData(), getCurrentUser(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(goal);
    }

    @GetMapping("/projects/{projectId}/goals")
    public ResponseEntity<List<ProjectGoal>> getGoals(@PathVariable Long projectId, Authentication auth) {
        return ResponseEntity.ok(projectGoalService.getGoals(projectId, getCurrentUser(auth)));
    }

    @GetMapping("/projects/{projectId}/goals/{goalId}")
    public ResponseEntity<ProjectGoal> getGoal(
        @PathVariable Long projectId, @PathVariable Long goalId, Authentication auth) {
        return ResponseEntity.ok(projectGoalService.getGoal(projectId, goalId, getCurrentUser(auth)));
    }

    @PatchMapping("/projects/{projectId}/goals/{goalId}")
    public ResponseEntity<ProjectGoal> updateGoal(
        @PathVariable Long projectId, @PathVariable Long goalId,
        @RequestBody GoalRequest request, Authentication auth) {
        return ResponseEntity.ok(
            projectGoalService.updateGoal(projectId, goalId, request.toData(), getCurrentUser(auth)));
    }

    @DeleteMapping("/projects/{projectId}/goals/{goalId}")
    public ResponseEntity<Void> deleteGoal(
        @PathVariable Long projectId, @PathVariable Long goalId, Authentication auth) {
        projectGoalService.deleteGoal(projectId, goalId, getCurrentUser(auth));
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns the project's progress derived from its goals and their tasks.
     */
    @GetMapping("/projects/{projectId}/progress")
    public ResponseEntity<ProjectProgress> getProgress(@PathVariable Long projectId, Authentication auth) {
        return ResponseEntity.ok(projectGoalService.computeProgress(projectId, getCurrentUser(auth)));
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
