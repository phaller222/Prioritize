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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

/**
 * Pure unit tests for {@link ProcessInstanceService} — membership authorization, the active-definition
 * gate, the derived business key, the platform-owned variables and the one-instance-per-task rule. No
 * Spring context, no database, no engine.
 *
 * @author peter haller
 */
class ProcessInstanceServiceTest {

    private RuntimeService runtimeService;
    private HistoryService historyService;
    private ProcessDefinitionRepository definitionRepository;
    private ProjectService projectService;
    private TaskRepository taskRepository;
    private ProcessInstanceQuery instanceQuery;
    private HistoricProcessInstanceQuery historicQuery;
    private ProcessInstanceService service;

    private PUser user;
    private Project project;

    @BeforeEach
    void setUp() {
        runtimeService = mock(RuntimeService.class);
        historyService = mock(HistoryService.class);
        definitionRepository = mock(ProcessDefinitionRepository.class);
        projectService = mock(ProjectService.class);
        taskRepository = mock(TaskRepository.class);
        service = new ProcessInstanceService(runtimeService, historyService, definitionRepository, projectService,
                taskRepository);

        user = PUser.builder().username("peter").build();
        project = Project.builder().name("Inspection round").build();
        project.setId(7L);
        when(projectService.findOrThrow(7L)).thenReturn(project);
        when(taskRepository.save(any(Task.class))).thenAnswer(i -> i.getArgument(0));

        instanceQuery = mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(instanceQuery);
        when(instanceQuery.processInstanceId(anyString())).thenReturn(instanceQuery);
        when(instanceQuery.processInstanceBusinessKey(anyString())).thenReturn(instanceQuery);
        when(instanceQuery.includeProcessVariables()).thenReturn(instanceQuery);
        when(instanceQuery.count()).thenReturn(0L);
        when(instanceQuery.list()).thenReturn(List.of());
        when(instanceQuery.singleResult()).thenReturn(null);

        historicQuery = mock(HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(historicQuery);
        when(historicQuery.processInstanceId(anyString())).thenReturn(historicQuery);
        when(historicQuery.processInstanceBusinessKey(anyString())).thenReturn(historicQuery);
        when(historicQuery.includeProcessVariables()).thenReturn(historicQuery);
        when(historicQuery.list()).thenReturn(List.of());
        when(historicQuery.singleResult()).thenReturn(null);
    }

    /** A registered, deployed definition the service will accept. */
    private ProcessDefinition activeDefinition() {
        ProcessDefinition definition = ProcessDefinition.builder()
                .processKey("inspectionRound")
                .state(ProcessDefinitionState.ACTIVE)
                .build();
        definition.setId(3L);
        when(definitionRepository.findById(3L)).thenReturn(Optional.of(definition));
        return definition;
    }

    /** The engine's answer to a start: a still-running instance. */
    private ProcessInstance engineStarts(String instanceId, boolean ended) {
        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.getId()).thenReturn(instanceId);
        when(instance.getProcessDefinitionKey()).thenReturn("inspectionRound");
        when(instance.getBusinessKey()).thenReturn("bk");
        when(instance.isEnded()).thenReturn(ended);
        when(instance.getStartTime()).thenReturn(new Date());
        when(runtimeService.startProcessInstanceByKey(anyString(), anyString(), anyMap())).thenReturn(instance);
        return instance;
    }

    private Task taskInProject(Long id, String linkedInstance) {
        Blackboard blackboard = Blackboard.builder().project(project).build();
        Task task = Task.builder().name("Check pump").blackboard(blackboard).processInstanceId(linkedInstance).build();
        task.setId(id);
        when(taskRepository.findById(id)).thenReturn(Optional.of(task));
        return task;
    }

    @Test
    @DisplayName("starts an instance for a project under the derived business key")
    void startsForProject() {
        activeDefinition();
        engineStarts("pi-1", false);

        ProcessInstanceDTO started = service.startForProject(7L, 3L, null, user);

        verify(runtimeService).startProcessInstanceByKey(eq("inspectionRound"), eq("project:7"), anyMap());
        assertEquals("pi-1", started.id());
        assertEquals(3L, started.definitionId());
        assertEquals(7L, started.projectId());
        assertNull(started.taskId());
        assertTrue(started.running());
        assertEquals("peter", started.startedBy());
    }

    @Test
    @DisplayName("starts an instance for a task and links it to the task")
    void startsForTaskAndLinks() {
        activeDefinition();
        engineStarts("pi-2", false);
        Task task = taskInProject(42L, null);

        ProcessInstanceDTO started = service.startForTask(42L, 3L, null, user);

        verify(runtimeService).startProcessInstanceByKey(eq("inspectionRound"), eq("task:42"), anyMap());
        assertEquals("pi-2", task.getProcessInstanceId());
        assertEquals(42L, started.taskId());
        assertEquals(7L, started.projectId());
        verify(taskRepository).save(task);
    }

