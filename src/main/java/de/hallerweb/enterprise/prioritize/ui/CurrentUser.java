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

package de.hallerweb.enterprise.prioritize.ui;

import de.hallerweb.enterprise.prioritize.config.CurrentUserResolver;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * UI-side counterpart of the REST {@link org.springframework.web.bind.support.WebArgumentResolver}
 * pattern: resolves the logged-in {@link PUser} for Vaadin views. Where controllers receive the
 * {@link org.springframework.security.core.Authentication} as a method argument, a Vaadin view runs
 * inside the servlet request that handles the UI event, so the authentication is read from the
 * {@link SecurityContextHolder} and delegated to the shared {@link CurrentUserResolver}. This keeps
 * the Authentication&nbsp;&rarr;&nbsp;{@link PUser} mapping (and the later Keycloak branch) in one place.
 *
 * @author peter haller
 */
@Component
@RequiredArgsConstructor
public class CurrentUser {

    private final CurrentUserResolver resolver;

    /**
     * @return the {@link PUser} for the current security context
     * @throws org.springframework.security.access.AccessDeniedException if no user is authenticated
     */
    public PUser require() {
        return resolver.resolve(SecurityContextHolder.getContext().getAuthentication());
    }
}
