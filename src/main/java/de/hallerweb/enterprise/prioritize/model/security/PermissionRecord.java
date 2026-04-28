package de.hallerweb.enterprise.prioritize.model.security;


import de.hallerweb.enterprise.prioritize.model.PObject;
import de.hallerweb.enterprise.prioritize.model.company.Department;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
public class PermissionRecord extends PObject implements PAuthorizedObject {

    private boolean createPermission;
    private boolean readPermission;
    private boolean updatePermission;
    private boolean deletePermission;

    private String absoluteObjectType;
    private String objectName;
    private int objectId;

    @ManyToOne(fetch = FetchType.EAGER)
    private Department department;

    // Logik zur Namensextraktion beim Setzen des Typs
    public void setAbsoluteObjectType(String absoluteObjectType) {
        this.absoluteObjectType = absoluteObjectType;
        if (absoluteObjectType != null && absoluteObjectType.contains(".")) {
            this.objectName = absoluteObjectType.substring(absoluteObjectType.lastIndexOf('.') + 1);
        } else {
            this.objectName = absoluteObjectType;
        }
    }
}