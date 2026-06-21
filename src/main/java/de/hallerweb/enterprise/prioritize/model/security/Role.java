package de.hallerweb.enterprise.prioritize.model.security;

import de.hallerweb.enterprise.prioritize.model.PObject;
import de.hallerweb.enterprise.prioritize.model.company.Department;
import jakarta.persistence.*;
import lombok.*;

import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class Role extends PObject implements PAuthorizedObject {

    @ToString.Include
    @EqualsAndHashCode.Include
    private String name;

    private String description;

    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PermissionRecord> permissions;

    @ManyToMany(mappedBy = "roles") // The opposite side resides in PUser
    private Set<PUser> users;

    @ManyToOne // Changed from OneToOne to ManyToOne, in case a department can have multiple roles
    private Department department;

    public void addPermission(PermissionRecord rec) {
        this.permissions.add(rec);
    }
}