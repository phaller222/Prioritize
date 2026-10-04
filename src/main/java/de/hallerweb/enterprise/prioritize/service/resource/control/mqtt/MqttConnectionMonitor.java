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

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.integration.mqtt.event.MqttConnectionFailedEvent;
import org.springframework.integration.mqtt.event.MqttSubscribedEvent;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Makes the state of the inbound MQTT connection visible in the log.
 * <p>
 * With Paho's automatic reconnect switched on, Spring Integration hands a failed first connect silently
 * to Paho, which then retries in the background without a single log line — a misspelled broker host
 * looks exactly like a healthy, quiet system. This monitor closes that gap:
 * <ul>
 *   <li>INFO when the inbound adapter is connected and subscribed (also after every reconnect),</li>
 *   <li>WARN with the cause when an established connection is lost,</li>
 *   <li>a watchdog that, while not connected, probes the broker address itself and WARNs with what it
 *       found — host unknown, connection refused, timeout, or reachable (then the broker itself refuses;
 *       its log says why). The first warning comes {@link #FIRST_WARNING} after start or loss, then at
 *       most every {@link #REPEAT_WARNING}, so a broker that stays down does not flood the log.</li>
 * </ul>
 *
 * @author peter haller
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "prioritize.mqtt.enabled", havingValue = "true")
@Log4j2
public class MqttConnectionMonitor {

    static final Duration FIRST_WARNING = Duration.ofSeconds(30);
    static final Duration REPEAT_WARNING = Duration.ofMinutes(10);
    private static final int PROBE_TIMEOUT_MS = 3000;

    private final String brokerUrl;
    private final Function<String, String> probe;

    private boolean connected;
    private Instant disconnectedSince = Instant.now();
    private Instant lastWarning;

    @Autowired
    public MqttConnectionMonitor(MqttProperties props) {
        this(props.getBrokerUrl(), MqttConnectionMonitor::probeBroker);
    }

    MqttConnectionMonitor(String brokerUrl, Function<String, String> probe) {
        this.brokerUrl = brokerUrl;
        this.probe = probe;
    }

    /** The inbound adapter connected (first time or after a reconnect) and holds its subscriptions. */
    @EventListener
    public synchronized void onSubscribed(MqttSubscribedEvent event) {
        if (!(event.getSource() instanceof MqttPahoMessageDrivenChannelAdapter)) {
            return;
        }
        if (!connected) {
            log.info("MQTT connected to {} — {}", brokerUrl, event.getMessage());
        }
        connected = true;
        lastWarning = null;
    }

    /** A connection failed or was lost; the inbound adapter's state drives the watchdog. */
    @EventListener
    public synchronized void onConnectionFailed(MqttConnectionFailedEvent event) {
        String side = event.getSource() instanceof MqttPahoMessageDrivenChannelAdapter ? "inbound" : "outbound";
        log.warn("MQTT {} connection to {} lost: {}", side, brokerUrl, describe(event.getCause()));
        if ("inbound".equals(side) && connected) {
            connected = false;
            disconnectedSince = Instant.now();
            lastWarning = null;
        }
    }

    @Scheduled(initialDelay = 10_000, fixedDelay = 10_000)
    void watchdog() {
        check(Instant.now()).ifPresent(log::warn);
    }

    /**
     * Decides whether a "still not connected" warning is due at {@code now} and builds it.
     *
     * @return the warning to log, or empty when connected or not due yet
     */
    synchronized Optional<String> check(Instant now) {
        if (connected) {
            return Optional.empty();
        }
        Duration down = Duration.between(disconnectedSince, now);
        boolean due = lastWarning == null
                ? down.compareTo(FIRST_WARNING) >= 0
                : Duration.between(lastWarning, now).compareTo(REPEAT_WARNING) >= 0;
        if (!due) {
            return Optional.empty();
        }
        lastWarning = now;
        return Optional.of("MQTT still not connected to " + brokerUrl + " after " + down.toSeconds()
                + " s (retrying in the background). Probe: " + probe.apply(brokerUrl));
    }

    /**
     * Opens and closes a plain TCP connection to the broker address and says what happened, in terms an
     * operator can act on.
     */
    static String probeBroker(String brokerUrl) {
        URI uri;
        try {
            uri = URI.create(brokerUrl);
        } catch (IllegalArgumentException e) {
            return "broker URL '" + brokerUrl + "' is malformed (expected e.g. tcp://host:1883).";
        }
        String host = uri.getHost();
        if (host == null) {
            return "broker URL '" + brokerUrl + "' has no host (expected e.g. tcp://host:1883).";
        }
        int port = uri.getPort() != -1 ? uri.getPort() : ("ssl".equals(uri.getScheme()) ? 8883 : 1883);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), PROBE_TIMEOUT_MS);
            return "TCP " + host + ":" + port + " is reachable, so the broker itself refuses the MQTT "
                    + "connection (credentials, client id, TLS?) — its log says why.";
        } catch (UnknownHostException e) {
            return "host '" + host + "' cannot be resolved — check the spelling (in Docker: the broker's "
                    + "container name, and that both containers share a network).";
        } catch (ConnectException e) {
            return "connection to " + host + ":" + port + " refused — nothing listens there "
                    + "(broker down, wrong port?).";
        } catch (SocketTimeoutException e) {
            return "no answer from " + host + ":" + port + " within " + PROBE_TIMEOUT_MS / 1000
                    + " s — packets are dropped (firewall, or a container reaching its own host's IP?).";
        } catch (IOException e) {
            return "connecting to " + host + ":" + port + " failed: " + e;
        }
    }

    private static String describe(Throwable cause) {
        if (cause == null) {
            return "no cause given";
        }
        Throwable root = cause;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root == cause ? cause.toString() : cause + " (caused by " + root + ")";
    }
}
