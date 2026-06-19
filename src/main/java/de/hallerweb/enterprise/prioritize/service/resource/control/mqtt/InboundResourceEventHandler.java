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
 * Verarbeitet eingehende MQTT-Nachrichten (Gerät → System) vom {@code mqttInboundChannel}.
 * <p>
 * Aktueller Umfang dieser Iteration: Status-Meldungen (online/offline). Discovery
 * (Selbstregistrierung neuer Geräte) und Telemetrie (Key/Value-Werte) sind als Hooks
 * vorbereitet, aber noch nicht implementiert — sie loggen vorerst nur.
 * <p>
 * Inbound-JSON (Beispiele):
 * <pre>
 * Status:   { "type": "STATUS", "uuid": "...", "online": true }
 * Discovery:{ "type": "DISCOVERY", "uuid": "...", ... }   (später)
 * Telemetry:{ "type": "VALUE", "uuid": "...", "name": "temp", "value": "21" } (später)
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
                default -> log.warn("Unbekannter Inbound-Typ '{}' auf Topic '{}': {}",
                        type, topic, payload);
            }
        } catch (Exception ex) {
            log.error("Inbound-MQTT-Nachricht auf Topic '{}' nicht verarbeitbar: {}",
                    topic, ex.getMessage());
        }
    }

    private void handleStatus(JsonNode node) {
        String uuid = node.path("uuid").asText(null);
        boolean online = node.path("online").asBoolean(false);
        if (uuid == null) {
            log.warn("STATUS-Meldung ohne uuid ignoriert.");
            return;
        }
        resourceService.setMqttResourceStatusByUuid(uuid, online);
        log.debug("Resource (uuid={}) Status gesetzt: online={}", uuid, online);
    }

    // ---------------- Hooks für spätere Iterationen ----------------

    private void handleDiscovery(JsonNode node) {
        // TODO (spätere Iteration): Selbstregistrierung neuer MQTT-Geräte.
        log.info("DISCOVERY empfangen (noch nicht implementiert): {}", node);
    }

    private void handleTelemetry(JsonNode node) {
        // TODO (spätere Iteration): NameValueEntry-Telemetrie speichern.
        log.debug("VALUE empfangen (noch nicht implementiert): {}", node);
    }
}
