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
 * Per project convention, controllers receive the {@link PUser} and pass it explicitly to the
 * services; the actual permission check remains in the service. Controllers do not call this class
 * directly — they declare a {@link AuthenticatedUser}-annotated parameter and
 * {@link AuthenticatedUserArgumentResolver} calls in here once per request. The Vaadin side uses
 * {@code ui.common.CurrentUser} instead, which reads the {@code SecurityContextHolder} itself.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final UserService userService;

    public PUser resolve(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Kein Benutzer angemeldet.");
        }
        return userService.findUserByUsername(usernameOf(auth));
    }

    /**
     * The account name behind an {@link Authentication}, kept here so the JWT special case lives in
     * exactly one place. Under the {@code keycloak} profile {@code auth.getName()} is the token
     * <em>subject</em>, not the login name — reading it directly is the mistake this method exists to
     * prevent (it once shipped in {@code DepartmentController}).
     *
     * @param auth the authentication to read, never {@code null} here
     * @return the username
     * @throws AccessDeniedException if a JWT carries no {@code preferred_username}
     */
    public static String usernameOf(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            String username = jwt.getClaimAsString("preferred_username");
            if (username == null || username.isBlank()) {
                throw new AccessDeniedException("Kein preferred_username im Token.");
            }
            return username;
        }
        // Basic Auth: principal is UserDetails, getName() returns the username.
        return auth.getName();
    }
}
