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

package de.hallerweb.enterprise.prioritize.service.project;

import de.hallerweb.enterprise.prioritize.model.document.DocumentInfo;
import de.hallerweb.enterprise.prioritize.model.project.Blackboard;
import de.hallerweb.enterprise.prioritize.model.project.Project;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.security.Action;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.project.ProjectRepository;
import de.hallerweb.enterprise.prioritize.service.document.DocumentService;
import de.hallerweb.enterprise.prioritize.service.resource.ResourceService;
import de.hallerweb.enterprise.prioritize.service.security.AuthorizationService;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Manages {@link Project projects} and their team, resources and documents. Access is
 * membership-based rather than role-based: the manager or any member may read a project,
 * while only the manager may modify it or manage its team. Each project owns a
 * {@link Blackboard} that is created together with the project.
 *
 * @author peter haller
 */
@Service
@RequiredArgsConstructor
@Transactional
@Log4j2
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserService userService;
    private final ResourceService resourceService;
    private final DocumentService documentService;
    private final AuthorizationService authService;

    /**
     * Immutable set of editable project fields, decoupling the service from HTTP DTOs.
     */
    public record ProjectData(String name, String description, int priority,
                              java.time.LocalDate beginDate, java.time.LocalDate dueDate,
                              int maxManDays) {
    }

    /**
     * Creates a new project with an empty blackboard. The creator becomes the manager and is
     * added as the first member.
     * <p>
     * A project has no owning container to authorize against (unlike resources/documents, which are
     * created into a department or group), so creation is gated by a <b>type-level</b>
     * {@link Action#CREATE} permission on {@link Project} — a {@link
     * de.hallerweb.enterprise.prioritize.model.security.PermissionRecord} with {@code objectId == 0}.
     * Admins are allowed implicitly. Read/update/delete of an existing project stay membership-based.
     *
     * @param data    the project's initial field values
     * @param creator the authenticated user creating the project
     * @return the persisted project
     * @throws AccessDeniedException if the creator may not create projects
     */
    public Project createProject(ProjectData data, PUser creator) {
        if (!authService.hasPermission(creator, Project.class.getCanonicalName(), 0L, Action.CREATE)) {
            throw new AccessDeniedException("No permission to create projects.");
        }
        Blackboard blackboard = Blackboard.builder()
                .title(data.name())
                .description("Blackboard for project " + data.name())
                .frozen(false)
                .build();

        Project project = Project.builder()
                .name(data.name())
                .description(data.description())
                .priority(data.priority())
                .beginDate(data.beginDate())
                .dueDate(data.dueDate())
                .maxManDays(data.maxManDays())
                .manager(creator)
                .blackboard(blackboard)
                .build();
        blackboard.setProject(project); // keep the bidirectional link consistent in-memory
        project.getMembers().add(creator);

        Project saved = projectRepository.save(project);
        log.info("Project '{}' (id={}) created by user '{}'.", saved.getName(), saved.getId(), creator.getUsername());
        return saved;
    }

    /**
     * Returns the project if the user is its manager or a member.
     *
     * @param projectId the project id
     * @param user      the requesting user
     * @return the project
     */
    @Transactional(readOnly = true)
    public Project getProject(Long projectId, PUser user) {
        Project project = findOrThrow(projectId);
        requireMemberOrManager(project, user);
        return project;
    }

    /**
     * Returns all projects the user manages or participates in.
     *
     * @param user the requesting user
     * @return the user's projects (possibly empty)
     */
    @Transactional(readOnly = true)
    public List<Project> getMyProjects(PUser user) {
        return projectRepository.findAll().stream()
                .filter(p -> isMemberOrManager(p, user))
                .toList();
    }

    /**
     * Updates the editable fields of a project. Manager only.
     *
     * @param projectId the project id
     * @param data      the new field values
     * @param user      the requesting user
     * @return the updated project
     */
    public Project updateProject(Long projectId, ProjectData data, PUser user) {
        Project project = findOrThrow(projectId);
        requireManager(project, user);
        project.setName(data.name());
        project.setDescription(data.description());
        project.setPriority(data.priority());
        project.setBeginDate(data.beginDate());
        project.setDueDate(data.dueDate());
        project.setMaxManDays(data.maxManDays());
        return project;
    }

    /**
     * Deletes a project (and, by cascade, its blackboard and tasks). Manager only.
     *
     * @param projectId the project id
     * @param user      the requesting user
     */
    public void deleteProject(Long projectId, PUser user) {
        Project project = findOrThrow(projectId);
        requireManager(project, user);
        projectRepository.delete(project);
        log.info("Project '{}' (id={}) deleted by user '{}'.", project.getName(), projectId, user.getUsername());
    }

    // --- Team / resource / document management (manager only) ---

    public Project addMember(Long projectId, Long userId, PUser user) {
        Project project = findOrThrow(projectId);
        requireManager(project, user);
        project.getMembers().add(userService.getUserById(userId));
        return project;
    }

    public Project removeMember(Long projectId, Long userId, PUser user) {
        Project project = findOrThrow(projectId);
        requireManager(project, user);
        if (project.getManager() != null && userId.equals(project.getManager().getId())) {
            throw new IllegalStateException("The manager cannot be removed from the project.");
        }
        project.getMembers().removeIf(m -> userId.equals(m.getId()));
        return project;
    }

    public Project addResource(Long projectId, Long resourceId, PUser user) {
        Project project = findOrThrow(projectId);
        requireManager(project, user);
        Resource resource = resourceService.getResource(resourceId, user);
        project.getResources().add(resource);
        return project;
    }

    public Project removeResource(Long projectId, Long resourceId, PUser user) {
        Project project = findOrThrow(projectId);
        requireManager(project, user);
        project.getResources().removeIf(r -> resourceId.equals(r.getId()));
        return project;
    }

    public Project addDocument(Long projectId, Long documentInfoId, PUser user) {
        Project project = findOrThrow(projectId);
        requireManager(project, user);
        DocumentInfo document = documentService.getDocument(documentInfoId, user);
        project.getDocuments().add(document);
        return project;
    }

    public Project removeDocument(Long projectId, Long documentInfoId, PUser user) {
        Project project = findOrThrow(projectId);
        requireManager(project, user);
        project.getDocuments().removeIf(d -> documentInfoId.equals(d.getId()));
        return project;
    }

    // --- Authorization helpers (membership-based) ---

    /**
     * Loads a project or throws if it does not exist.
     *
     * @param projectId the project id
     * @return the managed project
     * @throws NoSuchElementException if no such project exists
     */
    public Project findOrThrow(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new NoSuchElementException("Project not found"));
    }

    /**
     * Ensures the user is the manager or a member of the project.
     *
     * @throws AccessDeniedException if the user is neither manager nor member
     */
    public void requireMemberOrManager(Project project, PUser user) {
        if (!isMemberOrManager(project, user)) {
            throw new AccessDeniedException("No access to this project.");
        }
    }

    /**
     * Ensures the user is the manager of the project.
     *
     * @throws AccessDeniedException if the user is not the manager
     */
    public void requireManager(Project project, PUser user) {
        if (project.getManager() == null || !project.getManager().getId().equals(user.getId())) {
            throw new AccessDeniedException("Only the project manager may perform this action.");
        }
    }

    private boolean isMemberOrManager(Project project, PUser user) {
        boolean isManager = project.getManager() != null
                && project.getManager().getId().equals(user.getId());
        boolean isMember = project.getMembers().stream()
                .anyMatch(m -> m.getId().equals(user.getId()));
        return isManager || isMember;
    }
}
