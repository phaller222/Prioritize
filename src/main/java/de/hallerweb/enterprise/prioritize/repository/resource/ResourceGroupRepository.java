package de.hallerweb.enterprise.prioritize.repository.resource;

import de.hallerweb.enterprise.prioritize.model.resource.ResourceGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ResourceGroup entities.
 */
@Repository
public interface ResourceGroupRepository extends JpaRepository<ResourceGroup, Long> {

    // Finds all groups of a specific department
    List<ResourceGroup> findByDepartment_Id(Long departmentId);

    // Finds a specific group by name within a department
    // Important for the check on the "default" group
    Optional<ResourceGroup> findByNameAndDepartment_Id(String name, int departmentId);

    // Finds groups by name (global)
    List<ResourceGroup> findByNameContainingIgnoreCase(String name);
}