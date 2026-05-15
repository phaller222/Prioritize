package de.hallerweb.enterprise.prioritize.service;

import de.hallerweb.enterprise.prioritize.model.company.Address;
import de.hallerweb.enterprise.prioritize.model.company.Company;
import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.repository.company.CompanyRepository;
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
    private final CompanyRepository companyRepository;

    /**
     * Erstellt eine neue Abteilung oder aktualisiert eine bestehende.
     */
    public Department saveDepartment(Department department, Integer companyId) {
        Company company = companyRepository.findById(companyId).orElseThrow(() -> new EntityNotFoundException("Company with id " + companyId + " not found."));
        department.setCompany(company);
        company.addDepartment(department);
        return departmentRepository.save(department);
    }


    public Department updateDepartment(Integer id, Department departmentDetails) {
        Department existingDept = getDepartmentById(id);

        // Nur die Felder aktualisieren, die geändert werden dürfen
        existingDept.setName(departmentDetails.getName());
        existingDept.setDescription(departmentDetails.getDescription());
        if (departmentDetails.getAddress() != null) {
            if (existingDept.getAddress() != null) {
                // Wir kopieren die Werte in die bestehende Adresse, damit die ID gleich bleibt
                Address existingAddr = existingDept.getAddress();
                Address newAddr = departmentDetails.getAddress();
                existingAddr.setStreet(newAddr.getStreet());
                existingAddr.setCity(newAddr.getCity());
                existingAddr.setZipCode(newAddr.getZipCode());
                existingAddr.setCountry(newAddr.getCountry());
                existingAddr.setHousenumber(newAddr.getHousenumber());
                existingAddr.setFloor(newAddr.getFloor());

            } else {
                // Es gab noch keine Adresse, also neue setzen
                existingDept.setAddress(departmentDetails.getAddress());
            }
        }

        return departmentRepository.save(existingDept);
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