package de.hallerweb.enterprise.prioritize.controller.project;

import de.hallerweb.enterprise.prioritize.config.CurrentUserResolver;
import de.hallerweb.enterprise.prioritize.controller.project.ProjectGoalController.GoalRequest;
import de.hallerweb.enterprise.prioritize.model.project.goal.ProjectGoal;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.project.ProjectGoalService;
import de.hallerweb.enterprise.prioritize.service.project.ProjectGoalService.ProjectProgress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProjectGoalController}: delegation, status codes and request validation.
 * Plain Mockito (no Spring context).
 */
class ProjectGoalControllerTest {

    private ProjectGoalService projectGoalService;
    private ProjectGoalController controller;

    private final Authentication auth = mock(Authentication.class);
    private final PUser user = new PUser();

    @BeforeEach
    void setUp() {
        projectGoalService = mock(ProjectGoalService.class);
        CurrentUserResolver resolver = mock(CurrentUserResolver.class);
        controller = new ProjectGoalController(projectGoalService, resolver);
        when(resolver.resolve(auth)).thenReturn(user);
    }

    @Test
    @DisplayName("createGoal: delegates and answers 201 Created")
    void createGoal_created() {
        when(projectGoalService.createGoal(eq(3L), any(), eq(user))).thenReturn(new ProjectGoal());

        ResponseEntity<ProjectGoal> response = controller.createGoal(
                3L, new GoalRequest("Cool it", "d", null), auth);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(projectGoalService).createGoal(eq(3L), any(), eq(user));
    }

    @Test
    @DisplayName("createGoal: blank name is rejected before delegation")
    void createGoal_blankName_throws() {
        assertThrows(IllegalArgumentException.class, () -> controller.createGoal(
                3L, new GoalRequest("", "d", null), auth));
        verifyNoInteractions(projectGoalService);
    }

    @Test
    @DisplayName("getProgress: delegates to the service")
    void getProgress_delegates() {
        ProjectProgress result = new ProjectProgress(null, List.of());
        when(projectGoalService.computeProgress(eq(7L), eq(user))).thenReturn(result);

        ResponseEntity<ProjectProgress> response = controller.getProgress(7L, auth);

        assertEquals(result, response.getBody());
        verify(projectGoalService).computeProgress(eq(7L), eq(user));
    }

    @Test
    @DisplayName("deleteGoal: delegates and answers 204 No Content")
    void deleteGoal_noContent() {
        ResponseEntity<Void> response = controller.deleteGoal(3L, 9L, auth);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(projectGoalService).deleteGoal(eq(3L), eq(9L), eq(user));
    }
}
