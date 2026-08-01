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

package de.hallerweb.enterprise.prioritize.dto.document;

import de.hallerweb.enterprise.prioritize.model.document.DocumentInfo;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
/**
 * DTO for a summary of a document, contains ID, name, current version, lock and the user who released the lock.
 */
public class DocumentSummaryDTO {
    private Long id;
    private String name;
    private int currentVersion;
    private boolean locked;
    private String lockedBy;

    /**
     * Maps a {@link DocumentInfo} to its summary. Reads the current version's name/version and the lock
     * state; never serializes the version graph or the binary payload. Call within a transaction.
     */
    public static DocumentSummaryDTO from(DocumentInfo info) {
        return new DocumentSummaryDTO(
                info.getId(),
                info.getCurrentDocument().getName(),
                info.getCurrentDocument().getVersion(),
                info.isLocked(),
                info.getLockedBy() != null ? info.getLockedBy().getUsername() : null);
    }
}