package de.hallerweb.enterprise.prioritize.repository.document;

import de.hallerweb.enterprise.prioritize.model.document.DocumentInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentInfoRepository extends JpaRepository<DocumentInfo, Integer> {

    /**
     * Finds all DocumentInfo-Objects (logical document entities referencing the current document),
     * which belong to the given group.
     */
    List<DocumentInfo> findByDocumentGroup_Id(int groupId);
}