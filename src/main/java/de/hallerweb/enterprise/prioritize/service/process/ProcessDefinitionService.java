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

import de.hallerweb.enterprise.prioritize.model.document.Document;
import de.hallerweb.enterprise.prioritize.model.document.DocumentInfo;
import de.hallerweb.enterprise.prioritize.model.process.ProcessDefinition;
import de.hallerweb.enterprise.prioritize.model.process.ProcessDefinitionState;
import de.hallerweb.enterprise.prioritize.model.security.Action;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.process.ProcessDefinitionRepository;
import de.hallerweb.enterprise.prioritize.service.document.DocumentService;
import de.hallerweb.enterprise.prioritize.service.process.BpmnDefinitionReader.BpmnDefinitionInfo;
import de.hallerweb.enterprise.prioritize.service.security.AuthorizationService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registry of the BPMN process definitions the platform knows about. Registering links an existing
 * document — the BPMN source with its version history — to a process key; it does <b>not</b> deploy
 * anything. Deployment is a separate, deliberate act (see the activation slice).
 * <p>
 * <b>Authorization</b> follows the {@code createProject} precedent: a process definition has no owning
 * container to authorize against, so it is gated by a <b>type-level</b> permission — a {@code
 * PermissionRecord} with {@code objectId == 0} on {@link ProcessDefinition}. Admins are allowed
 * implicitly and no default grant is seeded, so a fresh installation starts closed. Reading the
 * underlying document is authorized by the documents subsystem itself, as it always was.
 *
 * @author peter haller
 */
