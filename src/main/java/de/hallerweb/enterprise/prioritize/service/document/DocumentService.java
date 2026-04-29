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

        // 1. Berechtigung auf die GRUPPE prüfen (Darf der User hier Dokumente anlegen?)
        if (!authService.hasPermission(user, group, Action.UPDATE)) {
            throw new AccessDeniedException("Keine Berechtigung, in dieser Gruppe Dokumente zu erstellen.");
        }

        // Die erste Version (Document) erstellen
        Document firstVersion = Document.builder()
                .name(name)
                .data(content)
                .mimeType(mimeType)
                .version(1)
                // lastModified und lastModifiedBy werden von Spring automatisch gesetzt!
                .build();

        // Logische Hülle (DocumentInfo) erstellen
        DocumentInfo docInfo = DocumentInfo.builder()
                .documentGroup(group)
                .currentDocument(firstVersion)
                .locked(false)
                .build();

        //  Rückverknüpfung für JPA (Document braucht die Info)
        firstVersion.setDocumentInfo(docInfo);
        docInfo.getRecentDocuments().add(firstVersion);

        //  Speichern (Cascade in DocumentInfo sorgt dafür, dass firstVersion mitgespeichert wird)
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


        if (!info.getLockedBy().equals(user) && !user.isAdmin()) {
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

        // WICHTIG: Security-Check auch hier!
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

        // Nur derjenige, der gesperrt hat (oder ein Admin), darf einchecken
        if (!info.getLockedBy().equals(user) && !user.isAdmin()) {
            throw new AccessDeniedException("Nur der Besitzer des Locks darf das Dokument einchecken.");
        }

        // Neue Version erstellen (Logik aus addNewVersion nutzen)
        Document newVersion = addNewVersion(documentInfoId, user, content, mimeType);

        // Lock aufheben
        info.setLocked(false);
        info.setLockedBy(null);
        documentInfoRepository.save(info);

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
        if (!info.getLockedBy().equals(user) && !user.isAdmin()) {
            throw new AccessDeniedException("Nur der Besitzer des Locks kann die Sperre aufheben.");
        }

        info.setLocked(false);
        info.setLockedBy(null);
        documentInfoRepository.save(info);
        log.info("Lock für Dokument ID {} wurde von User {} aufgehoben (Abbruch).", documentInfoId, user.getUsername());
    }

}