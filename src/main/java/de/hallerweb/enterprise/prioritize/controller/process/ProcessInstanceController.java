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

import de.hallerweb.enterprise.prioritize.config.CurrentUserResolver;
import de.hallerweb.enterprise.prioritize.dto.process.CancelProcessInstanceRequest;
import de.hallerweb.enterprise.prioritize.dto.process.ProcessInstanceDTO;
import de.hallerweb.enterprise.prioritize.dto.process.StartProcessInstanceRequest;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.process.ProcessInstanceService;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for BPMN process instances ({@link ProcessInstanceService}).
 * <p>
 * Starting is nested under what the instance is <em>for</em> — a project or one of its tasks — because
 * that is what decides both the business key and who is allowed to ask. Everything afterwards
 * addresses the instance by the engine's own id. Cancelling is an operation with a reason, not a
 * DELETE: the instance is not erased, it is stopped and the reason stays in the engine's history.
 * <p>
 * Authorization lives in the service (project membership to start and read, manager to cancel);
 * {@code GlobalExceptionHandler} maps the thrown exceptions to status codes — 403 denied, 404 unknown
 * or not started by the platform, 400 a missing definition id, 409 a definition that is not active or
 * a task that already runs one.
 *
 * @author peter haller
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProcessInstanceController {

    private final ProcessInstanceService processInstanceService;
    private final CurrentUserResolver currentUserResolver;

    private PUser getCurrentUser(Authentication auth) {
        return currentUserResolver.resolve(auth);
    }

    /**
     * Starts a process for a whole project.
     *
     * @return 201 Created with the started instance
     */
    @PostMapping("/projects/{projectId}/process-instances")
    public ResponseEntity<ProcessInstanceDTO> startForProject(@PathVariable Long projectId,
                                                              @RequestBody StartProcessInstanceRequest request,
                                                              Authentication auth) {
        ProcessInstanceDTO started = processInstanceService.startForProject(
                projectId, request.definitionId(), request.variables(), getCurrentUser(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(started);
    }

    /**
     * Starts a process for a single task and links the two.
     *
     * @return 201 Created with the started instance
     */
    @PostMapping("/tasks/{taskId}/process-instances")
    public ResponseEntity<ProcessInstanceDTO> startForTask(@PathVariable Long taskId,
                                                           @RequestBody StartProcessInstanceRequest request,
                                                           Authentication auth) {
        ProcessInstanceDTO started = processInstanceService.startForTask(
                taskId, request.definitionId(), request.variables(), getCurrentUser(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(started);
    }

    /**
     * All instances belonging to a project — its own and those of its tasks, running or finished.
     *
     * @return 200 OK with the instances
     */
    @GetMapping("/projects/{projectId}/process-instances")
    public ResponseEntity<List<ProcessInstanceDTO>> getForProject(@PathVariable Long projectId, Authentication auth) {
        return ResponseEntity.ok(processInstanceService.getForProject(projectId, getCurrentUser(auth)));
    }

    /**
     * The instance a task is linked to.
     *
     * @return 200 OK with the instance, 404 if the task never ran one
     */
    @GetMapping("/tasks/{taskId}/process-instance")
    public ResponseEntity<ProcessInstanceDTO> getForTask(@PathVariable Long taskId, Authentication auth) {
        return processInstanceService.getForTask(taskId, getCurrentUser(auth))
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NoSuchElementException("Task " + taskId + " is not linked to a process instance."));
    }

    /**
     * A single instance by its engine id.
     *
     * @return 200 OK with the instance
     */
    @GetMapping("/process-instances/{id}")
    public ResponseEntity<ProcessInstanceDTO> get(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(processInstanceService.get(id, getCurrentUser(auth)));
    }

    /**
     * Stops a running instance. Manager only; the reason is kept in the engine's history.
     *
     * @return 204 No Content
     */
    @PostMapping("/process-instances/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable String id,
                                       @RequestBody(required = false) CancelProcessInstanceRequest request,
                                       Authentication auth) {
        processInstanceService.cancel(id, request != null ? request.reason() : null, getCurrentUser(auth));
        return ResponseEntity.noContent().build();
    }
}
