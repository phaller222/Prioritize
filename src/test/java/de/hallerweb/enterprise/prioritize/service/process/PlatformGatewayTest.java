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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.hallerweb.enterprise.prioritize.model.project.Project;
import de.hallerweb.enterprise.prioritize.model.project.Task;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.project.ProjectService;
import de.hallerweb.enterprise.prioritize.service.project.TaskService;
import de.hallerweb.enterprise.prioritize.service.security.SystemIdentityProvider;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link PlatformGateway#createTask}: it resolves the project, delegates to the
 * trusted (user-less) create path, and never performs a membership check. No Spring context, no
 * database, no engine.
 *
 * @author peter haller
 */
class PlatformGatewayTest {

    private ProjectService projectService;
    private TaskService taskService;
    private SystemIdentityProvider systemIdentity;
    private PlatformGateway gateway;

    @BeforeEach
    void setUp() {
        projectService = mock(ProjectService.class);
        taskService = mock(TaskService.class);
        systemIdentity = mock(SystemIdentityProvider.class);
        gateway = new PlatformGateway(projectService, taskService, systemIdentity);

        PUser system = new PUser();
        system.setUsername(SystemIdentityProvider.SYSTEM_USERNAME);
        when(systemIdentity.get()).thenReturn(system);
    }

    @Test
    @DisplayName("createTask resolves the project and delegates to the trusted create path")
    void createTask_delegatesToTrustedPath() {
        Project project = new Project();
        project.setName("Line A");
        Task created = new Task();
        created.setName("Inspection");
        when(projectService.findOrThrow(7L)).thenReturn(project);
        when(taskService.createScheduledTask(eq(project), eq("Inspection"), eq("check it"), eq(3)))
                .thenReturn(created);

        Task result = gateway.createTask(7L, "Inspection", "check it", 3);

        assertSame(created, result);
        verify(taskService).createScheduledTask(project, "Inspection", "check it", 3);
    }

    @Test
    @DisplayName("createTask on an unknown project propagates and never creates a task")
    void createTask_unknownProject_propagates() {
        when(projectService.findOrThrow(99L)).thenThrow(new NoSuchElementException("No project 99"));

        assertThrows(NoSuchElementException.class, () -> gateway.createTask(99L, "x", "y", 1));

        verify(taskService, never()).createScheduledTask(any(), anyString(), anyString(), anyInt());
    }
}
