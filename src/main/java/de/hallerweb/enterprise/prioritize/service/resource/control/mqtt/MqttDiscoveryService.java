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
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.resource.ResourceGroup;
import de.hallerweb.enterprise.prioritize.repository.company.DepartmentRepository;
import de.hallerweb.enterprise.prioritize.repository.resource.ResourceGroupRepository;
import de.hallerweb.enterprise.prioritize.repository.resource.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Registers MQTT devices that announce themselves via a DISCOVERY message and keeps
 * their resource representation up to date. Newly discovered devices are placed into a
 * dedicated {@value #DISCOVERY_GROUP_NAME} group so an administrator can review and
 * move them deliberately. Registration is keyed by the device's MQTT UUID and is
 * idempotent: a device re-announcing itself (e.g. after a reboot) updates its existing
 * resource instead of creating a duplicate.
 * <p>
 * This is a system-level inbound process without an acting user, analogous to the
 * STATUS handling; therefore no permission check is applied.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class MqttDiscoveryService {

    /** Name of the group new, self-registered devices are placed into. */
    public static final String DISCOVERY_GROUP_NAME = "MQTT Discovered";

    private final ResourceRepository resourceRepository;
    private final ResourceGroupRepository resourceGroupRepository;
    private final DepartmentRepository departmentRepository;

    /**
     * Registers a newly discovered device or updates an already-known one (matched by
     * MQTT UUID). The caller is expected to have validated the mandatory fields.
     *
     * @param msg the parsed discovery message
     * @return the persisted resource
     */
    @Transactional
    public Resource registerOrUpdate(DiscoveryMessage msg) {
        ResourceGroup group = findOrCreateDiscoveryGroup();

        Resource resource = resourceRepository.findByMqttUUID(msg.uuid()).orElseGet(Resource::new);
        boolean isNew = resource.getId() == null;

        resource.setName(msg.name());
        resource.setDescription(msg.description());
        resource.setMqttResource(true);
        resource.setMqttOnline(true);
        resource.setMqttUUID(msg.uuid());
        resource.setMqttLastPing(LocalDateTime.now());
        resource.setResourceGroup(group);
        resource.setDepartment(group.getDepartment());

        if (isNew) {
            // Lombok @Builder.Default values do not apply to new Resource(); set sane defaults.
            resource.setStationary(true);
            resource.setRemote(true);
            resource.setBusy(false);
            resource.setAgent(false);
            resource.setMaxSlots(1);
            resource.setCurrentOccupiedSlots(0);
        }

        applyTransports(resource, msg.control());
        if (resource.getPort() == null) {
            resource.setPort(80);
        }
        applyCommands(resource, msg.commands());

        Resource saved = resourceRepository.save(resource);
        log.info("DISCOVERY: {} MQTT resource '{}' (uuid={}) with {} command(s) in group '{}'.",
            isNew ? "registered" : "updated", saved.getName(), saved.getMqttUUID(),
            saved.getMqttCommands() != null ? saved.getMqttCommands().size() : 0, group.getName());
        return saved;
    }

    private void applyTransports(Resource resource, DiscoveryMessage.Control control) {
        if (control == null) {
            return;
        }
        if (control.mqtt() != null) {
            resource.setMqttDataReceiveTopic(control.mqtt().receiveTopic());
            resource.setMqttDataSendTopic(control.mqtt().sendTopic());
        }
        if (control.rest() != null) {
            resource.setIp(control.rest().ip());
            if (control.rest().port() != null) {
                resource.setPort(control.rest().port());
            }
        }
    }

    /**
     * Rebuilds the resource's command set from the discovery message. On update the old
     * commands are cleared in place so JPA orphan removal deletes them (and their
     * parameters) cleanly, rather than replacing the managed collection reference.
     */
    private void applyCommands(Resource resource, List<DiscoveryMessage.CommandSpec> specs) {
        Set<MqttCommand> commands = mapCommands(specs);
        if (resource.getMqttCommands() == null) {
            resource.setMqttCommands(commands);
        } else {
            resource.getMqttCommands().clear();
            resource.getMqttCommands().addAll(commands);
        }
    }

    private Set<MqttCommand> mapCommands(List<DiscoveryMessage.CommandSpec> specs) {
        Set<MqttCommand> result = new HashSet<>();
        if (specs == null) {
            return result;
        }
        for (DiscoveryMessage.CommandSpec spec : specs) {
            if (spec.name() == null || spec.name().isBlank()) {
                continue;
            }
            MqttCommand command = new MqttCommand();
            command.setName(spec.name());
            command.setDescription(spec.description());
            command.setParameters(mapParameters(spec.parameters()));
            result.add(command);
        }
        return result;
    }

    private Set<MqttCommandParameter> mapParameters(List<DiscoveryMessage.ParameterSpec> specs) {
        Set<MqttCommandParameter> result = new HashSet<>();
        if (specs == null) {
            return result;
        }
        for (DiscoveryMessage.ParameterSpec spec : specs) {
            if (spec.name() == null || spec.name().isBlank()) {
                continue;
            }
            MqttCommandParameter param = new MqttCommandParameter();
            param.setName(spec.name());
            param.setType(spec.type());
            param.setMinValue(spec.min());
            param.setMaxValue(spec.max());
            param.setUnit(spec.unit());
            param.setRequired(Boolean.TRUE.equals(spec.required()));
            if (spec.values() != null) {
                param.setAllowedValues(new HashSet<>(spec.values()));
            }
            result.add(param);
        }
        return result;
    }

    private ResourceGroup findOrCreateDiscoveryGroup() {
        return resourceGroupRepository.findByNameContainingIgnoreCase(DISCOVERY_GROUP_NAME).stream()
            .filter(g -> DISCOVERY_GROUP_NAME.equalsIgnoreCase(g.getName()))
            .findFirst()
            .orElseGet(this::createDiscoveryGroup);
    }

    private ResourceGroup createDiscoveryGroup() {
        Department department = departmentRepository.findAll().stream().findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "Cannot register MQTT device: no department exists yet."));
        ResourceGroup group = ResourceGroup.builder()
            .name(DISCOVERY_GROUP_NAME)
            .department(department)
            .build();
        log.info("Created resource group '{}' for self-registered MQTT devices.", DISCOVERY_GROUP_NAME);
        return resourceGroupRepository.save(group);
    }
}
