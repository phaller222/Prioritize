package de.hallerweb.enterprise.prioritize.repository.document;

import de.hallerweb.enterprise.prioritize.model.document.DocumentInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentInfoRepository extends JpaRepository<DocumentInfo, Integer> {

    /**
     * Findet alle DocumentInfo-Objekte (logische Dokumente),
     * die zu einer bestimmten Gruppe gehören.
     */
    List<DocumentInfo> findByDocumentGroup_Id(int groupId);
}