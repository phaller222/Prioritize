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

import de.hallerweb.enterprise.prioritize.model.document.Document;
import de.hallerweb.enterprise.prioritize.model.document.DocumentInfo;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit tests (no Spring context) for {@link DocumentSummaryDTO#from(DocumentInfo)}: it reads the
 * current version's name/version and the lock state, flattens the locking user to a username, and is
 * null-safe when nothing holds the lock.
 */
class DocumentSummaryDtoMappingTest {

    private static DocumentInfo info(String name, int version, boolean locked, PUser lockedBy) {
        Document current = new Document();
        current.setName(name);
        current.setVersion(version);
        DocumentInfo info = new DocumentInfo();
        info.setId(3L);
        info.setCurrentDocument(current);
        info.setLocked(locked);
        info.setLockedBy(lockedBy);
        return info;
    }

    @Test
    @DisplayName("from carries id + current name/version and the locking username")
    void fromLocked() {
        PUser bob = new PUser();
        bob.setUsername("bob");

        DocumentSummaryDTO dto = DocumentSummaryDTO.from(info("Spec", 4, true, bob));
        assertEquals(3L, dto.getId());
        assertEquals("Spec", dto.getName());
        assertEquals(4, dto.getCurrentVersion());
        assertTrue(dto.isLocked());
        assertEquals("bob", dto.getLockedBy());
    }

    @Test
    @DisplayName("from is null-safe when the document is not locked")
    void fromUnlocked() {
        DocumentSummaryDTO dto = DocumentSummaryDTO.from(info("Spec", 1, false, null));
        assertFalse(dto.isLocked());
        assertNull(dto.getLockedBy());
    }
}
