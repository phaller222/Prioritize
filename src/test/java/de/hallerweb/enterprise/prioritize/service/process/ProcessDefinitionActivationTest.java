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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.hallerweb.enterprise.prioritize.dto.process.ProcessDefinitionDTO;
import de.hallerweb.enterprise.prioritize.model.document.Document;
import de.hallerweb.enterprise.prioritize.model.document.DocumentInfo;
import de.hallerweb.enterprise.prioritize.model.process.ProcessDefinition;
import de.hallerweb.enterprise.prioritize.model.process.ProcessDefinitionState;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.document.DocumentInfoRepository;
import de.hallerweb.enterprise.prioritize.repository.process.ProcessDefinitionRepository;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import java.nio.charset.StandardCharsets;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies activation against the <b>real Flowable engine</b> — that is the point of these tests: that
 * a document version actually becomes a startable process definition, that suspending stops new
 * instances without touching running ones, and that resuming does not pile up engine versions.
 * <p>
 * The fixture deliberately uses its own process key, not the {@code MyProcess} of the auto-deployed
 * test resource, so the two never interfere.
 *
 * @author peter haller
 */
@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
class ProcessDefinitionActivationTest {

    private static final String KEY = "activationProbe";

    @Autowired
    private ProcessDefinitionService service;
    @Autowired
    private ProcessDefinitionRepository definitionRepository;
    @Autowired
    private DocumentInfoRepository documentInfoRepository;
    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private UserService userService;

    private PUser admin;

    @BeforeEach
    void setUp() {
        admin = userService.findUserByUsername("admin");
    }

    /**
     * A process that waits in a receive task, so a started instance stays alive long enough to prove
     * that suspending its definition does not touch it.
     */
    private static byte[] bpmn(String key, String name) {
        return ("""
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             targetNamespace="http://prioritize.test">
                  <process id="%s" name="%s" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="toWait" sourceRef="start" targetRef="wait"/>
                    <receiveTask id="wait" name="Waiting"/>
                    <sequenceFlow id="toEnd" sourceRef="wait" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """.formatted(key, name)).getBytes(StandardCharsets.UTF_8);
    }

    /** Persists a document carrying BPMN and registers it, bypassing the upload path. */
    private ProcessDefinitionDTO registeredDefinition(byte[] content) {
        Document document = Document.builder()
                .name(KEY + ".bpmn").version(1).mimeType("text/xml").data(content).build();
        DocumentInfo info = DocumentInfo.builder().currentDocument(document).build();
        document.setDocumentInfo(info);
        info = documentInfoRepository.save(info);

        return service.register(info.getId(), admin);
    }

    private boolean isDeployedAndStartable() {
        var definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(KEY).latestVersion().singleResult();
        return definition != null && !definition.isSuspended();
    }

    @Test
    @DisplayName("activate: deploys the document version and makes the process startable")
    void activateDeploysAndStarts() {
        ProcessDefinitionDTO definition = registeredDefinition(bpmn(KEY, "Probe"));
        assertEquals(ProcessDefinitionState.DRAFT, definition.state());
        assertNull(definition.deploymentId(), "registering must not deploy anything");

        ProcessDefinitionDTO activated = service.activate(definition.id(), admin);

        assertEquals(ProcessDefinitionState.ACTIVE, activated.state());
        assertNotNull(activated.deploymentId());
        assertEquals(1, activated.deployedVersion());
        assertNotNull(activated.deployedAt());
        assertEquals(admin.getUsername(), activated.deployedBy());
        assertTrue(isDeployedAndStartable());

        // The real proof: the engine can start it.
        assertNotNull(runtimeService.startProcessInstanceByKey(KEY).getId());
    }

    @Test
    @DisplayName("activate: is idempotent while the document has not changed")
    void activateIsIdempotent() {
        ProcessDefinitionDTO definition = registeredDefinition(bpmn(KEY, "Probe"));
        String firstDeployment = service.activate(definition.id(), admin).deploymentId();

        String secondDeployment = service.activate(definition.id(), admin).deploymentId();

        assertEquals(firstDeployment, secondDeployment, "re-activating an unchanged definition must not redeploy");
        assertEquals(1, repositoryService.createProcessDefinitionQuery().processDefinitionKey(KEY).count());
    }

