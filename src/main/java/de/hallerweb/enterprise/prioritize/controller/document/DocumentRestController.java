package de.hallerweb.enterprise.prioritize.controller.document;

import de.hallerweb.enterprise.prioritize.dto.document.DocumentHistoryDTO;
import de.hallerweb.enterprise.prioritize.dto.document.DocumentSummaryDTO;
import de.hallerweb.enterprise.prioritize.model.document.Document;
import de.hallerweb.enterprise.prioritize.model.document.DocumentInfo;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.document.DocumentService;
import de.hallerweb.enterprise.prioritize.service.security.AuthorizationService;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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
    private final UserService userService; // To determine the current user
    private final AuthorizationService authorizationService;

    /**
     * ---------- Upload document to a DocumentGroup. ----------
     * <p>
     * http://[HOST]:[PORT]/api/v1/documents/upload/[GROUP_ID]
     * <p>
     * The group is identified by the groupId
     * -------------------------------------------------------
     */
    @PostMapping(value = "/upload/{groupId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentInfo> uploadDocument(
            @PathVariable Long groupId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name) throws IOException {

        log.info("Upload request received: Name={}, Group={}, Size={}", name, groupId, file.getSize());

        try {
            // In a real API we would get the user from the SecurityContext
            PUser currentUser = userService.getCurrentUser();
            log.info("User identified for upload: {}", currentUser.getUsername());
            DocumentInfo info = documentService.createDocument(
                    name,
                    groupId,
                    currentUser,
                    file.getBytes(),
                    file.getContentType()
            );
            log.info("Document successfully created.");

            return ResponseEntity.ok(info);
        } catch (Exception e) {
            log.error("Error during upload: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }


    /**
     * ---------- Download document contents of most recent document. ----------
     * <p>
     * http://[HOST]:[PORT]/api/v1/documents/download/[DOCUMENT_INFO_ID]
     * <p>
     * The DocumentInfo is identified by the documentInfoId
     * ------------------------------------------------------------------------
     */
    @GetMapping("/download/{documentInfoId}")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long documentInfoId) {
        PUser currentUser = userService.getCurrentUser();
        Document doc = documentService.getDocument(documentInfoId, currentUser).getCurrentDocument();

        String filename = doc.getName();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .body(doc.getData());
    }


    /**
     * ---------- List all documents of a DocumentGroup. ----------------------
     * <p>
     * http://[HOST]:[PORT]/api/v1/documents/group/[DOCUMENT_GROUP_ID]
     * <p>
     * The group is identified by the groupId. Returns a DocumentSummaryDTO
     * containing  the id of the documents.
     * ------------------------------------------------------------------------
     */
    @GetMapping("/group/{groupId}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<DocumentSummaryDTO>> getDocumentsInGroup(@PathVariable Long groupId) {
        PUser currentUser = userService.getCurrentUser();
        List<DocumentInfo> documents = documentService.getDocumentsInGroup(groupId, currentUser);

        // Mapping auf das schlanke DTO
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
     * ---------- Retrieves the history of a document ------------------------------------
     * <p>
     * http://[HOST]:[PORT]/api/v1/documents/[DOCUMENT_INFO_ID]/history
     * <p>
     * Retrieves a list with all changes on the document with the given id
     * containing name,version,lastModifiedBy, lastModifiedDate and comment (if present)
     * -----------------------------------------------------------------------------------
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
                )).toList();

        return ResponseEntity.ok(dtos);
    }


    /**
     * ---------- Download a specific document version ------------------------------------
     * <p>
     * http://[HOST]:[PORT]/api/v1/documents/[DOCUMENT_INFO_ID]/version/[VERSION_NUMBER]
     * <p>
     * Downloads a specific version of a document indicated by the version number.
     * ------------------------------------------------------------------------------------
     */
    @GetMapping("/{id}/version/{versionNumber}")
    public ResponseEntity<byte[]> downloadSpecificVersion(@PathVariable Long id, @PathVariable Long versionNumber) {
        PUser currentUser = userService.getCurrentUser();
        Document doc = documentService.getSpecificVersion(id, versionNumber, currentUser);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getName() + "\"")
                .body(doc.getData());
    }


    /**
     * ---------- Delete a document-------------------------------------------
     * <p>
     * http://[HOST]:[PORT]/api/v1/documents/[DOCUMENT_INFO_ID]
     * <p>
     * Deletes the document with the given ID.
     * ------------------------------------------------------------------------
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        log.info("Delete request for document: {}", id);
        PUser currentUser = userService.getCurrentUser();

        try {
            documentService.deleteDocument(id, currentUser);
            return ResponseEntity.noContent().build(); // 204 No Content is default
        } catch (Exception e) {
            log.error("Error deleting document {}: ", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }


    /**
     * ---------- Explicitly Lock a Document on the server for edits----------
     * <p>
     * http://[HOST]:[PORT]/api/v1/documents/[DOCUMENT_INFO_ID]/check-out
     * <p>
     * Call this method if a Document shall be edited by a user and during
     * that time no other user shall edit the Document.
     * ------------------------------------------------------------------------
     */
    @PostMapping("/{id}/check-out")
    public ResponseEntity<String> checkOut(@PathVariable int id) {
        log.info("Checking out document: {}", id);
        PUser currentUser = userService.getCurrentUser();
        documentService.checkOut(id, currentUser);
        return ResponseEntity.ok("Document successfully locked.");
    }


    /**
     * ---------- Unlock a Document locked by the user on the server----------
     * <p>
     * http://[HOST]:[PORT]/api/v1/documents/[DOCUMENT_INFO_ID]/check-in
     * <p>
     * Call this method after a document has been updated on the server by the user
     * to unlock the document for further editing by other users.
     * ------------------------------------------------------------------------
     */
    @PostMapping("/{id}/check-in")
    public ResponseEntity<DocumentInfo> checkIn(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "comment", required = false) String comment) throws IOException {

        String currentUsername = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();

        // 2. Get the user "cleanly" (without Hibernate doing an auto-flush)
        PUser currentUser = userService.findUserByUsername(currentUsername);
        Document newVersion = documentService.checkIn(
                id,
                file.getBytes(),
                file.getContentType(),
                comment,
                currentUser
        );
        return ResponseEntity.ok(newVersion.getDocumentInfo());
    }


    /**
     * ---------- search for a Document --------------------------------------------
     * <p>
     * http://[HOST]:[PORT]/api/v1/documents/search
     * <p>
     * Call this method to search a document by a part of the name.
     * effectively it is a like search : WHERE name LIKE %searchterm% .
     * ------------------------------------------------------------------------
     */
    @GetMapping("/search")
    public ResponseEntity<List<DocumentSummaryDTO>> search(@RequestParam String name) {
        PUser currentUser = userService.getCurrentUser();
        // Der Service gibt direkt die Liste der DTOs zurück
        return ResponseEntity.ok(documentService.searchDocumentsByName(name, currentUser));
    }


    /**
     * ---------- Show the latest 10 documents created -----------------------
     * <p>
     * http://[HOST]:[PORT]/api/v1/documents/recent
     * <p>
     * Call this method to show the latest doocuments added.
     * ------------------------------------------------------------------------
     */
    @GetMapping("/recent")
    public ResponseEntity<List<DocumentSummaryDTO>> getRecent() {
        PUser currentUser = userService.getCurrentUser();
        return ResponseEntity.ok(documentService.getRecentDocuments(currentUser));
    }

}