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

package de.hallerweb.enterprise.prioritize.controller.resource;

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
    private ResourceController controller;

    private final PUser user = new PUser();

    @BeforeEach
    void setUp() {
        resourceService = mock(ResourceService.class);
        DepartmentService departmentService = mock(DepartmentService.class);
        SkillService skillService = mock(SkillService.class);
        ResourceControlService resourceControlService = mock(ResourceControlService.class);

        controller = new ResourceController(
                resourceService, departmentService, skillService, resourceControlService);
    }

    @Test
    @DisplayName("getAllResources: delegates to the service and maps the readable resources to DTOs")
    void getAllResources_delegatesAndMaps() {
        de.hallerweb.enterprise.prioritize.model.resource.Resource r =
                new de.hallerweb.enterprise.prioritize.model.resource.Resource();
        r.setId(3L);
        r.setName("Robot");
        when(resourceService.getAllResources(user)).thenReturn(java.util.List.of(r));

        ResponseEntity<java.util.List<de.hallerweb.enterprise.prioritize.dto.resource.ResourceDTO>> response =
                controller.getAllResources(user);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(3L, response.getBody().get(0).id());
        assertEquals("Robot", response.getBody().get(0).name());
        verify(resourceService).getAllResources(user);
    }

    @Test
    @DisplayName("getResourceGroups: delegates to the service and maps the department's groups to DTOs")
    void getResourceGroups_delegatesAndMaps() {
        de.hallerweb.enterprise.prioritize.model.company.Department dept =
                new de.hallerweb.enterprise.prioritize.model.company.Department();
        dept.setId(7L);
        de.hallerweb.enterprise.prioritize.model.resource.ResourceGroup group =
                new de.hallerweb.enterprise.prioritize.model.resource.ResourceGroup();
        group.setId(4L);
        group.setName("Robots");
        group.setDepartment(dept);
        when(resourceService.getResourceGroupsByDepartment(7L, user)).thenReturn(java.util.List.of(group));

        ResponseEntity<java.util.List<de.hallerweb.enterprise.prioritize.dto.resource.ResourceGroupDTO>> response =
                controller.getResourceGroups(7L, user);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(4L, response.getBody().get(0).id());
        assertEquals("Robots", response.getBody().get(0).name());
        assertEquals(7L, response.getBody().get(0).departmentId());
        verify(resourceService).getResourceGroupsByDepartment(7L, user);
    }

    @Test
    @DisplayName("renameResourceGroup: delegates to the service and returns the renamed group")
    void renameResourceGroup_delegatesAndMaps() {
        de.hallerweb.enterprise.prioritize.model.resource.ResourceGroup group =
                new de.hallerweb.enterprise.prioritize.model.resource.ResourceGroup();
        group.setId(4L);
        group.setName("Drones");
        when(resourceService.renameResourceGroup(4L, "Drones", user)).thenReturn(group);

        ResponseEntity<de.hallerweb.enterprise.prioritize.dto.resource.ResourceGroupDTO> response =
                controller.renameResourceGroup(4L, "Drones", user);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Drones", response.getBody().name());
        verify(resourceService).renameResourceGroup(4L, "Drones", user);
    }

    @Test
    @DisplayName("getResourceStatus: delegates to the service and returns its list with 200 OK")
    void getResourceStatus_delegates() {
        de.hallerweb.enterprise.prioritize.dto.resource.ResourceStatusDTO status =
                new de.hallerweb.enterprise.prioritize.dto.resource.ResourceStatusDTO(
                        null, java.util.List.of(), java.util.List.of());
        when(resourceService.getResourceStatus(user)).thenReturn(java.util.List.of(status));

        ResponseEntity<java.util.List<de.hallerweb.enterprise.prioritize.dto.resource.ResourceStatusDTO>> response =
                controller.getResourceStatus(user);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        verify(resourceService).getResourceStatus(user);
    }

    @Test
    @DisplayName("getLatestValues: delegates to the service and returns its list with 200 OK")
    void getLatestValues_delegates() {
        de.hallerweb.enterprise.prioritize.dto.resource.ResourceValueDTO v =
                new de.hallerweb.enterprise.prioritize.dto.resource.ResourceValueDTO("temp", "42");
        when(resourceService.getLatestValues(5L, user)).thenReturn(java.util.List.of(v));

        ResponseEntity<java.util.List<de.hallerweb.enterprise.prioritize.dto.resource.ResourceValueDTO>> response =
                controller.getLatestValues(5L, user);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("temp", response.getBody().get(0).name());
        assertEquals("42", response.getBody().get(0).value());
        verify(resourceService).getLatestValues(5L, user);
    }

    @Test
    @DisplayName("recordValue: valid reading is delegated and answered with 202 Accepted")
    void recordValue_delegatesAndAccepts() {
        ResponseEntity<Void> response = controller.recordValue(
                7L, new ResourceController.ResourceValueRequest("temp", "42"), user);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(resourceService).recordMqttValue(eq(7L), eq("temp"), eq("42"), eq(user));
    }

    @Test
    @DisplayName("recordValue: missing value is rejected with IllegalArgumentException, no delegation")
    void recordValue_missingValue_throws() {
        assertThrows(IllegalArgumentException.class, () -> controller.recordValue(
                7L, new ResourceController.ResourceValueRequest("temp", null), user));

        verify(resourceService, org.mockito.Mockito.never())
                .recordMqttValue(any(), any(), any(), any());
    }

    @Test
    @DisplayName("recordValue: blank name is rejected with IllegalArgumentException, no delegation")
    void recordValue_blankName_throws() {
        assertThrows(IllegalArgumentException.class, () -> controller.recordValue(
                7L, new ResourceController.ResourceValueRequest("  ", "42"), user));

        verifyNoInteractions(resourceService);
    }
}
