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

import de.hallerweb.enterprise.prioritize.config.CurrentUserResolver;
import de.hallerweb.enterprise.prioritize.dto.document.DocumentSummaryDTO;
import de.hallerweb.enterprise.prioritize.model.document.DocumentGroup;
import de.hallerweb.enterprise.prioritize.service.document.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/document-groups")
@RequiredArgsConstructor
public class DocumentGroupRestController {

    private final DocumentService documentService;
    private final CurrentUserResolver currentUserResolver;

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
    public ResponseEntity<List<DocumentSummaryDTO>> getDocumentsInGroup(@PathVariable Long groupId, Authentication auth) {
        return ResponseEntity.ok(
                documentService.getDocumentsInGroupAsDTO(groupId, currentUserResolver.resolve(auth)));
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long groupId, Authentication auth) {
        documentService.deleteDocumentGroup(groupId, currentUserResolver.resolve(auth));
        return ResponseEntity.noContent().build();
    }
}