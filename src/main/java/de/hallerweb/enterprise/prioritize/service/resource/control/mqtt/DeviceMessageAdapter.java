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

import java.util.Map;
import java.util.Optional;

/**
 * Translates an MQTT message in a device's <em>own</em> format into what the platform understands.
 * Off-the-shelf firmware (Tasmota today; Shelly, ESPHome and the like are candidates) publishes on its
 * own topics with its own JSON, and should not have to be reflashed or bridged to speak the platform's
 * {@code VALUE}/{@code STATUS} messages.
 * <p>
 * The inbound handler asks every adapter in turn, before falling back to the native format; the first
 * one that recognizes the topic wins. Recognition is by topic shape only, so an adapter must not claim
 * a topic the native format uses ({@code DISCOVERY}, {@code <uuid>/status}, {@code <uuid>/values}).
 *
 * @author peter haller
 */
public interface DeviceMessageAdapter {

    /**
     * Parses {@code payload} if {@code topic} is one of this adapter's topics.
     *
     * @return the translated message, or empty if the topic is not this adapter's
     */
    Optional<DeviceMessage> parse(String topic, String payload);

    /**
     * A device message in platform terms. Exactly one of the two parts is set.
     *
     * @param deviceTopic the device's own topic, matched against {@code Resource.mqttDeviceTopic}
     * @param readings    data point name to value, in message order; {@code null} for an availability message
     * @param online      the device's availability; {@code null} for a readings message
     */
    record DeviceMessage(String deviceTopic, Map<String, String> readings, Boolean online) {

        static DeviceMessage readings(String deviceTopic, Map<String, String> readings) {
            return new DeviceMessage(deviceTopic, readings, null);
        }

        static DeviceMessage availability(String deviceTopic, boolean online) {
            return new DeviceMessage(deviceTopic, null, online);
        }
    }
}
