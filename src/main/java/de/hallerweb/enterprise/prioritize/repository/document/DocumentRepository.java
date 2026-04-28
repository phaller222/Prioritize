package de.hallerweb.enterprise.prioritize.repository.document;

import de.hallerweb.enterprise.prioritize.model.document.Document;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Integer> {

    // Lädt das Dokument und die Gruppe in einem SQL-Join (Performance!)
    @EntityGraph(attributePaths = {"documentGroup"})
    Optional<Document> findById(Integer id);

    List<Document> findByNameContaining(String name);

    // Wichtig für den DocumentService
    List<Document> findByDocumentInfo_DocumentGroup_Id(int documentGroupId);

    // Findet die neueste Version eines Dokuments anhand des Namens innerhalb einer Gruppe
    @Query("SELECT d FROM Document d WHERE d.name = :name AND d.documentInfo.documentGroup.id = :groupId ORDER BY d.version DESC")
    List<Document> findLatestVersionByName(@Param("name") String name, @Param("groupId") int groupId);

    // Deine Filter-Query
    @Query("SELECT d FROM Document d " +
            "WHERE (:name IS NULL OR d.name = :name) " +
            "AND (:tag IS NULL OR d.tag = :tag) " +
            "AND (:version IS NULL OR d.version = :version)")
    Collection<Document> findByFilter(@Param("name") String name,
                                      @Param("tag") String tag,
                                      @Param("version") Integer version);
}