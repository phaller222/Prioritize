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

package de.hallerweb.enterprise.prioritize.service.security;

import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.security.PermissionRecord;
import de.hallerweb.enterprise.prioritize.repository.company.DepartmentRepository;
import de.hallerweb.enterprise.prioritize.repository.security.PermissionRecordRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CRUD service for standalone {@link PermissionRecord}s (the C/R/U/D + target definition
 * of a single permission). Permissions owned by a {@link de.hallerweb.enterprise.prioritize.model.security.Role}
 * are managed through {@link RoleService}; this service handles records directly, e.g. for the
 * personal permissions of a user or the admin permission editor.
 *
 * <p>Like {@link UserService}, this is an admin-console service and therefore carries no
 * per-call authorization parameters — access is gated by the admin login in front of the UI.</p>
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PermissionRecordService {

    private final PermissionRecordRepository permissionRepository;
    private final DepartmentRepository departmentRepository;

    @Transactional(readOnly = true)
    public List<PermissionRecord> getAllPermissions() {
        return permissionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public PermissionRecord getPermissionById(Long id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("PermissionRecord with id " + id + " not found."));
    }

    @Transactional(readOnly = true)
    public List<PermissionRecord> getPermissionsByDepartment(Long departmentId) {
        return permissionRepository.findByDepartment_Id(departmentId);
    }

    @Transactional(readOnly = true)
    public List<PermissionRecord> getPermissionsByType(String absoluteObjectType) {
        return permissionRepository.findByAbsoluteObjectType(absoluteObjectType);
    }

    /**
     * Persists a new permission record. If {@code departmentId} is non-null the record is scoped
     * to that department; {@code null} leaves it unscoped (a global permission definition).
     */
    public PermissionRecord createPermission(PermissionRecord permission, Long departmentId) {
        permission.setDepartment(resolveDepartment(departmentId));
        return permissionRepository.save(permission);
    }

    /**
     * Updates the C/R/U/D flags and the target of an existing record. The {@code objectName} is
     * kept in sync automatically by the entity's {@code @PreUpdate} hook.
     */
    public PermissionRecord updatePermission(Long id, PermissionRecord details, Long departmentId) {
        PermissionRecord existing = getPermissionById(id);
        existing.setCreatePermission(details.isCreatePermission());
        existing.setReadPermission(details.isReadPermission());
        existing.setUpdatePermission(details.isUpdatePermission());
        existing.setDeletePermission(details.isDeletePermission());
        existing.setAbsoluteObjectType(details.getAbsoluteObjectType());
        existing.setObjectId(details.getObjectId());
        existing.setDepartment(resolveDepartment(departmentId));
        return permissionRepository.save(existing);
    }

    public void deletePermission(Long id) {
        if (!permissionRepository.existsById(id)) {
            throw new EntityNotFoundException("Delete failed: permission record " + id + " does not exist.");
        }
        permissionRepository.deleteById(id);
    }

    private Department resolveDepartment(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new EntityNotFoundException("Department with id " + departmentId + " not found."));
    }
}
