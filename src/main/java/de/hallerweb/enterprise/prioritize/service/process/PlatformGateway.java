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
import de.hallerweb.enterprise.prioritize.service.document.DocumentService;
import de.hallerweb.enterprise.prioritize.service.project.ProjectService;
import de.hallerweb.enterprise.prioritize.service.project.TaskService;
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
 *
 * @author peter haller
 */
@Service
public class PlatformGateway {

    private static final Logger log = LoggerFactory.getLogger(PlatformGateway.class);

    private final ProjectService projectService;
    private final TaskService taskService;
    private final DocumentService documentService;
    private final SystemIdentityProvider systemIdentity;

    public PlatformGateway(ProjectService projectService,
                           TaskService taskService,
                           DocumentService documentService,
                           SystemIdentityProvider systemIdentity) {
        this.projectService = projectService;
        this.taskService = taskService;
        this.documentService = documentService;
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
}
