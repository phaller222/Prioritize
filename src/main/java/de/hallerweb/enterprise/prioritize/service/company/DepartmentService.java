/*
 * Copyright 2026 Peter Michael Haller and contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
            throw new AccessDeniedException("No permission to create departments in this company.");
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
            throw new AccessDeniedException("No permission to update this department.");
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
            throw new AccessDeniedException("No permission to delete this department.");
        }
        if (!departmentRepository.existsById(id)) {
            throw new EntityNotFoundException("Delete failed: department " + id + " does not exist.");
        }
        departmentRepository.deleteById(id);
    }

    /**
     * Loads a department <b>without</b> checking anything. Internal lookup used by the services and by
     * {@link AuthorizationService} itself while resolving hierarchical permissions — which is why the
     * READ check cannot live here: the check would call back into this method and recurse forever.
     * Callers acting on behalf of a user want {@link #getDepartment(Long, PUser)}.
     */
    @Transactional(readOnly = true)
    public Department getDepartmentById(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Department with id " + id + " not found."));
    }

    /**
     * Reads a single department on behalf of a user, gated by {@link Action#READ} — the authorized
     * counterpart to {@link #getDepartmentById}.
     * <p>
     * Until 1.4.0 the REST read was ungated while the listing underneath a company was not, so a caller
     * denied the company's department list could still read every one of those departments one id at a
     * time. Ids are sequential, so that was the whole list with extra steps.
     */
    @Transactional(readOnly = true)
    public Department getDepartment(Long id, PUser requestingUser) {
        Department department = getDepartmentById(id);
        if (!authService.hasPermission(requestingUser, department, Action.READ)) {
            throw new AccessDeniedException("No permission to read this department.");
        }
        return department;
    }

    @Transactional(readOnly = true)
    public List<Department> getDepartmentsByCompany(Long companyId, PUser requestingUser) {
        if (!authService.hasPermission(requestingUser,
                "de.hallerweb.enterprise.prioritize.model.company.Company",
                companyId, Action.READ)) {
            throw new AccessDeniedException("No permission to read departments of this company.");
        }
        return departmentRepository.findByCompany_Id(companyId);
    }

    /**
     * Reads the department's address as a detached copy, initialized inside this transaction (see
     * {@link CompanyService#getMainAddress}). Returns {@code null} if the department has no address.
     */
    @Transactional(readOnly = true)
    public Address getAddress(Long id, PUser requestingUser) {
        if (!authService.hasPermission(requestingUser,
                "de.hallerweb.enterprise.prioritize.model.company.Department",
                id, Action.READ)) {
            throw new AccessDeniedException("No permission to read this department.");
        }
        return Address.copyOf(getDepartmentById(id).getAddress());
    }

    /**
     * Returns every department across all companies. Intended for admin-console screens (e.g. the role
     * editor's optional department scope selector); carries no per-call authorization, mirroring
     * {@link #getDepartmentById} and the sibling admin services.
     */
    @Transactional(readOnly = true)
    public List<Department> getAllDepartments() {
        return departmentRepository.findAll();
    }

    /**
     * Returns the departments the caller may read, across all companies — the entry point for a client
     * that has no company id yet.
     * <p>
     * This exists because departments were only reachable through {@code /companies/{id}/departments},
     * and a fresh installation has <em>no</em> company at all: {@code InitializationService} seeds an
     * admin and a default department, but never a company. A client therefore had no way to discover
     * the department id it needs for everything hanging below it (resource groups, users, resources).
     * <p>
     * Filters on {@link Action#READ} per department rather than returning {@link #getAllDepartments()}
     * unchanged: a flat listing is exactly the shape that turns "you may read this one" into "here is
     * every department in the installation". Admins short-circuit to the full list inside
     * {@link AuthorizationService#hasPermission}. The entities are already loaded, so the check runs
     * against the in-memory permission records rather than re-reading each department.
     */
    @Transactional(readOnly = true)
    public List<Department> getReadableDepartments(PUser requestingUser) {
        return departmentRepository.findAll().stream()
                .filter(department -> authService.hasPermission(requestingUser, department, Action.READ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Department> searchDepartments(String phrase) {
        return departmentRepository.findByNameContainingIgnoreCase(phrase);
    }

    @Transactional(readOnly = true)
    public Department getDepartmentByName(String name) {
        return departmentRepository.findByName(name)
                .orElseThrow(() -> new EntityNotFoundException("Department with name '" + name + "' not found."));
    }

    public void renameDepartment(Long id, String newName) {
        Department dept = getDepartmentById(id);
        dept.setName(newName);
    }
}
