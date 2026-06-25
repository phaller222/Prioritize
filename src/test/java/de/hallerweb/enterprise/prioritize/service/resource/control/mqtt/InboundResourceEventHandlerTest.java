package de.hallerweb.enterprise.prioritize.service.resource.control.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.hallerweb.enterprise.prioritize.service.resource.ResourceService;
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
        handler = new InboundResourceEventHandler(resourceService, new ObjectMapper(), discoveryService);
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
}
