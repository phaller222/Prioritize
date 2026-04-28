package de.hallerweb.enterprise.prioritize.repository.security;

import de.hallerweb.enterprise.prioritize.model.security.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Integer> {


    Optional<Role> findByName(String name);

    // Hilfreich für die Suche (Case Insensitive)
    List<Role> findByNameContainingIgnoreCase(String query);

    List<Role> findByPermissions_Id(int permissionRecordId);

    List<Role> findByDepartment_Id(int departmentId);
}