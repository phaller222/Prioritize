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

package de.hallerweb.enterprise.prioritize.dto.process;

import de.hallerweb.enterprise.prioritize.model.process.ProcessDefinition;
import de.hallerweb.enterprise.prioritize.model.process.ProcessDefinitionState;
import java.time.LocalDateTime;

/**
 * Flat, transport-safe view of a {@link ProcessDefinition}. Carries the source document's id rather
 * than the lazy {@code DocumentInfo} itself, so serializing a definition never triggers a
 * {@code LazyInitializationException} nor drags a document's whole history onto the wire. Mapped
 * inside a service transaction.
 *
 * @author peter haller
 */
public record ProcessDefinitionDTO(Long id,
                                   String processKey,
                                   String name,
                                   Long documentInfoId,
                                   ProcessDefinitionState state,
                                   String deploymentId,
                                   Integer deployedVersion,
                                   LocalDateTime deployedAt,
                                   String deployedBy) {

    /** Maps an entity to its DTO. Call within a transaction ({@code documentInfo} is lazy). */
    public static ProcessDefinitionDTO from(ProcessDefinition definition) {
        return new ProcessDefinitionDTO(
                definition.getId(),
                definition.getProcessKey(),
                definition.getName(),
                definition.getDocumentInfo() != null ? definition.getDocumentInfo().getId() : null,
                definition.getState(),
                definition.getDeploymentId(),
                definition.getDeployedVersion(),
                definition.getDeployedAt(),
                definition.getDeployedBy() != null ? definition.getDeployedBy().getUsername() : null);
    }
}
