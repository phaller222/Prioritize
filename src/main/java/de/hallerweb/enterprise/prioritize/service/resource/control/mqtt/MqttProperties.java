package de.hallerweb.enterprise.prioritize.service.resource.control.mqtt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Type-safe MQTT configuration, bound to the prefix {@code prioritize.mqtt}.
 * <p>
 * Example (application-mqtt.yaml):
 * <pre>
 * prioritize:
 *   mqtt:
 *     enabled: true
 *     broker-url: tcp://memoryalpha:1883
 *     client-id: prioritize-backend
 *     username: prioritize
 *     password: ${MQTT_PASSWORD}
 *     qos: 1
 *     subscribe-topics:
 *       - DISCOVERY
 *       - "+/status"
 * </pre>
 *
 * @author peter haller
 */
@Configuration
@ConditionalOnProperty(name = "prioritize.mqtt.enabled", havingValue = "true")
@ConfigurationProperties(prefix = "prioritize.mqtt")
@Getter
@Setter
public class MqttProperties {

    /** Arms the entire MQTT module. */
    private boolean enabled = false;

    /** Broker URL, e.g. tcp://memoryalpha:1883 (TLS later: ssl://...:8883). */
    private String brokerUrl = "tcp://localhost:1883";

    /** Basis-Client-ID; pub/sub erhalten Suffixe. */
    private String clientId = "prioritize-backend";

    private String username;
    private String password;

    /** Quality of Service (0, 1 or 2). */
    private int qos = 1;

    /** Topics that the backend subscribes to (discovery, status, ...). */
    private List<String> subscribeTopics = List.of("DISCOVERY");
}
