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
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.security.PermissionRecord;
import de.hallerweb.enterprise.prioritize.model.security.Role;
import de.hallerweb.enterprise.prioritize.repository.company.DepartmentRepository;
import de.hallerweb.enterprise.prioritize.repository.security.RoleRepository;
import de.hallerweb.enterprise.prioritize.repository.security.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

/**
 * CRUD service for {@link Role}s, including assignment to a {@link Department} and management of a
 * role's owned {@link PermissionRecord}s (the backing operations for the admin C/R/U/D permission
 * matrix). A role's permissions are a cascade-all / orphan-removal collection, so adding/removing a
 * record through this service persists or deletes it along with the role.
 *
 * <p>Like {@link UserService}, this is an admin-console service and therefore carries no per-call
 * authorization parameters — access is gated by the admin login in front of the UI.</p>
 */
@Service
@Transactional
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Role getRoleById(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Role with id " + id + " not found."));
    }

    @Transactional(readOnly = true)
    public Role getRoleByName(String name) {
        return roleRepository.findByName(name)
                .orElseThrow(() -> new EntityNotFoundException("Role with name '" + name + "' not found."));
    }

    @Transactional(readOnly = true)
    public List<Role> searchRoles(String phrase) {
        return roleRepository.findByNameContainingIgnoreCase(phrase);
    }

    @Transactional(readOnly = true)
    public List<Role> getRolesByDepartment(Long departmentId) {
        return roleRepository.findByDepartment_Id(departmentId);
    }

    /**
     * Persists a new role. If {@code departmentId} is non-null the role is scoped to that
     * department; {@code null} leaves it unscoped. The permission collection is initialized so the
     * role is immediately ready for the permission matrix.
     */
    public Role createRole(Role role, Long departmentId) {
        if (role.getPermissions() == null) {
            role.setPermissions(new HashSet<>());
        }
        role.setDepartment(resolveDepartment(departmentId));
        return roleRepository.save(role);
    }

    /**
     * Updates a role's base fields (name, description) and its department assignment. The permission
     * collection and user assignments are left untouched here — use {@link #addPermissionToRole} /
     * {@link #removePermissionFromRole} for the matrix.
     */
    public Role updateRole(Long id, Role details, Long departmentId) {
        Role existing = getRoleById(id);
        existing.setName(details.getName());
        existing.setDescription(details.getDescription());
        existing.setDepartment(resolveDepartment(departmentId));
        return roleRepository.save(existing);
    }

    /**
     * Deletes a role. The role is first detached from every user that holds it (the {@code user_roles}
     * join table is owned by {@link PUser}, so the association must be cleared from the owning side
     * before the role row can be removed), then deleted along with its owned permission records. The
     * holders are queried authoritatively from the join table rather than via the role's inverse
     * {@code users} collection, which may be stale or unloaded in the current session.
     */
    public void deleteRole(Long id) {
        Role role = getRoleById(id);
        for (PUser user : userRepository.findByRoles_Id(id)) {
            user.getRoles().remove(role);
            userRepository.save(user);
        }
        roleRepository.delete(role);
    }

    /**
     * Adds a permission record to a role. Thanks to cascade-all the record is persisted together
     * with the role.
     */
    public Role addPermissionToRole(Long roleId, PermissionRecord permission) {
        Role role = getRoleById(roleId);
        if (role.getPermissions() == null) {
            role.setPermissions(new HashSet<>());
        }
        role.getPermissions().add(permission);
        return roleRepository.save(role);
    }

    /**
     * Removes a permission record from a role. Thanks to orphan removal the detached record is
     * deleted from the database.
     */
    public Role removePermissionFromRole(Long roleId, Long permissionId) {
        Role role = getRoleById(roleId);
        if (role.getPermissions() != null) {
            role.getPermissions().removeIf(p -> p.getId().equals(permissionId));
        }
        return roleRepository.save(role);
    }

    private Department resolveDepartment(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new EntityNotFoundException("Department with id " + departmentId + " not found."));
    }
}
