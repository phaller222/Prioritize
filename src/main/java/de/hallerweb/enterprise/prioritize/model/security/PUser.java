package de.hallerweb.enterprise.prioritize.model.security;

import de.hallerweb.enterprise.prioritize.model.PActor;
import de.hallerweb.enterprise.prioritize.model.company.Address;
import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.skill.SkillRecord;
import jakarta.persistence.*;
import lombok.*;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "p_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true) // Wichtig: ID-basiert vergleichen
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class PUser extends PActor implements PAuthorizedObject {

    public enum Gender { MALE, FEMALE, OTHER, TECHNICAL_USER }

    @ToString.Include
    @EqualsAndHashCode.Include
    private String username;

    private String name;
    private String firstname;
    private String email;

    @Getter(AccessLevel.NONE) // Passwort sollte nicht einfach per getPassword() im JSON landen
    private String password;

    private String occupation;
    private String apiKey;

    @Temporal(TemporalType.TIMESTAMP)
    private Date lastLogin;

    @Temporal(TemporalType.DATE)
    private Date dateOfBirth;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    private Address address;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    // --- Rollen & Berechtigungen ---

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default // Damit der Builder kein null-Set erzeugt
    private Set<Role> roles = new HashSet<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    @Builder.Default
    private Set<PermissionRecord> personalPermissions = new HashSet<>();

    // --- Skills ---

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<SkillRecord> skills = new HashSet<>();

    // --- Hilfsmethoden für bequemes Handling ---

    public void addRole(Role role) {
        this.roles.add(role);
    }

    public void addPersonalPermission(PermissionRecord record) {
        this.personalPermissions.add(record);
    }

    // Manuelle Getter für das Passwort (falls gebraucht),
    // aber wir verhindern das versehentliche Senden an Clients
    public String getPassword() {
        return this.password;
    }
}