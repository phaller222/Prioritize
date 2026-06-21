package de.hallerweb.enterprise.prioritize.repository.company;

import de.hallerweb.enterprise.prioritize.model.company.Department;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    // Overrides the normal findAll and eagerly loads the Company and the resource groups
    @EntityGraph(attributePaths = {"company", "resourceGroups"})
    List<Department> findAll();

    // Finds a department by its name
    Optional<Department> findByName(String name);

    // Finds a department by its secret token
    // Extremely important for automated processes/devices
    Optional<Department> findByToken(String token);

    // Finds all departments of a specific company
    List<Department> findByCompany_Id(Long companyId);

    // Enables a search by department name (case insensitive)
    List<Department> findByNameContainingIgnoreCase(String name);
}