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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
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

    /** Business key prefix of an instance started for a whole project. */
    private static final String PROJECT_PREFIX = "project:";
    /** Business key prefix of an instance started for a single task. */
    private static final String TASK_PREFIX = "task:";

    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final ProcessDefinitionRepository definitionRepository;
    private final ProjectService projectService;
    private final TaskRepository taskRepository;

    public ProcessInstanceService(RuntimeService runtimeService,
                                  HistoryService historyService,
                                  ProcessDefinitionRepository definitionRepository,
                                  ProjectService projectService,
                                  TaskRepository taskRepository) {
        this.runtimeService = runtimeService;
        this.historyService = historyService;
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
        return PROJECT_PREFIX + projectId;
    }

    /** The business key of an instance started for a task. See {@link #businessKeyForProject}. */
    public static String businessKeyForTask(Long taskId) {
        return TASK_PREFIX + taskId;
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

    /**
     * The process instances belonging to a project — the ones started for the project itself plus the
     * ones linked to its tasks, running and already finished.
     *
     * @param projectId the project
     * @param user      the requesting user (must be manager or member)
     * @return the instances, empty if the project never started one
     */
    @Transactional(readOnly = true)
    public List<ProcessInstanceDTO> getForProject(Long projectId, PUser user) {
        Project project = projectService.findOrThrow(projectId);
        projectService.requireMemberOrManager(project, user);

        List<ProcessInstanceDTO> instances = new ArrayList<>(byBusinessKey(businessKeyForProject(projectId)));
        Blackboard blackboard = project.getBlackboard();
        if (blackboard != null) {
            // The link on the task is the authoritative pointer; asking by business key would also find
            // instances of tasks that have since been re-linked to a newer one.
            for (Task task : taskRepository.findByBlackboard_Id(blackboard.getId())) {
                if (task.getProcessInstanceId() != null) {
                    resolve(task.getProcessInstanceId()).ifPresent(instances::add);
                }
            }
        }
        return instances;
    }

    /**
     * The instance a task is linked to, running or finished.
     *
     * @param taskId the task
     * @param user   the requesting user (must be manager or member of the task's project)
     * @return the linked instance, or empty if the task never ran one
     */
    @Transactional(readOnly = true)
    public Optional<ProcessInstanceDTO> getForTask(Long taskId, PUser user) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NoSuchElementException("No task with id " + taskId));
        projectService.requireMemberOrManager(projectOf(task), user);
        return task.getProcessInstanceId() == null ? Optional.empty() : resolve(task.getProcessInstanceId());
    }

    /**
     * A single instance by its engine id.
     *
     * @param processInstanceId the engine's instance id
     * @param user              the requesting user (must be on the owning project)
     * @return the instance
     * @throws NoSuchElementException if the engine does not know it, or the platform did not start it
     */
    @Transactional(readOnly = true)
    public ProcessInstanceDTO get(String processInstanceId, PUser user) {
        ProcessInstanceDTO instance = resolveOrThrow(processInstanceId);
        projectService.requireMemberOrManager(owningProject(instance), user);
        return instance;
    }

    /**
     * Cancels a running instance. The engine deletes its runtime state and keeps the cancellation, with
     * the given reason, in its history — so what happened stays readable afterwards.
     * <p>
     * <b>Manager only.</b> Starting a process is everyday work on a project, cutting one short is not:
     * a half-finished process may leave tasks behind that nobody will pick up again. The task's link is
     * deliberately <b>not</b> cleared — it still points at what happened; the task simply becomes
     * startable again because the instance no longer runs.
     *
     * @param processInstanceId the engine's instance id
     * @param reason            why it was cancelled; kept in the engine's history
     * @param user              the requesting user (must be the owning project's manager)
     * @throws IllegalStateException if the instance has already finished
     */
    @Transactional
    public void cancel(String processInstanceId, String reason, PUser user) {
        ProcessInstanceDTO instance = resolveOrThrow(processInstanceId);
        projectService.requireManager(owningProject(instance), user);
        if (!instance.running()) {
            throw new IllegalStateException("Process instance " + processInstanceId + " has already finished.");
        }

        runtimeService.deleteProcessInstance(processInstanceId,
                reason == null || reason.isBlank() ? "cancelled by " + user.getUsername() : reason);
        log.info("Cancelled process instance {} ({}) on request of '{}': {}",
                processInstanceId, instance.businessKey(), user.getUsername(), reason);
    }

    /** Running instances first, then the finished ones the engine still has in its history. */
    private List<ProcessInstanceDTO> byBusinessKey(String businessKey) {
        List<ProcessInstanceDTO> instances = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (ProcessInstance running : runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey(businessKey).includeProcessVariables().list()) {
            seen.add(running.getId());
            instances.add(fromRuntime(running));
        }
        for (HistoricProcessInstance historic : historyService.createHistoricProcessInstanceQuery()
                .processInstanceBusinessKey(businessKey).includeProcessVariables().list()) {
            if (!seen.contains(historic.getId())) {
                instances.add(fromHistoric(historic));
            }
        }
        return instances;
    }

    /**
     * Looks an instance up wherever it currently lives: the runtime tables while it runs, the history
     * once it has ended. Both are asked, because "finished" is a normal answer here, not a miss.
     */
    private Optional<ProcessInstanceDTO> resolve(String processInstanceId) {
        ProcessInstance running = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).includeProcessVariables().singleResult();
        if (running != null) {
            return Optional.of(fromRuntime(running));
        }
        HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId).includeProcessVariables().singleResult();
        return Optional.ofNullable(historic).map(this::fromHistoric);
    }

    private ProcessInstanceDTO resolveOrThrow(String processInstanceId) {
        return resolve(processInstanceId)
                .orElseThrow(() -> new NoSuchElementException("No process instance " + processInstanceId));
    }

    /**
     * The project an instance belongs to, and therefore the thing to authorize against. Instances the
     * platform did not start — a definition deployed straight from the classpath trusted root, say —
     * carry neither the variables nor the business key to answer that, and this API does not serve
     * them: there would be nobody to check the caller against.
     */
    private Project owningProject(ProcessInstanceDTO instance) {
        if (instance.taskId() != null) {
            Task task = taskRepository.findById(instance.taskId())
                    .orElseThrow(() -> new NoSuchElementException("Process instance " + instance.id()
                            + " points at task " + instance.taskId() + ", which no longer exists."));
            return projectOf(task);
        }
        if (instance.projectId() != null) {
            return projectService.findOrThrow(instance.projectId());
        }
        throw new NoSuchElementException("Process instance " + instance.id()
                + " was not started by the platform and is not served here.");
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
        if (definitionId == null) {
            throw new IllegalArgumentException("Which process definition should be started? None was given.");
        }
        ProcessDefinition definition = definitionRepository.findById(definitionId)
                .orElseThrow(() -> new NoSuchElementException("No process definition with id " + definitionId));
        if (definition.getState() != ProcessDefinitionState.ACTIVE) {
            throw new IllegalStateException("Process definition '" + definition.getProcessKey()
                    + "' is " + definition.getState() + "; activate it before starting instances.");
        }
        return definition;
    }

    /** A running instance, as the runtime tables know it. */
    private ProcessInstanceDTO fromRuntime(ProcessInstance instance) {
        Map<String, Object> vars = instance.getProcessVariables();
        return new ProcessInstanceDTO(
                instance.getId(),
                instance.getProcessDefinitionKey(),
                definitionIdFor(instance.getProcessDefinitionKey()),
                instance.getBusinessKey(),
                ownerId(vars, VAR_PROJECT_ID, instance.getBusinessKey(), PROJECT_PREFIX),
                ownerId(vars, VAR_TASK_ID, instance.getBusinessKey(), TASK_PREFIX),
                true, // it is in the runtime tables, so it runs
                toInstant(instance.getStartTime()),
                stringVar(vars, VAR_STARTED_BY));
    }

    /** A finished (or, with some history levels, still running) instance from the engine's history. */
    private ProcessInstanceDTO fromHistoric(HistoricProcessInstance instance) {
        Map<String, Object> vars = instance.getProcessVariables();
        return new ProcessInstanceDTO(
                instance.getId(),
                instance.getProcessDefinitionKey(),
                definitionIdFor(instance.getProcessDefinitionKey()),
                instance.getBusinessKey(),
                ownerId(vars, VAR_PROJECT_ID, instance.getBusinessKey(), PROJECT_PREFIX),
                ownerId(vars, VAR_TASK_ID, instance.getBusinessKey(), TASK_PREFIX),
                instance.getEndTime() == null,
                toInstant(instance.getStartTime()),
                stringVar(vars, VAR_STARTED_BY));
    }

    /** The registry entry answering to a process key, or {@code null} for a key deployed outside it. */
    private Long definitionIdFor(String processKey) {
        return definitionRepository.findByProcessKey(processKey).map(ProcessDefinition::getId).orElse(null);
    }

    /**
     * Where an instance belongs: the process variable the platform set at start, falling back to the
     * business key. The fallback matters because process variables can be dropped by a lower history
     * level, while the business key is always there.
     */
    private static Long ownerId(Map<String, Object> variables, String variable, String businessKey, String prefix) {
        Object value = variables == null ? null : variables.get(variable);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (businessKey != null && businessKey.startsWith(prefix)) {
            try {
                return Long.valueOf(businessKey.substring(prefix.length()));
            } catch (NumberFormatException malformed) {
                return null; // not ours after all
            }
        }
        return null;
    }

    private static String stringVar(Map<String, Object> variables, String variable) {
        Object value = variables == null ? null : variables.get(variable);
        return value == null ? null : value.toString();
    }

    private static Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
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
                toInstant(instance.getStartTime()),
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
