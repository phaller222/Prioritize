package de.hallerweb.enterprise.prioritize.controller.document;

import de.hallerweb.enterprise.prioritize.model.document.Document;
import de.hallerweb.enterprise.prioritize.model.document.DocumentInfo;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.document.DocumentService;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
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
    private final UserService userService; // To determine the current user

    /**
     * Uploads a new document to a group.
     * The group is identified by the groupId
     */
    @PostMapping(value = "/upload/{groupId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentInfo> uploadDocument(
        @PathVariable int groupId,
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
     * Downloads the content of the most recent version of a document.
     * The DocumentInfo is passed as parameter
     */
    @GetMapping("/download/{documentInfoId}")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable int documentInfoId) {
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
     * Lists all document infos of a group.
     */
    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<DocumentInfo>> getDocumentsInGroup(@PathVariable int groupId) {
        PUser currentUser = userService.getCurrentUser();
        return ResponseEntity.ok(documentService.getDocumentsInGroup(groupId, currentUser));
    }

    @PostMapping("/{id}/check-out")
    public ResponseEntity<String> checkOut(@PathVariable int id) {
        log.info("Checking out document: {}", id);
        PUser currentUser = userService.getCurrentUser();
        documentService.checkOut(id, currentUser);
        return ResponseEntity.ok("Document successfully locked.");
    }

    @PostMapping("/{id}/check-in")
    public ResponseEntity<DocumentInfo> checkIn(
        @PathVariable int id,
        @RequestParam("file") MultipartFile file) throws IOException {

        String currentUsername = org.springframework.security.core.context.SecurityContextHolder
            .getContext().getAuthentication().getName();

        // 2. Get the user "cleanly" (without Hibernate doing an auto-flush)
        PUser currentUser = userService.getUserByUsername(currentUsername);
        Document newVersion = documentService.checkIn(
            id,
            file.getBytes(),
            file.getContentType(),
            currentUser
        );

        return ResponseEntity.ok(newVersion.getDocumentInfo());
    }

}