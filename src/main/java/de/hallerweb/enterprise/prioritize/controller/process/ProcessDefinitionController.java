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
import de.hallerweb.enterprise.prioritize.dto.process.ProcessDefinitionDTO;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.process.ProcessDefinitionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for BPMN process definitions ({@link ProcessDefinitionService}).
 * <p>
 * Registration is nested under the document carrying the BPMN, because that document is what a
 * definition is made of; everything afterwards addresses the definition by its own id. Activation and
 * deactivation are modelled as explicit operations rather than as a state field somebody could PATCH:
 * deploying to the engine is an act, and the API should read like one (same shape as the task
 * time-tracking endpoints).
 * <p>
 * Authorization lives in the service (type-level CREATE/READ/UPDATE/DELETE on the definition type, plus
 * the document's own READ right); {@code GlobalExceptionHandler} maps the thrown exceptions to status
 * codes — 403 denied, 404 unknown, 400 unusable BPMN, 409 key collision or wrong state.
 *
 * @author peter haller
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProcessDefinitionController {

    private final ProcessDefinitionService processDefinitionService;
    private final CurrentUserResolver currentUserResolver;

    private PUser getCurrentUser(Authentication auth) {
        return currentUserResolver.resolve(auth);
    }

    /**
     * Registers a document's current version as a process definition. Nothing is deployed yet — the
     * definition starts as a draft.
     *
     * @return 201 Created with the new definition
     */
    @PostMapping("/documents/{documentInfoId}/process-definition")
    public ResponseEntity<ProcessDefinitionDTO> register(@PathVariable Long documentInfoId, Authentication auth) {
        ProcessDefinitionDTO registered = processDefinitionService.register(documentInfoId, getCurrentUser(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(registered);
    }

    /**
     * Lists all registered process definitions, deployed or not.
     *
     * @return 200 OK with the definitions
     */
    @GetMapping("/process-definitions")
    public ResponseEntity<List<ProcessDefinitionDTO>> getAll(Authentication auth) {
        return ResponseEntity.ok(processDefinitionService.getAll(getCurrentUser(auth)));
    }

    /**
     * A single process definition.
     *
     * @return 200 OK with the definition
     */
    @GetMapping("/process-definitions/{id}")
    public ResponseEntity<ProcessDefinitionDTO> get(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(processDefinitionService.get(id, getCurrentUser(auth)));
    }

    /**
     * Deploys the definition's current document version and makes it startable. Idempotent: activating
     * an already active definition on an unchanged document does nothing.
     *
     * @return 200 OK with the activated definition
     */
    @PostMapping("/process-definitions/{id}/activate")
    public ResponseEntity<ProcessDefinitionDTO> activate(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(processDefinitionService.activate(id, getCurrentUser(auth)));
    }

    /**
     * Takes a definition out of service: no new instances, running ones continue.
     *
     * @return 200 OK with the suspended definition
     */
    @PostMapping("/process-definitions/{id}/deactivate")
    public ResponseEntity<ProcessDefinitionDTO> deactivate(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(processDefinitionService.deactivate(id, getCurrentUser(auth)));
    }

    /**
     * Removes a definition from the registry. A draft goes right away; a deployed one is refused (409)
     * unless {@code ?force=true} is given, which tears down its engine deployment as well. Forcing
     * still refuses while instances are running (409) — cancel them first.
     *
     * @return 204 No Content
     */
    @DeleteMapping("/process-definitions/{id}")
    public ResponseEntity<Void> unregister(@PathVariable Long id,
                                           @RequestParam(name = "force", defaultValue = "false") boolean force,
                                           Authentication auth) {
        processDefinitionService.unregister(id, force, getCurrentUser(auth));
        return ResponseEntity.noContent().build();
    }
}