    @Test
    @DisplayName("deactivate: stops new instances, leaves running ones alone")
    void deactivateSuspendsWithoutKillingInstances() {
        ProcessDefinitionDTO definition = registeredDefinition(bpmn(KEY, "Probe"));
        service.activate(definition.id(), admin);
        String runningInstance = runtimeService.startProcessInstanceByKey(KEY).getId();

        ProcessDefinitionDTO suspended = service.deactivate(definition.id(), admin);

        assertEquals(ProcessDefinitionState.SUSPENDED, suspended.state());
        assertNotNull(suspended.deploymentId(), "the deployment must survive deactivation");
        assertFalse(isDeployedAndStartable());
        // Narrow on purpose: a broad "throws anything" would also pass if starting failed for an
        // unrelated reason, and then this test would prove nothing about suspension.
        FlowableException refused = assertThrows(FlowableException.class,
                () -> runtimeService.startProcessInstanceByKey(KEY),
                "a suspended definition must not start new instances");
        assertTrue(refused.getMessage().toLowerCase().contains("suspend"), refused.getMessage());

        // The actual promise of deactivation: work already under way is not thrown away.
        assertEquals(1, runtimeService.createProcessInstanceQuery().processInstanceId(runningInstance).count(),
                "the instance started before deactivation must still be there");
        assertFalse(runtimeService.createProcessInstanceQuery()
                        .processInstanceId(runningInstance).singleResult().isSuspended(),
                "a running instance must not be suspended along with its definition");
    }

    @Test
    @DisplayName("activate: resumes a suspended definition instead of deploying it again")
    void activateResumesWithoutRedeploying() {
        ProcessDefinitionDTO definition = registeredDefinition(bpmn(KEY, "Probe"));
        String deploymentId = service.activate(definition.id(), admin).deploymentId();
        service.deactivate(definition.id(), admin);

        ProcessDefinitionDTO resumed = service.activate(definition.id(), admin);

        assertEquals(ProcessDefinitionState.ACTIVE, resumed.state());
        assertEquals(deploymentId, resumed.deploymentId(), "resuming must not create a second deployment");
        assertEquals(1, repositoryService.createProcessDefinitionQuery().processDefinitionKey(KEY).count());
        assertTrue(isDeployedAndStartable());
    }

    @Test
    @DisplayName("activate: deploys a new engine version once the document has a newer one")
    void activateDeploysNewDocumentVersion() {
        ProcessDefinitionDTO definition = registeredDefinition(bpmn(KEY, "Probe"));
        String firstDeployment = service.activate(definition.id(), admin).deploymentId();

        // A new document revision, as the documents subsystem would produce it.
        DocumentInfo info = documentInfoRepository.findById(definition.documentInfoId()).orElseThrow();
        info.getCurrentDocument().setVersion(2);
        info.getCurrentDocument().setData(bpmn(KEY, "Probe, revised"));
        documentInfoRepository.save(info);

        ProcessDefinitionDTO reactivated = service.activate(definition.id(), admin);

        assertEquals(2, reactivated.deployedVersion());
        org.junit.jupiter.api.Assertions.assertNotEquals(firstDeployment, reactivated.deploymentId(),
                "a newer document version must redeploy");
        assertEquals(2, repositoryService.createProcessDefinitionQuery().processDefinitionKey(KEY).count(),
                "the engine keeps both versions — running instances stay on the one they started with");
    }

    @Test
    @DisplayName("activate: refuses a document whose process key has changed since registration")
    void activateRefusesChangedKey() {
        ProcessDefinitionDTO definition = registeredDefinition(bpmn(KEY, "Probe"));

        DocumentInfo info = documentInfoRepository.findById(definition.documentInfoId()).orElseThrow();
        info.getCurrentDocument().setVersion(2);
        info.getCurrentDocument().setData(bpmn("somethingElse", "Different process"));
        documentInfoRepository.save(info);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.activate(definition.id(), admin));

        assertTrue(ex.getMessage().contains("somethingElse"), ex.getMessage());
        assertEquals(ProcessDefinitionState.DRAFT, definitionRepository.findById(definition.id())
                .orElseThrow().getState(), "a refused activation must leave the definition a draft");
    }

    @Test
    @DisplayName("deactivate: refuses a definition that is not active")
    void deactivateRefusesDraft() {
        ProcessDefinitionDTO definition = registeredDefinition(bpmn(KEY, "Probe"));

        assertThrows(IllegalStateException.class, () -> service.deactivate(definition.id(), admin));
    }
}
