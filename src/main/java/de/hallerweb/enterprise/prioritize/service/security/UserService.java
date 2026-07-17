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

import de.hallerweb.enterprise.prioritize.model.address.Address;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.security.Role;
import de.hallerweb.enterprise.prioritize.repository.security.RoleRepository;
import de.hallerweb.enterprise.prioritize.repository.security.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public List<PUser> getAllUsers() {
        return userRepository.findAll().stream()
                .filter(PUser::isActive)
                .toList();
    }

    public PUser createUser(PUser user) {
        // Encrypt the password before it goes into the DB
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }


    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Looks up the PUser in your database
        PUser pUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // Changes PUser to a Spring-Security-UserDetails Object
        return org.springframework.security.core.userdetails.User.builder()
                .username(pUser.getUsername())
                .password(pUser.getPassword()) // The password from the DB (BCrypt-encoded)
                .authorities(pUser.isAdmin() ? "ROLE_ADMIN" : "ROLE_USER")
                .disabled(!pUser.isActive()) // Inactive users are blocked by Spring Security
                .build();
    }


    public PUser findUserByUsername(String username) {
        PUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("User " + username + " not found"));
        if (!user.isActive()) {
            throw new NoSuchElementException("User " + username + " not found");
        }
        return user;
    }

    @Transactional
    public PUser updateUser(PUser user) {
        if (!userRepository.existsById(user.getId())) {
            throw new NoSuchElementException("User with id " + user.getId() + " does not exist.");
        }
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public PUser getUserById(Long id) {
        PUser user = userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User with id " + id + " not found"));
        if (!user.isActive()) {
            throw new NoSuchElementException("User with id " + id + " not found");
        }
        return user;
    }

    @Transactional
    public PUser partialUpdateUser(Long id, PUser patch) {
        PUser existing = getUserById(id);

        // Only non-critical fields modifiable via PATCH
        if (patch.getName() != null) existing.setName(patch.getName());
        if (patch.getFirstname() != null) existing.setFirstname(patch.getFirstname());
        if (patch.getEmail() != null) existing.setEmail(patch.getEmail());
        if (patch.getOccupation() != null) existing.setOccupation(patch.getOccupation());
        if (patch.getGender() != null) existing.setGender(patch.getGender());
        if (patch.getDateOfBirth() != null) existing.setDateOfBirth(patch.getDateOfBirth());
        if (patch.getAddress() != null) existing.setAddress(patch.getAddress());

        // Only change the password if explicitly supplied – and then encrypt it
        if (patch.getPassword() != null && !patch.getPassword().isBlank()) {
            existing.setPassword(passwordEncoder.encode(patch.getPassword()));
        }

        // Roles, permissions and the admin flag are never modifiable via PATCH!
        // That requires dedicated endpoints with elevated authorization.

        return userRepository.save(existing);
    }

    /**
     * Reads the user's address as a detached copy, initialized inside this transaction (see
     * {@link de.hallerweb.enterprise.prioritize.service.company.CompanyService#getMainAddress}).
     * Returns {@code null} if the user has no address.
     */
    @Transactional(readOnly = true)
    public Address getAddress(Long id) {
        PUser user = userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User with id " + id + " not found"));
        return Address.copyOf(user.getAddress());
    }

    @Transactional
    public void deactivateUser(Long id) {
        PUser user = userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User with id " + id + " not found"));
        if (user.isAdmin()) {
            throw new IllegalArgumentException("Admin users cannot be deactivated.");
        }
        user.setActive(false);
    }

    /**
     * Returns the ids of the roles currently assigned to the user. Because {@code PUser.roles} is a lazy
     * collection, this must be read inside a transaction — the caller (e.g. the admin role selector in the
     * user form) uses the ids to pre-select role items loaded separately, rather than touching the lazy
     * association off a detached entity.
     */
    @Transactional(readOnly = true)
    public Set<Long> getRoleIds(Long userId) {
        PUser user = getUserById(userId);
        return user.getRoles().stream().map(Role::getId).collect(Collectors.toSet());
    }

    /**
     * Replaces the user's assigned roles with exactly the roles identified by {@code roleIds} (a full
     * replace: roles not in the set are unassigned, new ones are added). This is the dedicated,
     * elevated-authorization role-assignment operation deliberately excluded from {@link #partialUpdateUser};
     * like the rest of this admin-console service it carries no per-call authorization parameter and is gated
     * by the admin login in front of the UI. {@code user_roles} is owned by {@link PUser}, so setting the
     * managed collection and saving persists the join rows. A {@code null} or empty set clears all roles.
     */
    @Transactional
    public PUser setRoles(Long userId, Set<Long> roleIds) {
        PUser user = getUserById(userId);
        Set<Role> roles = new HashSet<>();
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                roles.add(roleRepository.findById(roleId)
                        .orElseThrow(() -> new EntityNotFoundException("Role with id " + roleId + " not found.")));
            }
        }
        user.setRoles(roles);
        return userRepository.save(user);
    }
}