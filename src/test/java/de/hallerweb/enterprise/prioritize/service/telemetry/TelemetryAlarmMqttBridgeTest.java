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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.hallerweb.enterprise.prioritize.model.telemetry.Severity;
import de.hallerweb.enterprise.prioritize.model.telemetry.TelemetryState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link TelemetryAlarmMqttBridge} — no Spring context, no broker.
 *
 * @author peter haller
 */
class TelemetryAlarmMqttBridgeTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private static TelemetryThresholdEvent alarmEvent() {
        return new TelemetryThresholdEvent(5L, 7L, "temp", 35.0,
                TelemetryState.ALARM, Severity.CRITICAL, Instant.parse("2026-07-19T10:15:30Z"));
    }

    @Test
    @DisplayName("onThreshold: publiziert die Transition als JSON auf telemetry/alarm/<resourceId>")
    void onThreshold_publishesToPerResourceTopic() {
        MessageChannel channel = mock(MessageChannel.class);
        when(channel.send(any())).thenReturn(true);
        TelemetryAlarmMqttBridge bridge = new TelemetryAlarmMqttBridge(channel, objectMapper);

        bridge.onThreshold(alarmEvent());

        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
        verify(channel).send(captor.capture());
        Message<?> sent = captor.getValue();

        assertEquals("telemetry/alarm/7", sent.getHeaders().get(MqttHeaders.TOPIC));
        String json = (String) sent.getPayload();
        assertTrue(json.contains("\"type\":\"TELEMETRY_ALARM\""), json);
        assertTrue(json.contains("\"ruleId\":5"), json);
        assertTrue(json.contains("\"resourceId\":7"), json);
        assertTrue(json.contains("\"datapoint\":\"temp\""), json);
        assertTrue(json.contains("\"state\":\"ALARM\""), json);
        assertTrue(json.contains("\"severity\":\"CRITICAL\""), json);
    }

    @Test
    @DisplayName("onThreshold: Serialisierungsfehler wird verschluckt, nichts wird gesendet")
    void onThreshold_serializationFailure_isSwallowed() throws JsonProcessingException {
        MessageChannel channel = mock(MessageChannel.class);
        ObjectMapper failing = mock(ObjectMapper.class);
        when(failing.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {
        });
        TelemetryAlarmMqttBridge bridge = new TelemetryAlarmMqttBridge(channel, failing);

        assertDoesNotThrow(() -> bridge.onThreshold(alarmEvent()));
        verify(channel, never()).send(any());
    }
}
