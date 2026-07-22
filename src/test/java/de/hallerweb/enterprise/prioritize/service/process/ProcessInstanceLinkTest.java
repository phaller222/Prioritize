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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.hallerweb.enterprise.prioritize.dto.process.ProcessDefinitionDTO;
import de.hallerweb.enterprise.prioritize.dto.process.ProcessInstanceDTO;
import de.hallerweb.enterprise.prioritize.model.document.Document;
import de.hallerweb.enterprise.prioritize.model.document.DocumentInfo;
import de.hallerweb.enterprise.prioritize.model.project.Project;
import de.hallerweb.enterprise.prioritize.model.project.Task;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.document.DocumentInfoRepository;
import de.hallerweb.enterprise.prioritize.repository.project.TaskRepository;
import de.hallerweb.enterprise.prioritize.service.project.ProjectService;
import de.hallerweb.enterprise.prioritize.service.project.ProjectService.ProjectData;
import de.hallerweb.enterprise.prioritize.service.project.TaskService;
import de.hallerweb.enterprise.prioritize.service.project.TaskService.TaskData;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.flowable.engine.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the link between a task and a running process against the <b>real Flowable engine</b>: that
 * starting really starts something, that the task points at it, that the same task cannot run two
 * instances at once, and that cancelling ends it while the history keeps what happened.
 * <p>
 * The fixture uses its own process key and waits in a receive task, so a started instance stays alive
 * long enough to be looked at.
 *
 * @author peter haller
 */
@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
class ProcessInstanceLinkTest {

    private static final String KEY = "instanceLinkProbe";

    @Autowired
    private ProcessInstanceService instanceService;
    @Autowired
    private ProcessDefinitionService definitionService;
    @Autowired
    private DocumentInfoRepository documentInfoRepository;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private UserService userService;

    private PUser admin;
    private Project project;
    private Task task;
    private ProcessDefinitionDTO definition;

    @BeforeEach
    void setUp() {
        admin = userService.findUserByUsername("admin");
        project = projectService.createProject(
                new ProjectData("Instance link probe", "runs a process", 1,
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 10),
                admin);
        task = taskService.createTask(project.getId(), new TaskData("Check pump", "on site", 2), admin);
        definition = activatedDefinition();
    }

    /** A process that waits in a receive task, so the instance is still there to be inspected. */
    private static byte[] bpmn() {
        return ("""
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             targetNamespace="http://prioritize.test">
                  <process id="%s" name="Instance link probe" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="toWait" sourceRef="start" targetRef="wait"/>
                    <receiveTask id="wait" name="Waiting"/>
                    <sequenceFlow id="toEnd" sourceRef="wait" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """.formatted(KEY)).getBytes(StandardCharsets.UTF_8);
    }

    private ProcessDefinitionDTO activatedDefinition() {
        Document document = Document.builder()
                .name(KEY + ".bpmn").version(1).mimeType("text/xml").data(bpmn()).build();
        DocumentInfo info = DocumentInfo.builder().currentDocument(document).build();
        document.setDocumentInfo(info);
        info = documentInfoRepository.save(info);

        ProcessDefinitionDTO registered = definitionService.register(info.getId(), admin);
        return definitionService.activate(registered.id(), admin);
    }

    @Test
    @DisplayName("starting for a task really starts a process and links the task to it")
    void startsAndLinks() {
        ProcessInstanceDTO started = instanceService.startForTask(task.getId(), definition.id(),
                Map.of("threshold", 80), admin);

        assertNotNull(started.id());
        assertTrue(started.running());
        assertEquals("task:" + task.getId(), started.businessKey());
        assertEquals(definition.id(), started.definitionId());
        assertEquals(admin.getUsername(), started.startedBy());
        assertEquals(started.id(), taskRepository.findById(task.getId()).orElseThrow().getProcessInstanceId());

        // and the engine answers the same instance back, with what the platform put into it
        ProcessInstanceDTO read = instanceService.getForTask(task.getId(), admin).orElseThrow();
        assertEquals(started.id(), read.id());
        assertEquals(task.getId(), read.taskId());
        assertEquals(project.getId(), read.projectId());
    }

