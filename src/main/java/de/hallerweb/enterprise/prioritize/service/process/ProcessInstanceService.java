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

import de.hallerweb.enterprise.prioritize.dto.process.ProcessInstanceDTO;
import de.hallerweb.enterprise.prioritize.model.process.ProcessDefinition;
import de.hallerweb.enterprise.prioritize.model.process.ProcessDefinitionState;
import de.hallerweb.enterprise.prioritize.model.project.Blackboard;
import de.hallerweb.enterprise.prioritize.model.project.Project;
import de.hallerweb.enterprise.prioritize.model.project.Task;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.process.ProcessDefinitionRepository;
import de.hallerweb.enterprise.prioritize.repository.project.TaskRepository;
import de.hallerweb.enterprise.prioritize.service.project.ProjectService;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Starts BPMN process instances for a project or one of its tasks — the point where the engine
 * actually begins to run something.
 * <p>
 * <b>Orchestration, not ownership.</b> A process instance never owns a task's lifecycle: it creates
 * and coordinates work, while Prioritize stays the system of record for status, time tracking and
 * everything else. The whole coupling is one string, {@link Task#getProcessInstanceId()}, plus the
 * instance's business key in the other direction. Remove Flowable and the core still works.
 * <p>
 * <b>Authorization is project membership</b> ({@code requireMemberOrManager}, the same rule as
 * creating a task): whoever may work on a project may start a process on it. There is deliberately no
 * second permission on top — <em>which</em> definitions can run at all is already decided by the
 * activation right in {@link ProcessDefinitionService}, and a definition that nobody activated cannot
 * be started here.
 * <p>
 * <b>Only registered definitions can be started.</b> Definitions deployed straight from the classpath
 * trusted root are visible to the engine but not to this service; that is what makes them the
 * break-glass path rather than a second public API (see {@code ProcessDefinitionStartupReport}).
 *
 * @author peter haller
 */
@Service
public class ProcessInstanceService {

    private static final Logger log = LoggerFactory.getLogger(ProcessInstanceService.class);

    /** Process variable carrying the owning project's id into the diagram. */
    public static final String VAR_PROJECT_ID = "projectId";
    /** Process variable carrying the owning task's id, absent for a project-level instance. */
    public static final String VAR_TASK_ID = "taskId";
    /** Process variable carrying the username of whoever started the instance. */
    public static final String VAR_STARTED_BY = "startedBy";

    private final RuntimeService runtimeService;
    private final ProcessDefinitionRepository definitionRepository;
    private final ProjectService projectService;
    private final TaskRepository taskRepository;

    public ProcessInstanceService(RuntimeService runtimeService,
                                  ProcessDefinitionRepository definitionRepository,
                                  ProjectService projectService,
                                  TaskRepository taskRepository) {
        this.runtimeService = runtimeService;
        this.definitionRepository = definitionRepository;
        this.projectService = projectService;
        this.taskRepository = taskRepository;
    }

    /**
     * The business key of an instance started for a project. One rule, derived, never supplied by the
     * caller: the business key answers "what is this instance about", and that must not be up for
     * negotiation. Event correlation gets its own rule later — this one is not overloaded with it.
     */
    public static String businessKeyForProject(Long projectId) {
        return "project:" + projectId;
    }

    /** The business key of an instance started for a task. See {@link #businessKeyForProject}. */
    public static String businessKeyForTask(Long taskId) {
        return "task:" + taskId;
    }

    /**
     * Starts an instance of an active definition for a whole project.
     *
     * @param projectId    the project the instance belongs to
     * @param definitionId the registered definition to start
     * @param variables    optional initial process variables; may be {@code null}
     * @param user         the requesting user (must be manager or member)
     * @return the started instance
     * @throws org.springframework.security.access.AccessDeniedException if the user is not on the project
     * @throws NoSuchElementException if project or definition do not exist
     * @throws IllegalStateException  if the definition is not active
     */
    @Transactional
    public ProcessInstanceDTO startForProject(Long projectId, Long definitionId,
                                              Map<String, Object> variables, PUser user) {
        Project project = projectService.findOrThrow(projectId);
        projectService.requireMemberOrManager(project, user);

        ProcessDefinition definition = requireActiveDefinition(definitionId);
        return start(definition, businessKeyForProject(projectId), project, null, variables, user);
    }

    /**
     * Starts an instance of an active definition for a single task and links the two.
     * <p>
     * A task can only carry one instance at a time: starting a second one while the first is still
     * running would overwrite the link and leave the running instance unattributable. Once the linked
     * instance has ended, the task can be started into a new one.
     *
     * @param taskId       the task the instance belongs to
     * @param definitionId the registered definition to start
     * @param variables    optional initial process variables; may be {@code null}
     * @param user         the requesting user (must be manager or member of the task's project)
     * @return the started instance
     * @throws IllegalStateException if the definition is not active, or the task already runs one
     */
    @Transactional
    public ProcessInstanceDTO startForTask(Long taskId, Long definitionId,
                                           Map<String, Object> variables, PUser user) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NoSuchElementException("No task with id " + taskId));
        Project project = projectOf(task);
        projectService.requireMemberOrManager(project, user);

        ProcessDefinition definition = requireActiveDefinition(definitionId);
        String linked = task.getProcessInstanceId();
        if (linked != null && isRunning(linked)) {
            throw new IllegalStateException("Task " + taskId + " is already linked to running process instance "
                    + linked + ".");
        }

        ProcessInstanceDTO started = start(definition, businessKeyForTask(taskId), project, task, variables, user);
        task.setProcessInstanceId(started.id());
        taskRepository.save(task);
        return started;
    }

    private ProcessInstanceDTO start(ProcessDefinition definition, String businessKey, Project project,
                                     Task task, Map<String, Object> variables, PUser user) {
        // Caller variables first, platform ones last: what the platform states about an instance —
        // where it belongs and who started it — cannot be overwritten from the outside.
        Map<String, Object> vars = new HashMap<>();
        if (variables != null) {
            vars.putAll(variables);
        }
        vars.put(VAR_PROJECT_ID, project.getId());
        if (task != null) {
            vars.put(VAR_TASK_ID, task.getId());
        }
        vars.put(VAR_STARTED_BY, user.getUsername());

        ProcessInstance instance =
                runtimeService.startProcessInstanceByKey(definition.getProcessKey(), businessKey, vars);

        log.info("Started process '{}' as instance {} for {} by '{}'.",
                definition.getProcessKey(), instance.getId(), businessKey, user.getUsername());
        return toDto(instance, definition.getId(), project.getId(), task != null ? task.getId() : null,
                user.getUsername());
    }

    /** Whether the engine still holds a runtime state for this instance. */
    private boolean isRunning(String processInstanceId) {
        return runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).count() > 0;
    }

    private ProcessDefinition requireActiveDefinition(Long definitionId) {
        ProcessDefinition definition = definitionRepository.findById(definitionId)
                .orElseThrow(() -> new NoSuchElementException("No process definition with id " + definitionId));
        if (definition.getState() != ProcessDefinitionState.ACTIVE) {
            throw new IllegalStateException("Process definition '" + definition.getProcessKey()
                    + "' is " + definition.getState() + "; activate it before starting instances.");
        }
        return definition;
    }

    private static ProcessInstanceDTO toDto(ProcessInstance instance, Long definitionId, Long projectId,
                                            Long taskId, String startedBy) {
        // A process without a wait state runs to its end inside startProcessInstanceByKey; the engine
        // then reports it as ended right away. That is a normal outcome, not a failure.
        return new ProcessInstanceDTO(
                instance.getId(),
                instance.getProcessDefinitionKey(),
                definitionId,
                instance.getBusinessKey(),
                projectId,
                taskId,
                !instance.isEnded(),
                instance.getStartTime() != null ? instance.getStartTime().toInstant() : null,
                startedBy);
    }

    private static Project projectOf(Task task) {
        Blackboard blackboard = task.getBlackboard();
        Project project = blackboard != null ? blackboard.getProject() : null;
        if (project == null) {
            throw new IllegalStateException("Task " + task.getId() + " is not attached to a project.");
        }
        return project;
    }
}
