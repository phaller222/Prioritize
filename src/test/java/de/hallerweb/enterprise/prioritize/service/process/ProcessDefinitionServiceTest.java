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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.hallerweb.enterprise.prioritize.dto.process.ProcessDefinitionDTO;
import de.hallerweb.enterprise.prioritize.model.document.Document;
import de.hallerweb.enterprise.prioritize.model.document.DocumentInfo;
import de.hallerweb.enterprise.prioritize.model.process.ProcessDefinition;
import de.hallerweb.enterprise.prioritize.model.process.ProcessDefinitionState;
import de.hallerweb.enterprise.prioritize.model.security.Action;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.process.ProcessDefinitionRepository;
import de.hallerweb.enterprise.prioritize.service.document.DocumentService;
import de.hallerweb.enterprise.prioritize.service.process.BpmnDefinitionReader.BpmnDefinitionInfo;
import de.hallerweb.enterprise.prioritize.service.security.AuthorizationService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

/**
 * Pure unit tests for {@link ProcessDefinitionService} — the type-level authorization, reading the key
 * out of the document, the collision rules and the draft-only unregister. No Spring context, no
 * database, no engine.
 *
 * @author peter haller
 */
class ProcessDefinitionServiceTest {

    private static final String TYPE = ProcessDefinition.class.getCanonicalName();
    private static final byte[] CONTENT = "<bpmn/>".getBytes(StandardCharsets.UTF_8);

    private ProcessDefinitionRepository definitionRepository;
    private DocumentService documentService;
    private BpmnDefinitionReader definitionReader;
    private AuthorizationService authService;
    private org.flowable.engine.RepositoryService repositoryService;
    private ProcessDefinitionQuery engineQuery;
    private ProcessDefinitionService service;

    private PUser user;

    @BeforeEach
    void setUp() {
        definitionRepository = mock(ProcessDefinitionRepository.class);
        documentService = mock(DocumentService.class);
        definitionReader = mock(BpmnDefinitionReader.class);
        authService = mock(AuthorizationService.class);
        repositoryService = mock(org.flowable.engine.RepositoryService.class);
        service = new ProcessDefinitionService(definitionRepository, documentService, definitionReader, authService,
                repositoryService);

        user = PUser.builder().username("peter").build();
        when(definitionRepository.save(any(ProcessDefinition.class))).thenAnswer(i -> i.getArgument(0));

        // The engine is queried on every registration to catch keys deployed outside the registry.
        engineQuery = mock(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(engineQuery);
        when(engineQuery.processDefinitionKey(anyString())).thenReturn(engineQuery);
        when(engineQuery.count()).thenReturn(0L);
    }

    private void allow(Action action) {
        when(authService.hasPermission(user, TYPE, 0L, action)).thenReturn(true);
    }

    private DocumentInfo documentWith(byte[] data, String name, int version) {
        Document current = Document.builder().name(name).version(version).data(data).build();
        DocumentInfo info = DocumentInfo.builder().currentDocument(current).build();
        when(documentService.getDocument(1L, user)).thenReturn(info);
        return info;
    }

    @Test
    @DisplayName("registers the document's current version as a draft definition")
    void registersAsDraft() {
        allow(Action.CREATE);
        DocumentInfo info = documentWith(CONTENT, "order-handling.bpmn", 3);
        when(definitionReader.read(CONTENT)).thenReturn(new BpmnDefinitionInfo("orderHandling", "Order handling"));
        when(definitionRepository.findByProcessKey("orderHandling")).thenReturn(Optional.empty());
        when(definitionRepository.findByDocumentInfo_Id(1L)).thenReturn(List.of());

        ProcessDefinitionDTO registered = service.register(1L, user);

        assertEquals("orderHandling", registered.processKey());
        assertEquals("Order handling", registered.name());
        assertEquals(ProcessDefinitionState.DRAFT, registered.state());
        // Nothing is deployed by registering.
        assertNull(registered.deploymentId());
        assertNull(registered.deployedVersion());

        ArgumentCaptor<ProcessDefinition> saved = ArgumentCaptor.forClass(ProcessDefinition.class);
        verify(definitionRepository).save(saved.capture());
        assertSame(info, saved.getValue().getDocumentInfo(), "the definition must point at the source document");
    }

    @Test
    @DisplayName("falls back to the document name when the diagram carries no process name")
    void fallsBackToDocumentName() {
        allow(Action.CREATE);
        documentWith(CONTENT, "order-handling.bpmn", 1);
        when(definitionReader.read(CONTENT)).thenReturn(new BpmnDefinitionInfo("orderHandling", null));
        when(definitionRepository.findByProcessKey("orderHandling")).thenReturn(Optional.empty());
        when(definitionRepository.findByDocumentInfo_Id(1L)).thenReturn(List.of());

        assertEquals("order-handling.bpmn", service.register(1L, user).name());
    }

    @Test
    @DisplayName("refuses registration without the type-level CREATE permission, before touching the document")
    void refusesRegistrationWithoutPermission() {
        assertThrows(AccessDeniedException.class, () -> service.register(1L, user));

        verify(documentService, never()).getDocument(anyLong(), any());
        verify(definitionRepository, never()).save(any());
    }

    @Test
    @DisplayName("refuses a document without content")
    void refusesEmptyDocument() {
        allow(Action.CREATE);
        documentWith(new byte[0], "empty.bpmn", 1);

        assertThrows(IllegalArgumentException.class, () -> service.register(1L, user));
        verify(definitionReader, never()).read(any());
    }

    @Test
    @DisplayName("refuses a process key that is already registered — order of deployment must not decide")
    void refusesDuplicateKey() {
        allow(Action.CREATE);
        documentWith(CONTENT, "copy.bpmn", 1);
        when(definitionReader.read(CONTENT)).thenReturn(new BpmnDefinitionInfo("orderHandling", null));
        ProcessDefinition existing = ProcessDefinition.builder().processKey("orderHandling").build();
        existing.setId(7L);
        when(definitionRepository.findByProcessKey("orderHandling")).thenReturn(Optional.of(existing));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.register(1L, user));

        assertTrue(ex.getMessage().contains("already registered"), ex.getMessage());
        verify(definitionRepository, never()).save(any());
    }

