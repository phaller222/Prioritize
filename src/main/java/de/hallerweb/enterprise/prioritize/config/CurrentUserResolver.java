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

package de.hallerweb.enterprise.prioritize.config;

import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Central bridge between Spring Security {@link Authentication} and the
 * domain entity {@link PUser}.
 * <p>
 * In dev mode (Basic Auth), {@code auth.getName()} returns the username.
 * For the later Keycloak mode (OAuth2 Resource Server), the JWT branch is
 * added here, which evaluates the {@code preferred_username} claim.
 * <p>
 * Per project convention, controllers determine the {@link PUser} via this
 * resolver and pass it explicitly to the services. The actual
 * permission check remains in the service.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final UserService userService;

    public PUser resolve(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Kein Benutzer angemeldet.");
        }

        String username;
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            username = jwt.getClaimAsString("preferred_username");
            if (username == null || username.isBlank()) {
                throw new AccessDeniedException("Kein preferred_username im Token.");
            }
        } else {
            // Basic Auth: principal is UserDetails, getName() returns the username.
            username = auth.getName();
        }

        return userService.findUserByUsername(username);
    }
}
