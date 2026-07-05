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

package de.hallerweb.enterprise.prioritize.repository;

import de.hallerweb.enterprise.prioritize.model.PActor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Polymorphic repository over the {@link PActor} hierarchy. Thanks to JOINED inheritance a
 * {@code findById} resolves to the concrete actor subtype (e.g. {@code PUser} or
 * {@code Resource}), which is useful when an entity references an actor abstractly (such as a
 * task assignee).
 */
@Repository
public interface PActorRepository extends JpaRepository<PActor, Long> {
}
