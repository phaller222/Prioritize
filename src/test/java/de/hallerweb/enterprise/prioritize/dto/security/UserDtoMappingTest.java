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
import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit tests (no Spring context) for the request/response DTO mapping of the user endpoints. Pins that
 * the request never carries a password (passwordless-by-design) or a JPA id, that it builds the entity via
 * the no-arg constructor (so builder defaults don't fill in absent fields, keeping create/PUT/PATCH
 * behaviour unchanged), and that the response never exposes credentials.
 */
class UserDtoMappingTest {

    @Test
    @DisplayName("UserRequest.toUser maps supplied fields, never a password or id")
    void toUserFull() {
        UserRequest req = new UserRequest("peter", "Haller", "Peter", "p@x.de", "trainer",
                null, PUser.Gender.MALE,
                new AddressRequest("Main St", "7", null, "12345", "Town", "DE", null, null, null),
                true, true);
        PUser u = req.toUser();

        assertNull(u.getId());
        assertNull(u.getPassword(), "a request must never carry a password");
        assertEquals("peter", u.getUsername());
        assertEquals("Haller", u.getName());
        assertEquals("Peter", u.getFirstname());
        assertEquals("p@x.de", u.getEmail());
        assertEquals("trainer", u.getOccupation());
        assertEquals(PUser.Gender.MALE, u.getGender());
        assertEquals("Town", u.getAddress().getCity());
        assertTrue(u.isAdmin());
        assertTrue(u.isActive());
    }

    @Test
    @DisplayName("UserRequest.toUser applies admin/active only when supplied (else the ctor default stands)")
    void toUserFlagsOnlyWhenSupplied() {
        PUser fresh = new PUser();
        PUser absent = new UserRequest("peter", null, null, null, null, null, null, null, null, null).toUser();
        // absent flags leave whatever the no-arg constructor yields (mirrors the previous Jackson path)
        assertEquals(fresh.isAdmin(), absent.isAdmin());
        assertEquals(fresh.isActive(), absent.isActive());
        assertNull(absent.getAddress());

        // explicitly supplied flags are honoured
        PUser off = new UserRequest("peter", null, null, null, null, null, null, null, false, false).toUser();
        assertFalse(off.isAdmin());
        assertFalse(off.isActive());
    }

    @Test
    @DisplayName("UserDTO.from carries the profile + department id, never the password/roles")
    void userDtoFrom() {
        Department dept = new Department();
        dept.setId(3L);
        PUser u = new PUser();
        u.setId(8L);
        u.setUsername("peter");
        u.setName("Haller");
        u.setEmail("p@x.de");
        u.setGender(PUser.Gender.MALE);
        u.setDepartment(dept);
        u.setAdmin(true);
        u.setActive(true);

        UserDTO dto = UserDTO.from(u);
        assertEquals(8L, dto.id());
        assertEquals("peter", dto.username());
        assertEquals("Haller", dto.name());
        assertEquals("p@x.de", dto.email());
        assertEquals(PUser.Gender.MALE, dto.gender());
        assertEquals(3L, dto.departmentId());
        assertTrue(dto.admin());
        assertTrue(dto.active());

        PUser loose = new PUser();
        loose.setId(1L);
        assertNull(UserDTO.from(loose).departmentId());
    }
}
