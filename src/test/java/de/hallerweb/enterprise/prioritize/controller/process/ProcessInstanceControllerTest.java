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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.hallerweb.enterprise.prioritize.config.CurrentUserResolver;
import de.hallerweb.enterprise.prioritize.dto.process.CancelProcessInstanceRequest;
import de.hallerweb.enterprise.prioritize.dto.process.ProcessInstanceDTO;
import de.hallerweb.enterprise.prioritize.dto.process.StartProcessInstanceRequest;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.process.ProcessInstanceService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

/**
 * Unit tests for {@link ProcessInstanceController}: delegation to {@link ProcessInstanceService} and
 * the returned status codes. Plain Mockito, no Spring context.
 *
 * @author peter haller
 */
class ProcessInstanceControllerTest {

    private ProcessInstanceService service;
    private ProcessInstanceController controller;

    private final Authentication auth = mock(Authentication.class);
    private final PUser user = new PUser();

    @BeforeEach
    void setUp() {
        service = mock(ProcessInstanceService.class);
        CurrentUserResolver resolver = mock(CurrentUserResolver.class);
        controller = new ProcessInstanceController(service, resolver);
        when(resolver.resolve(auth)).thenReturn(user);
    }

    private static ProcessInstanceDTO running() {
        return new ProcessInstanceDTO("pi-1", "inspectionRound", 3L, "task:42", 7L, 42L, true,
                Instant.parse("2026-07-22T09:00:00Z"), "peter");
    }

    @Test
    @DisplayName("startForProject: delegiert und antwortet mit 201 Created")
    void startForProject_created() {
        ProcessInstanceDTO dto = running();
        Map<String, Object> variables = Map.of("threshold", 80);
        when(service.startForProject(eq(7L), eq(3L), eq(variables), eq(user))).thenReturn(dto);

        ResponseEntity<ProcessInstanceDTO> response =
                controller.startForProject(7L, new StartProcessInstanceRequest(3L, variables), auth);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(dto, response.getBody());
    }

    @Test
    @DisplayName("startForTask: delegiert und antwortet mit 201 Created")
    void startForTask_created() {
        ProcessInstanceDTO dto = running();
        when(service.startForTask(eq(42L), eq(3L), isNull(), eq(user))).thenReturn(dto);

        ResponseEntity<ProcessInstanceDTO> response =
                controller.startForTask(42L, new StartProcessInstanceRequest(3L, null), auth);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(dto, response.getBody());
    }

    @Test
    @DisplayName("getForProject: liefert die Liste mit 200 OK")
    void getForProject_ok() {
        when(service.getForProject(7L, user)).thenReturn(List.of(running()));

        ResponseEntity<List<ProcessInstanceDTO>> response = controller.getForProject(7L, auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    @DisplayName("getForTask: liefert die verknüpfte Instanz mit 200 OK")
    void getForTask_ok() {
        ProcessInstanceDTO dto = running();
        when(service.getForTask(42L, user)).thenReturn(Optional.of(dto));

        ResponseEntity<ProcessInstanceDTO> response = controller.getForTask(42L, auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(dto, response.getBody());
    }

    @Test
    @DisplayName("getForTask: ohne verknüpfte Instanz ein 404 statt eines leeren Bodys")
    void getForTask_notFound() {
        when(service.getForTask(42L, user)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> controller.getForTask(42L, auth));
    }

    @Test
    @DisplayName("get: liefert eine einzelne Instanz mit 200 OK")
    void get_ok() {
        ProcessInstanceDTO dto = running();
        when(service.get("pi-1", user)).thenReturn(dto);

        ResponseEntity<ProcessInstanceDTO> response = controller.get("pi-1", auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(dto, response.getBody());
    }

    @Test
    @DisplayName("cancel: reicht den Grund durch und antwortet mit 204 No Content")
    void cancel_noContent() {
        ResponseEntity<Void> response =
                controller.cancel("pi-1", new CancelProcessInstanceRequest("wrong process picked"), auth);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(service).cancel("pi-1", "wrong process picked", user);
    }

    @Test
    @DisplayName("cancel: funktioniert auch ganz ohne Body")
    void cancel_withoutBody() {
        ResponseEntity<Void> response = controller.cancel("pi-1", null, auth);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(service).cancel("pi-1", null, user);
    }
}
