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

package de.hallerweb.enterprise.prioritize.controller.telemetry;

import de.hallerweb.enterprise.prioritize.config.CurrentUserResolver;
import de.hallerweb.enterprise.prioritize.dto.telemetry.TelemetryRuleDTO;
import de.hallerweb.enterprise.prioritize.dto.telemetry.TelemetryRuleRequest;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.telemetry.Severity;
import de.hallerweb.enterprise.prioritize.model.telemetry.TelemetryOperator;
import de.hallerweb.enterprise.prioritize.model.telemetry.TelemetryState;
import de.hallerweb.enterprise.prioritize.service.telemetry.TelemetryRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TelemetryRuleController}: delegation to {@link TelemetryRuleService} and the
 * returned status codes. Plain Mockito, no Spring context.
 *
 * @author peter haller
 */
class TelemetryRuleControllerTest {

    private TelemetryRuleService service;
    private TelemetryRuleController controller;

    private final Authentication auth = mock(Authentication.class);
    private final PUser user = new PUser();

    @BeforeEach
    void setUp() {
        service = mock(TelemetryRuleService.class);
        CurrentUserResolver resolver = mock(CurrentUserResolver.class);
        controller = new TelemetryRuleController(service, resolver);
        when(resolver.resolve(auth)).thenReturn(user);
    }

    private static TelemetryRuleDTO dto() {
        return new TelemetryRuleDTO(11L, 7L, "temp", TelemetryOperator.GT,
                30.0, null, 2.0, Severity.WARNING, true, TelemetryState.OK, null);
    }

    @Test
    @DisplayName("createRule: delegiert und antwortet mit 201 Created")
    void createRule_created() {
        TelemetryRuleRequest req = new TelemetryRuleRequest(
                "temp", TelemetryOperator.GT, 30.0, null, 2.0, Severity.WARNING, true);
        when(service.createRule(eq(7L), eq(req), eq(user))).thenReturn(dto());

        ResponseEntity<TelemetryRuleDTO> response = controller.createRule(7L, req, auth);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(11L, response.getBody().id());
        verify(service).createRule(7L, req, user);
    }

    @Test
    @DisplayName("getRules: delegiert und antwortet mit 200 OK")
    void getRules_ok() {
        when(service.getRules(7L, user)).thenReturn(List.of(dto()));

        ResponseEntity<List<TelemetryRuleDTO>> response = controller.getRules(7L, auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    @DisplayName("getRule: delegiert und antwortet mit 200 OK")
    void getRule_ok() {
        when(service.getRule(11L, user)).thenReturn(dto());

        ResponseEntity<TelemetryRuleDTO> response = controller.getRule(11L, auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(11L, response.getBody().id());
    }

    @Test
    @DisplayName("updateRule: delegiert und antwortet mit 200 OK")
    void updateRule_ok() {
        TelemetryRuleRequest patch = new TelemetryRuleRequest(
                null, null, 32.0, null, null, null, false);
        when(service.updateRule(eq(11L), eq(patch), eq(user))).thenReturn(dto());

        ResponseEntity<TelemetryRuleDTO> response = controller.updateRule(11L, patch, auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(service).updateRule(11L, patch, user);
    }

    @Test
    @DisplayName("deleteRule: delegiert und antwortet mit 204 No Content")
    void deleteRule_noContent() {
        ResponseEntity<Void> response = controller.deleteRule(11L, auth);

        assertSame(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(service).deleteRule(11L, user);
    }
}
