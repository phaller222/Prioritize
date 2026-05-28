package de.hallerweb.enterprise.prioritize.controller.document;

import de.hallerweb.enterprise.prioritize.dto.document.DocumentSummaryDTO;
import de.hallerweb.enterprise.prioritize.model.document.DocumentGroup;
import de.hallerweb.enterprise.prioritize.service.document.DocumentService;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/document-groups")
@RequiredArgsConstructor
public class DocumentGroupRestController {

    private final DocumentService documentService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<DocumentGroup> createGroup(@RequestBody DocumentGroup group) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(documentService.createDocumentGroup(group));
    }

    @GetMapping
    public ResponseEntity<List<DocumentGroup>> getAllGroups() {
        return ResponseEntity.ok(documentService.getAllDocumentGroups());
    }

    @GetMapping("/{groupId}/documents")
    public ResponseEntity<List<DocumentSummaryDTO>> getDocumentsInGroup(@PathVariable Long groupId) {
        return ResponseEntity.ok(
            documentService.getDocumentsInGroupAsDTO(groupId, userService.getCurrentUser()));
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long groupId) {
        documentService.deleteDocumentGroup(groupId, userService.getCurrentUser());
        return ResponseEntity.noContent().build();
    }
}