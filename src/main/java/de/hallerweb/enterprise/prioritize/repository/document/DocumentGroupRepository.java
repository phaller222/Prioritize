package de.hallerweb.enterprise.prioritize.repository.document;

import de.hallerweb.enterprise.prioritize.model.document.DocumentGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository für DocumentGroup Entitäten (Verzeichnisse).
 */
@Repository
public interface DocumentGroupRepository extends JpaRepository<DocumentGroup, Long> {

    // Findet alle Dokumentengruppen einer bestimmten Abteilung
    List<DocumentGroup> findByDepartment_Id(Long departmentId);

    // Findet eine spezifische Gruppe nach Namen innerhalb einer Abteilung
    // Essenziell für den Check auf die "Default"-Gruppe im DataInitializer
    Optional<DocumentGroup> findByNameAndDepartment_Id(String name, Long departmentId);

    // Findet Gruppen nach Namen (Global, ignoriert Groß-/Kleinschreibung)
    List<DocumentGroup> findByNameContainingIgnoreCase(String name);
}