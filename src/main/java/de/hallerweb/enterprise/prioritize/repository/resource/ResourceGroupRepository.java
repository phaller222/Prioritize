package de.hallerweb.enterprise.prioritize.repository.resource;

import de.hallerweb.enterprise.prioritize.model.resource.ResourceGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository für ResourceGroup Entitäten.
 */
@Repository
public interface ResourceGroupRepository extends JpaRepository<ResourceGroup, Long> {

    // Findet alle Gruppen einer bestimmten Abteilung
    List<ResourceGroup> findByDepartment_Id(Long departmentId);

    // Findet eine spezifische Gruppe nach Namen innerhalb einer Abteilung
    // Wichtig für den Check auf die "Default"-Gruppe
    Optional<ResourceGroup> findByNameAndDepartment_Id(String name, int departmentId);

    // Findet Gruppen nach Namen (Global)
    List<ResourceGroup> findByNameContainingIgnoreCase(String name);
}