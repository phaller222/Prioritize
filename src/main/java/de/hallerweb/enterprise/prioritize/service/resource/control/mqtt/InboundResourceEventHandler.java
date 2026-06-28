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
 * Current scope: status messages (online/offline), discovery (self-registration of new
 * devices, see {@link MqttDiscoveryService}) and telemetry (key/value readings, persisted
 * as {@code NameValueEntry} history on the resource).
 * <p>
 * Inbound JSON (examples):
 * <pre>
 * Status:   { "type": "STATUS", "uuid": "...", "online": true }
 * Discovery:{ "type": "DISCOVERY", "uuid": "...", "name": "...", "description": "...", ... }
 * Telemetry:{ "type": "VALUE", "uuid": "...", "name": "temp", "value": "21" }
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
                case "VALUE" -> handleTelemetry(node);
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

    /**
     * Telemetry (a single key/value reading). Reads the mandatory uuid, name and value and
     * delegates to {@link ResourceService#recordMqttValueByUuid(String, String, String)},
     * which appends the value to the data point's history.
     */
    private void handleTelemetry(JsonNode node) {
        String uuid = node.path("uuid").asText(null);
        String name = node.path("name").asText(null);
        String value = node.path("value").asText(null);
        if (uuid == null || name == null || value == null) {
            log.warn("VALUE ignored: uuid, name and value are required. Payload: {}", node);
            return;
        }
        resourceService.recordMqttValueByUuid(uuid, name, value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
