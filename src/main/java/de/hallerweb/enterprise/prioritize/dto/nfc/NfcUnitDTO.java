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

import java.time.Instant;

/**
 * Flat, transport-safe view of a {@link NfcUnit}. Carries the tag's scalar state plus the owning
 * resource and the bound task by id, but never the lazy {@code Resource} or {@code Task} entities
 * themselves — so serializing a unit never triggers a {@code LazyInitializationException} nor drags
 * those graphs onto the wire. The resource/task ids read off the lazy proxies without initializing
 * them (safe under open-in-view).
 *
 * @author peter haller
 */
public record NfcUnitDTO(Long id,
                         String uuid,
                         String name,
                         String description,
                         NfcUnitType type,
                         String payload,
                         long sequenceNumber,
                         Instant lastScanTime,
                         Long resourceId,
                         Long boundTaskId) {

    /** Maps an entity to its DTO. Reads the lazy resource/task ids only (safe under open-in-view). */
    public static NfcUnitDTO from(NfcUnit unit) {
        return new NfcUnitDTO(
                unit.getId(),
                unit.getUuid(),
                unit.getName(),
                unit.getDescription(),
                unit.getType(),
                unit.getPayload(),
                unit.getSequenceNumber(),
                unit.getLastScanTime(),
                unit.getResource() != null ? unit.getResource().getId() : null,
                unit.getBoundTaskId());
    }
}
