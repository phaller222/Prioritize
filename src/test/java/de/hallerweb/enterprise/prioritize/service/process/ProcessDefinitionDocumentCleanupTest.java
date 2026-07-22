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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.hallerweb.enterprise.prioritize.dto.process.ProcessDefinitionDTO;
import de.hallerweb.enterprise.prioritize.model.document.Document;
import de.hallerweb.enterprise.prioritize.model.document.DocumentInfo;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.document.DocumentInfoRepository;
import de.hallerweb.enterprise.prioritize.repository.process.ProcessDefinitionRepository;
import de.hallerweb.enterprise.prioritize.service.document.DocumentService;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * What happens to a process definition when the document carrying its BPMN is deleted: a draft goes
 * with it, a deployed one vetoes the deletion. Runs against the real database and the real engine
 * because the interplay of the deletion event, the foreign key and the deployment is the subject.
 *
 * @author peter haller
 */
@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
class ProcessDefinitionDocumentCleanupTest {

    private static final String KEY = "cleanupProbe";

    @Autowired
    private ProcessDefinitionService definitionService;
    @Autowired
    private ProcessDefinitionRepository definitionRepository;
    @Autowired
    private DocumentService documentService;
    @Autowired
    private DocumentInfoRepository documentInfoRepository;
    @Autowired
    private UserService userService;

    private PUser admin;

    @BeforeEach
    void setUp() {
        admin = userService.findUserByUsername("admin");
    }

    private static byte[] bpmn() {
        return ("""
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             targetNamespace="http://prioritize.test">
                  <process id="%s" name="Cleanup probe" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="toEnd" sourceRef="start" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """.formatted(KEY)).getBytes(StandardCharsets.UTF_8);
    }

    private ProcessDefinitionDTO registeredDefinition() {
        Document document = Document.builder()
                .name(KEY + ".bpmn").version(1).mimeType("text/xml").data(bpmn()).build();
        DocumentInfo info = DocumentInfo.builder().currentDocument(document).build();
        document.setDocumentInfo(info);
        info = documentInfoRepository.save(info);
        return definitionService.register(info.getId(), admin);
    }

    @Test
    @DisplayName("deleting a document takes a draft definition with it")
    void deletingDocumentRemovesDraftDefinition() {
        ProcessDefinitionDTO draft = registeredDefinition();
        assertFalse(definitionRepository.findByDocumentInfo_Id(draft.documentInfoId()).isEmpty(),
                "precondition: the document carries a definition");

        assertDoesNotThrow(() -> documentService.deleteDocument(draft.documentInfoId(), admin));

        assertTrue(definitionRepository.findById(draft.id()).isEmpty(),
                "the draft definition must be gone with its document");
    }

    @Test
    @DisplayName("deleting the document of a deployed definition is refused")
    void deletingDocumentOfDeployedDefinitionIsRefused() {
        ProcessDefinitionDTO definition = registeredDefinition();
        definitionService.activate(definition.id(), admin);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> documentService.deleteDocument(definition.documentInfoId(), admin));

        assertTrue(ex.getMessage().contains("deactivate the definition instead"), ex.getMessage());
        assertTrue(documentInfoRepository.findById(definition.documentInfoId()).isPresent(),
                "the document must survive the refused deletion");
        assertTrue(definitionRepository.findById(definition.id()).isPresent(),
                "and so must its definition");
    }

    @Test
    @DisplayName("a suspended definition still protects its document")
    void deletingDocumentOfSuspendedDefinitionIsRefused() {
        ProcessDefinitionDTO definition = registeredDefinition();
        definitionService.activate(definition.id(), admin);
        definitionService.deactivate(definition.id(), admin);

        assertThrows(IllegalStateException.class,
                () -> documentService.deleteDocument(definition.documentInfoId(), admin));
    }

    @Test
    @DisplayName("a key already deployed in the engine cannot be claimed by a document")
    void refusesKeyAlreadyDeployedOutsideTheRegistry() {
        // MyProcess comes from the auto-deployed test resource, i.e. exactly the classpath trusted root
        // this check is about: it is deployed, but no registry entry claims it.
        Document document = Document.builder()
                .name("stolen-key.bpmn").version(1).mimeType("text/xml")
                .data(("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                     targetNamespace="http://prioritize.test">
                          <process id="MyProcess" name="Impostor" isExecutable="true">
                            <startEvent id="start"/>
                          </process>
                        </definitions>
                        """).getBytes(StandardCharsets.UTF_8))
                .build();
        DocumentInfo info = DocumentInfo.builder().currentDocument(document).build();
        document.setDocumentInfo(info);
        DocumentInfo saved = documentInfoRepository.save(info);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> definitionService.register(saved.getId(), admin));

        assertTrue(ex.getMessage().contains("outside the registry"), ex.getMessage());
        assertEquals(0, definitionRepository.findByDocumentInfo_Id(saved.getId()).size());
    }
}
