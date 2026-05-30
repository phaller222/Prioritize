package de.hallerweb.enterprise.prioritize.model.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.hallerweb.enterprise.prioritize.model.PActor;
import de.hallerweb.enterprise.prioritize.model.address.Address;
import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.skill.SkillRecord;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@DiscriminatorValue("USER")
public class PUser extends PActor implements PAuthorizedObject {

    @Override
    public String toString() {
        return "PUser{username='" + username + "'}"; // Nur Basisfelder!
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PUser)) return false;
        PUser pUser = (PUser) o;
        return username != null && username.equals(pUser.username);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }


    public enum Gender {MALE, FEMALE, OTHER, TECHNICAL_USER}

    @ToString.Include
    @EqualsAndHashCode.Include
    private String username;

    private String name;
    private String firstname;
    private String email;

    @Getter(AccessLevel.NONE) // Passwort sollte nicht einfach per getPassword() im JSON landen
    @JsonIgnore
    private String password;

    private String occupation;
    @JsonIgnore
    private String apiKey;

    private LocalDateTime lastLogin;
    private LocalDateTime dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = true)
    private Gender gender;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    private Address address;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    @JsonIgnore
    private Department department;

    // --- Rollen & Berechtigungen ---

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default // Damit der Builder kein null-Set erzeugt
    @JsonIgnore
    private Set<Role> roles = new HashSet<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @Builder.Default
    @JsonIgnore
    private Set<PermissionRecord> personalPermissions = new HashSet<>();

    // --- Skills ---

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<SkillRecord> skills = new HashSet<>();

    private boolean admin;

    @Builder.Default
    private boolean active = true;

    public boolean isAdmin() {
        return admin;
    }

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