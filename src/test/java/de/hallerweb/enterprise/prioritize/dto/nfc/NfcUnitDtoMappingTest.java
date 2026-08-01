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

package de.hallerweb.enterprise.prioritize.dto.nfc;

import de.hallerweb.enterprise.prioritize.model.nfc.NfcUnit;
import de.hallerweb.enterprise.prioritize.model.nfc.NfcUnit.NfcUnitType;
import de.hallerweb.enterprise.prioritize.model.project.Task;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Plain unit tests (no Spring context) for {@link NfcUnitDTO#from(NfcUnit)}: the scalar tag state maps
 * across, the owning resource and the bound task flatten to their ids, and both relations are null-safe.
 */
class NfcUnitDtoMappingTest {

    @Test
    @DisplayName("NfcUnitDTO.from carries scalars + resource/task ids")
    void nfcUnitDtoFrom() {
        Instant scanned = Instant.parse("2026-05-15T14:00:00Z");

        Resource resource = new Resource();
        resource.setId(4L);
        Task task = new Task();
        task.setId(9L);

        NfcUnit unit = NfcUnit.builder()
                .uuid("uuid-1")
                .name("Tag")
                .description("d")
                .type(NfcUnitType.TIMETRACKER)
                .payload("hello")
                .sequenceNumber(7)
                .lastScanTime(scanned)
                .resource(resource)
                .task(task)
                .build();
        unit.setId(3L);

        NfcUnitDTO dto = NfcUnitDTO.from(unit);
        assertEquals(3L, dto.id());
        assertEquals("uuid-1", dto.uuid());
        assertEquals("Tag", dto.name());
        assertEquals("d", dto.description());
        assertEquals(NfcUnitType.TIMETRACKER, dto.type());
        assertEquals("hello", dto.payload());
        assertEquals(7, dto.sequenceNumber());
        assertEquals(scanned, dto.lastScanTime());
        assertEquals(4L, dto.resourceId());
        assertEquals(9L, dto.boundTaskId());
    }

    @Test
    @DisplayName("NfcUnitDTO.from is null-safe for an unmounted, unbound tag")
    void nfcUnitDtoFromNullSafe() {
        NfcUnit bare = new NfcUnit();
        bare.setId(1L);

        NfcUnitDTO dto = NfcUnitDTO.from(bare);
        assertEquals(1L, dto.id());
        assertNull(dto.resourceId());
        assertNull(dto.boundTaskId());
    }
}
