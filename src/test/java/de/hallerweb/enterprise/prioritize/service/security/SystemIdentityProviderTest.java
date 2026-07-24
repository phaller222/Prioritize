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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.hallerweb.enterprise.prioritize.model.security.PUser;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link SystemIdentityProvider}: it returns the seeded system principal and fails
 * loudly if it was never seeded.
 *
 * @author peter haller
 */
class SystemIdentityProviderTest {

    private UserService userService;
    private SystemIdentityProvider provider;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        provider = new SystemIdentityProvider(userService);
    }

    @Test
    @DisplayName("get returns the seeded system principal")
    void get_returnsSeededPrincipal() {
        PUser system = new PUser();
        system.setUsername(SystemIdentityProvider.SYSTEM_USERNAME);
        when(userService.findOptionalByUsername(SystemIdentityProvider.SYSTEM_USERNAME))
                .thenReturn(Optional.of(system));

        assertSame(system, provider.get());
    }

    @Test
    @DisplayName("get fails loudly when the principal was never seeded")
    void get_missing_throws() {
        when(userService.findOptionalByUsername(SystemIdentityProvider.SYSTEM_USERNAME))
                .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> provider.get());
    }
}
