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

import de.hallerweb.enterprise.prioritize.config.CurrentUserResolver;
import de.hallerweb.enterprise.prioritize.controller.project.TaskController.TaskRequest;
import de.hallerweb.enterprise.prioritize.controller.project.TaskController.TaskStatusRequest;
import de.hallerweb.enterprise.prioritize.model.project.Task;
import de.hallerweb.enterprise.prioritize.model.project.TaskStatus;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.project.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    private final Authentication auth = mock(Authentication.class);
    private final PUser user = new PUser();

    @BeforeEach
    void setUp() {
        taskService = mock(TaskService.class);
        CurrentUserResolver resolver = mock(CurrentUserResolver.class);
        controller = new TaskController(taskService, resolver);
        when(resolver.resolve(auth)).thenReturn(user);
    }

    @Test
    @DisplayName("createTask: delegates and answers 201 Created")
    void createTask_created() {
        when(taskService.createTask(eq(3L), any(), eq(user))).thenReturn(new Task());

        ResponseEntity<Task> response = controller.createTask(
                3L, new TaskRequest("Design", "d", 1), auth);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(taskService).createTask(eq(3L), any(), eq(user));
    }

    @Test
    @DisplayName("createTask: blank name is rejected before delegation")
    void createTask_blankName_throws() {
        assertThrows(IllegalArgumentException.class, () -> controller.createTask(
                3L, new TaskRequest("", "d", 1), auth));
        verifyNoInteractions(taskService);
    }

    @Test
    @DisplayName("assignTask: delegates to the service")
    void assignTask_delegates() {
        when(taskService.assignTask(eq(5L), eq(9L), eq(user))).thenReturn(new Task());
        controller.assignTask(5L, 9L, auth);
        verify(taskService).assignTask(eq(5L), eq(9L), eq(user));
    }

    @Test
    @DisplayName("changeStatus: missing status is rejected before delegation")
    void changeStatus_missingStatus_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.changeStatus(5L, new TaskStatusRequest(null), auth));
        verifyNoInteractions(taskService);
    }

    @Test
    @DisplayName("changeStatus: delegates with the requested status")
    void changeStatus_delegates() {
        when(taskService.changeStatus(eq(5L), eq(TaskStatus.STARTED), eq(user))).thenReturn(new Task());
        controller.changeStatus(5L, new TaskStatusRequest(TaskStatus.STARTED), auth);
        verify(taskService).changeStatus(eq(5L), eq(TaskStatus.STARTED), eq(user));
    }

    @Test
    @DisplayName("startTracking: delegates to the service")
    void startTracking_delegates() {
        when(taskService.startTracking(eq(5L), eq(user))).thenReturn(new Task());
        controller.startTracking(5L, auth);
        verify(taskService).startTracking(eq(5L), eq(user));
    }

    @Test
    @DisplayName("toggleTracking: delegates to the service")
    void toggleTracking_delegates() {
        when(taskService.toggleTracking(eq(5L), eq(user))).thenReturn(new Task());
        controller.toggleTracking(5L, auth);
        verify(taskService).toggleTracking(eq(5L), eq(user));
    }
}
