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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pure logic tests of the transport resolution strategy and the slot derivation from
 * the active reservation (no Spring context, no broker).
 */
class ResourceControlServiceTest {

    private AuthorizationService authService;
    private RestResourceControlAdapter restAdapter;
    private MqttResourceControlAdapter mqttAdapter;
    private ObjectProvider<MqttResourceControlAdapter> mqttProvider;
    private ResourceReservationRepository reservationRepository;
    private ResourceControlService service;

    private PUser user;
    private Resource resource;

    private static final int RESERVED_SLOT = 3;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        authService = mock(AuthorizationService.class);
        restAdapter = mock(RestResourceControlAdapter.class);
        mqttAdapter = mock(MqttResourceControlAdapter.class);
        mqttProvider = mock(ObjectProvider.class);
        reservationRepository = mock(ResourceReservationRepository.class);

        service = new ResourceControlService(authService, restAdapter, mqttProvider, reservationRepository);

        user = new PUser();
        user.setId(7L);
        user.setUsername("admin");

        resource = new Resource();
        resource.setId(42L);

        // By default: user is allowed to control
        when(authService.hasPermission(eq(user), eq(resource), eq(Action.UPDATE))).thenReturn(true);

        // By default: exactly one active reservation on slot RESERVED_SLOT
        when(reservationRepository.findActiveReservationsByUser(eq(42L), eq(7L), any(Instant.class)))
            .thenReturn(List.of(reservationWithSlot(RESERVED_SLOT)));
    }

    private ResourceReservation reservationWithSlot(int slot) {
        ResourceReservation r = new ResourceReservation();
        r.setSlotNumber(slot);
        r.setReservedBy(user);
        r.setResource(resource);
        return r;
    }

    @Test
    @DisplayName("MQTT online → Command geht via MQTT, mit reserviertem Slot")
    void mqttOnline_usesMqtt() {
        when(mqttProvider.getIfAvailable()).thenReturn(mqttAdapter);
        when(mqttAdapter.isAvailable(resource)).thenReturn(true);

        service.sendCommand(resource, "ON", "1", user);

        verify(mqttAdapter).sendCommand(resource, "ON", "1", RESERVED_SLOT);
        verify(restAdapter, never()).sendCommand(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("MQTT-Capability, aber offline + REST vorhanden → REST-Fallback")
    void mqttOffline_fallsBackToRest() {
        when(mqttProvider.getIfAvailable()).thenReturn(mqttAdapter);
        when(mqttAdapter.isAvailable(resource)).thenReturn(false);
        when(mqttAdapter.supports(resource)).thenReturn(true);
        when(restAdapter.isAvailable(resource)).thenReturn(true);

        service.sendCommand(resource, "ON", null, user);

        verify(restAdapter).sendCommand(resource, "ON", null, RESERVED_SLOT);
        verify(mqttAdapter, never()).sendCommand(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("Keine MQTT-Capability → REST, mit reserviertem Slot")
    void noMqtt_usesRest() {
        when(mqttProvider.getIfAvailable()).thenReturn(null);
        when(restAdapter.isAvailable(resource)).thenReturn(true);

        service.sendCommand(resource, "ON", "5", user);

        verify(restAdapter).sendCommand(resource, "ON", "5", RESERVED_SLOT);
    }

    @Test
    @DisplayName("MQTT offline und kein REST-Endpunkt → ResourceOfflineException")
    void mqttOfflineNoRest_throws() {
        when(mqttProvider.getIfAvailable()).thenReturn(mqttAdapter);
        when(mqttAdapter.isAvailable(resource)).thenReturn(false);
        when(mqttAdapter.supports(resource)).thenReturn(true);
        when(restAdapter.isAvailable(resource)).thenReturn(false);

        assertThrows(ResourceOfflineException.class,
            () -> service.sendCommand(resource, "ON", null, user));
        verify(mqttAdapter, never()).sendCommand(any(), any(), any(), anyInt());
        verify(restAdapter, never()).sendCommand(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("Fehlende Berechtigung → AccessDeniedException, kein Transport berührt")
    void noPermission_throws() {
        when(authService.hasPermission(eq(user), eq(resource), eq(Action.UPDATE))).thenReturn(false);

        assertThrows(AccessDeniedException.class,
            () -> service.sendCommand(resource, "ON", null, user));
        verifyNoInteractions(restAdapter);
    }

    @Test
    @DisplayName("Keine aktive Reservierung → SlotNotReservedException, kein Transport berührt")
    void noReservation_throws() {
        when(reservationRepository.findActiveReservationsByUser(eq(42L), eq(7L), any(Instant.class)))
            .thenReturn(List.of());

        assertThrows(SlotNotReservedException.class,
            () -> service.sendCommand(resource, "ON", null, user));
        verify(restAdapter, never()).sendCommand(any(), any(), any(), anyInt());
        verifyNoInteractions(mqttAdapter);
    }

    @Test
    @DisplayName("Mehrere aktive Reservierungen → SlotNotReservedException (Slot mehrdeutig)")
    void ambiguousReservation_throws() {
        when(reservationRepository.findActiveReservationsByUser(eq(42L), eq(7L), any(Instant.class)))
            .thenReturn(List.of(reservationWithSlot(1), reservationWithSlot(2)));

        assertThrows(SlotNotReservedException.class,
            () -> service.sendCommand(resource, "ON", null, user));
        verify(restAdapter, never()).sendCommand(any(), any(), any(), anyInt());
    }
}