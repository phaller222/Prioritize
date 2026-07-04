package de.hallerweb.enterprise.prioritize.controller.resource;

import de.hallerweb.enterprise.prioritize.config.CurrentUserResolver;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.company.DepartmentService;
import de.hallerweb.enterprise.prioritize.service.resource.ResourceService;
import de.hallerweb.enterprise.prioritize.service.resource.control.ResourceControlService;
import de.hallerweb.enterprise.prioritize.service.skill.SkillService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the telemetry REST ingest endpoint of {@link ResourceController}: input
 * validation, delegation to {@link ResourceService} and the returned status code. Plain
 * Mockito (no Spring context), matching the style of the MQTT inbound tests.
 */
class ResourceControllerTest {

    private ResourceService resourceService;
    private CurrentUserResolver currentUserResolver;
    private ResourceController controller;

    private final Authentication auth = mock(Authentication.class);
    private final PUser user = new PUser();

    @BeforeEach
    void setUp() {
        resourceService = mock(ResourceService.class);
        currentUserResolver = mock(CurrentUserResolver.class);
        DepartmentService departmentService = mock(DepartmentService.class);
        SkillService skillService = mock(SkillService.class);
        ResourceControlService resourceControlService = mock(ResourceControlService.class);

        controller = new ResourceController(
                resourceService, currentUserResolver, departmentService, skillService, resourceControlService);

        when(currentUserResolver.resolve(auth)).thenReturn(user);
    }

    @Test
    @DisplayName("recordValue: valid reading is delegated and answered with 202 Accepted")
    void recordValue_delegatesAndAccepts() {
        ResponseEntity<Void> response = controller.recordValue(
                7L, new ResourceController.ResourceValueRequest("temp", "42"), auth);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(resourceService).recordMqttValue(eq(7L), eq("temp"), eq("42"), eq(user));
    }

    @Test
    @DisplayName("recordValue: missing value is rejected with IllegalArgumentException, no delegation")
    void recordValue_missingValue_throws() {
        assertThrows(IllegalArgumentException.class, () -> controller.recordValue(
                7L, new ResourceController.ResourceValueRequest("temp", null), auth));

        verify(resourceService, org.mockito.Mockito.never())
                .recordMqttValue(any(), any(), any(), any());
    }

    @Test
    @DisplayName("recordValue: blank name is rejected with IllegalArgumentException, no delegation")
    void recordValue_blankName_throws() {
        assertThrows(IllegalArgumentException.class, () -> controller.recordValue(
                7L, new ResourceController.ResourceValueRequest("  ", "42"), auth));

        verifyNoInteractions(resourceService);
    }
}
