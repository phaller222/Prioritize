package de.hallerweb.enterprise.prioritize.controller.document;

import de.hallerweb.enterprise.prioritize.dto.document.DocumentHistoryDTO;
import de.hallerweb.enterprise.prioritize.dto.document.DocumentSummaryDTO;
import de.hallerweb.enterprise.prioritize.model.document.Document;
import de.hallerweb.enterprise.prioritize.model.document.DocumentInfo;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.config.CurrentUserResolver;
import de.hallerweb.enterprise.prioritize.service.document.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
    private final CurrentUserResolver currentUserResolver;

    private PUser getCurrentUser(Authentication auth) {
        return currentUserResolver.resolve(auth);
    }

    /**
     * Upload of a new document into a DocumentGroup.
     * POST /api/v1/documents/upload/{groupId}
     */
    @PostMapping(value = "/upload/{groupId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentInfo> uploadDocument(
            @PathVariable Long groupId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            Authentication auth) throws IOException {

        log.info("Upload request received: Name={}, Group={}, Size={}", name, groupId, file.getSize());
        PUser currentUser = getCurrentUser(auth);
        DocumentInfo info = documentService.createDocument(
                name, groupId, currentUser, file.getBytes(), file.getContentType());
        log.info("Document successfully created by user '{}'.", currentUser.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(info);
    }

    /**
     * Download of the current version of a document.
     * GET /api/v1/documents/download/{documentInfoId}
     */
    @GetMapping("/download/{documentInfoId}")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long documentInfoId, Authentication auth) {
        PUser currentUser = getCurrentUser(auth);
        Document doc = documentService.getDocument(documentInfoId, currentUser).getCurrentDocument();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getName() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .body(doc.getData());
    }

    /**
     * Download of a specific version of a document.
     * GET /api/v1/documents/{id}/version/{versionNumber}
     */
    @GetMapping("/{id}/version/{versionNumber}")
    public ResponseEntity<byte[]> downloadSpecificVersion(
            @PathVariable Long id,
            @PathVariable Long versionNumber,
            Authentication auth) {
        PUser currentUser = getCurrentUser(auth);
        Document doc = documentService.getSpecificVersion(id, versionNumber, currentUser);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getName() + "\"")
                .body(doc.getData());
    }

    /**
     * All documents of a DocumentGroup as a DTO list.
     * GET /api/v1/documents/group/{groupId}
     */
    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<DocumentSummaryDTO>> getDocumentsInGroup(@PathVariable Long groupId, Authentication auth) {
        PUser currentUser = getCurrentUser(auth);
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
    public ResponseEntity<List<DocumentHistoryDTO>> getHistory(@PathVariable Long id, Authentication auth) {
        PUser currentUser = getCurrentUser(auth);
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
     * Delete document.
     * DELETE /api/v1/documents/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id, Authentication auth) {
        log.info("Delete request for document: {}", id);
        PUser currentUser = getCurrentUser(auth);
        documentService.deleteDocument(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    /**
     * Dokument auschecken (sperren).
     * POST /api/v1/documents/{id}/check-out
     */
    @PostMapping("/{id}/check-out")
    public ResponseEntity<Void> checkOut(@PathVariable Long id, Authentication auth) {
        log.info("Checking out document: {}", id);
        documentService.checkOut(id, getCurrentUser(auth));
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
            @RequestParam(value = "comment", required = false) String comment,
            Authentication auth) throws IOException {

        PUser currentUser = getCurrentUser(auth);
        Document newVersion = documentService.checkIn(
                id, file.getBytes(), file.getContentType(), comment, currentUser);
        return ResponseEntity.ok(newVersion.getDocumentInfo());
    }

    /**
     * Cancel checkout (unlock without a new version).
     * POST /api/v1/documents/{id}/cancel-check-out
     */
    @PostMapping("/{id}/cancel-check-out")
    public ResponseEntity<Void> cancelCheckOut(@PathVariable Long id, Authentication auth) {
        documentService.cancelCheckOut(id, getCurrentUser(auth));
        return ResponseEntity.noContent().build();
    }

    /**
     * Search documents by name.
     * GET /api/v1/documents/search?name=...
     */
    @GetMapping("/search")
    public ResponseEntity<List<DocumentSummaryDTO>> search(@RequestParam String name, Authentication auth) {
        return ResponseEntity.ok(documentService.searchDocumentsByName(name, getCurrentUser(auth)));
    }

    /**
     * The 10 most recently modified documents.
     * GET /api/v1/documents/recent
     */
    @GetMapping("/recent")
    public ResponseEntity<List<DocumentSummaryDTO>> getRecent(Authentication auth) {
        return ResponseEntity.ok(documentService.getRecentDocuments(getCurrentUser(auth)));
    }
}