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

package de.hallerweb.enterprise.prioritize.service.resource.control.mqtt;

import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.resource.MqttCommand;
import de.hallerweb.enterprise.prioritize.model.resource.MqttCommandParameter;
import de.hallerweb.enterprise.prioritize.model.resource.MqttParameterType;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.resource.ResourceGroup;
import de.hallerweb.enterprise.prioritize.repository.company.DepartmentRepository;
import de.hallerweb.enterprise.prioritize.repository.resource.ResourceGroupRepository;
import de.hallerweb.enterprise.prioritize.repository.resource.ResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure logic tests for the MQTT discovery (self-registration) flow: no Spring context,
 * no broker, repositories mocked.
 */
class MqttDiscoveryServiceTest {

    private ResourceRepository resourceRepository;
    private ResourceGroupRepository resourceGroupRepository;
    private DepartmentRepository departmentRepository;
    private MqttDiscoveryService service;

    private Department department;

    @BeforeEach
    void setUp() {
        resourceRepository = mock(ResourceRepository.class);
        resourceGroupRepository = mock(ResourceGroupRepository.class);
        departmentRepository = mock(DepartmentRepository.class);
        service = new MqttDiscoveryService(resourceRepository, resourceGroupRepository, departmentRepository);

        department = new Department();
        department.setId(1L);

        when(departmentRepository.findAll()).thenReturn(List.of(department));
        // No discovery group exists yet by default.
        when(resourceGroupRepository.findByNameContainingIgnoreCase(anyString())).thenReturn(List.of());
        when(resourceGroupRepository.save(any(ResourceGroup.class))).thenAnswer(inv -> inv.getArgument(0));
        // Unknown device by default.
        when(resourceRepository.findByMqttUUID(anyString())).thenReturn(Optional.empty());
        when(resourceRepository.save(any(Resource.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private DiscoveryMessage lampMessage(String uuid, String name) {
        return new DiscoveryMessage(
            uuid, name, "Dimmable RGB lamp",
            new DiscoveryMessage.Control(
                new DiscoveryMessage.MqttControl("devices/lamp/cmd", "devices/lamp/evt"),
                new DiscoveryMessage.RestControl("10.0.0.5", 8080)),
            List.of(
                new DiscoveryMessage.CommandSpec("ON", null, null),
                new DiscoveryMessage.CommandSpec("SET_BRIGHTNESS", "Brightness in percent",
                    List.of(new DiscoveryMessage.ParameterSpec(
                        "level", MqttParameterType.INT, 0.0, 100.0, "%", true, null)))));
    }

    @Test
    @DisplayName("New device: registered into the 'MQTT Discovered' group with mapped fields and commands")
    void newDevice_isRegistered() {
        DiscoveryMessage msg = lampMessage("uuid-1", "Living Room Lamp");

        service.registerOrUpdate(msg);

        ArgumentCaptor<Resource> captor = ArgumentCaptor.forClass(Resource.class);
        verify(resourceRepository).save(captor.capture());
        Resource r = captor.getValue();

        assertEquals("Living Room Lamp", r.getName());
        assertEquals("Dimmable RGB lamp", r.getDescription());
        assertTrue(r.getMqttResource());
        assertTrue(r.getMqttOnline());
        assertEquals("uuid-1", r.getMqttUUID());
        assertEquals("devices/lamp/cmd", r.getMqttDataReceiveTopic());
        assertEquals("devices/lamp/evt", r.getMqttDataSendTopic());
        assertEquals("10.0.0.5", r.getIp());
        assertEquals(8080, r.getPort());

        assertNotNull(r.getResourceGroup());
        assertEquals(MqttDiscoveryService.DISCOVERY_GROUP_NAME, r.getResourceGroup().getName());
        assertEquals(department, r.getDepartment());

        assertEquals(2, r.getMqttCommands().size());
        MqttCommand brightness = r.getMqttCommands().stream()
            .filter(c -> c.getName().equals("SET_BRIGHTNESS")).findFirst().orElseThrow();
        assertEquals(1, brightness.getParameters().size());
        MqttCommandParameter p = brightness.getParameters().iterator().next();
        assertEquals("level", p.getName());
        assertEquals(MqttParameterType.INT, p.getType());
        assertEquals(0.0, p.getMinValue());
        assertEquals(100.0, p.getMaxValue());
        assertEquals("%", p.getUnit());
        assertTrue(p.isRequired());

        // Group did not exist -> it was created exactly once.
        verify(resourceGroupRepository).save(any(ResourceGroup.class));
    }

    @Test
    @DisplayName("Known device (same uuid): updated in place, no duplicate, group reused")
    void knownDevice_isUpdated() {
        Resource existing = new Resource();
        existing.setId(42L);
        existing.setMqttUUID("uuid-1");
        existing.setMqttCommands(new HashSet<>());
        when(resourceRepository.findByMqttUUID("uuid-1")).thenReturn(Optional.of(existing));

        ResourceGroup group = ResourceGroup.builder()
            .name(MqttDiscoveryService.DISCOVERY_GROUP_NAME).department(department).build();
        when(resourceGroupRepository.findByNameContainingIgnoreCase(anyString())).thenReturn(List.of(group));

        DiscoveryMessage msg = new DiscoveryMessage("uuid-1", "Lamp v2", "Updated description", null, List.of());

        Resource saved = service.registerOrUpdate(msg);

        assertEquals(42L, saved.getId(), "same entity must be updated, not duplicated");
        assertEquals("Lamp v2", saved.getName());
        assertEquals("Updated description", saved.getDescription());
        verify(resourceRepository).save(existing);
        // Existing group is reused -> no new group persisted.
        verify(resourceGroupRepository, never()).save(any(ResourceGroup.class));
    }
}
