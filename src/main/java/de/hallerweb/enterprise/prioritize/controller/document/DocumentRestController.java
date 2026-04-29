package de.hallerweb.enterprise.prioritize.controller.document;

import de.hallerweb.enterprise.prioritize.model.document.Document;
import de.hallerweb.enterprise.prioritize.model.document.DocumentInfo;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.document.DocumentService;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import lombok.RequiredArgsConstructor;
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
public class DocumentRestController {

    private final DocumentService documentService;
    private final UserService userService; // Um den aktuellen User zu bestimmen

    /**
     * Lädt ein neues Dokument in eine Gruppe hoch.
     */
    @PostMapping(value = "/upload/{groupId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentInfo> uploadDocument(
            @PathVariable int groupId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name) throws IOException {

        // In einer echten API würden wir den User aus dem SecurityContext holen
        PUser currentUser = userService.getCurrentUser();

        DocumentInfo info = documentService.createDocument(
                name,
                groupId,
                currentUser,
                file.getBytes(),
                file.getContentType()
        );

        return ResponseEntity.ok(info);
    }

    /**
     * Lädt den Inhalt der aktuellsten Version eines Dokuments herunter.
     */
    @GetMapping("/download/{documentInfoId}")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable int documentInfoId) {
        PUser currentUser = userService.getCurrentUser();
        Document doc = documentService.getDocument(documentInfoId, currentUser).getCurrentDocument();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getName() + "\"")
                .body(doc.getData());
    }

    /**
     * Listet alle Dokument-Infos einer Gruppe auf.
     */
    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<DocumentInfo>> getDocumentsInGroup(@PathVariable int groupId) {
        PUser currentUser = userService.getCurrentUser();
        return ResponseEntity.ok(documentService.getDocumentsInGroup(groupId,currentUser));
    }

    @PostMapping("/{id}/check-out")
    public ResponseEntity<String> checkOut(@PathVariable int id) {
        PUser currentUser = userService.getCurrentUser();
        documentService.checkOut(id, currentUser);
        return ResponseEntity.ok("Dokument erfolgreich gesperrt.");
    }

    @PostMapping("/{id}/check-in")
    public ResponseEntity<DocumentInfo> checkIn(
            @PathVariable int id,
            @RequestParam("file") MultipartFile file) throws IOException {

        PUser currentUser = userService.getCurrentUser();
        Document newVersion = documentService.checkIn(
                id,
                file.getBytes(),
                file.getContentType(),
                currentUser
        );

        return ResponseEntity.ok(newVersion.getDocumentInfo());
    }

}