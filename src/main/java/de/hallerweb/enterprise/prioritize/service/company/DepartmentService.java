package de.hallerweb.enterprise.prioritize.service.company;

import de.hallerweb.enterprise.prioritize.model.address.Address;
import de.hallerweb.enterprise.prioritize.model.company.Company;
import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.security.Action;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.company.CompanyRepository;
import de.hallerweb.enterprise.prioritize.repository.company.DepartmentRepository;
import de.hallerweb.enterprise.prioritize.service.security.AuthorizationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final CompanyRepository companyRepository;
    private final AuthorizationService authService;

    public Department saveDepartment(Department department, Long companyId, PUser requestingUser) {
        if (!authService.hasPermission(requestingUser,
                "de.hallerweb.enterprise.prioritize.model.company.Company",
                companyId, Action.CREATE)) {
            throw new AccessDeniedException("Keine Berechtigung, Abteilungen in dieser Company anzulegen.");
        }
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new EntityNotFoundException("Company with id " + companyId + " not found."));
        department.setCompany(company);
        company.addDepartment(department);
        return departmentRepository.save(department);
    }

    public Department updateDepartment(Long id, Department departmentDetails, PUser requestingUser) {
        if (!authService.hasPermission(requestingUser,
                "de.hallerweb.enterprise.prioritize.model.company.Department",
                id, Action.UPDATE)) {
            throw new AccessDeniedException("Keine Berechtigung, diese Abteilung zu bearbeiten.");
        }
        Department existingDept = getDepartmentById(id);
        existingDept.setName(departmentDetails.getName());
        existingDept.setDescription(departmentDetails.getDescription());

        if (departmentDetails.getAddress() != null) {
            if (existingDept.getAddress() != null) {
                Address existingAddr = existingDept.getAddress();
                Address newAddr = departmentDetails.getAddress();
                existingAddr.setStreet(newAddr.getStreet());
                existingAddr.setCity(newAddr.getCity());
                existingAddr.setZipCode(newAddr.getZipCode());
                existingAddr.setCountry(newAddr.getCountry());
                existingAddr.setHousenumber(newAddr.getHousenumber());
                existingAddr.setFloor(newAddr.getFloor());
            } else {
                existingDept.setAddress(departmentDetails.getAddress());
            }
        }
        return departmentRepository.save(existingDept);
    }

    public void deleteDepartment(Long id, PUser requestingUser) {
        if (!authService.hasPermission(requestingUser,
                "de.hallerweb.enterprise.prioritize.model.company.Department",
                id, Action.DELETE)) {
            throw new AccessDeniedException("Keine Berechtigung, diese Abteilung zu löschen.");
        }
        if (!departmentRepository.existsById(id)) {
            throw new EntityNotFoundException("Löschen fehlgeschlagen: Department " + id + " existiert nicht.");
        }
        departmentRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Department getDepartmentById(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Department mit ID " + id + " nicht gefunden."));
    }

    @Transactional(readOnly = true)
    public List<Department> getDepartmentsByCompany(Long companyId, PUser requestingUser) {
        if (!authService.hasPermission(requestingUser,
                "de.hallerweb.enterprise.prioritize.model.company.Company",
                companyId, Action.READ)) {
            throw new AccessDeniedException("Keine Berechtigung, Abteilungen dieser Company zu lesen.");
        }
        return departmentRepository.findByCompany_Id(companyId);
    }

    @Transactional(readOnly = true)
    public List<Department> searchDepartments(String phrase) {
        return departmentRepository.findByNameContainingIgnoreCase(phrase);
    }

    @Transactional(readOnly = true)
    public Department getDepartmentByName(String name) {
        return departmentRepository.findByName(name)
                .orElseThrow(() -> new EntityNotFoundException("Department mit Name '" + name + "' nicht gefunden."));
    }

    public void renameDepartment(Long id, String newName) {
        Department dept = getDepartmentById(id);
        dept.setName(newName);
    }
}
