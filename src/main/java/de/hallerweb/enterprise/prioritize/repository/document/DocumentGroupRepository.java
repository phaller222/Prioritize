package de.hallerweb.enterprise.prioritize.repository.document;

import de.hallerweb.enterprise.prioritize.model.document.DocumentGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for DocumentGroup entities (directories).
 */
@Repository
public interface DocumentGroupRepository extends JpaRepository<DocumentGroup, Long> {

    // Finds all document groups of a specific department
    List<DocumentGroup> findByDepartment_Id(Long departmentId);

    // Finds a specific group by name within a department
    // Essential for the check on the "default" group in the DataInitializer
    Optional<DocumentGroup> findByNameAndDepartment_Id(String name, Long departmentId);

    // Finds groups by name (global, case-insensitive)
    List<DocumentGroup> findByNameContainingIgnoreCase(String name);
}