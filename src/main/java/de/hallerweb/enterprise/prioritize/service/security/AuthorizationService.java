package de.hallerweb.enterprise.prioritize.service.security;

import de.hallerweb.enterprise.prioritize.model.security.Action;
import de.hallerweb.enterprise.prioritize.model.security.PAuthorizedObject;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.security.PermissionRecord;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {

    // Jetzt mit Action Enum statt String
    public boolean hasPermission(PUser user, PAuthorizedObject targetObject, Action action) {
        if (isAdmin(user)) {
            return true;
        }

        return user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .anyMatch(record -> isPermissionMatching(record, targetObject, action));
    }

    private boolean isAdmin(PUser user) {
        return user.getRoles().stream()
                .anyMatch(role -> role.getName().equalsIgnoreCase("ADMIN"));
    }

    private boolean isPermissionMatching(PermissionRecord record, PAuthorizedObject target, Action action) {
        return isMatchingType(record, target)
                && isMatchingInstance(record, target)
                && hasActionAllowed(record, action);
    }

    private boolean isMatchingType(PermissionRecord record, PAuthorizedObject target) {
        // Nutzt den Canonical Name (z.B. de.hallerweb...Resource)
        return record.getAbsoluteObjectType().equals(target.getClass().getCanonicalName());
    }

    private boolean isMatchingInstance(PermissionRecord record, PAuthorizedObject target) {
        // 0 bedeutet "alle Instanzen dieses Typs"
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