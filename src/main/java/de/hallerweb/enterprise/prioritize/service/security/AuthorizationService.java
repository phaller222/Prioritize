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

import de.hallerweb.enterprise.prioritize.model.company.Company;
import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.security.Action;
import de.hallerweb.enterprise.prioritize.model.security.PAuthorizedObject;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.security.PermissionRecord;
import de.hallerweb.enterprise.prioritize.model.security.Role;
import de.hallerweb.enterprise.prioritize.service.company.CompanyService; // If present
import de.hallerweb.enterprise.prioritize.service.company.DepartmentService;
import org.hibernate.Hibernate;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Stream;

@Service
@Transactional
public class AuthorizationService {

    private final DepartmentService departmentService;

    // @Lazy prevents circular dependencies in case services need each other
    public AuthorizationService(@Lazy DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    /**
     * OVERLOAD FOR CONTROLLER: Allows the call with class name and ID directly from the API path.
     */
    public boolean hasPermission(PUser user, String absoluteObjectType, Long objectId, Action action) {

        if (user == null) {
            return false; // Whoever is not logged in has no rights!
        }
        if (isAdmin(user)) {
            return true;
        }

        // 1. First check whether a direct PermissionRecord exists for this type + ID
        boolean hasDirect = checkDirectWithStrings(user, absoluteObjectType, objectId, action);
        if (hasDirect) {
            return true;
        }

        // 2. If not directly allowed, we must load the actual object for the hierarchical inheritance
        if (absoluteObjectType.equals("de.hallerweb.enterprise.prioritize.model.company.Department")) {
            Department department = departmentService.getDepartmentById(objectId);
            return hasPermission(user, department, action); // Switches to the hierarchical object check
        }

        // For a Company there is no higher hierarchy (top level)
        return false;
    }

    /**
     * MAIN METHOD: Checks rights directly on a loaded JPA object (incl. inheritance).
     */
    public boolean hasPermission(PUser user, PAuthorizedObject targetObject, Action action) {

        if (user == null || targetObject == null) {
            return false;
        }
        if (isAdmin(user)) {
            return true;
        }

        // Check direct permission on the object
        if (hasDirectPermission(user, targetObject, action)) {
            return true;
        }

        // Hierarchical check (inheritance downward)
        Object unproxiedTarget = Hibernate.unproxy(targetObject);

        if (unproxiedTarget instanceof Department department) {
            Company parentCompany = department.getCompany();
            return hasPermission(user, parentCompany, action);
        }

        if (unproxiedTarget instanceof PUser targetUser) {
            Department parentDepartment = targetUser.getDepartment();
            return hasPermission(user, parentDepartment, action);
        }

        if (unproxiedTarget instanceof Role targetRole) {
            Department parentDepartment = targetRole.getDepartment();
            return hasPermission(user, parentDepartment, action);
        }

        return false;
    }

    /**
     * Internal check for the String-based controller method
     */
    private boolean checkDirectWithStrings(PUser user, String type, Long id, Action action) {
        return Stream.concat(
                user.getRoles().stream().flatMap(role -> role.getPermissions().stream()),
                user.getPersonalPermissions().stream()
        ).anyMatch(record ->
                record.getAbsoluteObjectType().equals(type)
                        && (record.getObjectId() == 0 || record.getObjectId() == id)
                        && hasActionAllowed(record, action)
        );
    }

    private boolean hasDirectPermission(PUser user, PAuthorizedObject targetObject, Action action) {
        return Stream.concat(
                user.getRoles().stream().flatMap(role -> role.getPermissions().stream()),
                user.getPersonalPermissions().stream()
        ).anyMatch(record -> isPermissionMatching(record, targetObject, action));
    }

    /**
     * Whether the user is an administrator — either by the flag on the account or through a role named
     * {@code ADMIN}. Public because the membership-based parts of the system (see {@code ProjectService})
     * decide on admin overrides themselves: they are not expressed as {@link PermissionRecord}s and
     * therefore never pass through {@link #hasPermission}.
     */
    public boolean isAdmin(PUser user) {
        return user.isAdmin() || user.getRoles().stream().anyMatch(
                role -> role.getName().equalsIgnoreCase("ADMIN"));
    }

    private boolean isPermissionMatching(PermissionRecord record, PAuthorizedObject target, Action action) {
        return isMatchingType(record, target)
                && isMatchingInstance(record, target)
                && hasActionAllowed(record, action);
    }

    private boolean isMatchingType(PermissionRecord record, PAuthorizedObject target) {
        return record.getAbsoluteObjectType().equals(Hibernate.unproxy(target).getClass().getCanonicalName());
    }

    private boolean isMatchingInstance(PermissionRecord record, PAuthorizedObject target) {
        return record.getObjectId() == 0 || record.getObjectId() == target.getId();
    }

    private boolean hasActionAllowed(PermissionRecord record, Action action) {
        return switch (action) {
            case CREATE -> record.isCreatePermission();
            case READ -> record.isReadPermission();
            case UPDATE -> record.isUpdatePermission();
            case DELETE -> record.isDeletePermission();
        };
    }
}