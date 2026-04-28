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

    @ManyToMany(mappedBy = "roles") // Die Gegenseite liegt im PUser
    private Set<PUser> users;

    @ManyToOne // Geändert von OneToOne auf ManyToOne, falls eine Abteilung mehrere Rollen haben kann
    private Department department;

    public void addPermission(PermissionRecord rec) {
        this.permissions.add(rec);
    }
}