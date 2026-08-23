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

package de.hallerweb.enterprise.prioritize.controller.project;

import de.hallerweb.enterprise.prioritize.controller.project.TaskController.StopAtRequest;
import de.hallerweb.enterprise.prioritize.controller.project.TaskController.TaskRequest;
import de.hallerweb.enterprise.prioritize.controller.project.TaskController.TaskStatusRequest;
import de.hallerweb.enterprise.prioritize.controller.project.TaskController.WorkSessionRequest;
import de.hallerweb.enterprise.prioritize.dto.project.TaskDTO;
import de.hallerweb.enterprise.prioritize.model.project.Task;
import de.hallerweb.enterprise.prioritize.model.project.TaskStatus;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.project.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TaskController}: delegation, status codes and request validation.
 * Plain Mockito (no Spring context).
 */
class TaskControllerTest {

    private TaskService taskService;
    private TaskController controller;

    private final PUser user = new PUser();

    @BeforeEach
    void setUp() {
        taskService = mock(TaskService.class);
        controller = new TaskController(taskService);
    }

    @Test
    @DisplayName("createTask: delegates and answers 201 Created")
    void createTask_created() {
        when(taskService.createTask(eq(3L), any(), eq(user))).thenReturn(new Task());

        ResponseEntity<TaskDTO> response = controller.createTask(
                3L, new TaskRequest("Design", "d", 1), user);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(taskService).createTask(eq(3L), any(), eq(user));
    }

    @Test
    @DisplayName("createTask: blank name is rejected before delegation")
    void createTask_blankName_throws() {
        assertThrows(IllegalArgumentException.class, () -> controller.createTask(
                3L, new TaskRequest("", "d", 1), user));
        verifyNoInteractions(taskService);
    }

    @Test
    @DisplayName("assignTask: delegates to the service")
    void assignTask_delegates() {
        when(taskService.assignTask(eq(5L), eq(9L), eq(user))).thenReturn(new Task());
        controller.assignTask(5L, 9L, user);
        verify(taskService).assignTask(eq(5L), eq(9L), eq(user));
    }

    @Test
    @DisplayName("changeStatus: missing status is rejected before delegation")
    void changeStatus_missingStatus_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.changeStatus(5L, new TaskStatusRequest(null), user));
        verifyNoInteractions(taskService);
    }

    @Test
    @DisplayName("changeStatus: delegates with the requested status")
    void changeStatus_delegates() {
        when(taskService.changeStatus(eq(5L), eq(TaskStatus.STARTED), eq(user))).thenReturn(new Task());
        controller.changeStatus(5L, new TaskStatusRequest(TaskStatus.STARTED), user);
        verify(taskService).changeStatus(eq(5L), eq(TaskStatus.STARTED), eq(user));
    }

    @Test
    @DisplayName("startTracking: delegates to the service")
    void startTracking_delegates() {
        when(taskService.startTracking(eq(5L), eq(user))).thenReturn(new Task());
        controller.startTracking(5L, user);
        verify(taskService).startTracking(eq(5L), eq(user));
    }

    @Test
    @DisplayName("toggleTracking: delegates to the service")
    void toggleTracking_delegates() {
        when(taskService.toggleTracking(eq(5L), eq(user))).thenReturn(new Task());
        controller.toggleTracking(5L, user);
        verify(taskService).toggleTracking(eq(5L), eq(user));
    }

    @Test
    @DisplayName("getTracking: delegates and returns the tracking summary")
    void getTracking_delegates() {
        TaskService.TrackingSummary summary =
                new TaskService.TrackingSummary(5L, false, 0, 90L, "PT1M30S", null);
        when(taskService.getTrackingSummary(eq(5L), eq(user))).thenReturn(summary);

        ResponseEntity<TaskService.TrackingSummary> response = controller.getTracking(5L, user);

        assertEquals(summary, response.getBody());
        verify(taskService).getTrackingSummary(eq(5L), eq(user));
    }

    // --- Correcting tracked time ---

    @Test
    @DisplayName("stopTrackingAt: delegates end and reason to the service")
    void stopTrackingAt_delegates() {
        Instant until = Instant.parse("2026-08-08T17:00:00Z");
        when(taskService.stopTrackingAt(eq(5L), eq(until), eq("vergessen"), isNull(), eq(user)))
                .thenReturn(new Task());

        controller.stopTrackingAt(5L, new StopAtRequest(until, "vergessen", null), user);

        verify(taskService).stopTrackingAt(eq(5L), eq(until), eq("vergessen"), isNull(), eq(user));
    }

    @Test
    @DisplayName("addTrackingSession: delegates and answers 201 Created")
    void addTrackingSession_created() {
        Instant from = Instant.parse("2026-08-08T08:00:00Z");
        Instant until = Instant.parse("2026-08-08T12:00:00Z");
        TaskService.WorkSession session = new TaskService.WorkSession(
                7L, 9L, "geselle", from, until, 14400L, false, null);
        when(taskService.addWorkSession(eq(5L), eq(from), eq(until), eq("nachgetragen"), eq(9L), eq(user)))
                .thenReturn(session);

        ResponseEntity<TaskService.WorkSession> response = controller.addTrackingSession(
                5L, new WorkSessionRequest(from, until, "nachgetragen", 9L), user);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(session, response.getBody());
    }

    @Test
    @DisplayName("updateTrackingSession: delegates the corrected bounds, ignoring userId")
    void updateTrackingSession_delegates() {
        Instant from = Instant.parse("2026-08-08T08:00:00Z");
        Instant until = Instant.parse("2026-08-08T12:00:00Z");
        TaskService.WorkSession session = new TaskService.WorkSession(
                7L, 9L, "geselle", from, until, 14400L, false, null);
        when(taskService.updateWorkSession(eq(5L), eq(7L), eq(from), eq(until), eq("korrigiert"), eq(user)))
                .thenReturn(session);

        ResponseEntity<TaskService.WorkSession> response = controller.updateTrackingSession(
                5L, 7L, new WorkSessionRequest(from, until, "korrigiert", 9L), user);

        assertEquals(session, response.getBody());
        verify(taskService).updateWorkSession(eq(5L), eq(7L), eq(from), eq(until), eq("korrigiert"), eq(user));
    }

    @Test
    @DisplayName("deleteTrackingSession: delegates and answers 204 No Content")
    void deleteTrackingSession_noContent() {
        ResponseEntity<Void> response = controller.deleteTrackingSession(5L, 7L, user);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(taskService).deleteWorkSession(eq(5L), eq(7L), eq(user));
    }
}
