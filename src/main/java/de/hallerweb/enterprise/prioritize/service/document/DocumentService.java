package de.hallerweb.enterprise.prioritize.service.document;

import de.hallerweb.enterprise.prioritize.dto.document.DocumentSummaryDTO;
import de.hallerweb.enterprise.prioritize.model.document.Document;
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

    /**
     * ---------- Creates a new logical document (DocumentInfo) and the first version (Document). ----------
     * Call this method to create a new document
     * <p>
     * -----------------------------------------------------------------------------------------------------
     */
    @Transactional
    public DocumentInfo createDocument(String name, Long groupId, PUser user, byte[] content, String mimeType) {
        DocumentGroup group = documentGroupRepository.findById(groupId)
            .orElseThrow(() -> new NoSuchElementException("Gruppe nicht gefunden."));

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
                .orElseThrow(() -> new NoSuchElementException("Dokument-Info nicht gefunden."));

            // Check authorization on the logical document.
            if (!authService.hasPermission(user, info, Action.UPDATE)) {
                throw new AccessDeniedException("Keine Berechtigung für eine neue Version.");
            }

            // SECURITY CHECK:
            if (!info.isLocked()) {
                throw new IllegalStateException("Version kann nicht erstellt werden: Dokument muss zuerst ausgecheckt werden.");
            }


            if (info.getLockedBy().getId() != user.getId() && !user.isAdmin()) {
                throw new AccessDeniedException("Nur der Besitzer des Locks darf eine neue Version hochladen.");
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
            .orElseThrow(() -> new NoSuchElementException("Gruppe nicht gefunden."));

        if (!authService.hasPermission(user, group, Action.READ)) {
            throw new AccessDeniedException("Keine Leseberechtigung für diese Gruppe.");
        }

        return documentInfoRepository.findByDocumentGroup_Id(groupId);
    }

    public DocumentInfo getDocument(Long documentInfoId, PUser user) {
        DocumentInfo info = documentInfoRepository.findById(documentInfoId)
            .orElseThrow(() -> new NoSuchElementException("Dokument mit ID " + documentInfoId + " nicht gefunden."));

        if (!authService.hasPermission(user, info, Action.READ)) {
            throw new AccessDeniedException("Keine Leseberechtigung für dieses Dokument.");
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
            .orElseThrow(() -> new NoSuchElementException("Dokument nicht gefunden."));

        if (info.isLocked()) {
            // make sure info is not null
            String lockerName = (info.getLockedBy() != null) ? info.getLockedBy().getUsername() : "Unknown";
            throw new IllegalStateException("Dokument ist bereits von " + lockerName + " gesperrt.");
        }

        // Check authorization; UPDATE permission is required for locking.
        if (!authService.hasPermission(user, info, Action.UPDATE)) {
            throw new AccessDeniedException("Keine Berechtigung zum Sperren dieses Dokuments.");
        }

        info.setLocked(true);
        info.setLockedBy(user);
        documentInfoRepository.save(info);
        log.info("Dokument ID {} wurde von User {} ausgecheckt.", documentInfoId, user.getUsername());
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
            .orElseThrow(() -> new NoSuchElementException("Dokument nicht gefunden."));

        if (!info.isLocked()) {
            throw new IllegalStateException("Dokument ist nicht gesperrt.");
        }

        // Safe check against null and ID comparison.
        PUser locker = info.getLockedBy();
        if (locker == null) {
            log.warn("Dokument {} war gesperrt, aber lockedBy war null!", documentInfoId);
        } else if (locker.getId() != user.getId() && !user.isAdmin()) {
            throw new AccessDeniedException("Nur der Besitzer des Locks (" + locker.getUsername() + ") darf einchecken.");
        }

        // Create new version by using the logic from addNewVersion.
        Document newVersion = addNewVersion(documentInfoId, user, content, mimeType, comment);

        // Release lock.
        info.setLocked(false);
        info.setLockedBy(null);
        documentInfoRepository.save(info);

        log.info("Dokument ID {} wurde erfolgreich von User {} eingecheckt.", documentInfoId, user.getUsername());
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
            .orElseThrow(() -> new NoSuchElementException("Dokument nicht gefunden."));

        if (!info.isLocked()) {
            return; // Nothing to do.
        }

        // Only the owner or an admin may cancel.
        if (info.getLockedBy().getId() != user.getId() && !user.isAdmin()) {
            throw new AccessDeniedException("Nur der Besitzer des Locks kann die Sperre aufheben.");
        }

        info.setLocked(false);
        info.setLockedBy(null);
        documentInfoRepository.save(info);
        log.info("Lock für Dokument ID {} wurde von User {} aufgehoben (Abbruch).", documentInfoId, user.getUsername());
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
            .orElseThrow(() -> new NoSuchElementException("Dokument nicht gefunden."));

        // check for Action.DELETE
        if (!authService.hasPermission(user, info, Action.DELETE)) {
            throw new AccessDeniedException("Keine Berechtigung zum Löschen.");
        }

        // If document is locked, it cannot be deleted except by admins
        if (info.isLocked() && !user.isAdmin()) {
            throw new IllegalStateException("Gesperrte Dokumente können nicht gelöscht werden.");
        }

        documentInfoRepository.delete(info);
        log.info("Dokument {} wurde von User {} gelöscht.", documentInfoId, user.getUsername());
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
            // Nur Dokumente zeigen, für die der User Leserechte hat
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
        // Schutz: Default-Gruppe darf nicht manuell angelegt werden
        if (DocumentGroup.DEFAULT_GROUP_NAME.equalsIgnoreCase(group.getName())) {
            throw new IllegalArgumentException("Eine Gruppe mit dem Namen 'Default' kann nicht manuell angelegt werden.");
        }
        return documentGroupRepository.save(group);
    }

    @Transactional(readOnly = true)
    public List<DocumentGroup> getAllDocumentGroups() {
        return documentGroupRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<DocumentSummaryDTO> getDocumentsInGroupAsDTO(Long groupId, PUser user) {
        return getDocumentsInGroup(groupId, user).stream()
            .map(this::convertToDTO)
            .toList();
    }

    public void deleteDocumentGroup(Long groupId, PUser user) {
        DocumentGroup group = documentGroupRepository.findById(groupId)
            .orElseThrow(() -> new NoSuchElementException("Dokumentengruppe mit ID " + groupId + " nicht gefunden."));

        if (DocumentGroup.DEFAULT_GROUP_NAME.equalsIgnoreCase(group.getName())) {
            throw new IllegalStateException("Die Default-Gruppe kann nicht gelöscht werden.");
        }

        if (!authService.hasPermission(user, group, Action.DELETE)) {
            throw new AccessDeniedException("Keine Berechtigung zum Löschen dieser Gruppe.");
        }

        documentGroupRepository.delete(group);
        log.info("Dokumentengruppe '{}' (ID: {}) wurde von User '{}' gelöscht.",
            group.getName(), groupId, user.getUsername());
    }


}