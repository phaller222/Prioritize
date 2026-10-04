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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Reads Tasmota's telemetry with its default {@code FullTopic} ({@code %prefix%/%topic%/}):
 * <ul>
 *   <li>{@code tele/<topic>/SENSOR} — the periodic sensor report. Its JSON is flattened into data points
 *       named by their path, e.g. {@code {"MT681":{"Total_in":15119.035}}} becomes
 *       {@code MT681.Total_in = 15119.035}; array elements get their index ({@code ENERGY.Voltage.0}).
 *       The top-level {@code Time} is dropped: it is local time without an offset, and the platform
 *       stamps readings with their arrival time anyway.</li>
 *   <li>{@code tele/<topic>/LWT} — the last will, {@code Online} or {@code Offline}, which becomes the
 *       resource's online flag. This is how a reader that lost power or Wi-Fi shows up.</li>
 * </ul>
 * Everything else under {@code tele/} ({@code STATE}, {@code INFO1}…) is not claimed and falls through.
 *
 * @author peter haller
 */
@Component
@ConditionalOnProperty(name = "prioritize.mqtt.enabled", havingValue = "true")
@RequiredArgsConstructor
@Log4j2
public class TasmotaMessageAdapter implements DeviceMessageAdapter {

    private static final Pattern TELE_TOPIC = Pattern.compile("tele/([^/]+)/(SENSOR|LWT)");

    private final ObjectMapper objectMapper;

    @Override
    public Optional<DeviceMessage> parse(String topic, String payload) {
        if (topic == null) {
            return Optional.empty();
        }
        Matcher matcher = TELE_TOPIC.matcher(topic);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String deviceTopic = matcher.group(1);
        if ("LWT".equals(matcher.group(2))) {
            return Optional.of(DeviceMessage.availability(deviceTopic, "Online".equalsIgnoreCase(payload.trim())));
        }
        try {
            return Optional.of(DeviceMessage.readings(deviceTopic, flatten(objectMapper.readTree(payload))));
        } catch (JsonProcessingException e) {
            log.warn("Tasmota SENSOR message on '{}' is not valid JSON: {}", topic, e.getOriginalMessage());
            return Optional.of(DeviceMessage.readings(deviceTopic, Map.of()));
        }
    }

    /** Flattens a SENSOR report into path-named readings, dropping the top-level {@code Time}. */
    static Map<String, String> flatten(JsonNode root) {
        Map<String, String> readings = new LinkedHashMap<>();
        if (root != null && root.isObject()) {
            root.properties().forEach(field -> {
                if (!"Time".equals(field.getKey())) {
                    collect(field.getKey(), field.getValue(), readings);
                }
            });
        }
        return readings;
    }

    private static void collect(String path, JsonNode node, Map<String, String> readings) {
        if (node.isObject()) {
            node.properties().forEach(field -> collect(path + "." + field.getKey(), field.getValue(), readings));
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                collect(path + "." + i, node.get(i), readings);
            }
        } else if (!node.isNull()) {
            readings.put(path, node.asText());
        }
    }
}