    @Test
    @DisplayName("refuses a key the engine already answers to — the classpath trusted root deploys too")
    void refusesKeyAlreadyDeployedInTheEngine() {
        allow(Action.CREATE);
        documentWith(CONTENT, "impostor.bpmn", 1);
        when(definitionReader.read(CONTENT)).thenReturn(new BpmnDefinitionInfo("orderHandling", null));
        when(definitionRepository.findByProcessKey("orderHandling")).thenReturn(Optional.empty());
        when(engineQuery.count()).thenReturn(1L); // deployed, but no registry entry claims it

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.register(1L, user));

        assertTrue(ex.getMessage().contains("outside the registry"), ex.getMessage());
        verify(definitionRepository, never()).save(any());
    }

    @Test
    @DisplayName("refuses to register the same document twice")
    void refusesAlreadyRegisteredDocument() {
        allow(Action.CREATE);
        documentWith(CONTENT, "order-handling.bpmn", 1);
        when(definitionReader.read(CONTENT)).thenReturn(new BpmnDefinitionInfo("orderHandling", null));
        when(definitionRepository.findByProcessKey("orderHandling")).thenReturn(Optional.empty());
        when(definitionRepository.findByDocumentInfo_Id(1L))
                .thenReturn(List.of(ProcessDefinition.builder().processKey("orderHandling").build()));

        assertThrows(IllegalStateException.class, () -> service.register(1L, user));
        verify(definitionRepository, never()).save(any());
    }

    @Test
    @DisplayName("reads require the type-level READ permission")
    void readsRequirePermission() {
        assertThrows(AccessDeniedException.class, () -> service.getAll(user));
        assertThrows(AccessDeniedException.class, () -> service.get(1L, user));

        allow(Action.READ);
        when(definitionRepository.findAll()).thenReturn(List.of(ProcessDefinition.builder().build()));
        assertEquals(1, service.getAll(user).size());

        when(definitionRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.get(1L, user));
    }

    @Test
    @DisplayName("unregisters a draft definition")
    void unregistersDraft() {
        allow(Action.DELETE);
        ProcessDefinition draft = ProcessDefinition.builder()
                .processKey("orderHandling").state(ProcessDefinitionState.DRAFT).build();
        when(definitionRepository.findById(5L)).thenReturn(Optional.of(draft));

        service.unregister(5L, user);

        ArgumentCaptor<ProcessDefinition> deleted = ArgumentCaptor.forClass(ProcessDefinition.class);
        verify(definitionRepository).delete(deleted.capture());
        assertSame(draft, deleted.getValue());
    }

    @Test
    @DisplayName("refuses to unregister a deployed definition — its deployment would become unattributable")
    void refusesUnregisteringDeployedDefinition() {
        allow(Action.DELETE);
        for (ProcessDefinitionState state : List.of(ProcessDefinitionState.ACTIVE, ProcessDefinitionState.SUSPENDED)) {
            ProcessDefinition deployed = ProcessDefinition.builder()
                    .processKey("orderHandling").state(state).deploymentId("dep-1").build();
            when(definitionRepository.findById(5L)).thenReturn(Optional.of(deployed));

            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.unregister(5L, user));
            assertTrue(ex.getMessage().contains("deactivate it instead"), ex.getMessage());
        }
        verify(definitionRepository, never()).delete(any());
    }

    @Test
    @DisplayName("each operation checks its own action, so read rights alone grant nothing else")
    void actionsAreCheckedSeparately() {
        allow(Action.READ);

        assertThrows(AccessDeniedException.class, () -> service.register(1L, user));
        assertThrows(AccessDeniedException.class, () -> service.unregister(1L, user));
        verify(authService).hasPermission(eq(user), eq(TYPE), eq(0L), eq(Action.CREATE));
        verify(authService).hasPermission(eq(user), eq(TYPE), eq(0L), eq(Action.DELETE));
    }
}
