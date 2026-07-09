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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.hallerweb.enterprise.prioritize.model.nfc.NfcUnit.NfcUnitType;
import de.hallerweb.enterprise.prioritize.service.nfc.NfcUnitService.ScanResult;
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
 * Pure unit tests for {@link NfcScanMqttBridge} — no Spring context, no broker.
 *
 * @author peter haller
 */
class NfcScanMqttBridgeTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private static NfcScannedEvent trackerStartedEvent(String uuid) {
        ScanResult result = new ScanResult(uuid, NfcUnitType.TIMETRACKER, "TRACKING_STARTED",
                42L, true, 0L);
        return new NfcScannedEvent(result, 7L, "admin", Instant.parse("2026-07-09T10:15:30Z"));
    }

    @Test
    @DisplayName("onScan: publiziert das ScanResult als JSON auf nfc/scan/<uuid>")
    void onScan_publishesToPerTagTopic() {
        MessageChannel channel = mock(MessageChannel.class);
        when(channel.send(any())).thenReturn(true);
        NfcScanMqttBridge bridge = new NfcScanMqttBridge(channel, objectMapper);

        bridge.onScan(trackerStartedEvent("tag-123"));

        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
        verify(channel).send(captor.capture());
        Message<?> sent = captor.getValue();

        assertEquals("nfc/scan/tag-123", sent.getHeaders().get(MqttHeaders.TOPIC));
        String json = (String) sent.getPayload();
        assertTrue(json.contains("\"type\":\"NFC_SCAN\""), json);
        assertTrue(json.contains("\"nfcType\":\"TIMETRACKER\""), json);
        assertTrue(json.contains("\"action\":\"TRACKING_STARTED\""), json);
        assertTrue(json.contains("\"taskId\":42"), json);
        assertTrue(json.contains("\"tracking\":true"), json);
        assertTrue(json.contains("\"resourceId\":7"), json);
        assertTrue(json.contains("\"scannedBy\":\"admin\""), json);
    }

    @Test
    @DisplayName("onScan: Serialisierungsfehler wird verschluckt, nichts wird gesendet")
    void onScan_serializationFailure_isSwallowed() throws JsonProcessingException {
        MessageChannel channel = mock(MessageChannel.class);
        ObjectMapper failing = mock(ObjectMapper.class);
        when(failing.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {
        });
        NfcScanMqttBridge bridge = new NfcScanMqttBridge(channel, failing);

        assertDoesNotThrow(() -> bridge.onScan(trackerStartedEvent("tag-err")));
        verify(channel, never()).send(any());
    }
}