@Service
public class ProcessDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(ProcessDefinitionService.class);

    /** Target type of the type-level permission records gating this service. */
    private static final String TYPE = ProcessDefinition.class.getCanonicalName();

    private final ProcessDefinitionRepository definitionRepository;
    private final DocumentService documentService;
    private final BpmnDefinitionReader definitionReader;
    private final AuthorizationService authService;
    private final RepositoryService repositoryService;

    public ProcessDefinitionService(ProcessDefinitionRepository definitionRepository,
                                    DocumentService documentService,
                                    BpmnDefinitionReader definitionReader,
                                    AuthorizationService authService,
                                    RepositoryService repositoryService) {
        this.definitionRepository = definitionRepository;
        this.documentService = documentService;
        this.definitionReader = definitionReader;
        this.authService = authService;
        this.repositoryService = repositoryService;
    }

    /**
     * Registers the document's current version as a process definition, in state {@link
     * ProcessDefinitionState#DRAFT}.
     * <p>
     * The process key is read out of the file rather than taken from the caller — the diagram is the
     * truth about what it defines. A key already in use is refused: which definition answers to a key
     * must not depend on deployment order, and a collision is a mistake worth seeing immediately.
     *
     * @param documentInfoId the document carrying the BPMN source
     * @param user           the authenticated caller
     * @return the registered definition
     * @throws AccessDeniedException    if the caller may not register definitions or not read the document
     * @throws NoSuchElementException   if no such document exists
     * @throws IllegalArgumentException if the document holds no usable BPMN process
     * @throws IllegalStateException    if the key is taken or the document is already registered
     */
    @Transactional
    public ProcessDefinition register(Long documentInfoId, PUser user) {
        requirePermission(user, Action.CREATE, "register process definitions");

        DocumentInfo info = documentService.getDocument(documentInfoId, user);
        Document current = info.getCurrentDocument();
        if (current == null || current.getData() == null || current.getData().length == 0) {
            throw new IllegalArgumentException("Document " + documentInfoId + " has no content to read a process from.");
        }

        BpmnDefinitionInfo parsed = definitionReader.read(current.getData());

        definitionRepository.findByProcessKey(parsed.processKey()).ifPresent(existing -> {
            throw new IllegalStateException("The process key '" + parsed.processKey()
                    + "' is already registered by definition " + existing.getId() + ".");
        });
        if (!definitionRepository.findByDocumentInfo_Id(documentInfoId).isEmpty()) {
            throw new IllegalStateException("Document " + documentInfoId + " is already registered as a process definition.");
        }

        ProcessDefinition definition = ProcessDefinition.builder()
                .processKey(parsed.processKey())
                .name(parsed.processName() != null ? parsed.processName() : current.getName())
                .documentInfo(info)
                .state(ProcessDefinitionState.DRAFT)
                .build();

        ProcessDefinition saved = definitionRepository.save(definition);
        log.info("Registered process definition '{}' from document {} (version {}) as draft.",
                saved.getProcessKey(), documentInfoId, current.getVersion());
        return saved;
    }

    /** All registered definitions, deployed or not. */
    @Transactional(readOnly = true)
    public List<ProcessDefinition> getAll(PUser user) {
        requirePermission(user, Action.READ, "read process definitions");
        return definitionRepository.findAll();
    }

    /**
     * A single definition.
     *
     * @throws NoSuchElementException if there is none with that id
     */
    @Transactional(readOnly = true)
    public ProcessDefinition get(Long id, PUser user) {
        requirePermission(user, Action.READ, "read process definitions");
        return findOrThrow(id);
    }

    /**
     * Deploys the definition's current document version to the engine and makes it startable.
     * <p>
     * This is the deliberate act the whole design turns on: revising a diagram does not deploy it,
     * somebody with the permission has to say so. Activation is <b>idempotent</b> — activating a
     * definition that is already active on the current document version changes nothing.
     * <p>
     * A new deployment is created only when there is something new to deploy (never deployed before,
     * or the document has a newer version since the last activation). Otherwise a suspended definition
     * is simply resumed, so taking something out of service and back in does not pile up engine
     * versions. The BPMN is re-read first: if the document's process key changed since registration,
     * the file no longer defines the process this entry stands for and activation is refused.
     *
     * @throws AccessDeniedException  if the caller may not activate definitions
     * @throws NoSuchElementException if there is no such definition
     * @throws IllegalStateException  if the document's process key no longer matches
     */
    @Transactional
    public ProcessDefinition activate(Long id, PUser user) {
        requirePermission(user, Action.UPDATE, "activate process definitions");

        ProcessDefinition definition = findOrThrow(id);
        Document current = currentContentOf(definition);
        BpmnDefinitionInfo parsed = definitionReader.read(current.getData());
        if (!parsed.processKey().equals(definition.getProcessKey())) {
            throw new IllegalStateException("Document now defines process '" + parsed.processKey()
                    + "' instead of '" + definition.getProcessKey() + "'; register it as its own definition.");
        }

        boolean nothingNewToDeploy = definition.getDeploymentId() != null
                && Objects.equals(definition.getDeployedVersion(), current.getVersion());

        if (!nothingNewToDeploy) {
            Deployment deployment = repositoryService.createDeployment()
                    .name(definition.getName() != null ? definition.getName() : definition.getProcessKey())
                    .key(definition.getProcessKey())
                    // The resource name must carry a BPMN suffix or the engine will not parse it.
                    .addBytes(definition.getProcessKey() + ".bpmn", current.getData())
                    .deploy();
            definition.setDeploymentId(deployment.getId());
            definition.setDeployedVersion(current.getVersion());
            log.info("Deployed process definition '{}' from document version {} as deployment {}.",
                    definition.getProcessKey(), current.getVersion(), deployment.getId());
        } else if (definition.getState() == ProcessDefinitionState.SUSPENDED) {
            repositoryService.activateProcessDefinitionByKey(definition.getProcessKey());
            log.info("Resumed suspended process definition '{}'.", definition.getProcessKey());
        } else {
            return definition; // already active on this very version — nothing to do, not an error
        }

        definition.setState(ProcessDefinitionState.ACTIVE);
        definition.setDeployedAt(LocalDateTime.now());
        definition.setDeployedBy(user);
        return definitionRepository.save(definition);
    }

    /**
     * Takes an active definition out of service: no new instances can be started from it, while
     * instances that are already running continue untouched. The deployment is never deleted —
     * running instances are real work, and the engine keeps the definition they were started with.
     *
     * @throws IllegalStateException if the definition is not currently active
     */
    @Transactional
    public ProcessDefinition deactivate(Long id, PUser user) {
        requirePermission(user, Action.UPDATE, "deactivate process definitions");

        ProcessDefinition definition = findOrThrow(id);
        if (definition.getState() != ProcessDefinitionState.ACTIVE) {
            throw new IllegalStateException("Process definition '" + definition.getProcessKey() + "' is not active.");
        }

        // Suspends every deployed version of this key; deliberately does not suspend running instances.
        repositoryService.suspendProcessDefinitionByKey(definition.getProcessKey());
        definition.setState(ProcessDefinitionState.SUSPENDED);
        log.info("Suspended process definition '{}'; running instances continue.", definition.getProcessKey());
        return definitionRepository.save(definition);
    }

    /**
     * Removes a definition from the registry.
     * <p>
     * Only a {@link ProcessDefinitionState#DRAFT} definition can be unregistered. Once it has been
     * deployed, the engine holds a deployment and possibly running instances, and this entity is the
     * only thing mapping them back to their document — dropping it would leave that deployment
     * unattributable. Deactivating a definition is the way to take it out of service.
     *
     * @throws IllegalStateException if the definition has been deployed
     */
    @Transactional
    public void unregister(Long id, PUser user) {
        requirePermission(user, Action.DELETE, "remove process definitions");

        ProcessDefinition definition = findOrThrow(id);
        if (definition.getState() != ProcessDefinitionState.DRAFT) {
            throw new IllegalStateException("Process definition '" + definition.getProcessKey()
                    + "' has been deployed and cannot be unregistered; deactivate it instead.");
        }

        definitionRepository.delete(definition);
        log.info("Unregistered draft process definition '{}'.", definition.getProcessKey());
    }

    private void requirePermission(PUser user, Action action, String what) {
        if (!authService.hasPermission(user, TYPE, 0L, action)) {
            throw new AccessDeniedException("No permission to " + what + ".");
        }
    }

    private ProcessDefinition findOrThrow(Long id) {
        return definitionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No process definition with id " + id));
    }

    /** The document version a deployment would use, refusing a definition whose source went missing. */
    private Document currentContentOf(ProcessDefinition definition) {
        DocumentInfo info = definition.getDocumentInfo();
        Document current = info == null ? null : info.getCurrentDocument();
        if (current == null || current.getData() == null || current.getData().length == 0) {
            throw new IllegalStateException("Process definition '" + definition.getProcessKey()
                    + "' has no document content to deploy.");
        }
        return current;
    }
}
