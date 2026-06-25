package de.hallerweb.enterprise.prioritize.service.resource.control.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.hallerweb.enterprise.prioritize.service.resource.ResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Processes inbound MQTT messages (device → system) from the {@code mqttInboundChannel}.
 * <p>
 * Current scope of this iteration: status messages (online/offline). Discovery
 * (self-registration of new devices) and telemetry (key/value values) are prepared
 * as hooks but not yet implemented — for now they only log.
 * <p>
 * Inbound JSON (examples):
 * <pre>
 * Status:   { "type": "STATUS", "uuid": "...", "online": true }
 * Discovery:{ "type": "DISCOVERY", "uuid": "...", ... }   (later)
 * Telemetry:{ "type": "VALUE", "uuid": "...", "name": "temp", "value": "21" } (later)
 * </pre>
 *
 * @author peter haller
 */
@Component
@ConditionalOnProperty(name = "prioritize.mqtt.enabled", havingValue = "true")
@RequiredArgsConstructor
@Log4j2
public class InboundResourceEventHandler {

    private final ResourceService resourceService;
    private final ObjectMapper objectMapper;

    @ServiceActivator(inputChannel = "mqttInboundChannel")
    public void handle(Message<?> message,
                       @Header(name = "mqtt_receivedTopic", required = false) String topic) {
        String payload = String.valueOf(message.getPayload());
        try {
            JsonNode node = objectMapper.readTree(payload);
            String type = node.path("type").asText("");

            switch (type) {
                case "STATUS" -> handleStatus(node);
                case "DISCOVERY" -> handleDiscovery(node);   // Hook, s.u.
                case "VALUE" -> handleTelemetry(node);       // Hook, s.u.
                default -> log.warn("Unknown inbound type '{}' on topic '{}': {}",
                        type, topic, payload);
            }
        } catch (Exception ex) {
            log.error("Inbound MQTT message on topic '{}' could not be processed: {}",
                    topic, ex.getMessage());
        }
    }

    private void handleStatus(JsonNode node) {
        String uuid = node.path("uuid").asText(null);
        boolean online = node.path("online").asBoolean(false);
        if (uuid == null) {
            log.warn("STATUS message without uuid ignored.");
            return;
        }
        resourceService.setMqttResourceStatusByUuid(uuid, online);
        log.debug("Resource (uuid={}) status set: online={}", uuid, online);
    }

    // ---------------- Hooks for later iterations ----------------

    private void handleDiscovery(JsonNode node) {
        // TODO (later iteration): self-registration of new MQTT devices.
        log.info("DISCOVERY received (not yet implemented): {}", node);
    }

    private void handleTelemetry(JsonNode node) {
        // TODO (later iteration): persist NameValueEntry telemetry.
        log.debug("VALUE received (not yet implemented): {}", node);
    }
}
