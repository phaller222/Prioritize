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

package de.hallerweb.enterprise.prioritize.service.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Broadcasts {@link TelemetryThresholdEvent telemetry transitions} over MQTT so dashboards and
 * devices see alarms (raised and cleared) live. Reuses the existing outbound
 * {@code mqttOutboundChannel} and publishes to {@code telemetry/alarm/<resourceId>}, a namespace
 * outside the app's own {@code +/status} / {@code +/values} subscriptions (no self-echo). This is
 * the first — and so far only — reaction wired to the event; task creation, workflow start and
 * inbox messages are follow-up slices that listen for the same event.
 * <p>
 * Only active when {@code prioritize.mqtt.enabled=true}; without MQTT this bean does not exist and
 * the event simply has no listener, so ingest works unchanged. Handled
 * {@link TransactionPhase#AFTER_COMMIT AFTER_COMMIT}, so only persisted transitions are broadcast;
 * a publish failure is logged and never propagated back to the (already committed) ingest.
 *
 * @author peter haller
 */
@Component
@ConditionalOnProperty(name = "prioritize.mqtt.enabled", havingValue = "true")
@RequiredArgsConstructor
@Log4j2
public class TelemetryAlarmMqttBridge {

    /** Topic prefix; the resource id is appended. Not covered by the inbound subscriptions. */
    static final String TOPIC_PREFIX = "telemetry/alarm/";

    /** Outbound channel; a handler in the integration config publishes to the MQTT topic. */
    private final MessageChannel mqttOutboundChannel;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onThreshold(TelemetryThresholdEvent event) {
        try {
            String json = objectMapper.writeValueAsString(TelemetryAlarmBroadcast.from(event));
            mqttOutboundChannel.send(MessageBuilder
                    .withPayload(json)
                    .setHeader(MqttHeaders.TOPIC, TOPIC_PREFIX + event.resourceId())
                    .build());
            log.debug("Telemetry alarm for resource {} ({} {}) broadcast on topic '{}{}'.",
                    event.resourceId(), event.datapoint(), event.newState(),
                    TOPIC_PREFIX, event.resourceId());
        } catch (Exception ex) {
            // Never let a broadcast failure affect the already-committed ingest.
            log.warn("Telemetry alarm broadcast for resource {} failed: {}",
                    event.resourceId(), ex.getMessage());
        }
    }
}
