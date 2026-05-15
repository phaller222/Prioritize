package de.hallerweb.enterprise.prioritize.repository.company;

import de.hallerweb.enterprise.prioritize.model.company.Department;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Integer> {

    // Überschreibt das normale findAll und lädt die Company und die Resource-Gruppen sofort mit
    @EntityGraph(attributePaths = {"company", "resourceGroups"})
    List<Department> findAll();

    // Findet eine Abteilung anhand ihres Namens
    Optional<Department> findByName(String name);

    // Findet eine Abteilung anhand des geheimen Tokens
    // Extrem wichtig für automatisierte Prozesse/Geräte
    Optional<Department> findByToken(String token);

    // Findet alle Abteilungen einer bestimmten Firma
    List<Department> findByCompany_Id(int companyId);

    // Ermöglicht eine Suche nach Abteilungsnamen (Case Insensitive)
    List<Department> findByNameContainingIgnoreCase(String name);
}