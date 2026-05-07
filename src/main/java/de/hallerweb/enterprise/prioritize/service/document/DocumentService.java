package de.hallerweb.enterprise.prioritize.service.document;

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
     * Erstellt ein neues logisches Dokument (DocumentInfo) und die erste Version (Document).
     */
    @Transactional
    public DocumentInfo createDocument(String name, int groupId, PUser user, byte[] content, String mimeType) {
        DocumentGroup group = documentGroupRepository.findById(groupId)
                .orElseThrow(() -> new NoSuchElementException("Gruppe nicht gefunden."));

        // 1. Dokument-Hülle erstellen und speichern (damit docInfo eine ID bekommt)
        DocumentInfo docInfo = new DocumentInfo();
        docInfo.setDocumentGroup(group);
        docInfo.setLocked(false);
        docInfo = documentInfoRepository.save(docInfo); // ID wird generiert!

        // 2. Version erstellen

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
                .build();

        // 3. Jetzt verknüpfen (Beide haben nun IDs oder sind stabil)
        docInfo.setCurrentDocument(firstVersion);
        docInfo.getRecentDocuments().add(firstVersion);

        // 4. Finales Speichern (aktualisiert die Verknüpfung)
        return documentInfoRepository.save(docInfo);
    }

    /**
     * Erstellt eine neue Version für ein bestehendes Dokument.
     */
    @Transactional
    public Document addNewVersion(int documentInfoId, PUser user, byte[] content, String mimeType) {
        DocumentInfo info = documentInfoRepository.findById(documentInfoId)
                .orElseThrow(() -> new NoSuchElementException("Dokument-Info nicht gefunden."));

        // Berechtigung am logischen Dokument prüfen
        if (!authService.hasPermission(user, info, Action.UPDATE)) {
            throw new AccessDeniedException("Keine Berechtigung für eine neue Version.");
        }

        // SICHERHEITS-CHECK:
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
                .documentInfo(info)
                .build();

        info.setCurrentDocument(newVersion);
        info.getRecentDocuments().add(newVersion);

        documentInfoRepository.save(info);
        return newVersion;
    }

    public List<DocumentInfo> getDocumentsInGroup(int groupId, PUser user) {
        DocumentGroup group = documentGroupRepository.findById(groupId)
                .orElseThrow(() -> new NoSuchElementException("Gruppe nicht gefunden."));

        if (!authService.hasPermission(user, group, Action.READ)) {
            throw new AccessDeniedException("Keine Leseberechtigung für diese Gruppe.");
        }

        return documentInfoRepository.findByDocumentGroup_Id(groupId);
    }

    public DocumentInfo getDocument(int documentInfoId, PUser user) {
        DocumentInfo info = documentInfoRepository.findById(documentInfoId)
                .orElseThrow(() -> new NoSuchElementException("Dokument mit ID " + documentInfoId + " nicht gefunden."));

        if (!authService.hasPermission(user, info, Action.READ)) {
            throw new AccessDeniedException("Keine Leseberechtigung für dieses Dokument.");
        }

        return info;
    }

    @Transactional
    public void checkOut(int documentInfoId, PUser user) {
        log.info("Suche DocumentInfo mit ID: {}", documentInfoId);
        DocumentInfo info = documentInfoRepository.findById(documentInfoId)
                .orElseThrow(() -> new NoSuchElementException("Dokument nicht gefunden."));

        if (info.isLocked()) {
            throw new IllegalStateException("Dokument ist bereits von " + info.getLockedBy().getName() + " gesperrt.");
        }

        // Berechtigung prüfen (UPDATE-Recht nötig zum Sperren)
        if (!authService.hasPermission(user, info, Action.UPDATE)) {
            throw new AccessDeniedException("Keine Berechtigung zum Sperren dieses Dokuments.");
        }

        info.setLocked(true);
        info.setLockedBy(user);
        documentInfoRepository.save(info);
        log.info("Dokument ID {} wurde von User {} ausgecheckt.", documentInfoId, user.getUsername());
    }

    @Transactional
    public Document checkIn(int documentInfoId, byte[] content, String mimeType, PUser user) {
        DocumentInfo info = documentInfoRepository.findById(documentInfoId)
                .orElseThrow(() -> new NoSuchElementException("Dokument nicht gefunden."));

        if (!info.isLocked()) {
            throw new IllegalStateException("Dokument ist nicht gesperrt.");
        }

        // Sicherer Check gegen Null und ID-Vergleich
        PUser locker = info.getLockedBy();
        if (locker == null) {
            log.warn("Dokument {} war gesperrt, aber lockedBy war null!", documentInfoId);
        } else if (locker.getId() != user.getId() && !user.isAdmin()) {
            throw new AccessDeniedException("Nur der Besitzer des Locks (" + locker.getUsername() + ") darf einchecken.");
        }

        // Neue Version erstellen (Logik aus addNewVersion nutzen)
        Document newVersion = addNewVersion(documentInfoId, user, content, mimeType);

        // Lock aufheben
        info.setLocked(false);
        info.setLockedBy(null);
        documentInfoRepository.saveAndFlush(info);

        log.info("Dokument ID {} wurde erfolgreich von User {} eingecheckt.", documentInfoId, user.getUsername());
        return newVersion;
    }

    @Transactional
    public void cancelCheckOut(int documentInfoId, PUser user) {
        DocumentInfo info = documentInfoRepository.findById(documentInfoId)
                .orElseThrow(() -> new NoSuchElementException("Dokument nicht gefunden."));

        if (!info.isLocked()) {
            return; // Nichts zu tun
        }

        // Nur Besitzer oder Admin dürfen abbrechen
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
            return type.getExtension(); // Liefert z.B. ".pdf"
        } catch (Exception e) {
            return "";
        }
    }

}