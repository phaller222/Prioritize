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

package de.hallerweb.enterprise.prioritize.service.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.hallerweb.enterprise.prioritize.model.resource.NameValueEntry;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.telemetry.TelemetryRule;
import de.hallerweb.enterprise.prioritize.model.telemetry.TelemetryState;
import de.hallerweb.enterprise.prioritize.repository.telemetry.TelemetryRuleRepository;
import de.hallerweb.enterprise.prioritize.service.resource.ResourceService;
import de.hallerweb.enterprise.prioritize.service.security.SystemIdentityProvider;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link PlatformState}, the read bean a gateway condition uses. The resource read is
 * mocked; what matters here is the mapping from platform state to the plain values a diagram compares.
 *
 * @author peter haller
 */
class PlatformStateTest {

    private ResourceService resourceService;
    private TelemetryRuleRepository telemetryRuleRepository;
    private PlatformState platformState;
    private final PUser system = new PUser();

    @BeforeEach
    void setUp() {
        resourceService = mock(ResourceService.class);
        telemetryRuleRepository = mock(TelemetryRuleRepository.class);
        SystemIdentityProvider systemIdentity = mock(SystemIdentityProvider.class);
        when(systemIdentity.get()).thenReturn(system);
        platformState = new PlatformState(resourceService, telemetryRuleRepository, systemIdentity);
    }

    private Resource resourceWith(Long id) {
        Resource resource = new Resource();
        when(resourceService.getResource(eq(id), eq(system))).thenReturn(resource);
        return resource;
    }

    private static NameValueEntry entry(String name, String history) {
        NameValueEntry e = new NameValueEntry();
        e.setMqttName(name);
        e.setMqttValues(history);
        return e;
    }

    private static TelemetryRule ruleInState(TelemetryState state) {
        TelemetryRule rule = new TelemetryRule();
        rule.setState(state);
        return rule;
    }

    @Test
    @DisplayName("resourceOnline reports an online MQTT resource")
    void resourceOnlineTrue() {
        resourceWith(5L).setMqttOnline(true);
        assertTrue(platformState.resourceOnline(5L));
    }

    @Test
    @DisplayName("resourceOnline reports a resource with no presence as offline")
    void resourceOnlineFalseWhenNull() {
        resourceWith(5L); // mqttOnline left null
        assertFalse(platformState.resourceOnline(5L));
    }

    @Test
    @DisplayName("telemetryState is ALARM when any enabled rule on the data point is in alarm")
    void telemetryStateAlarm() {
        resourceWith(5L);
        when(telemetryRuleRepository.findByResource_IdAndDatapointAndEnabledTrue(5L, "temperature"))
                .thenReturn(List.of(ruleInState(TelemetryState.OK), ruleInState(TelemetryState.ALARM)));
        assertEquals("ALARM", platformState.telemetryState(5L, "temperature"));
    }

    @Test
    @DisplayName("telemetryState is OK when no rule on the data point is in alarm")
    void telemetryStateOk() {
        resourceWith(5L);
        when(telemetryRuleRepository.findByResource_IdAndDatapointAndEnabledTrue(5L, "temperature"))
                .thenReturn(List.of(ruleInState(TelemetryState.OK)));
        assertEquals("OK", platformState.telemetryState(5L, "temperature"));
    }

    @Test
    @DisplayName("telemetryState of a data point with no rule reads OK, not a third state")
    void telemetryStateNoRuleIsOk() {
        resourceWith(5L);
        when(telemetryRuleRepository.findByResource_IdAndDatapointAndEnabledTrue(5L, "temperature"))
                .thenReturn(List.of());
        assertEquals("OK", platformState.telemetryState(5L, "temperature"));
    }

    @Test
    @DisplayName("latestValue returns the last entry of the data point's history")
    void latestValueReadsLast() {
        resourceWith(5L).setMqttValues(Set.of(entry("temperature", "20.0,21.5,91.2")));
        assertEquals(91.2, platformState.latestValue(5L, "temperature"));
    }

    @Test
    @DisplayName("latestValue fails loudly when the data point never reported")
    void latestValueMissingThrows() {
        resourceWith(5L).setMqttValues(Set.of(entry("humidity", "40")));
        assertThrows(NoSuchElementException.class, () -> platformState.latestValue(5L, "temperature"));
    }

    @Test
    @DisplayName("latestValue fails loudly when the last value is not numeric")
    void latestValueNonNumericThrows() {
        resourceWith(5L).setMqttValues(Set.of(entry("state", "on")));
        assertThrows(NoSuchElementException.class, () -> platformState.latestValue(5L, "state"));
    }
}
