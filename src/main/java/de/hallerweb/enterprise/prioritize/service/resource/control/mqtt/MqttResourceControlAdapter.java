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
 * {@link ResourceControlAdapter}-Implementierung für MQTT-gesteuerte Resourcen.
 * <p>
 * Publisht Kommandos als JSON auf das geräteseitige Receive-Topic der Resource.
 * Der eigentliche Paho-Lifecycle (connect/reconnect/subscribe) wird von Spring
 * Integration verwaltet (siehe {@code MqttIntegrationConfig}); dieser Adapter kennt
 * nur einen ausgehenden {@link MessageChannel} und nichts von Paho.
 * <p>
 * Nur aktiv, wenn {@code prioritize.mqtt.enabled=true} gesetzt ist.
 *
 * @author peter haller
 */
@Component
@ConditionalOnProperty(name = "prioritize.mqtt.enabled", havingValue = "true")
@RequiredArgsConstructor
@Log4j2
public class MqttResourceControlAdapter implements ResourceControlAdapter {

    /** Ausgehender Channel; ein Handler in der Integration-Config publisht ans MQTT-Topic. */
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
    public void sendCommand(Resource resource, String command, String param) {
        String topic = resource.getMqttDataReceiveTopic();
        if (topic == null || topic.isBlank()) {
            throw new IllegalStateException(
                    "MQTT-Resource " + resource.getId() + " hat kein Receive-Topic konfiguriert.");
        }

        ResourceCommandMessage payload = new ResourceCommandMessage(command, param, 0);
        try {
            String json = objectMapper.writeValueAsString(payload);
            mqttOutboundChannel.send(MessageBuilder
                    .withPayload(json)
                    .setHeader(MqttHeaders.TOPIC, topic)
                    .build());
            log.debug("MQTT-Command '{}' an Resource {} (Topic {}) gesendet.",
                    command, resource.getId(), topic);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Command-Payload konnte nicht serialisiert werden.", e);
        }
    }

    @Override
    public String getTransportName() {
        return "MQTT";
    }
}
