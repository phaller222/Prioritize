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

package de.hallerweb.enterprise.prioritize.dto.security;

import de.hallerweb.enterprise.prioritize.dto.address.AddressDTO;
import de.hallerweb.enterprise.prioritize.model.security.PUser;

import java.time.LocalDateTime;

/**
 * Flat, transport-safe view of a {@link PUser}. Carries the profile scalars, the eager {@code address} and
 * the owning department by id. Deliberately omits the security-sensitive and lazy relations — password and
 * apiKey (already {@code @JsonIgnore} on the entity), roles, personal permissions and skills — so a user
 * never leaks credentials or drags its authorization graph onto the wire. Roles/permissions are managed
 * through their own endpoints; a user's skills via {@code GET /users/{id}/skills}.
 *
 * @author peter haller
 */
public record UserDTO(Long id,
                      String username,
                      String name,
                      String firstname,
                      String email,
                      String occupation,
                      LocalDateTime lastLogin,
                      LocalDateTime dateOfBirth,
                      PUser.Gender gender,
                      AddressDTO address,
                      Long departmentId,
                      boolean admin,
                      boolean active) {

    /** Maps an entity to its DTO. Reads the lazy department id only (safe under open-in-view). */
    public static UserDTO from(PUser user) {
        return new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getName(),
                user.getFirstname(),
                user.getEmail(),
                user.getOccupation(),
                user.getLastLogin(),
                user.getDateOfBirth(),
                user.getGender(),
                AddressDTO.from(user.getAddress()),
                user.getDepartment() != null ? user.getDepartment().getId() : null,
                user.isAdmin(),
                user.isActive());
    }
}
