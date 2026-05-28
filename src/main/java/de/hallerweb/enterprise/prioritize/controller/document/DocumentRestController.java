package de.hallerweb.enterprise.prioritize.controller.document;

import de.hallerweb.enterprise.prioritize.dto.document.DocumentHistoryDTO;
import de.hallerweb.enterprise.prioritize.dto.document.DocumentSummaryDTO;
import de.hallerweb.enterprise.prioritize.model.document.Document;
import de.hallerweb.enterprise.prioritize.model.document.DocumentInfo;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.document.DocumentService;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Log4j2
public class DocumentRestController {

    private final DocumentService documentService;
    private final UserService userService;

    /**
     * Upload eines neuen Dokuments in eine DocumentGroup.
     * POST /api/v1/documents/upload/{groupId}
     */
    @PostMapping(value = "/upload/{groupId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentInfo> uploadDocument(
        @PathVariable Long groupId,
        @RequestParam("file") MultipartFile file,
        @RequestParam("name") String name) throws IOException {

        log.info("Upload request received: Name={}, Group={}, Size={}", name, groupId, file.getSize());
        PUser currentUser = userService.getCurrentUser();
        DocumentInfo info = documentService.createDocument(
            name, groupId, currentUser, file.getBytes(), file.getContentType());
        log.info("Document successfully created by user '{}'.", currentUser.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(info);
    }

    /**
     * Download der aktuellen Version eines Dokuments.
     * GET /api/v1/documents/download/{documentInfoId}
     */
    @GetMapping("/download/{documentInfoId}")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long documentInfoId) {
        PUser currentUser = userService.getCurrentUser();
        Document doc = documentService.getDocument(documentInfoId, currentUser).getCurrentDocument();
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(doc.getMimeType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getName() + "\"")
            .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
            .body(doc.getData());
    }

    /**
     * Download einer spezifischen Version eines Dokuments.
     * GET /api/v1/documents/{id}/version/{versionNumber}
     */
    @GetMapping("/{id}/version/{versionNumber}")
    public ResponseEntity<byte[]> downloadSpecificVersion(
        @PathVariable Long id,
        @PathVariable Long versionNumber) {
        PUser currentUser = userService.getCurrentUser();
        Document doc = documentService.getSpecificVersion(id, versionNumber, currentUser);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(doc.getMimeType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getName() + "\"")
            .body(doc.getData());
    }

    /**
     * Alle Dokumente einer DocumentGroup als DTO-Liste.
     * GET /api/v1/documents/group/{groupId}
     */
    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<DocumentSummaryDTO>> getDocumentsInGroup(@PathVariable Long groupId) {
        PUser currentUser = userService.getCurrentUser();
        List<DocumentInfo> documents = documentService.getDocumentsInGroup(groupId, currentUser);
        List<DocumentSummaryDTO> summary = documents.stream()
            .map(doc -> new DocumentSummaryDTO(
                doc.getId(),
                doc.getCurrentDocument().getName(),
                doc.getCurrentDocument().getVersion(),
                doc.isLocked(),
                doc.getLockedBy() != null ? doc.getLockedBy().getUsername() : null
            ))
            .toList();
        return ResponseEntity.ok(summary);
    }

    /**
     * Versionshistorie eines Dokuments.
     * GET /api/v1/documents/{id}/history
     */
    @GetMapping("/{id}/history")
    public ResponseEntity<List<DocumentHistoryDTO>> getHistory(@PathVariable Long id) {
        PUser currentUser = userService.getCurrentUser();
        List<Document> history = documentService.getDocumentHistory(id, currentUser);
        List<DocumentHistoryDTO> dtos = history.stream()
            .map(d -> new DocumentHistoryDTO(
                d.getVersion(),
                d.getName(),
                d.getLastModifiedBy().getUsername(),
                d.getChanges(),
                d.getLastModified()
            ))
            .toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Dokument löschen.
     * DELETE /api/v1/documents/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        log.info("Delete request for document: {}", id);
        PUser currentUser = userService.getCurrentUser();
        documentService.deleteDocument(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    /**
     * Dokument auschecken (sperren).
     * POST /api/v1/documents/{id}/check-out
     */
    @PostMapping("/{id}/check-out")
    public ResponseEntity<Void> checkOut(@PathVariable Long id) {
        log.info("Checking out document: {}", id);
        documentService.checkOut(id, userService.getCurrentUser());
        return ResponseEntity.noContent().build();
    }

    /**
     * Dokument einchecken (neue Version hochladen + entsperren).
     * POST /api/v1/documents/{id}/check-in
     */
    @PostMapping("/{id}/check-in")
    public ResponseEntity<DocumentInfo> checkIn(
        @PathVariable Long id,
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "comment", required = false) String comment) throws IOException {

        PUser currentUser = userService.getCurrentUser();
        Document newVersion = documentService.checkIn(
            id, file.getBytes(), file.getContentType(), comment, currentUser);
        return ResponseEntity.ok(newVersion.getDocumentInfo());
    }

    /**
     * Checkout abbrechen (entsperren ohne neue Version).
     * POST /api/v1/documents/{id}/cancel-check-out
     */
    @PostMapping("/{id}/cancel-check-out")
    public ResponseEntity<Void> cancelCheckOut(@PathVariable Long id) {
        documentService.cancelCheckOut(id, userService.getCurrentUser());
        return ResponseEntity.noContent().build();
    }

    /**
     * Dokumente nach Name suchen.
     * GET /api/v1/documents/search?name=...
     */
    @GetMapping("/search")
    public ResponseEntity<List<DocumentSummaryDTO>> search(@RequestParam String name) {
        return ResponseEntity.ok(documentService.searchDocumentsByName(name, userService.getCurrentUser()));
    }

    /**
     * Die 10 zuletzt geänderten Dokumente.
     * GET /api/v1/documents/recent
     */
    @GetMapping("/recent")
    public ResponseEntity<List<DocumentSummaryDTO>> getRecent() {
        return ResponseEntity.ok(documentService.getRecentDocuments(userService.getCurrentUser()));
    }
}