    @Test
    @DisplayName("passes the caller's variables but keeps the platform's own")
    void platformVariablesWin() {
        activeDefinition();
        engineStarts("pi-3", false);
        taskInProject(42L, null);

        service.startForTask(42L, 3L, Map.of("threshold", 80, ProcessInstanceService.VAR_PROJECT_ID, 999L), user);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.forClass(Map.class);
        verify(runtimeService).startProcessInstanceByKey(anyString(), anyString(), vars.capture());
        assertEquals(80, vars.getValue().get("threshold"));
        assertEquals(7L, vars.getValue().get(ProcessInstanceService.VAR_PROJECT_ID)); // not the spoofed 999
        assertEquals(42L, vars.getValue().get(ProcessInstanceService.VAR_TASK_ID));
        assertEquals("peter", vars.getValue().get(ProcessInstanceService.VAR_STARTED_BY));
    }

    @Test
    @DisplayName("reports an instance that ran to its end during the start as not running")
    void endedDuringStart() {
        activeDefinition();
        engineStarts("pi-4", true);

        ProcessInstanceDTO started = service.startForProject(7L, 3L, null, user);

        assertFalse(started.running());
    }

    @Test
    @DisplayName("refuses to start a definition that is not active")
    void refusesInactiveDefinition() {
        ProcessDefinition draft = ProcessDefinition.builder()
                .processKey("inspectionRound")
                .state(ProcessDefinitionState.DRAFT)
                .build();
        draft.setId(3L);
        when(definitionRepository.findById(3L)).thenReturn(Optional.of(draft));

        assertThrows(IllegalStateException.class, () -> service.startForProject(7L, 3L, null, user));
        verify(runtimeService, never()).startProcessInstanceByKey(anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("refuses an unknown definition")
    void refusesUnknownDefinition() {
        when(definitionRepository.findById(3L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.startForProject(7L, 3L, null, user));
    }

    @Test
    @DisplayName("refuses a caller who is not on the project")
    void refusesNonMember() {
        activeDefinition();
        org.mockito.Mockito.doThrow(new AccessDeniedException("not a member"))
                .when(projectService).requireMemberOrManager(project, user);

        assertThrows(AccessDeniedException.class, () -> service.startForProject(7L, 3L, null, user));
        verify(runtimeService, never()).startProcessInstanceByKey(anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("refuses a second instance while the task's first one still runs")
    void refusesSecondRunningInstance() {
        activeDefinition();
        taskInProject(42L, "pi-old");
        when(instanceQuery.count()).thenReturn(1L); // pi-old is still in the runtime tables

        assertThrows(IllegalStateException.class, () -> service.startForTask(42L, 3L, null, user));
        verify(runtimeService, never()).startProcessInstanceByKey(anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("starts again once the task's previous instance has ended")
    void startsAgainAfterPreviousEnded() {
        activeDefinition();
        engineStarts("pi-5", false);
        Task task = taskInProject(42L, "pi-old");
        when(instanceQuery.count()).thenReturn(0L); // pi-old has finished

        service.startForTask(42L, 3L, null, user);

        assertEquals("pi-5", task.getProcessInstanceId());
    }

    @Test
    @DisplayName("refuses a task that is not attached to a project")
    void refusesDetachedTask() {
        Task task = Task.builder().name("orphan").build();
        task.setId(43L);
        when(taskRepository.findById(43L)).thenReturn(Optional.of(task));

        assertThrows(IllegalStateException.class, () -> service.startForTask(43L, 3L, null, user));
    }

    // --- reading and cancelling ---

    // Note: these helpers build their mock and only THEN feed it to a stub. Calling them inside
    // when(...).thenReturn(...) would stub one mock while another stubbing is still open, which
    // Mockito rejects as UnfinishedStubbing.

    /** A running instance as the runtime tables would answer it, with the platform's variables. */
    private ProcessInstance runningInstance(String id, String businessKey, Map<String, Object> variables) {
        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.getId()).thenReturn(id);
        when(instance.getProcessDefinitionKey()).thenReturn("inspectionRound");
        when(instance.getBusinessKey()).thenReturn(businessKey);
        when(instance.getStartTime()).thenReturn(new Date());
        when(instance.getProcessVariables()).thenReturn(variables);
        return instance;
    }

    /** A finished instance as the history would answer it. */
    private HistoricProcessInstance finishedInstance(String id, String businessKey, Map<String, Object> variables) {
        HistoricProcessInstance instance = mock(HistoricProcessInstance.class);
        when(instance.getId()).thenReturn(id);
        when(instance.getProcessDefinitionKey()).thenReturn("inspectionRound");
        when(instance.getBusinessKey()).thenReturn(businessKey);
        when(instance.getStartTime()).thenReturn(new Date());
        when(instance.getEndTime()).thenReturn(new Date());
        when(instance.getProcessVariables()).thenReturn(variables);
        return instance;
    }

    @Test
    @DisplayName("reads the instance a task is linked to while it runs")
    void readsRunningInstanceOfTask() {
        taskInProject(42L, "pi-2");
        ProcessInstance running =
                runningInstance("pi-2", "task:42", Map.of("projectId", 7L, "taskId", 42L, "startedBy", "peter"));
        when(instanceQuery.singleResult()).thenReturn(running);

        ProcessInstanceDTO instance = service.getForTask(42L, user).orElseThrow();

        assertEquals("pi-2", instance.id());
        assertTrue(instance.running());
        assertEquals(42L, instance.taskId());
        assertEquals("peter", instance.startedBy());
    }

    @Test
    @DisplayName("falls back to the history once the linked instance has finished")
    void readsFinishedInstanceFromHistory() {
        taskInProject(42L, "pi-2");
        HistoricProcessInstance finished = finishedInstance("pi-2", "task:42", Map.of("projectId", 7L, "taskId", 42L));
        when(historicQuery.singleResult()).thenReturn(finished);

        ProcessInstanceDTO instance = service.getForTask(42L, user).orElseThrow();

        assertFalse(instance.running());
    }

    @Test
    @DisplayName("answers empty for a task that never ran a process")
    void readsNothingForUnlinkedTask() {
        taskInProject(42L, null);

        assertTrue(service.getForTask(42L, user).isEmpty());
    }

    @Test
    @DisplayName("collects a project's own instances and those of its tasks")
    void collectsProjectInstances() {
        Blackboard blackboard = Blackboard.builder().project(project).build();
        blackboard.setId(9L);
        project.setBlackboard(blackboard);
        Task task = Task.builder().name("Check pump").blackboard(blackboard).processInstanceId("pi-task").build();
        task.setId(42L);
        when(taskRepository.findByBlackboard_Id(9L)).thenReturn(List.of(task));
        ProcessInstance projectLevel = runningInstance("pi-project", "project:7", Map.of());
        ProcessInstance taskLevel = runningInstance("pi-task", "task:42", Map.of("projectId", 7L, "taskId", 42L));
        when(instanceQuery.list()).thenReturn(List.of(projectLevel));
        when(instanceQuery.singleResult()).thenReturn(taskLevel);

        List<ProcessInstanceDTO> instances = service.getForProject(7L, user);

        assertEquals(2, instances.size());
        assertEquals("pi-project", instances.get(0).id());
        assertEquals(7L, instances.get(0).projectId()); // derived from the business key, no variables needed
        assertEquals("pi-task", instances.get(1).id());
    }

    @Test
    @DisplayName("refuses to show an instance to somebody outside its project")
    void refusesForeignInstance() {
        ProcessInstance running = runningInstance("pi-9", "project:7", Map.of());
        when(instanceQuery.singleResult()).thenReturn(running);
        org.mockito.Mockito.doThrow(new AccessDeniedException("not a member"))
                .when(projectService).requireMemberOrManager(project, user);

        assertThrows(AccessDeniedException.class, () -> service.get("pi-9", user));
    }

    @Test
    @DisplayName("does not serve instances the platform did not start")
    void refusesForeignlyStartedInstance() {
        ProcessInstance foreign = runningInstance("pi-x", "somebody-elses-key", Map.of());
        when(instanceQuery.singleResult()).thenReturn(foreign);

        assertThrows(NoSuchElementException.class, () -> service.get("pi-x", user));
    }

    @Test
    @DisplayName("reports an unknown instance as not found")
    void unknownInstance() {
        assertThrows(NoSuchElementException.class, () -> service.get("pi-nope", user));
    }

    @Test
    @DisplayName("cancels a running instance with a reason, manager only")
    void cancelsRunningInstance() {
        ProcessInstance running = runningInstance("pi-9", "project:7", Map.of());
        when(instanceQuery.singleResult()).thenReturn(running);

        service.cancel("pi-9", "wrong process picked", user);

        verify(projectService).requireManager(project, user);
        verify(runtimeService).deleteProcessInstance("pi-9", "wrong process picked");
    }

    @Test
    @DisplayName("records who cancelled when no reason is given")
    void cancelsWithDefaultReason() {
        ProcessInstance running = runningInstance("pi-9", "project:7", Map.of());
        when(instanceQuery.singleResult()).thenReturn(running);

        service.cancel("pi-9", "  ", user);

        verify(runtimeService).deleteProcessInstance("pi-9", "cancelled by peter");
    }

    @Test
    @DisplayName("refuses to cancel an instance that has already finished")
    void refusesCancellingFinishedInstance() {
        HistoricProcessInstance finished = finishedInstance("pi-9", "project:7", Map.of());
        when(historicQuery.singleResult()).thenReturn(finished);

        assertThrows(IllegalStateException.class, () -> service.cancel("pi-9", "too late", user));
        verify(runtimeService, never()).deleteProcessInstance(anyString(), anyString());
    }

    @Test
    @DisplayName("refuses cancelling to a member who is not the manager")
    void refusesCancellingAsMember() {
        ProcessInstance running = runningInstance("pi-9", "project:7", Map.of());
        when(instanceQuery.singleResult()).thenReturn(running);
        org.mockito.Mockito.doThrow(new AccessDeniedException("not the manager"))
                .when(projectService).requireManager(project, user);

        assertThrows(AccessDeniedException.class, () -> service.cancel("pi-9", "no", user));
        verify(runtimeService, never()).deleteProcessInstance(anyString(), anyString());
    }
}
