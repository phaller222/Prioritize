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

package de.hallerweb.enterprise.prioritize.controller.document;

import de.hallerweb.enterprise.prioritize.config.AuthenticatedUser;
import de.hallerweb.enterprise.prioritize.dto.WireTime;
import de.hallerweb.enterprise.prioritize.dto.document.DocumentHistoryDTO;
import de.hallerweb.enterprise.prioritize.dto.document.DocumentSummaryDTO;
import de.hallerweb.enterprise.prioritize.model.document.Document;
import de.hallerweb.enterprise.prioritize.model.document.DocumentInfo;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.document.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

@Tag(name = "Documents", description = "Upload, version, check out/in, download, search and delete documents.")
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Log4j2
public class DocumentRestController {

    private final DocumentService documentService;

    /**
     * Upload of a new document into a DocumentGroup.
     * POST /api/v1/documents/upload/{groupId}
     */
    @Operation(summary = "Upload of a new document into a DocumentGroup")
    @PostMapping(value = "/upload/{groupId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentSummaryDTO> uploadDocument(
            @PathVariable Long groupId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @AuthenticatedUser PUser currentUser) throws IOException {

        log.info("Upload request received: Name={}, Group={}, Size={}", name, groupId, file.getSize());
        DocumentInfo info = documentService.createDocument(
                name, groupId, currentUser, file.getBytes(), file.getContentType());
        log.info("Document successfully created by user '{}'.", currentUser.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(DocumentSummaryDTO.from(info));
    }

    /**
     * Download of the current version of a document.
     * GET /api/v1/documents/download/{documentInfoId}
     */
    @Operation(summary = "Download of the current version of a document")
    @GetMapping("/download/{documentInfoId}")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long documentInfoId, @AuthenticatedUser PUser currentUser) {
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
    @Operation(summary = "Download of a specific version of a document")
    @GetMapping("/{id}/version/{versionNumber}")
    public ResponseEntity<byte[]> downloadSpecificVersion(
            @PathVariable Long id,
            @PathVariable Long versionNumber,
            @AuthenticatedUser PUser currentUser) {
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
    @Operation(summary = "All documents of a DocumentGroup as a DTO list")
    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<DocumentSummaryDTO>> getDocumentsInGroup(@PathVariable Long groupId, @AuthenticatedUser PUser currentUser) {
        List<DocumentInfo> documents = documentService.getDocumentsInGroup(groupId, currentUser);
        List<DocumentSummaryDTO> summary = documents.stream()
                .map(DocumentSummaryDTO::from)
                .toList();
        return ResponseEntity.ok(summary);
    }

    /**
     * Versionshistorie eines Dokuments.
     * GET /api/v1/documents/{id}/history
     */
    @Operation(summary = "Version history of a document")
    @GetMapping("/{id}/history")
    public ResponseEntity<List<DocumentHistoryDTO>> getHistory(@PathVariable Long id, @AuthenticatedUser PUser currentUser) {
        List<Document> history = documentService.getDocumentHistory(id, currentUser);
        List<DocumentHistoryDTO> dtos = history.stream()
                .map(d -> new DocumentHistoryDTO(
                        d.getVersion(),
                        d.getName(),
                        d.getLastModifiedBy().getUsername(),
                        d.getChanges(),
                        WireTime.toInstant(d.getLastModified())
                ))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Delete document.
     * DELETE /api/v1/documents/{id}
     */
    @Operation(summary = "Delete document")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id, @AuthenticatedUser PUser currentUser) {
        log.info("Delete request for document: {}", id);
        documentService.deleteDocument(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    /**
     * Dokument auschecken (sperren).
     * POST /api/v1/documents/{id}/check-out
     */
    @Operation(summary = "Check out a document (lock it)")
    @PostMapping("/{id}/check-out")
    public ResponseEntity<Void> checkOut(@PathVariable Long id, @AuthenticatedUser PUser currentUser) {
        log.info("Checking out document: {}", id);
        documentService.checkOut(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    /**
     * Dokument einchecken (neue Version hochladen + entsperren).
     * POST /api/v1/documents/{id}/check-in
     */
    @Operation(summary = "Check in a document (upload a new version and unlock)")
    @PostMapping("/{id}/check-in")
    public ResponseEntity<DocumentSummaryDTO> checkIn(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "comment", required = false) String comment,
            @AuthenticatedUser PUser currentUser) throws IOException {

        Document newVersion = documentService.checkIn(
                id, file.getBytes(), file.getContentType(), comment, currentUser);
        return ResponseEntity.ok(DocumentSummaryDTO.from(newVersion.getDocumentInfo()));
    }

    /**
     * Cancel checkout (unlock without a new version).
     * POST /api/v1/documents/{id}/cancel-check-out
     */
    @Operation(summary = "Cancel checkout (unlock without a new version)")
    @PostMapping("/{id}/cancel-check-out")
    public ResponseEntity<Void> cancelCheckOut(@PathVariable Long id, @AuthenticatedUser PUser currentUser) {
        documentService.cancelCheckOut(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    /**
     * Search documents by name.
     * GET /api/v1/documents/search?name=...
     */
    @Operation(summary = "Search documents by name")
    @GetMapping("/search")
    public ResponseEntity<List<DocumentSummaryDTO>> search(@RequestParam String name, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(documentService.searchDocumentsByName(name, currentUser));
    }

    /**
     * The 10 most recently modified documents.
     * GET /api/v1/documents/recent
     */
    @Operation(summary = "The 10 most recently modified documents")
    @GetMapping("/recent")
    public ResponseEntity<List<DocumentSummaryDTO>> getRecent(@AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(documentService.getRecentDocuments(currentUser));
    }
}