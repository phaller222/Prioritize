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

import de.hallerweb.enterprise.prioritize.model.document.DocumentInfo;
import de.hallerweb.enterprise.prioritize.model.project.Project;
import de.hallerweb.enterprise.prioritize.model.project.Task;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.document.DocumentService;
import de.hallerweb.enterprise.prioritize.service.project.ProjectService;
import de.hallerweb.enterprise.prioritize.service.project.TaskService;
import de.hallerweb.enterprise.prioritize.service.resource.ResourceService;
import de.hallerweb.enterprise.prioritize.service.resource.control.ResourceControlService;
import de.hallerweb.enterprise.prioritize.service.security.SystemIdentityProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The inbound facade: the one narrow place through which a BPMN process invokes platform operations —
 * create a task, store a document, control a resource — under a single, defined system identity instead
 * of through three scattered back doors.
 * <p>
 * <b>Why one facade.</b> Each of these operations already exists, but bound to a logged-in user because
 * it was built for humans. A process has no user, so without this facade every delegate would reach for
 * its own trusted path, and the system identity — and its limits — would be defined in three places or
 * nowhere. Here it is defined once: the acting principal is {@link SystemIdentityProvider}, and what a
 * process may do is whatever that principal is allowed to do.
 * <p>
 * <b>Orchestration, not ownership</b> (the line held throughout the Flowable work): the facade lets a
 * process <em>start</em> work and <em>record</em> results; it never becomes the owner of a task's
 * lifecycle. Remove Flowable and none of these operations lose their non-facade, user-driven form.
 * <p>
 * <b>How a BPMN process reaches it.</b> This class is a Spring bean named {@code platformGateway}, so a
 * service task invokes it directly by expression — no per-operation delegate class is needed, the facade
 * itself is the delegate surface:
 * <pre>{@code
 * <serviceTask id="create"
 *     flowable:expression="${platformGateway.createTask(projectId, 'Round', 'inspect', 2)}"/>
 * }</pre>
 * where {@code projectId} is a process variable the platform set at start (see
 * {@link ProcessInstanceService}).
 *
 * @author peter haller
 */
@Service
public class PlatformGateway {

    private static final Logger log = LoggerFactory.getLogger(PlatformGateway.class);

    private final ProjectService projectService;
    private final TaskService taskService;
    private final DocumentService documentService;
    private final ResourceService resourceService;
    private final ResourceControlService resourceControlService;
    private final SystemIdentityProvider systemIdentity;

    public PlatformGateway(ProjectService projectService,
                           TaskService taskService,
                           DocumentService documentService,
                           ResourceService resourceService,
                           ResourceControlService resourceControlService,
                           SystemIdentityProvider systemIdentity) {
        this.projectService = projectService;
        this.taskService = taskService;
        this.documentService = documentService;
        this.resourceService = resourceService;
        this.resourceControlService = resourceControlService;
        this.systemIdentity = systemIdentity;
    }

    /**
     * Creates a task on a project's blackboard on behalf of the platform.
     * <p>
     * This is a <b>trusted path with no membership check</b> — the caller is the engine acting as the
     * system principal, not a user working on the project, the same shape as the scheduler's
     * {@link TaskService#createScheduledTask}. The task is attributed to the system identity.
     *
     * @param projectId   the owning project's id
     * @param name        the task's name
     * @param description the task's description
     * @param priority    the task's priority
     * @return the persisted task
     * @throws java.util.NoSuchElementException if no project has that id
     */
    @Transactional
    public Task createTask(Long projectId, String name, String description, int priority) {
        Project project = projectService.findOrThrow(projectId);
        Task task = taskService.createScheduledTask(project, name, description, priority);
        log.info("PlatformGateway: task '{}' (id={}) created on project '{}' as '{}'.",
                task.getName(), task.getId(), project.getName(), systemIdentity.get().getUsername());
        return task;
    }

    /**
     * Stores a new document in a document group on behalf of the platform — the trusted inbound path a
     * BPMN process uses to record a result (a report, an acknowledgement, a snapshot).
     * <p>
     * The document is authored by the <b>system principal</b>: {@code lastModifiedBy} carries the
     * platform's own identity, so "who produced this?" has a real, queryable answer rather than a null.
     * The document is versioned by the Documents subsystem like any other, and what goes <em>inside</em>
     * it (PDF rendering, templates) is a vertical's concern, not the platform's.
     *
     * @param name     the document's name; an extension is derived from {@code mimeType} if absent
     * @param groupId  the target document group's id
     * @param content  the raw bytes of the first version
     * @param mimeType the content's MIME type
     * @return the created document
     * @throws java.util.NoSuchElementException if no document group has that id
     */
    @Transactional
    public DocumentInfo storeDocument(String name, Long groupId, byte[] content, String mimeType) {
        DocumentInfo stored = documentService.createDocument(name, groupId, systemIdentity.get(), content, mimeType);
        log.info("PlatformGateway: document '{}' (id={}) stored in group {} as '{}'.",
                name, stored.getId(), groupId, systemIdentity.get().getUsername());
        return stored;
    }

    /**
     * Sends a control command to a resource on behalf of the platform — the trusted inbound path a BPMN
     * process uses to switch an aggregate on or off, set a value, and so on.
     * <p>
     * A process holds no reservation, so the target <b>slot is supplied explicitly</b> (0 for a
     * single-slot resource). The command is refused (409) if that slot currently holds an active
     * reservation, so a process can never seize a human's reserved control window. Permission is checked
     * against the system principal (resource {@code READ} to load it, {@code UPDATE} to control it), so
     * what a process may control is a checked property of the platform's own identity.
     *
     * @param resourceId the target resource's id
     * @param command    the command identifier
     * @param param      optional free parameter (may be {@code null})
     * @param slot       the slot to address
     * @throws java.util.NoSuchElementException if no resource has that id
     * @throws org.springframework.security.access.AccessDeniedException if the system principal may not control it
     * @throws de.hallerweb.enterprise.prioritize.exception.SlotOccupiedException if the slot is reserved
     */
    @Transactional
    public void controlResource(Long resourceId, String command, String param, int slot) {
        PUser system = systemIdentity.get();
        Resource resource = resourceService.getResource(resourceId, system);
        resourceControlService.sendCommand(resource, command, param, slot, system);
        log.info("PlatformGateway: command '{}' sent to resource {} (slot {}) as '{}'.",
                command, resourceId, slot, system.getUsername());
    }
}
