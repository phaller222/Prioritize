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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.service.resource.control.ResourceCommandMessage;
import de.hallerweb.enterprise.prioritize.service.resource.control.ResourceControlAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * {@link ResourceControlAdapter} implementation for MQTT-controlled resources.
 * <p>
 * Publishes commands as JSON to the resource's device-side receive topic.
 * The actual Paho lifecycle (connect/reconnect/subscribe) is managed by Spring
 * Integration (see {@code MqttIntegrationConfig}); this adapter knows
 * only an outbound {@link MessageChannel} and nothing about Paho.
 * <p>
 * Only active when {@code prioritize.mqtt.enabled=true} is set.
 *
 * @author peter haller
 */
@Component
@ConditionalOnProperty(name = "prioritize.mqtt.enabled", havingValue = "true")
@RequiredArgsConstructor
@Log4j2
public class MqttResourceControlAdapter implements ResourceControlAdapter {

    /** Outbound channel; a handler in the integration config publishes to the MQTT topic. */
    private final MessageChannel mqttOutboundChannel;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(Resource resource) {
        return Boolean.TRUE.equals(resource.getMqttResource());
    }

    @Override
    public boolean isAvailable(Resource resource) {
        return supports(resource) && Boolean.TRUE.equals(resource.getMqttOnline());
    }

    @Override
    public void sendCommand(Resource resource, String command, String param, int slot) {
        String topic = resource.getMqttDataReceiveTopic();
        if (topic == null || topic.isBlank()) {
            throw new IllegalStateException(
                "MQTT resource " + resource.getId() + " has no receive topic configured.");
        }

        ResourceCommandMessage payload = new ResourceCommandMessage(command, param, slot);
        try {
            String json = objectMapper.writeValueAsString(payload);
            mqttOutboundChannel.send(MessageBuilder
                .withPayload(json)
                .setHeader(MqttHeaders.TOPIC, topic)
                .build());
            log.debug("MQTT command '{}' sent to resource {} (slot {}, topic {}).",
                command, resource.getId(), slot, topic);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Command payload could not be serialized.", e);
        }
    }

    @Override
    public String getTransportName() {
        return "MQTT";
    }
}