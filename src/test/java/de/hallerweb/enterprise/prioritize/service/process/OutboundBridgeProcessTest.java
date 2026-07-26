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
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * The outbound bridge's acceptance test against the <b>real Flowable engine</b>: a process that
 * <em>waits</em> for an NFC scan of a resource is correlated to that scan by
 * {@link EngineEventBridge#matchingExecutionIds} and, once the message is delivered, runs on to do real
 * platform work. This is the "it does something" proof for slice 5 — from here a real-world signal can
 * drive a running process forward.
 * <p>
 * The test exercises the correlation against the real engine and delivers the message within its own
 * transaction, so the started instance and the created task are visible to the assertions. The bridge's
 * production path additionally wraps each delivery in a <em>new</em> transaction (it fires in the source
 * event's {@code AFTER_COMMIT} phase, where the committed instance is already visible) — that wrapper is
 * covered by the unit test's REQUIRES_NEW guard and the live smoke, and cannot be exercised here because
 * a new transaction would not see the instance this test starts in its own uncommitted one.
 *
 * @author peter haller
 */
@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
class OutboundBridgeProcessTest {

    private static final String KEY = "outboundBridgeProbe";
    private static final String WOKEN_TASK_NAME = "Woken task";
    private static final Long AWAITED_RESOURCE_ID = 4242L;

    @Autowired
    private ProcessInstanceService instanceService;
    @Autowired
    private ProcessDefinitionService definitionService;
    @Autowired
    private EngineEventBridge bridge;
    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private DocumentInfoRepository documentInfoRepository;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private UserService userService;

    private PUser admin;
    private Project project;
    private ProcessDefinitionDTO definition;

    @BeforeEach
    void setUp() {
        admin = userService.findUserByUsername("admin");
        project = projectService.createProject(
                new ProjectData("Outbound bridge probe", "waits for a scan, then creates a task", 1,
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 10),
                admin);
        definition = activatedDefinition();
    }

    /** A process that waits on the {@code nfcScan} message, then creates a task through the facade. */
    private static byte[] bpmn() {
        return ("""
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             targetNamespace="http://prioritize.test">
                  <message id="nfcScanMsg" name="nfcScan"/>
                  <process id="%s" name="Outbound bridge probe" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="toAwait" sourceRef="start" targetRef="await"/>
                    <intermediateCatchEvent id="await">
                      <messageEventDefinition messageRef="nfcScanMsg"/>
                    </intermediateCatchEvent>
                    <sequenceFlow id="toCreate" sourceRef="await" targetRef="create"/>
                    <serviceTask id="create" name="Create a task after the scan"
                                 flowable:expression="${platformGateway.createTask(projectId, '%s', 'created after an NFC scan', 2)}"/>
                    <sequenceFlow id="toEnd" sourceRef="create" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """.formatted(KEY, WOKEN_TASK_NAME)).getBytes(StandardCharsets.UTF_8);
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
    @DisplayName("a scan of the awaited resource wakes the waiting instance, which then creates a task")
    void scanWakesWaitingInstanceAndItWorks() {
        // Start the process; it declares which resource it waits for via the awaitedResourceId variable.
        ProcessInstanceDTO started = instanceService.startForProject(project.getId(), definition.id(),
                Map.of(EngineEventBridge.VAR_AWAITED_RESOURCE_ID, AWAITED_RESOURCE_ID), admin);
        assertTrue(started.running(), "the process is waiting at the message catch event");

        List<Task> before = taskRepository.findByBlackboard_Id(project.getBlackboard().getId());
        assertFalse(before.stream().anyMatch(t -> WOKEN_TASK_NAME.equals(t.getName())),
                "precondition: the woken task does not exist while the process still waits");

        // The real-world signal arrives: a scan of the awaited resource. The bridge's correlation finds
        // exactly the one waiting execution among the engine's subscriptions.
        List<String> targets = bridge.matchingExecutionIds(EngineEventBridge.MSG_NFC_SCAN, AWAITED_RESOURCE_ID);
        assertEquals(List.of(started.id()), execProcessInstanceIds(targets),
                "correlation resolves to exactly the waiting instance");

        // Deliver the message (in this test's transaction, so the continuation is visible to the asserts).
        for (String executionId : targets) {
            runtimeService.messageEventReceived(EngineEventBridge.MSG_NFC_SCAN, executionId,
                    Map.of(EngineEventBridge.VAR_NFC_SCAN_RESOURCE_ID, AWAITED_RESOURCE_ID));
        }

        assertFalse(instanceService.get(started.id(), admin).running(),
                "the woken process ran on to its end");
        List<Task> after = taskRepository.findByBlackboard_Id(project.getBlackboard().getId());
        assertEquals(1, after.stream().filter(t -> WOKEN_TASK_NAME.equals(t.getName())).count(),
                "the woken service task created exactly one task on the project through the facade");
    }

    @Test
    @DisplayName("a scan of a different resource correlates to no instance")
    void scanOfOtherResourceDoesNotWake() {
        ProcessInstanceDTO started = instanceService.startForProject(project.getId(), definition.id(),
                Map.of(EngineEventBridge.VAR_AWAITED_RESOURCE_ID, AWAITED_RESOURCE_ID), admin);
        assertTrue(started.running());

        List<String> targets = bridge.matchingExecutionIds(EngineEventBridge.MSG_NFC_SCAN, 9999L);
        assertTrue(targets.isEmpty(),
                "an unrelated scan must not correlate to a process waiting for another resource");

        assertTrue(instanceService.get(started.id(), admin).running(), "the instance is still waiting");
        List<Task> after = taskRepository.findByBlackboard_Id(project.getBlackboard().getId());
        assertFalse(after.stream().anyMatch(t -> WOKEN_TASK_NAME.equals(t.getName())),
                "no task is created while the process is still waiting");
    }

    /** The process-instance id each matched execution belongs to (an execution is not its instance). */
    private List<String> execProcessInstanceIds(List<String> executionIds) {
        return executionIds.stream()
                .map(id -> runtimeService.createExecutionQuery().executionId(id).singleResult().getProcessInstanceId())
                .toList();
    }
}
