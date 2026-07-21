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

    /**
     * Login name, unique across all accounts — including deactivated ones, which keep their name
     * because a delete only clears {@link #active}. Enforced in {@code UserService} (case-insensitive,
     * so {@code Admin} cannot shadow {@code admin}); the column constraint is the second line of
     * defence and only reaches an existing database once its schema is regenerated.
     */
    @Column(unique = true)
    @ToString.Include
    @EqualsAndHashCode.Include
    private String username;

    private String name;
    private String firstname;
    private String email;

    @Getter(AccessLevel.NONE) // Password should not simply end up in the JSON via getPassword()
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

    // --- Roles & permissions ---

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default // So that the builder does not create a null set
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

    // --- Helper methods for convenient handling ---

    public void addRole(Role role) {
        this.roles.add(role);
    }

    public void addPersonalPermission(PermissionRecord record) {
        this.personalPermissions.add(record);
    }

    // Manual getters for the password (if needed),
    // but we prevent it from being sent to clients accidentally
    public String getPassword() {
        return this.password;
    }
}