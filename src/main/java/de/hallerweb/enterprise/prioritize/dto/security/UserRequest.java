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

import de.hallerweb.enterprise.prioritize.dto.address.AddressRequest;
import de.hallerweb.enterprise.prioritize.model.security.PUser;

/**
 * Request body for creating or updating a {@link PUser}. Carries the profile base data only. It deliberately
 * has NO password field: passwords are {@code @JsonIgnore} on the entity and never travel through the JSON
 * API by design (a REST-created record is passwordless; login-able users are made via the admin GUI). It
 * also omits roles, personal permissions and the department — those are managed through dedicated,
 * elevated-authorization endpoints. {@code admin}/{@code active} are boxed so a PATCH leaves them untouched
 * when absent.
 *
 * @author peter haller
 */
public record UserRequest(String username,
                          String name,
                          String firstname,
                          String email,
                          String occupation,
                          java.time.LocalDateTime dateOfBirth,
                          PUser.Gender gender,
                          AddressRequest address,
                          Boolean admin,
                          Boolean active) {

    /**
     * Builds a plain, id-less {@link PUser} carrying exactly the supplied fields. Uses the no-arg
     * constructor and setters on purpose — NOT the Lombok builder — so the {@code @Builder.Default} values
     * (e.g. {@code active=true}, the collection defaults) do not fill in fields the request left out. This
     * mirrors what Jackson used to deserialize straight into the entity, keeping the create/PUT/PATCH
     * semantics unchanged; {@code admin}/{@code active} are only applied when explicitly supplied.
     */
    public PUser toUser() {
        PUser user = new PUser();
        user.setUsername(username);
        user.setName(name);
        user.setFirstname(firstname);
        user.setEmail(email);
        user.setOccupation(occupation);
        user.setDateOfBirth(dateOfBirth);
        user.setGender(gender);
        user.setAddress(address != null ? address.toAddress() : null);
        if (admin != null) {
            user.setAdmin(admin);
        }
        if (active != null) {
            user.setActive(active);
        }
        return user;
    }
}
