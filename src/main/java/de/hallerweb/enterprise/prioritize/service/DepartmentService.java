package de.hallerweb.enterprise.prioritize.service;

import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.repository.company.DepartmentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    /**
     * Erstellt eine neue Abteilung oder aktualisiert eine bestehende.
     */
    public Department saveDepartment(Department department) {
        return departmentRepository.save(department);
    }

    /**
     * Findet eine Abteilung anhand der ID.
     */
    @Transactional(readOnly = true)
    public Department getDepartmentById(Integer id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Department mit ID " + id + " nicht gefunden."));
    }

    /**
     * Liefert alle Abteilungen einer Firma zurück.
     */
    @Transactional(readOnly = true)
    public List<Department> getDepartmentsByCompany(Integer companyId) {
        return departmentRepository.findByCompany_Id(companyId);
    }

    /**
     * Sucht Abteilungen anhand eines Namens-Teilstücks (Case-Insensitive).
     */
    @Transactional(readOnly = true)
    public List<Department> searchDepartments(String phrase) {
        return departmentRepository.findByNameContainingIgnoreCase(phrase);
    }

    /**
     * Findet eine Abteilung anhand des exakten Namens.
     */
    @Transactional(readOnly = true)
    public Department getDepartmentByName(String name) {
        return departmentRepository.findByName(name)
                .orElseThrow(() -> new EntityNotFoundException("Department mit Name '" + name + "' nicht gefunden."));
    }

    /**
     * Löscht eine Abteilung anhand der ID.
     * Durch cascade=ALL und orphanRemoval in der Entity werden DocumentGroups etc. mitgelöscht.
     */
    public void deleteDepartment(Integer id) {
        if (!departmentRepository.existsById(id)) {
            throw new EntityNotFoundException("Löschen fehlgeschlagen: Department " + id + " existiert nicht.");
        }
        departmentRepository.deleteById(id);
    }

    /**
     * Beispiel für eine gezielte Namensänderung.
     */
    public void renameDepartment(Integer id, String newName) {
        Department dept = getDepartmentById(id);
        dept.setName(newName);
        // departmentRepository.save(dept); // Optional, da @Transactional Änderungen am Managed Object automatisch speichert
    }
}