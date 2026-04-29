package de.hallerweb.enterprise.prioritize.controller.document;

import de.hallerweb.enterprise.prioritize.model.document.DocumentGroup;
import de.hallerweb.enterprise.prioritize.model.document.DocumentInfo;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.document.DocumentGroupRepository;
import de.hallerweb.enterprise.prioritize.service.document.DocumentService;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/document-groups") // Eindeutig und klar benannt
@RequiredArgsConstructor
public class DocumentGroupRestController {

    private final DocumentGroupRepository groupRepository;
    private final UserService userService;
    private final DocumentService documentService;
    // Eventuell brauchst du hier auch den AuthorizationService

    @PostMapping
    public ResponseEntity<DocumentGroup> createGroup(@RequestBody DocumentGroup group) {
        // Optional: Hier könntest du prüfen, ob der User Admin ist
        // PUser currentUser = userService.getCurrentUser();

        DocumentGroup savedGroup = groupRepository.save(group);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedGroup);
    }

    @GetMapping
    public ResponseEntity<List<DocumentGroup>> getAllGroups() {
        return ResponseEntity.ok(groupRepository.findAll());
    }

    /**
     * Listet alle Dokument-Infos einer spezifischen Gruppe auf.
     * Pfad: /api/v1/document-groups/{groupId}/documents
     */
    @GetMapping("/{groupId}/documents")
    public ResponseEntity<List<DocumentInfo>> getDocumentsInGroup(@PathVariable int groupId) {
        PUser currentUser = userService.getCurrentUser();
        List<DocumentInfo> documents = documentService.getDocumentsInGroup(groupId, currentUser);
        return ResponseEntity.ok(documents);
    }

}