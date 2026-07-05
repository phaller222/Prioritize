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

package de.hallerweb.enterprise.prioritize.service.resource.control;

import de.hallerweb.enterprise.prioritize.model.resource.Resource;

/**
 * Port (hexagonal architecture) for the outbound control of a {@link Resource}.
 * <p>
 * The rest of the system controls a resource solely through this interface and
 * does not know <em>how</em> (over which transport) the command reaches the device.
 * Concrete implementations encapsulate the transport (MQTT, REST, ...).
 * <p>
 * The <em>inbound</em> direction (device → system: discovery, status, telemetry) is
 * deliberately NOT part of this interface, since it is asymmetric to REST and is
 * processed via a separate inbound path.
 *
 * @author peter haller
 */
public interface ResourceControlAdapter {

    /**
     * Indicates whether this adapter can control the given resource in principle
     * (capability check, independent of the current online state).
     *
     * @param resource the resource to check
     * @return {@code true} if this adapter is responsible for the resource
     */
    boolean supports(Resource resource);

    /**
     * Indicates whether the resource is currently reachable via this adapter
     * (e.g. MQTT: online; REST: IP set). Only meaningful if {@link #supports} is true.
     *
     * @param resource the resource to check
     * @return {@code true} if the resource is currently controllable via this transport
     */
    boolean isAvailable(Resource resource);

    /**
     * Sends a control command with an optional free parameter to the resource.
     *
     * @param resource target resource
     * @param command  command identifier (freely defined)
     * @param param    optional, freely defined parameter value (may be {@code null})
     * @param slot     the addressed slot (derived from the user's active reservation)
     */
    void sendCommand(Resource resource, String command, String param, int slot);

    /**
     * Transport identifier for logging/diagnostics (e.g. "MQTT", "REST").
     *
     * @return short transport name
     */
    String getTransportName();
}