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

import de.hallerweb.enterprise.prioritize.dto.document.DocumentSummaryDTO;
import de.hallerweb.enterprise.prioritize.model.document.Document;
import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.document.DocumentGroup;
import de.hallerweb.enterprise.prioritize.model.document.DocumentInfo;
import de.hallerweb.enterprise.prioritize.model.security.Action;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.document.DocumentGroupRepository;
import de.hallerweb.enterprise.prioritize.repository.document.DocumentInfoRepository;
import de.hallerweb.enterprise.prioritize.service.security.AuthorizationService;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypes;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentInfoRepository documentInfoRepository;
    private final DocumentGroupRepository documentGroupRepository;
    private final AuthorizationService authService;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * ---------- Creates a new logical document (DocumentInfo) and the first version (Document). ----------
     * Call this method to create a new document
     * <p>
     * -----------------------------------------------------------------------------------------------------
     */
    @Transactional
    public DocumentInfo createDocument(String name, Long groupId, PUser user, byte[] content, String mimeType) {
        DocumentGroup group = documentGroupRepository.findById(groupId)
            .orElseThrow(() -> new NoSuchElementException("Group not found."));

        // 1. Create and save the document wrapper so that docInfo gets an ID.
        DocumentInfo docInfo = new DocumentInfo();
        docInfo.setDocumentGroup(group);
        docInfo.setLocked(false);
        docInfo = documentInfoRepository.save(docInfo); // ID is generated!

        // 2. Create version.

        String finalName = name;
        if (!name.contains(".")) {
            String extension = getExtension(mimeType);
            finalName = name + extension;
        }

        Document firstVersion = Document.builder()
            .name(finalName)
            .data(content)
            .mimeType(mimeType)
            .version(1)
            .documentInfo(docInfo)
            .lastModifiedBy(user)
            .build();

        // 3. Link now; both now have IDs or are stable.
        docInfo.setCurrentDocument(firstVersion);
        docInfo.getRecentDocuments().add(firstVersion);

        // 4. Final save updates the link.
        return documentInfoRepository.save(docInfo);
    }


    /**
     * ---------- Creates a new version for an existing document ----------
     * <p> A new document version is beeing created and uploaded.
     * <p>
     * ---------------------------------------------------------------------
     */
    @Transactional
    public Document addNewVersion(Long documentInfoId, PUser user, byte[] content, String mimeType, String comment) {
        {
            DocumentInfo info = documentInfoRepository.findById(documentInfoId)
                .orElseThrow(() -> new NoSuchElementException("Document info not found."));

            // Check authorization on the logical document.
            if (!authService.hasPermission(user, info, Action.UPDATE)) {
                throw new AccessDeniedException("No permission to create a new version.");
            }

            // SECURITY CHECK:
            if (!info.isLocked()) {
                throw new IllegalStateException("Version cannot be created: document must be checked out first.");
            }


            if (info.getLockedBy().getId() != user.getId() && !user.isAdmin()) {
                throw new AccessDeniedException("Only the lock owner may upload a new version.");
            }

            int nextVersion = info.getCurrentDocument().getVersion() + 1;

            Document newVersion = Document.builder()
                .name(info.getCurrentDocument().getName())
                .data(content)
                .mimeType(mimeType)
                .version(nextVersion)
                .changes(comment)
                .documentInfo(info)
                .build();

            info.setCurrentDocument(newVersion);
            info.getRecentDocuments().add(newVersion);

            documentInfoRepository.save(info);
            return newVersion;
        }
    }

    /**
     * Returns all versions, newest first
     *
     * @param documentInfoId
     * @param user
     * @return documents
     */
    public List<Document> getDocumentHistory(Long documentInfoId, PUser user) {
        DocumentInfo info = getDocument(documentInfoId, user);
        // returns all versions, newest first
        return info.getRecentDocuments().stream()
            .sorted((a, b) -> Integer.compare(b.getVersion(), a.getVersion()))
            .toList();
    }

    public Document getSpecificVersion(Long documentInfoId, Long version, PUser user) {
        DocumentInfo info = getDocument(documentInfoId, user);
        return info.getRecentDocuments().stream()
            .filter(d -> d.getVersion() == version)
            .findFirst()
            .orElseThrow(() -> new NoSuchElementException("Version not found."));
    }


    /**
     * ---------- Returns all current documents in the given DocumentGroup ----------
     * <p>
     * -----------------------------------------------------------------------------------------------------
     */
    public List<DocumentInfo> getDocumentsInGroup(Long groupId, PUser user) {
        DocumentGroup group = documentGroupRepository.findById(groupId)
            .orElseThrow(() -> new NoSuchElementException("Group not found."));

        if (!authService.hasPermission(user, group, Action.READ)) {
            throw new AccessDeniedException("No read permission for this group.");
        }

        return documentInfoRepository.findByDocumentGroup_Id(groupId);
    }

    public DocumentInfo getDocument(Long documentInfoId, PUser user) {
        DocumentInfo info = documentInfoRepository.findById(documentInfoId)
            .orElseThrow(() -> new NoSuchElementException("Document with id " + documentInfoId + " not found."));

        if (!authService.hasPermission(user, info, Action.READ)) {
            throw new AccessDeniedException("No read permission for this document.");
        }

        return info;
    }


    /**
     * ----------Checks out a document (lock the document in the DB) ----------
     * <p> If documents are beeing edited after download and shall be
     * put back as new version, users must lock the document first to
     * avoid conflicts.
     * -----------------------------------------------------------------------------------------------------
     */
    @Transactional
    public void checkOut(Long documentInfoId, PUser user) {
        DocumentInfo info = documentInfoRepository.findByIdWithLockedBy(documentInfoId)
            .orElseThrow(() -> new NoSuchElementException("Document not found."));

        if (info.isLocked()) {
            // make sure info is not null
            String lockerName = (info.getLockedBy() != null) ? info.getLockedBy().getUsername() : "Unknown";
            throw new IllegalStateException("Document is already locked by " + lockerName + ".");
        }

        // Check authorization; UPDATE permission is required for locking.
        if (!authService.hasPermission(user, info, Action.UPDATE)) {
            throw new AccessDeniedException("No permission to lock this document.");
        }

        info.setLocked(true);
        info.setLockedBy(user);
        documentInfoRepository.save(info);
        log.info("Document id {} checked out by user {}.", documentInfoId, user.getUsername());
    }


    /**
     * ----------Checks in a document (unlock the document in the DB) ----------
     * <p> If documents had been processed and wrítten back to the server ,
     * they must be unlock so that other users can edit them too.
     * -----------------------------------------------------------------------------------------------------
     */
    @Transactional
    public Document checkIn(Long documentInfoId, byte[] content, String mimeType, String comment, PUser user) {
        DocumentInfo info = documentInfoRepository.findById(documentInfoId)
            .orElseThrow(() -> new NoSuchElementException("Document not found."));

        if (!info.isLocked()) {
            throw new IllegalStateException("Document is not locked.");
        }

        // Safe check against null and ID comparison.
        PUser locker = info.getLockedBy();
        if (locker == null) {
            log.warn("Document {} was locked but lockedBy was null!", documentInfoId);
        } else if (locker.getId() != user.getId() && !user.isAdmin()) {
            throw new AccessDeniedException("Only the lock owner (" + locker.getUsername() + ") may check in.");
        }

        // Create new version by using the logic from addNewVersion.
        Document newVersion = addNewVersion(documentInfoId, user, content, mimeType, comment);

        // Release lock.
        info.setLocked(false);
        info.setLockedBy(null);
        documentInfoRepository.save(info);

        log.info("Document id {} successfully checked in by user {}.", documentInfoId, user.getUsername());
        return newVersion;
    }


    /**
     * ----------Cancels a check out process----------
     * <p> If user decides to cancel to edit the document without changing
     * anything, there must be a way to simply cancel the checkout and
     * unlock the document without changes.
     * -----------------------------------------------------------------------------------------------------
     */
    @Transactional
    public void cancelCheckOut(Long documentInfoId, PUser user) {
        DocumentInfo info = documentInfoRepository.findById(documentInfoId)
            .orElseThrow(() -> new NoSuchElementException("Document not found."));

        if (!info.isLocked()) {
            return; // Nothing to do.
        }

        // Only the owner or an admin may cancel.
        if (info.getLockedBy().getId() != user.getId() && !user.isAdmin()) {
            throw new AccessDeniedException("Only the lock owner may cancel the checkout.");
        }

        info.setLocked(false);
        info.setLockedBy(null);
        documentInfoRepository.save(info);
        log.info("Lock for document id {} released by user {} (cancelled).", documentInfoId, user.getUsername());
    }

    public String getExtension(String contentType) {
        MimeTypes allTypes = MimeTypes.getDefaultMimeTypes();
        try {
            MimeType type = allTypes.forName(contentType);
            return type.getExtension();
        } catch (Exception e) {
            return "";
        }
    }

    @Transactional
    public void deleteDocument(Long documentInfoId, PUser user) {
        DocumentInfo info = documentInfoRepository.findById(documentInfoId)
            .orElseThrow(() -> new NoSuchElementException("Document not found."));

        // check for Action.DELETE
        if (!authService.hasPermission(user, info, Action.DELETE)) {
            throw new AccessDeniedException("No permission to delete.");
        }

        // If document is locked, it cannot be deleted except by admins
        if (info.isLocked() && !user.isAdmin()) {
            throw new IllegalStateException("Locked documents cannot be deleted.");
        }

        // Published before the delete and handled synchronously: satellites that reference this document
        // either clean up their own rows or veto the deletion by throwing (see DocumentDeletionEvent).
        eventPublisher.publishEvent(new DocumentDeletionEvent(documentInfoId));

        documentInfoRepository.delete(info);
        log.info("Document {} deleted by user {}.", documentInfoId, user.getUsername());
    }

    public List<DocumentSummaryDTO> searchDocumentsByName(String name, PUser user) {
        return documentInfoRepository.findByCurrentDocument_NameContainingIgnoreCase(name).stream()
            .filter(info -> authService.hasPermission(user, info, Action.READ))
            .map(this::convertToDTO)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentSummaryDTO> getRecentDocuments(PUser user) {
        return documentInfoRepository.findTop10ByOrderByCurrentDocument_LastModifiedDesc()
            .stream()
            // Only show documents for which the user has read rights
            .filter(info -> authService.hasPermission(user, info, Action.READ))
            .map(this::convertToDTO)
            .toList();
    }


    private DocumentSummaryDTO convertToDTO(DocumentInfo info) {
        return new DocumentSummaryDTO(
            info.getId(),
            info.getCurrentDocument().getName(),
            info.getCurrentDocument().getVersion(),
            info.isLocked(),
            info.getLockedBy() != null ? info.getLockedBy().getUsername() : null
        );
    }

    // ==========================================
    // DOCUMENT GROUP MANAGEMENT
    // ==========================================

    public DocumentGroup createDocumentGroup(DocumentGroup group) {
        // Protection: the default group must not be created manually
        if (DocumentGroup.DEFAULT_GROUP_NAME.equalsIgnoreCase(group.getName())) {
            throw new IllegalArgumentException("A group named 'Default' cannot be created manually.");
        }
        return documentGroupRepository.save(group);
    }

    /**
     * Creates a document group inside a department (for the admin group management screen). Mirrors
     * {@link de.hallerweb.enterprise.prioritize.service.resource.ResourceService#createResourceGroup}:
     * it protects the default group name and requires {@link Action#CREATE} on the department. The
     * existing {@link #createDocumentGroup(DocumentGroup)} (used by the REST controller) is left unchanged.
     */
    @Transactional
    public DocumentGroup createDocumentGroup(String name, Department dept, PUser user) {
        if (DocumentGroup.DEFAULT_GROUP_NAME.equalsIgnoreCase(name)) {
            throw new IllegalArgumentException("A group named 'Default' cannot be created manually.");
        }
        if (!authService.hasPermission(user, dept, Action.CREATE)) {
            throw new AccessDeniedException("No permission to create groups in this department.");
        }
        DocumentGroup group = DocumentGroup.builder().name(name).department(dept).build();
        return documentGroupRepository.save(group);
    }

    @Transactional(readOnly = true)
    public List<DocumentGroup> getAllDocumentGroups() {
        return documentGroupRepository.findAll();
    }

    /**
     * Lists the document groups of a department (for the admin group management screen). Requires
     * {@link Action#READ} on the department, mirroring
     * {@link de.hallerweb.enterprise.prioritize.service.company.DepartmentService#getDepartmentsByCompany}.
     */
    @Transactional(readOnly = true)
    public List<DocumentGroup> getDocumentGroupsByDepartment(Long departmentId, PUser user) {
        if (!authService.hasPermission(user,
                "de.hallerweb.enterprise.prioritize.model.company.Department", departmentId, Action.READ)) {
            throw new AccessDeniedException("No permission to read document groups of this department.");
        }
        return documentGroupRepository.findByDepartment_Id(departmentId);
    }

    /**
     * Renames a document group (the only editable field). The default group is protected — as with
     * {@link #deleteDocumentGroup} it cannot be renamed — and the caller needs {@link Action#UPDATE} on it.
     */
    @Transactional
    public DocumentGroup renameDocumentGroup(Long groupId, String newName, PUser user) {
        DocumentGroup group = documentGroupRepository.findById(groupId)
                .orElseThrow(() -> new NoSuchElementException("Document group with id " + groupId + " not found."));
        if (DocumentGroup.DEFAULT_GROUP_NAME.equalsIgnoreCase(group.getName())) {
            throw new IllegalStateException("The default group cannot be renamed.");
        }
        if (!authService.hasPermission(user, group, Action.UPDATE)) {
            throw new AccessDeniedException("No permission to update this group.");
        }
        group.setName(newName);
        return documentGroupRepository.save(group);
    }

    @Transactional(readOnly = true)
    public List<DocumentSummaryDTO> getDocumentsInGroupAsDTO(Long groupId, PUser user) {
        return getDocumentsInGroup(groupId, user).stream()
            .map(this::convertToDTO)
            .toList();
    }

    /**
     * Paged variant of {@link #getDocumentsInGroupAsDTO} for the admin document list. The admin GUI feeds
     * this into a lazy Vaadin grid so a large group never loads its whole document set into memory at once.
     * Requires {@link Action#READ} on the group.
     */
    @Transactional(readOnly = true)
    public Page<DocumentSummaryDTO> getDocumentsInGroup(Long groupId, PUser user, Pageable pageable) {
        DocumentGroup group = documentGroupRepository.findById(groupId)
            .orElseThrow(() -> new NoSuchElementException("Document group with id " + groupId + " not found."));
        if (!authService.hasPermission(user, group, Action.READ)) {
            throw new AccessDeniedException("No read permission for this group.");
        }
        return documentInfoRepository.findByDocumentGroup_Id(groupId, pageable).map(this::convertToDTO);
    }

    /** Count of documents in a group (the count callback for the lazy admin grid). Requires READ on the group. */
    @Transactional(readOnly = true)
    public long countDocumentsInGroup(Long groupId, PUser user) {
        DocumentGroup group = documentGroupRepository.findById(groupId)
            .orElseThrow(() -> new NoSuchElementException("Document group with id " + groupId + " not found."));
        if (!authService.hasPermission(user, group, Action.READ)) {
            throw new AccessDeniedException("No read permission for this group.");
        }
        return documentInfoRepository.countByDocumentGroup_Id(groupId);
    }

    /**
     * The binary payload of a document's current version, for a download-for-control action in the admin
     * GUI (view/download only — the admin never edits or checks out documents). The bytes are read inside
     * the transaction and returned detached in a {@link DocumentDownload}. Requires {@link Action#READ}.
     */
    @Transactional(readOnly = true)
    public DocumentDownload getCurrentVersionForDownload(Long documentInfoId, PUser user) {
        DocumentInfo info = documentInfoRepository.findById(documentInfoId)
            .orElseThrow(() -> new NoSuchElementException("Document with id " + documentInfoId + " not found."));
        if (!authService.hasPermission(user, info, Action.READ)) {
            throw new AccessDeniedException("No read permission for this document.");
        }
        Document current = info.getCurrentDocument();
        String baseName = current.getName();
        // Tika returns the extension WITH a leading dot (e.g. ".txt"), or "" if unknown.
        String extension = getExtension(current.getMimeType());
        String filename = (extension.isEmpty() || baseName.toLowerCase().endsWith(extension.toLowerCase()))
            ? baseName : baseName + extension;
        return new DocumentDownload(filename, current.getMimeType(), current.getData());
    }

    /** Detached download payload of a document's current version (filename, MIME type, raw bytes). */
    public record DocumentDownload(String filename, String mimeType, byte[] data) {
    }

    public void deleteDocumentGroup(Long groupId, PUser user) {
        DocumentGroup group = documentGroupRepository.findById(groupId)
            .orElseThrow(() -> new NoSuchElementException("Document group with id " + groupId + " not found."));

        if (DocumentGroup.DEFAULT_GROUP_NAME.equalsIgnoreCase(group.getName())) {
            throw new IllegalStateException("The default group cannot be deleted.");
        }

        if (!authService.hasPermission(user, group, Action.DELETE)) {
            throw new AccessDeniedException("No permission to delete this group.");
        }

        documentGroupRepository.delete(group);
        log.info("Document group '{}' (id: {}) deleted by user '{}'.",
            group.getName(), groupId, user.getUsername());
    }


}
