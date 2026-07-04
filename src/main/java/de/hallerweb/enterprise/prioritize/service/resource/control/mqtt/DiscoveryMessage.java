package de.hallerweb.enterprise.prioritize.service.resource.control.mqtt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.hallerweb.enterprise.prioritize.model.resource.MqttParameterType;

import java.util.List;

/**
 * Inbound DISCOVERY wire format (device -&gt; system). A device announces itself so the
 * system can register or update it as a controllable resource without manual setup.
 * <p>
 * Mandatory: {@code uuid}, {@code name}, {@code description}. The optional
 * {@code control} block declares the supported transports (MQTT and/or REST); the
 * optional {@code commands} list declares the device's commands and their parameters.
 * Unknown fields (such as the routing {@code type}) are ignored.
 *
 * <pre>
 * {
 *   "type": "DISCOVERY",
 *   "uuid": "69178331-8dd9-4dd1-87f6-368f424006c2",
 *   "name": "Living Room Lamp",
 *   "description": "Dimmable RGB lamp",
 *   "control": { "mqtt": { "receiveTopic": "devices/lamp-1/cmd", "sendTopic": "devices/lamp-1/evt" },
 *                "rest": { "ip": "192.168.1.50", "port": 80 } },
 *   "commands": [
 *     { "name": "ON" },
 *     { "name": "SET_BRIGHTNESS", "description": "Brightness in percent",
 *       "parameters": [ { "name": "level", "type": "INT", "min": 0, "max": 100, "unit": "%" } ] }
 *   ]
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DiscoveryMessage(
    String uuid,
    String name,
    String description,
    Control control,
    List<CommandSpec> commands) {

    /** Declares which transports the device supports; either side may be absent. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Control(MqttControl mqtt, RestControl rest) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MqttControl(String receiveTopic, String sendTopic) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RestControl(String ip, Integer port) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommandSpec(String name, String description, List<ParameterSpec> parameters) {
    }

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
