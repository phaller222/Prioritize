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

package de.hallerweb.enterprise.prioritize.repository.security;

import de.hallerweb.enterprise.prioritize.model.security.PUser;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<PUser, Long> {
    @QueryHints({@QueryHint(name = "org.hibernate.flushMode", value = "MANUAL")})
    Optional<PUser> findByUsername(String username);

    /**
     * Case-insensitive lookup used to keep usernames unique. Deliberately unfiltered: a deactivated
     * account still occupies its name, because {@code findByUsername} — the login path — does not
     * filter by {@code active} either and would break on a second row.
     * <p>
     * Returns a list rather than an {@code Optional} on purpose. A database written before the name
     * was unique may still hold duplicates, and an {@code Optional} query would answer such legacy
     * data with {@code IncorrectResultSizeDataAccessException} instead of the clean "taken" this is
     * asked for.
     * <p>
     * {@code MANUAL} flush mode, like {@code findByUsername} above: the caller checks a name while an
     * entity carrying the very change under test may be dirty in the persistence context. With the
     * default flush-before-query that pending change would be written first and the query would find
     * the collision it is supposed to prevent.
     */
    @QueryHints({@QueryHint(name = "org.hibernate.flushMode", value = "MANUAL")})
    List<PUser> findAllByUsernameIgnoreCase(String username);

    // Authoritative "which users hold this role" via the user_roles join table (owning side).
    List<PUser> findByRoles_Id(Long roleId);
}