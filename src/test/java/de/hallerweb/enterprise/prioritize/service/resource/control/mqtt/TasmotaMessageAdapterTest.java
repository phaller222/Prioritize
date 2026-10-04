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
import de.hallerweb.enterprise.prioritize.service.resource.control.mqtt.DeviceMessageAdapter.DeviceMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for {@link TasmotaMessageAdapter}: which topics it claims and how a SENSOR report is
 * flattened. The meter payload is the real one read off a bitShake/Tasmota reader at an ISKRA MT681.
 */
class TasmotaMessageAdapterTest {

    private final TasmotaMessageAdapter adapter = new TasmotaMessageAdapter(new ObjectMapper());

    @Test
    @DisplayName("A meter report becomes path-named readings in message order, without Time")
    void sensor_isFlattened() {
        DeviceMessage msg = adapter.parse("tele/tasmota_6F0690/SENSOR", """
            {"Time":"2026-09-28T21:20:25","MT681":{"Total_in":15119.035,"Power_cur":377,"Power_p1":14,
             "Power_p2":45,"Power_p3":317,"Total_out":62331.037,"Meter_id":"0a01495352000"}}
            """).orElseThrow();

        assertEquals("tasmota_6F0690", msg.deviceTopic());
        assertNull(msg.online());
        assertEquals(List.of("MT681.Total_in", "MT681.Power_cur", "MT681.Power_p1", "MT681.Power_p2",
                "MT681.Power_p3", "MT681.Total_out", "MT681.Meter_id"), List.copyOf(msg.readings().keySet()));
        assertEquals("15119.035", msg.readings().get("MT681.Total_in"));
        assertEquals("0a01495352000", msg.readings().get("MT681.Meter_id"), "text stays text");
    }

    @Test
    @DisplayName("Arrays get their index, booleans become text, nulls are dropped")
    void sensor_arraysBooleansNulls() {
        DeviceMessage msg = adapter.parse("tele/plug/SENSOR", """
            {"ENERGY":{"Voltage":[230,231],"Today":0.5,"Factor":null},"Switch1":true}
            """).orElseThrow();

        assertEquals(Map.of("ENERGY.Voltage.0", "230", "ENERGY.Voltage.1", "231",
                "ENERGY.Today", "0.5", "Switch1", "true"), msg.readings());
    }

    @Test
    @DisplayName("LWT maps Online/Offline to the availability flag")
    void lwt_isAvailability() {
        assertEquals(true, adapter.parse("tele/reader/LWT", "Online").orElseThrow().online());
        assertEquals(false, adapter.parse("tele/reader/LWT", "Offline").orElseThrow().online());
    }

    @Test
    @DisplayName("Native and other Tasmota topics are not claimed")
    void otherTopics_areNotClaimed() {
        assertTrue(adapter.parse("tele/reader/STATE", "{}").isEmpty());
        assertTrue(adapter.parse("stat/reader/POWER", "ON").isEmpty());
        assertTrue(adapter.parse("u1/values", "{}").isEmpty());
        assertTrue(adapter.parse("DISCOVERY", "{}").isEmpty());
        assertTrue(adapter.parse(null, "{}").isEmpty());
    }

    @Test
    @DisplayName("A broken SENSOR payload is claimed but yields no readings")
    void sensor_brokenJson_yieldsNothing() {
        DeviceMessage msg = adapter.parse("tele/reader/SENSOR", "{not json").orElseThrow();

        assertTrue(msg.readings().isEmpty());
    }
}
