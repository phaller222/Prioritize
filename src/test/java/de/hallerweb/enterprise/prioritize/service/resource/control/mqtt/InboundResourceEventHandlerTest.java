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

import com.fasterxml.jackson.databind.ObjectMapper;
import de.hallerweb.enterprise.prioritize.service.resource.ResourceService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests the inbound routing of {@link InboundResourceEventHandler}: JSON parsing,
 * mandatory-field guard for DISCOVERY and delegation to the right service. Uses a real
 * {@link ObjectMapper} and mocked services (no Spring context, no broker).
 */
class InboundResourceEventHandlerTest {

    private ResourceService resourceService;
    private MqttDiscoveryService discoveryService;
    private InboundResourceEventHandler handler;

    @BeforeEach
    void setUp() {
        resourceService = mock(ResourceService.class);
        discoveryService = mock(MqttDiscoveryService.class);
        handler = new InboundResourceEventHandler(resourceService, new ObjectMapper(), discoveryService,
                java.util.List.of(new TasmotaMessageAdapter(new ObjectMapper())));
    }

    private void dispatch(String payload) {
        Message<String> message = new GenericMessage<>(payload);
        handler.handle(message, "devices/test/inbound");
    }

    @Test
    @DisplayName("Valid DISCOVERY is parsed and delegated to the discovery service")
    void discovery_isDelegated() {
        dispatch("""
            { "type": "DISCOVERY", "uuid": "u1", "name": "Lamp", "description": "d",
              "commands": [ { "name": "ON" } ] }
            """);

        ArgumentCaptor<DiscoveryMessage> captor = ArgumentCaptor.forClass(DiscoveryMessage.class);
        verify(discoveryService).registerOrUpdate(captor.capture());
        DiscoveryMessage msg = captor.getValue();
        assertEquals("u1", msg.uuid());
        assertEquals("Lamp", msg.name());
        assertEquals(1, msg.commands().size());
    }

    @Test
    @DisplayName("DISCOVERY missing a mandatory field (description) is ignored")
    void discovery_missingMandatoryField_isIgnored() {
        dispatch("""
            { "type": "DISCOVERY", "uuid": "u1", "name": "Lamp" }
            """);

        verifyNoInteractions(discoveryService);
    }

    @Test
    @DisplayName("STATUS still routes to the resource service (regression)")
    void status_isDelegated() {
        dispatch("""
            { "type": "STATUS", "uuid": "u1", "online": true }
            """);

        verify(resourceService).setMqttResourceStatusByUuid("u1", true);
        verifyNoInteractions(discoveryService);
    }

    @Test
    @DisplayName("VALUE routes to the resource service with uuid, name and value")
    void value_isDelegated() {
        dispatch("""
            { "type": "VALUE", "uuid": "u1", "name": "temp", "value": "21" }
            """);

        verify(resourceService).recordMqttValueByUuid("u1", "temp", "21");
        verifyNoInteractions(discoveryService);
    }

    @Test
    @DisplayName("VALUE missing a mandatory field (value) is ignored")
    void value_missingField_isIgnored() {
        dispatch("""
            { "type": "VALUE", "uuid": "u1", "name": "temp" }
            """);

        verifyNoInteractions(resourceService);
        verifyNoInteractions(discoveryService);
    }

    @Test
    @DisplayName("A Tasmota SENSOR report is flattened and recorded under its device topic")
    void tasmotaSensor_isRecordedByDeviceTopic() {
        handler.handle(new GenericMessage<>("""
            {"Time":"2026-09-28T21:20:25","MT681":{"Total_in":15119.035,"Power_cur":-377,"Total_out":62331.037}}
            """), "tele/tasmota_6F0690/SENSOR");

        verify(resourceService).recordDeviceReadings("tasmota_6F0690", Map.of(
                "MT681.Total_in", "15119.035",
                "MT681.Power_cur", "-377",
                "MT681.Total_out", "62331.037"));
        verifyNoInteractions(discoveryService);
    }

    @Test
    @DisplayName("Tasmota's last will sets the device online flag; a non-JSON payload is fine")
    void tasmotaLwt_setsOnlineFlag() {
        handler.handle(new GenericMessage<>("Offline"), "tele/tasmota_6F0690/LWT");
        handler.handle(new GenericMessage<>("Online"), "tele/tasmota_6F0690/LWT");

        verify(resourceService).setDeviceOnline("tasmota_6F0690", false);
        verify(resourceService).setDeviceOnline("tasmota_6F0690", true);
    }

    @Test
    @DisplayName("Tasmota topics the adapter does not read (STATE) fall through without side effects")
    void tasmotaState_isNotClaimed() {
        handler.handle(new GenericMessage<>("""
            {"Time":"2026-09-28T21:20:25","Uptime":"0T01:00:00","Wifi":{"RSSI":60}}
            """), "tele/tasmota_6F0690/STATE");

        verifyNoInteractions(resourceService);
        verifyNoInteractions(discoveryService);
    }
}
