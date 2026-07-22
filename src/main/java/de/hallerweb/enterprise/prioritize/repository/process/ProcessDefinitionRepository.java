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

package de.hallerweb.enterprise.prioritize.repository.process;

import de.hallerweb.enterprise.prioritize.model.process.ProcessDefinition;
import de.hallerweb.enterprise.prioritize.model.process.ProcessDefinitionState;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link ProcessDefinition}s.
 *
 * @author peter haller
 */
public interface ProcessDefinitionRepository extends JpaRepository<ProcessDefinition, Long> {

    /** Resolves a definition by its BPMN process id — the collision check on registration. */
    Optional<ProcessDefinition> findByProcessKey(String processKey);

    /** All definitions in a given state, e.g. the deployed ones to re-check at startup. */
    List<ProcessDefinition> findByState(ProcessDefinitionState state);

    /**
     * The definitions carried by a given document. Normally at most one, but queried as a list so a
     * stale duplicate answers the question instead of throwing (same reasoning as the unique-username
     * lookup).
     */
    List<ProcessDefinition> findByDocumentInfo_Id(Long documentInfoId);
}
