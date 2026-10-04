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

import java.net.ServerSocket;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.integration.mqtt.event.MqttConnectionFailedEvent;
import org.springframework.integration.mqtt.event.MqttSubscribedEvent;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link MqttConnectionMonitor}: when the watchdog warns, that connection events reset it,
 * and what the broker probe reports for the failure modes met in practice. No broker, no Spring context;
 * the probe tests use local sockets only.
 */
class MqttConnectionMonitorTest {

    private static final String URL = "tcp://mosquitto:1883";

    private final MqttPahoMessageDrivenChannelAdapter inbound =
            new MqttPahoMessageDrivenChannelAdapter(URL, "test-sub", "t");

    @Test
    @DisplayName("Not connected: first warning after 30 s with the probe result, then at most every 10 min")
    void warnsOnceThenThrottles() {
        Instant start = Instant.now();
        MqttConnectionMonitor monitor = new MqttConnectionMonitor(URL, url -> "PROBE");

        assertTrue(monitor.check(start.plusSeconds(10)).isEmpty(), "too early");
        String warning = monitor.check(start.plusSeconds(31)).orElseThrow();
        assertTrue(warning.contains(URL) && warning.contains("PROBE"), warning);
        assertTrue(monitor.check(start.plusSeconds(120)).isEmpty(), "throttled");
        assertTrue(monitor.check(start.plusSeconds(31 + 601)).isPresent(), "repeated after 10 min");
    }

    @Test
    @DisplayName("Subscribed silences the watchdog; losing the inbound connection re-arms it")
    void connectionEventsDriveTheWatchdog() {
        MqttConnectionMonitor monitor = new MqttConnectionMonitor(URL, url -> "PROBE");

        monitor.onSubscribed(new MqttSubscribedEvent(inbound, "Connected and subscribed to [t]"));
        assertTrue(monitor.check(Instant.now().plusSeconds(3600)).isEmpty(), "connected: never warns");

        monitor.onConnectionFailed(new MqttConnectionFailedEvent(inbound, new RuntimeException("gone")));
        assertTrue(monitor.check(Instant.now().plusSeconds(5)).isEmpty(), "just lost: not yet");
        assertTrue(monitor.check(Instant.now().plusSeconds(31)).isPresent(), "lost for 30 s: warns");
    }

    @Test
    @DisplayName("A failing outbound connection does not mark the inbound side as down")
    void outboundFailure_doesNotAffectInboundState() {
        MqttConnectionMonitor monitor = new MqttConnectionMonitor(URL, url -> "PROBE");
        monitor.onSubscribed(new MqttSubscribedEvent(inbound, "Connected and subscribed to [t]"));

        monitor.onConnectionFailed(new MqttConnectionFailedEvent(new Object(), new RuntimeException("pub")));

        assertTrue(monitor.check(Instant.now().plusSeconds(3600)).isEmpty());
    }

    @Test
    @DisplayName("Probe: a misspelled host is reported as unresolvable")
    void probe_unknownHost() {
        String result = MqttConnectionMonitor.probeBroker("tcp://mosquito.invalid:1883");
        assertTrue(result.contains("cannot be resolved"), result);
    }

    @Test
    @DisplayName("Probe: a closed port is reported as refused, an open one as reachable")
    void probe_refusedAndReachable() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        String refused = MqttConnectionMonitor.probeBroker("tcp://127.0.0.1:" + closedPort);
        assertTrue(refused.contains("refused"), refused);

        try (ServerSocket open = new ServerSocket(0)) {
            String reachable = MqttConnectionMonitor.probeBroker("tcp://127.0.0.1:" + open.getLocalPort());
            assertTrue(reachable.contains("reachable"), reachable);
        }
    }

    @Test
    @DisplayName("Probe: a URL without host is named as such")
    void probe_noHost() {
        String result = MqttConnectionMonitor.probeBroker("mosquitto:1883");
        assertTrue(result.contains("no host"), result);
    }
}
