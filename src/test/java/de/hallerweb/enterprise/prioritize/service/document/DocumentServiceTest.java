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

package de.hallerweb.enterprise.prioritize.service.document;

import de.hallerweb.enterprise.prioritize.model.document.DocumentGroup;
import de.hallerweb.enterprise.prioritize.model.document.DocumentInfo;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.document.DocumentGroupRepository;
import de.hallerweb.enterprise.prioritize.repository.document.DocumentInfoRepository;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
class DocumentServiceTest {

    @Autowired private DocumentService documentService;
    @Autowired private DocumentGroupRepository groupRepository;
    @Autowired private DocumentInfoRepository documentInfoRepository;
    @Autowired private UserService userService;

    private PUser adminUser;
    private PUser regularUser;
    private DocumentGroup testGroup;
    private DocumentInfo testDoc;

    @BeforeEach
    void setUp() {
        // Fetch admin user from the DB (created by the InitializationService)
        adminUser = userService.findUserByUsername("admin");

        // Zweiten Testuser anlegen
        regularUser = PUser.builder()
            .username("doc-test-user-" + System.currentTimeMillis())
            .name("DocTester")
            .firstname("Test")
            .email("doc.tester@example.com")
            .password("test123")
            .admin(false)
            .build();
        regularUser = userService.createUser(regularUser);

        // Testgruppe anlegen
        testGroup = new DocumentGroup();
        testGroup.setName("Test-Dokumentengruppe");
        testGroup = groupRepository.save(testGroup);

        // Ein erstes Testdokument anlegen
        testDoc = documentService.createDocument(
            "testdokument",
            testGroup.getId(),
            adminUser,
            "Testinhalt".getBytes(),
            "text/plain"
        );
    }

    // ==========================================
    // createDocument
    // ==========================================

    @Test
    @DisplayName("createDocument: DocumentInfo wird mit Version 1 angelegt")
    void createDocument_ShouldCreateVersion1() {
        assertNotNull(testDoc.getId());
        assertNotNull(testDoc.getCurrentDocument());
        assertEquals(1, testDoc.getCurrentDocument().getVersion());
    }

    @Test
    @DisplayName("createDocument: Dateiendung wird aus MimeType abgeleitet")
    void createDocument_ShouldAppendExtension() {
        assertTrue(testDoc.getCurrentDocument().getName().endsWith(".txt"));
    }

    @Test
    @DisplayName("createDocument: Unbekannte Gruppe wirft NoSuchElementException")
    void createDocument_UnknownGroup_ShouldThrow() {
        assertThrows(NoSuchElementException.class,
            () -> documentService.createDocument(
                "test", -999L, adminUser, "data".getBytes(), "text/plain"));
    }

    // ==========================================
    // getDocument
    // ==========================================

    @Test
    @DisplayName("getDocument: Existierendes Dokument wird zurückgegeben")
    void getDocument_ShouldReturnDocumentInfo() {
        DocumentInfo found = documentService.getDocument(testDoc.getId(), adminUser);
        assertNotNull(found);
        assertEquals(testDoc.getId(), found.getId());
    }

    @Test
    @DisplayName("getDocument: Unbekannte ID wirft NoSuchElementException")
    void getDocument_UnknownId_ShouldThrow() {
        assertThrows(NoSuchElementException.class,
            () -> documentService.getDocument(-999L, adminUser));
    }

    // ==========================================
    // checkOut / checkIn
    // ==========================================

    @Test
    @DisplayName("checkOut: Dokument wird gesperrt")
    void checkOut_ShouldLockDocument() {
        documentService.checkOut(testDoc.getId(), adminUser);

        DocumentInfo locked = documentInfoRepository.findById(testDoc.getId()).orElseThrow();
        assertTrue(locked.isLocked());
        assertEquals(adminUser.getId(), locked.getLockedBy().getId());
    }

    @Test
    @DisplayName("checkOut: Bereits gesperrtes Dokument wirft IllegalStateException")
    void checkOut_AlreadyLocked_ShouldThrow() {
        documentService.checkOut(testDoc.getId(), adminUser);
        assertThrows(IllegalStateException.class,
            () -> documentService.checkOut(testDoc.getId(), adminUser));
    }

    @Test
    @DisplayName("checkIn: Neue Version wird erstellt und Lock wird aufgehoben")
    void checkIn_ShouldCreateNewVersionAndUnlock() {
        documentService.checkOut(testDoc.getId(), adminUser);

        documentService.checkIn(
            testDoc.getId(),
            "Neuer Inhalt".getBytes(),
            "text/plain",
            "Update v2",
            adminUser
        );

        DocumentInfo updated = documentInfoRepository.findById(testDoc.getId()).orElseThrow();
        assertFalse(updated.isLocked());
        assertEquals(2, updated.getCurrentDocument().getVersion());
        assertEquals("Update v2", updated.getCurrentDocument().getChanges());
    }

    @Test
    @DisplayName("checkIn: Ohne vorherigen checkOut wirft IllegalStateException")
    void checkIn_WithoutCheckOut_ShouldThrow() {
        assertThrows(IllegalStateException.class,
            () -> documentService.checkIn(
                testDoc.getId(), "data".getBytes(), "text/plain", null, adminUser));
    }

    // ==========================================
    // cancelCheckOut
    // ==========================================

    @Test
    @DisplayName("cancelCheckOut: Lock wird aufgehoben ohne neue Version")
    void cancelCheckOut_ShouldUnlockWithoutNewVersion() {
        documentService.checkOut(testDoc.getId(), adminUser);
        documentService.cancelCheckOut(testDoc.getId(), adminUser);

        DocumentInfo doc = documentInfoRepository.findById(testDoc.getId()).orElseThrow();
        assertFalse(doc.isLocked());
        assertEquals(1, doc.getCurrentDocument().getVersion()); // Version unchanged
    }

    // ==========================================
    // deleteDocument
    // ==========================================

    @Test
    @DisplayName("deleteDocument: Gesperrtes Dokument kann nicht von Nicht-Admin geloescht werden")
    void deleteDocument_Locked_NonAdmin_ShouldThrow() {
        documentService.checkOut(testDoc.getId(), adminUser);

        // regularUser has no DELETE permission -> AccessDeniedException
        assertThrows(AccessDeniedException.class,
            () -> documentService.deleteDocument(testDoc.getId(), regularUser));
    }

    @Test
    @DisplayName("deleteDocument: Admin darf gesperrtes Dokument loeschen")
    void deleteDocument_Locked_AdminCanDelete() {
        documentService.checkOut(testDoc.getId(), adminUser);

        // Admin may also delete locked documents
        assertDoesNotThrow(() -> documentService.deleteDocument(testDoc.getId(), adminUser));
        assertFalse(documentInfoRepository.existsById(testDoc.getId()));
    }
    // ==========================================
    // getDocumentHistory
    // ==========================================

    @Test
    @DisplayName("getDocumentHistory: Liefert alle Versionen, neueste zuerst")
    void getDocumentHistory_ShouldReturnVersionsNewestFirst() {
        // v2 anlegen
        documentService.checkOut(testDoc.getId(), adminUser);
        documentService.checkIn(testDoc.getId(), "v2".getBytes(), "text/plain", "v2", adminUser);

        List<?> history = documentService.getDocumentHistory(testDoc.getId(), adminUser);

        assertEquals(2, history.size());
    }
}