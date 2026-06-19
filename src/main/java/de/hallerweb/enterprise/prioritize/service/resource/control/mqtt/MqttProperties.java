package de.hallerweb.enterprise.prioritize.service.resource.control.mqtt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Typsichere MQTT-Konfiguration, gebunden an den Prefix {@code prioritize.mqtt}.
 * <p>
 * Beispiel (application-mqtt.yaml):
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

    /** Schaltet das gesamte MQTT-Modul scharf. */
    private boolean enabled = false;

    /** Broker-URL, z.B. tcp://memoryalpha:1883 (TLS später: ssl://...:8883). */
    private String brokerUrl = "tcp://localhost:1883";

    /** Basis-Client-ID; pub/sub erhalten Suffixe. */
    private String clientId = "prioritize-backend";

    private String username;
    private String password;

    /** Quality of Service (0, 1 oder 2). */
    private int qos = 1;

    /** Topics, die das Backend abonniert (Discovery, Status, ...). */
    private List<String> subscribeTopics = List.of("DISCOVERY");
}
