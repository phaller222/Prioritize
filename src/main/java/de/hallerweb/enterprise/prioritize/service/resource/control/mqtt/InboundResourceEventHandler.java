package de.hallerweb.enterprise.prioritize.service.resource.control.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
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
 * Current scope: status messages (online/offline) and discovery (self-registration
 * of new devices, see {@link MqttDiscoveryService}). Telemetry (key/value values) is
 * prepared as a hook but not yet implemented — for now it only logs.
 * <p>
 * Inbound JSON (examples):
 * <pre>
 * Status:   { "type": "STATUS", "uuid": "...", "online": true }
 * Discovery:{ "type": "DISCOVERY", "uuid": "...", "name": "...", "description": "...", ... }
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
    private final MqttDiscoveryService discoveryService;

    @ServiceActivator(inputChannel = "mqttInboundChannel")
    public void handle(Message<?> message,
                       @Header(name = "mqtt_receivedTopic", required = false) String topic) {
        String payload = String.valueOf(message.getPayload());
        try {
            JsonNode node = objectMapper.readTree(payload);
            String type = node.path("type").asText("");

            switch (type) {
                case "STATUS" -> handleStatus(node);
                case "DISCOVERY" -> handleDiscovery(node);
                case "VALUE" -> handleTelemetry(node);       // Hook, see below
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

    /**
     * Self-registration of a new (or already known) MQTT device. Parses the payload into
     * a {@link DiscoveryMessage}, enforces the mandatory fields (uuid, name, description)
     * and delegates persistence to {@link MqttDiscoveryService}.
     */
    private void handleDiscovery(JsonNode node) {
        DiscoveryMessage msg;
        try {
            msg = objectMapper.treeToValue(node, DiscoveryMessage.class);
        } catch (JsonProcessingException e) {
            log.warn("DISCOVERY message could not be parsed: {}", e.getMessage());
            return;
        }
        if (isBlank(msg.uuid()) || isBlank(msg.name()) || isBlank(msg.description())) {
            log.warn("DISCOVERY ignored: uuid, name and description are mandatory. Payload: {}", node);
            return;
        }
        discoveryService.registerOrUpdate(msg);
    }

    // ---------------- Hook for a later iteration ----------------

    private void handleTelemetry(JsonNode node) {
        // TODO (later iteration): persist NameValueEntry telemetry.
        log.debug("VALUE received (not yet implemented): {}", node);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
