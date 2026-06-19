package de.hallerweb.enterprise.prioritize.service.resource.control;

import de.hallerweb.enterprise.prioritize.exception.ResourceOfflineException;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.security.Action;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.resource.control.mqtt.MqttResourceControlAdapter;
import de.hallerweb.enterprise.prioritize.service.resource.control.rest.RestResourceControlAdapter;
import de.hallerweb.enterprise.prioritize.service.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Reine Logik-Tests der Transport-Resolution-Strategie (kein Spring-Context, kein Broker).
 */
class ResourceControlServiceTest {

    private AuthorizationService authService;
    private RestResourceControlAdapter restAdapter;
    private MqttResourceControlAdapter mqttAdapter;
    private ObjectProvider<MqttResourceControlAdapter> mqttProvider;
    private ResourceControlService service;

    private PUser user;
    private Resource resource;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        authService = mock(AuthorizationService.class);
        restAdapter = mock(RestResourceControlAdapter.class);
        mqttAdapter = mock(MqttResourceControlAdapter.class);
        mqttProvider = mock(ObjectProvider.class);

        service = new ResourceControlService(authService, restAdapter, mqttProvider);

        user = new PUser();
        user.setUsername("admin");

        resource = new Resource();
        resource.setId(42L);

        // Standardmäßig: Benutzer darf steuern
        when(authService.hasPermission(eq(user), eq(resource), eq(Action.UPDATE))).thenReturn(true);
    }

    @Test
    @DisplayName("MQTT online → Command geht via MQTT")
    void mqttOnline_usesMqtt() {
        when(mqttProvider.getIfAvailable()).thenReturn(mqttAdapter);
        when(mqttAdapter.isAvailable(resource)).thenReturn(true);

        service.sendCommand(resource, "ON", "1", user);

        verify(mqttAdapter).sendCommand(resource, "ON", "1");
        verify(restAdapter, never()).sendCommand(any(), any(), any());
    }

    @Test
    @DisplayName("MQTT-Capability, aber offline + REST vorhanden → REST-Fallback")
    void mqttOffline_fallsBackToRest() {
        when(mqttProvider.getIfAvailable()).thenReturn(mqttAdapter);
        when(mqttAdapter.isAvailable(resource)).thenReturn(false);
        when(mqttAdapter.supports(resource)).thenReturn(true);
        when(restAdapter.isAvailable(resource)).thenReturn(true);

        service.sendCommand(resource, "ON", null, user);

        verify(restAdapter).sendCommand(resource, "ON", null);
        verify(mqttAdapter, never()).sendCommand(any(), any(), any());
    }

    @Test
    @DisplayName("Keine MQTT-Capability → REST")
    void noMqtt_usesRest() {
        when(mqttProvider.getIfAvailable()).thenReturn(null);
        when(restAdapter.isAvailable(resource)).thenReturn(true);

        service.sendCommand(resource, "ON", "5", user);

        verify(restAdapter).sendCommand(resource, "ON", "5");
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
        verify(mqttAdapter, never()).sendCommand(any(), any(), any());
        verify(restAdapter, never()).sendCommand(any(), any(), any());
    }

    @Test
    @DisplayName("Fehlende Berechtigung → AccessDeniedException, kein Transport berührt")
    void noPermission_throws() {
        when(authService.hasPermission(eq(user), eq(resource), eq(Action.UPDATE))).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> service.sendCommand(resource, "ON", null, user));
        verifyNoInteractions(restAdapter);
    }
}
