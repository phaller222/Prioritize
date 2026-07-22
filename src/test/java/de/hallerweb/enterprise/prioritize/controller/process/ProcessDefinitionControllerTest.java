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

package de.hallerweb.enterprise.prioritize.controller.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.hallerweb.enterprise.prioritize.config.CurrentUserResolver;
import de.hallerweb.enterprise.prioritize.dto.process.ProcessDefinitionDTO;
import de.hallerweb.enterprise.prioritize.model.process.ProcessDefinitionState;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.process.ProcessDefinitionService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

/**
 * Unit tests for {@link ProcessDefinitionController}: delegation to {@link ProcessDefinitionService}
 * and the returned status codes. Plain Mockito, no Spring context.
 *
 * @author peter haller
 */
class ProcessDefinitionControllerTest {

    private ProcessDefinitionService service;
    private ProcessDefinitionController controller;

    private final Authentication auth = mock(Authentication.class);
    private final PUser user = new PUser();

    @BeforeEach
    void setUp() {
        service = mock(ProcessDefinitionService.class);
        CurrentUserResolver resolver = mock(CurrentUserResolver.class);
        controller = new ProcessDefinitionController(service, resolver);
        when(resolver.resolve(auth)).thenReturn(user);
    }

    private static ProcessDefinitionDTO draft() {
        return new ProcessDefinitionDTO(42L, "orderHandling", "Order handling", 7L,
                ProcessDefinitionState.DRAFT, null, null, null, null);
    }

    private static ProcessDefinitionDTO active() {
        return new ProcessDefinitionDTO(42L, "orderHandling", "Order handling", 7L,
                ProcessDefinitionState.ACTIVE, "dep-1", 2, LocalDateTime.of(2026, 7, 22, 9, 0), "peter");
    }

    @Test
    @DisplayName("register: delegiert und antwortet mit 201 Created")
    void register_created() {
        ProcessDefinitionDTO dto = draft();
        when(service.register(eq(7L), eq(user))).thenReturn(dto);

        ResponseEntity<ProcessDefinitionDTO> response = controller.register(7L, auth);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(dto, response.getBody());
    }

    @Test
    @DisplayName("getAll: liefert die Liste mit 200 OK")
    void getAll_ok() {
        when(service.getAll(user)).thenReturn(List.of(draft()));

        ResponseEntity<List<ProcessDefinitionDTO>> response = controller.getAll(auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    @DisplayName("get: liefert eine Definition mit 200 OK")
    void get_ok() {
        ProcessDefinitionDTO dto = draft();
        when(service.get(eq(42L), eq(user))).thenReturn(dto);

        ResponseEntity<ProcessDefinitionDTO> response = controller.get(42L, auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(dto, response.getBody());
    }

    @Test
    @DisplayName("activate: delegiert und liefert die aktivierte Definition")
    void activate_ok() {
        ProcessDefinitionDTO dto = active();
        when(service.activate(eq(42L), eq(user))).thenReturn(dto);

        ResponseEntity<ProcessDefinitionDTO> response = controller.activate(42L, auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(ProcessDefinitionState.ACTIVE, response.getBody().state());
        assertEquals("dep-1", response.getBody().deploymentId());
    }

    @Test
    @DisplayName("deactivate: delegiert und liefert die suspendierte Definition")
    void deactivate_ok() {
        ProcessDefinitionDTO suspended = new ProcessDefinitionDTO(42L, "orderHandling", "Order handling", 7L,
                ProcessDefinitionState.SUSPENDED, "dep-1", 2, LocalDateTime.of(2026, 7, 22, 9, 0), "peter");
        when(service.deactivate(eq(42L), eq(user))).thenReturn(suspended);

        ResponseEntity<ProcessDefinitionDTO> response = controller.deactivate(42L, auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(ProcessDefinitionState.SUSPENDED, response.getBody().state());
    }

    @Test
    @DisplayName("unregister: delegiert und antwortet mit 204 No Content")
    void unregister_noContent() {
        ResponseEntity<Void> response = controller.unregister(42L, auth);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertNull(response.getBody());
        verify(service).unregister(42L, user);
    }
}