    @Test
    @DisplayName("a task runs at most one process at a time")
    void refusesSecondInstance() {
        instanceService.startForTask(task.getId(), definition.id(), null, admin);

        assertThrows(IllegalStateException.class,
                () -> instanceService.startForTask(task.getId(), definition.id(), null, admin));
    }

    @Test
    @DisplayName("a project sees its own instances and those of its tasks")
    void collectsProjectInstances() {
        ProcessInstanceDTO forProject = instanceService.startForProject(project.getId(), definition.id(), null, admin);
        ProcessInstanceDTO forTask = instanceService.startForTask(task.getId(), definition.id(), null, admin);

        List<String> ids = instanceService.getForProject(project.getId(), admin).stream()
                .map(ProcessInstanceDTO::id).toList();

        assertTrue(ids.contains(forProject.id()));
        assertTrue(ids.contains(forTask.id()));
    }

    @Test
    @DisplayName("cancelling ends the instance but leaves the task's link pointing at what happened")
    void cancelEndsInstanceAndKeepsLink() {
        ProcessInstanceDTO started = instanceService.startForTask(task.getId(), definition.id(), null, admin);

        instanceService.cancel(started.id(), "picked the wrong process", admin);

        ProcessInstanceDTO afterwards = instanceService.getForTask(task.getId(), admin).orElseThrow();
        assertEquals(started.id(), afterwards.id(), "the link still points at the cancelled instance");
        assertFalse(afterwards.running());
        assertEquals(started.id(), taskRepository.findById(task.getId()).orElseThrow().getProcessInstanceId());

        // ... and because it no longer runs, the task can be started into a new process
        ProcessInstanceDTO again = instanceService.startForTask(task.getId(), definition.id(), null, admin);
        assertTrue(again.running());
        assertEquals(again.id(), taskRepository.findById(task.getId()).orElseThrow().getProcessInstanceId());
    }

    @Test
    @DisplayName("a cancelled instance cannot be cancelled twice")
    void refusesCancellingTwice() {
        ProcessInstanceDTO started = instanceService.startForTask(task.getId(), definition.id(), null, admin);
        instanceService.cancel(started.id(), "once", admin);

        assertThrows(IllegalStateException.class, () -> instanceService.cancel(started.id(), "twice", admin));
    }

    @Test
    @DisplayName("a definition that is not active cannot be started")
    void refusesSuspendedDefinition() {
        definitionService.deactivate(definition.id(), admin);

        assertThrows(IllegalStateException.class,
                () -> instanceService.startForTask(task.getId(), definition.id(), null, admin));
    }

    @Test
    @DisplayName("force-unregister tears the definition out of the engine and frees its key")
    void forceUnregisterRemovesFromEngine() {
        definitionService.unregister(definition.id(), true, admin); // no running instances

        assertThrows(NoSuchElementException.class, () -> definitionService.get(definition.id(), admin));
        assertEquals(0, repositoryService.createProcessDefinitionQuery().processDefinitionKey(KEY).count(),
                "the engine must no longer answer to the key");

        // the key is free again: a fresh document defining the same process registers cleanly
        Document document = Document.builder().name(KEY + ".bpmn").version(1).mimeType("text/xml").data(bpmn()).build();
        DocumentInfo info = DocumentInfo.builder().currentDocument(document).build();
        document.setDocumentInfo(info);
        info = documentInfoRepository.save(info);
        assertEquals(KEY, definitionService.register(info.getId(), admin).processKey());
    }

    @Test
    @DisplayName("force-unregister refuses while an instance is still running")
    void forceUnregisterRefusesWithRunningInstance() {
        instanceService.startForTask(task.getId(), definition.id(), null, admin);

        assertThrows(IllegalStateException.class, () -> definitionService.unregister(definition.id(), true, admin));
        assertEquals(KEY, definitionService.get(definition.id(), admin).processKey()); // still there
    }
}
