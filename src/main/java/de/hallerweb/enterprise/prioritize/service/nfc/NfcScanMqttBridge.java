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

package de.hallerweb.enterprise.prioritize.service.nfc;

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
 * Broadcasts {@link NfcScannedEvent scan events} over MQTT so devices and dashboards see tag scans
 * (tracking started/stopped, counter, check-in) live. Reuses the existing outbound
 * {@code mqttOutboundChannel} &mdash; the same path {@code MqttResourceControlAdapter} uses for
 * device commands &mdash; and publishes to {@code nfc/scan/<tag-uuid>}, a namespace deliberately
 * outside the app's own {@code +/status} / {@code +/values} subscriptions (no self-echo).
 * <p>
 * Only active when {@code prioritize.mqtt.enabled=true}; without MQTT this bean does not exist and
 * the raised event simply has no listener, so scanning works unchanged. The event is handled
 * {@link TransactionPhase#AFTER_COMMIT AFTER_COMMIT}, so only actually persisted scans are
 * broadcast; a publish failure is logged and never propagated back to the (already committed) scan.
 *
 * @author peter haller
 */
@Component
@ConditionalOnProperty(name = "prioritize.mqtt.enabled", havingValue = "true")
@RequiredArgsConstructor
@Log4j2
public class NfcScanMqttBridge {

    /** Topic prefix; the tag uuid is appended. Not covered by the inbound subscriptions. */
    static final String TOPIC_PREFIX = "nfc/scan/";

    /** Outbound channel; a handler in the integration config publishes to the MQTT topic. */
    private final MessageChannel mqttOutboundChannel;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onScan(NfcScannedEvent event) {
        String uuid = event.result().uuid();
        try {
            String json = objectMapper.writeValueAsString(NfcScanBroadcast.from(event));
            mqttOutboundChannel.send(MessageBuilder
                    .withPayload(json)
                    .setHeader(MqttHeaders.TOPIC, TOPIC_PREFIX + uuid)
                    .build());
            log.debug("NFC scan of tag '{}' broadcast on topic '{}{}'.", uuid, TOPIC_PREFIX, uuid);
        } catch (Exception ex) {
            // Never let a broadcast failure affect the already-committed scan.
            log.warn("NFC scan broadcast for tag '{}' failed: {}", uuid, ex.getMessage());
        }
    }
}
