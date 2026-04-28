package de.hallerweb.enterprise.prioritize.service.document;

import de.hallerweb.enterprise.prioritize.model.document.Document;
import de.hallerweb.enterprise.prioritize.model.document.DocumentGroup;
import de.hallerweb.enterprise.prioritize.model.document.DocumentInfo;
import de.hallerweb.enterprise.prioritize.model.security.Action;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.document.DocumentGroupRepository;
import de.hallerweb.enterprise.prioritize.repository.document.DocumentInfoRepository;
import de.hallerweb.enterprise.prioritize.service.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentInfoRepository documentInfoRepository;
    private final DocumentGroupRepository documentGroupRepository;
    private final AuthorizationService authService;

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

        // 5. Speichern (Cascade in DocumentInfo sorgt dafür, dass firstVersion mitgespeichert wird)
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
}