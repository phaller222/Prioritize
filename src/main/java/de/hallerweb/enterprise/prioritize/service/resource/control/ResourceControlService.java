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

import de.hallerweb.enterprise.prioritize.exception.ResourceOfflineException;
import de.hallerweb.enterprise.prioritize.exception.SlotNotReservedException;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.resource.ResourceReservation;
import de.hallerweb.enterprise.prioritize.model.security.Action;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.resource.ResourceReservationRepository;
import de.hallerweb.enterprise.prioritize.service.resource.control.mqtt.MqttResourceControlAdapter;
import de.hallerweb.enterprise.prioritize.service.resource.control.rest.RestResourceControlAdapter;
import de.hallerweb.enterprise.prioritize.service.security.AuthorizationService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Central entry point for controlling resources. Selects the appropriate transport
 * per command and enforces the permission check (convention: authorization
 * in the service layer via exception).
 * <p>
 * <strong>Slot derivation:</strong> The addressed slot is NOT supplied by the client,
 * but determined from the user's active reservation on the resource. A command
 * is therefore only possible while the user holds an ongoing reservation; the slot is
 * always the reserved one. If the user (in the rare case) holds multiple active reservations
 * on the same resource, the slot is ambiguous → {@link SlotNotReservedException} (409).
 * <p>
 * <strong>Resolution strategy (capability set with fallback):</strong>
 * <ol>
 *   <li>Resource has MQTT capability and is online → MQTT</li>
 *   <li>Resource has MQTT capability but is offline → REST fallback,
 *       provided a REST endpoint (ip) exists</li>
 *   <li>No MQTT capability → REST</li>
 *   <li>No reachable transport → {@link ResourceOfflineException}</li>
 * </ol>
 * <p>
 * REST is the always-active base; MQTT is an optional, additional capability.
 * The MQTT adapter is only present when {@code prioritize.mqtt.enabled=true} —
 * therefore it is resolved optionally via an {@link ObjectProvider}.
 *
 * @author peter haller
 */
@Service
@Log4j2
public class ResourceControlService {

    private final AuthorizationService authService;
    private final RestResourceControlAdapter restAdapter;
    private final ObjectProvider<MqttResourceControlAdapter> mqttAdapterProvider;
    private final ResourceReservationRepository reservationRepository;

    public ResourceControlService(AuthorizationService authService,
                                  RestResourceControlAdapter restAdapter,
                                  ObjectProvider<MqttResourceControlAdapter> mqttAdapterProvider,
                                  ResourceReservationRepository reservationRepository) {
        this.authService = authService;
        this.restAdapter = restAdapter;
        this.mqttAdapterProvider = mqttAdapterProvider;
        this.reservationRepository = reservationRepository;
    }

    /**
     * Sends a control command to a resource. Controlling counts as a state change and
     * therefore requires {@link Action#UPDATE} permission on the resource. The addressed
     * slot is derived from the user's active reservation.
     *
     * @param resource target resource
     * @param command  command identifier
     * @param param    optional free parameter (may be {@code null})
     * @param user     the executing user (permission check + slot derivation)
     * @throws AccessDeniedException      if the user is not allowed to control
     * @throws SlotNotReservedException   if the user does not hold a unique active reservation
     * @throws ResourceOfflineException   if no reachable transport exists
     */
    public void sendCommand(Resource resource, String command, String param, PUser user) {
        if (!authService.hasPermission(user, resource, Action.UPDATE)) {
            throw new AccessDeniedException(
                "No permission to control this resource.");
        }

        int slot = resolveReservedSlot(resource, user);

        ResourceControlAdapter adapter = resolveAdapter(resource);
        log.info("Command '{}' an Resource {} (Slot {}) via {} (User: {}).",
            command, resource.getId(), slot, adapter.getTransportName(), user.getUsername());
        adapter.sendCommand(resource, command, param, slot);
    }

    /**
     * Derives the slot to control from the user's active reservation.
     * <p>
     * Exactly one active reservation of the user on the resource must exist:
     * none → {@link SlotNotReservedException}; multiple → also
     * {@link SlotNotReservedException} (ambiguous, slot not uniquely determinable).
     */
    private int resolveReservedSlot(Resource resource, PUser user) {
        Instant now = Instant.now();
        List<ResourceReservation> active =
            reservationRepository.findActiveReservationsByUser(resource.getId(), user.getId(), now);

        if (active.isEmpty()) {
            throw new SlotNotReservedException(
                "User '" + user.getUsername() + "' has no active reservation on resource "
                    + resource.getId() + ". A control command requires an active reservation.");
        }
        if (active.size() > 1) {
            throw new SlotNotReservedException(
                "User '" + user.getUsername() + "' holds multiple active reservations on resource "
                    + resource.getId() + "; the target slot is ambiguous.");
        }
        return active.get(0).getSlotNumber();
    }

    /**
     * Selects the transport according to the capability-set strategy with REST fallback.
     */
    private ResourceControlAdapter resolveAdapter(Resource resource) {
        MqttResourceControlAdapter mqtt = mqttAdapterProvider.getIfAvailable();

        // 1. MQTT preferred, if capability present AND online
        if (mqtt != null && mqtt.isAvailable(resource)) {
            return mqtt;
        }

        // 2./3. REST-Fallback, sofern ein REST-Endpunkt existiert
        if (restAdapter.isAvailable(resource)) {
            if (mqtt != null && mqtt.supports(resource)) {
                log.debug("Resource {} ist MQTT-Resource, aber offline → REST-Fallback.",
                    resource.getId());
            }
            return restAdapter;
        }

        // 4. Kein Transport erreichbar
        throw new ResourceOfflineException(
            "Resource " + resource.getId() + " is offline and has no control channel "
                + "(MQTT offline, no REST endpoint).");
    }
}