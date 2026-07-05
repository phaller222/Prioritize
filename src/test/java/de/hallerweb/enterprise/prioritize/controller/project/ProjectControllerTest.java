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
import de.hallerweb.enterprise.prioritize.controller.project.ProjectController.ProjectRequest;
import de.hallerweb.enterprise.prioritize.model.project.Project;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.project.ProjectService;
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
 * Unit tests for {@link ProjectController}: delegation, status codes and request validation.
 * Plain Mockito (no Spring context), matching the style of the other controller tests.
 */
class ProjectControllerTest {

    private ProjectService projectService;
    private ProjectController controller;

    private final Authentication auth = mock(Authentication.class);
    private final PUser user = new PUser();

    @BeforeEach
    void setUp() {
        projectService = mock(ProjectService.class);
        TaskService taskService = mock(TaskService.class);
        CurrentUserResolver resolver = mock(CurrentUserResolver.class);
        controller = new ProjectController(projectService, taskService, resolver);
        when(resolver.resolve(auth)).thenReturn(user);
    }

    @Test
    @DisplayName("createProject: delegates and answers 201 Created")
    void createProject_created() {
        Project project = new Project();
        when(projectService.createProject(any(), eq(user))).thenReturn(project);

        ResponseEntity<Project> response = controller.createProject(
                new ProjectRequest("Apollo", "d", 1, null, null, 10), auth);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(projectService).createProject(any(), eq(user));
    }

    @Test
    @DisplayName("createProject: blank name is rejected before delegation")
    void createProject_blankName_throws() {
        assertThrows(IllegalArgumentException.class, () -> controller.createProject(
                new ProjectRequest("  ", "d", 1, null, null, 10), auth));
        verifyNoInteractions(projectService);
    }

    @Test
    @DisplayName("deleteProject: answers 204 No Content")
    void deleteProject_noContent() {
        ResponseEntity<Void> response = controller.deleteProject(7L, auth);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(projectService).deleteProject(eq(7L), eq(user));
    }
}
