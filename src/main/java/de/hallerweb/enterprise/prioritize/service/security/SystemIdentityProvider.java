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

import de.hallerweb.enterprise.prioritize.model.security.PUser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the platform's own principal — the single, defined identity under which the inbound process
 * facade acts when a BPMN process, rather than a logged-in user, drives a platform operation.
 * <p>
 * The principal is <b>seeded once at startup</b> (see {@code InitializationService}) and is deliberately
 * shaped so it can only ever be an attribution, never a login:
 * <ul>
 *   <li><b>deactivated</b> ({@code active == false}) — Spring Security reports it as {@code disabled},
 *       so no password ever authenticates it, and it is filtered out of the user lists the admin GUI
 *       shows;</li>
 *   <li><b>non-admin</b> — it holds exactly the permissions the platform chooses to lend to processes
 *       and nothing more. That is the whole point of giving processes an explicit identity instead of a
 *       user-less back door: what a process may do is a checked property of this principal, not an
 *       unbounded bypass.</li>
 * </ul>
 * The name is reserved: the unique-username rule keeps any human from later registering it.
 *
 * @author peter haller
 */
@Component
public class SystemIdentityProvider {

    /** Reserved login name of the platform's system principal. */
    public static final String SYSTEM_USERNAME = "system";

    private final UserService userService;

    public SystemIdentityProvider(UserService userService) {
        this.userService = userService;
    }

    /**
     * The seeded system principal.
     *
     * @return the platform's own {@link PUser}
     * @throws IllegalStateException if it was never seeded — a startup misconfiguration, not a normal
     *                               runtime condition
     */
    @Transactional(readOnly = true)
    public PUser get() {
        return userService.findOptionalByUsername(SYSTEM_USERNAME)
                .orElseThrow(() -> new IllegalStateException("The system principal '" + SYSTEM_USERNAME
                        + "' is missing; it should have been seeded at startup."));
    }
}
