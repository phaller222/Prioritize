package de.hallerweb.enterprise.prioritize.repository.document;

import de.hallerweb.enterprise.prioritize.model.document.DocumentInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentInfoRepository extends JpaRepository<DocumentInfo, Long> {

    /**
     * Finds all DocumentInfo-Objects (logical document entities referencing the current document),
     * which belong to the given group.
     */
    List<DocumentInfo> findByDocumentGroup_Id(Long groupId);

    @Query("SELECT d FROM DocumentInfo d LEFT JOIN FETCH d.lockedBy WHERE d.id = :id")
    Optional<DocumentInfo> findByIdWithLockedBy(@Param("id") Long id);

    // Search via the "currentDocument" relationship on the "name" field
    List<DocumentInfo> findByCurrentDocument_NameContainingIgnoreCase(String name);


    // Search by tags (if you use the 'tag' field in Document)
    List<DocumentInfo> findByCurrentDocument_Tag(String tag);

    // The newest X documents (for a dashboard)
    List<DocumentInfo> findTop10ByOrderByCurrentDocument_LastModifiedDesc();


    // Search via the comment in the current version:
    List<DocumentInfo> findByCurrentDocument_ChangesContainingIgnoreCase(String comment);

}

