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

package de.hallerweb.enterprise.prioritize.controller.scheduling;

import de.hallerweb.enterprise.prioritize.config.CurrentUserResolver;
import de.hallerweb.enterprise.prioritize.dto.scheduling.TaskScheduleDTO;
import de.hallerweb.enterprise.prioritize.dto.scheduling.TaskScheduleRequest;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.scheduling.TaskScheduleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TaskScheduleController}: delegation to {@link TaskScheduleService} and the
 * returned status codes. Plain Mockito, no Spring context.
 *
 * @author peter haller
 */
class TaskScheduleControllerTest {

    private TaskScheduleService service;
    private TaskScheduleController controller;

    private final Authentication auth = mock(Authentication.class);
    private final PUser user = new PUser();

    @BeforeEach
    void setUp() {
        service = mock(TaskScheduleService.class);
        CurrentUserResolver resolver = mock(CurrentUserResolver.class);
        controller = new TaskScheduleController(service, resolver);
        when(resolver.resolve(auth)).thenReturn(user);
    }

    private static TaskScheduleDTO dto() {
        return new TaskScheduleDTO(42L, 5L, "Nightly", "Generate report", "auto", 3,
                "0 0 8 * * *", "Europe/Berlin", true, LocalDateTime.of(2026, 1, 2, 8, 0), null);
    }

    @Test
    @DisplayName("createSchedule: delegiert und antwortet mit 201 Created")
    void createSchedule_created() {
        TaskScheduleRequest req = new TaskScheduleRequest("Nightly", "Generate report", "auto", 3,
                "0 0 8 * * *", "Europe/Berlin", true);
        when(service.createSchedule(eq(5L), eq(req), eq(user))).thenReturn(dto());

        ResponseEntity<TaskScheduleDTO> response = controller.createSchedule(5L, req, auth);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(42L, response.getBody().id());
        verify(service).createSchedule(5L, req, user);
    }

    @Test
    @DisplayName("getSchedules: delegiert und antwortet mit 200 OK")
    void getSchedules_ok() {
        when(service.getSchedules(5L, user)).thenReturn(List.of(dto()));

        ResponseEntity<List<TaskScheduleDTO>> response = controller.getSchedules(5L, auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    @DisplayName("getSchedule: delegiert und antwortet mit 200 OK")
    void getSchedule_ok() {
        when(service.getSchedule(42L, user)).thenReturn(dto());

        ResponseEntity<TaskScheduleDTO> response = controller.getSchedule(42L, auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(42L, response.getBody().id());
    }

    @Test
    @DisplayName("updateSchedule: delegiert und antwortet mit 200 OK")
    void updateSchedule_ok() {
        TaskScheduleRequest patch = new TaskScheduleRequest(
                null, null, null, null, "0 0/5 * * * *", null, false);
        when(service.updateSchedule(eq(42L), eq(patch), eq(user))).thenReturn(dto());

        ResponseEntity<TaskScheduleDTO> response = controller.updateSchedule(42L, patch, auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(service).updateSchedule(42L, patch, user);
    }

    @Test
    @DisplayName("deleteSchedule: delegiert und antwortet mit 204 No Content")
    void deleteSchedule_noContent() {
        ResponseEntity<Void> response = controller.deleteSchedule(42L, auth);

        assertSame(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(service).deleteSchedule(42L, user);
    }
}
