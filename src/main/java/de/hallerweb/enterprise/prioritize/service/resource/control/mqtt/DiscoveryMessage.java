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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.hallerweb.enterprise.prioritize.model.resource.MqttParameterType;

import java.util.List;

/**
 * Immutable representation of a device's MQTT DISCOVERY payload, by which a device
 * announces (self-registers) itself and declares its transports and controllable
 * commands. Parsed from JSON and consumed by {@link MqttDiscoveryService}.
 * <p>
 * Only {@code uuid}, {@code name} and {@code description} are mandatory (enforced by
 * {@link InboundResourceEventHandler}); {@code control} and {@code commands} are optional.
 * Unknown JSON fields (e.g. the routing {@code type} discriminator) are ignored so the
 * same envelope can carry the message type without breaking deserialization.
 *
 * @param uuid        the device's stable MQTT UUID (registration key)
 * @param name        human-readable device name
 * @param description short description of the device
 * @param control     available transports (MQTT/REST); may be {@code null}
 * @param commands    controllable commands declared by the device; may be {@code null}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DiscoveryMessage(
        String uuid,
        String name,
        String description,
        Control control,
        List<CommandSpec> commands) {

    /**
     * The transports a device offers. Either side may be {@code null} if the device does
     * not support that transport.
     *
     * @param mqtt MQTT transport (command/event topics); may be {@code null}
     * @param rest REST transport (ip/port); may be {@code null}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Control(MqttControl mqtt, RestControl rest) {
    }

    /**
     * MQTT transport coordinates.
     *
     * @param receiveTopic topic the device receives commands on (backend → device)
     * @param sendTopic    topic the device publishes events on (device → backend)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MqttControl(String receiveTopic, String sendTopic) {
    }

    /**
     * REST transport coordinates.
     *
     * @param ip   the device's IP address
     * @param port the device's HTTP port; may be {@code null} (defaults applied downstream)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RestControl(String ip, Integer port) {
    }

    /**
     * A single controllable command declared by the device.
     *
     * @param name        command name (e.g. {@code SET_BRIGHTNESS}); mandatory
     * @param description short description; may be {@code null}
     * @param parameters  parameters accepted by the command; may be {@code null}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommandSpec(String name, String description, List<ParameterSpec> parameters) {
    }

    /**
     * A single parameter of a {@link CommandSpec}.
     *
     * @param name     parameter name (e.g. {@code level}); mandatory
     * @param type     value type
     * @param min      inclusive lower bound for numeric types; may be {@code null}
     * @param max      inclusive upper bound for numeric types; may be {@code null}
     * @param unit     optional display unit (e.g. {@code %}); may be {@code null}
     * @param required whether the parameter must be supplied; may be {@code null} (treated as false)
     * @param values   allowed values for {@link MqttParameterType#ENUM}; may be {@code null}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ParameterSpec(
            String name,
            MqttParameterType type,
            Double min,
            Double max,
            String unit,
            Boolean required,
            List<String> values) {
    }
}
