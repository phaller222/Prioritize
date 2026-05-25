package de.hallerweb.enterprise.prioritize.service.security;

import de.hallerweb.enterprise.prioritize.model.company.Company;
import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.security.Action;
import de.hallerweb.enterprise.prioritize.model.security.PAuthorizedObject;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.security.PermissionRecord;
import de.hallerweb.enterprise.prioritize.model.security.Role;
import de.hallerweb.enterprise.prioritize.service.company.CompanyService; // Falls vorhanden
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

    // @Lazy verhindert zirkuläre Abhängigkeiten, falls Services sich gegenseitig brauchen
    public AuthorizationService(@Lazy DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    /**
     * ÜBERLADUNG FÜR CONTROLLER: Erlaubt den Aufruf mit Klassenname und ID direkt aus dem API-Pfad.
     */
    public boolean hasPermission(PUser user, String absoluteObjectType, int objectId, Action action) {

        if (user == null) {
            return false; // Wer nicht eingeloggt ist, hat keine Rechte!
        }
        if (isAdmin(user)) {
            return true;
        }

        // 1. Erst prüfen, ob ein direkter PermissionRecord für diesen Typ + ID existiert
        boolean hasDirect = checkDirectWithStrings(user, absoluteObjectType, objectId, action);
        if (hasDirect) {
            return true;
        }

        // 2. Wenn nicht direkt erlaubt, müssen wir für die hierarchische Vererbung das echte Objekt laden
        if (absoluteObjectType.equals("de.hallerweb.enterprise.prioritize.model.company.Department")) {
            Department department = departmentService.getDepartmentById(objectId);
            return hasPermission(user, department, action); // Wechselt in die hierarchische Objekt-Prüfung
        }

        // Für eine Company gibt es keine höhere Hierarchie (oberste Ebene)
        return false;
    }

    /**
     * HAUPTMETHODE: Prüft Rechte direkt auf einem geladenen JPA-Objekt (inkl. Vererbung).
     */
    public boolean hasPermission(PUser user, PAuthorizedObject targetObject, Action action) {

        if (user == null || targetObject == null) {
            return false;
        }
        if (isAdmin(user)) {
            return true;
        }

        // Direkte Berechtigung auf das Objekt prüfen
        if (hasDirectPermission(user, targetObject, action)) {
            return true;
        }

        // Hierarchische Prüfung (Vererbung nach unten)
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
     * Interne Prüfung für die String-basierte Controller-Methode
     */
    private boolean checkDirectWithStrings(PUser user, String type, int id, Action action) {
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

    private boolean isAdmin(PUser user) {
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