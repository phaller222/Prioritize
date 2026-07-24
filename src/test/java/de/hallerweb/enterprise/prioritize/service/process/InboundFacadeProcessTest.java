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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * The inbound facade's acceptance test against the <b>real Flowable engine</b>: a BPMN service task,
 * driving nothing but a {@code ${platformGateway...}} expression, actually creates platform work. This
 * is the "it does something" proof for slice 4 — the point from which a process is no longer only
 * started and observed, but reaches back into the platform under the system identity.
 * <p>
 * The fixture has no wait state, so the service task fires synchronously inside the start call.
 *
 * @author peter haller
 */
@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
class InboundFacadeProcessTest {

    private static final String KEY = "inboundFacadeProbe";
    private static final String CREATED_TASK_NAME = "Auto task";

    @Autowired
    private ProcessInstanceService instanceService;
    @Autowired
    private ProcessDefinitionService definitionService;
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
                new ProjectData("Inbound facade probe", "runs a process that creates a task", 1,
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 10),
                admin);
        definition = activatedDefinition();
    }

    /** A process whose single service task calls the facade by expression, then ends. */
    private static byte[] bpmn() {
        return ("""
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://prioritize.test">
                  <process id="%s" name="Inbound facade probe" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="toCreate" sourceRef="start" targetRef="create"/>
                    <serviceTask id="create" name="Create a task via the facade"
                                 flowable:expression="${platformGateway.createTask(projectId, '%s', 'created by a BPMN process', 2)}"/>
                    <sequenceFlow id="toEnd" sourceRef="create" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """.formatted(KEY, CREATED_TASK_NAME)).getBytes(StandardCharsets.UTF_8);
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
    @DisplayName("a service task creates a real task on the project through the facade")
    void serviceTaskCreatesTaskThroughFacade() {
        List<Task> before = taskRepository.findByBlackboard_Id(project.getBlackboard().getId());
        assertFalse(before.stream().anyMatch(t -> CREATED_TASK_NAME.equals(t.getName())),
                "precondition: the auto task does not exist yet");

        // Starting sets the projectId variable the expression reads; the service task fires synchronously.
        ProcessInstanceDTO started =
                instanceService.startForProject(project.getId(), definition.id(), null, admin);

        // No wait state, so the instance has already run to its end.
        assertFalse(started.running(), "the process ran to completion inside the start call");

        List<Task> after = taskRepository.findByBlackboard_Id(project.getBlackboard().getId());
        assertEquals(1, after.stream().filter(t -> CREATED_TASK_NAME.equals(t.getName())).count(),
                "the BPMN service task created exactly one task on the project through the facade");
    }
}
