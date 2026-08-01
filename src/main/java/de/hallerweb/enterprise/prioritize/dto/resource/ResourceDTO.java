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

package de.hallerweb.enterprise.prioritize.dto.resource;

import de.hallerweb.enterprise.prioritize.model.resource.Resource;

import java.time.LocalDateTime;

/**
 * Flat, transport-safe view of a {@link Resource}. Carries the scalar state plus the owning department and
 * group by id, but never the lazy collections (reservations, skills, NFC units), the MQTT command/value
 * history or the binary payload buffers — so serializing a resource never triggers a
 * {@code LazyInitializationException} nor drags heavy relations onto the wire. {@code occupiedSlots} is the
 * transient reservation count "now" (populated by {@code getResource}; null/0 in a plain group listing).
 * The department/group ids read off the lazy proxies without initializing them.
 *
 * @author peter haller
 */
public record ResourceDTO(Long id,
                          String name,
                          String description,
                          Boolean stationary,
                          Boolean remote,
                          String ip,
                          Integer port,
                          Boolean busy,
                          Integer maxSlots,
                          String latitude,
                          String longitude,
                          Boolean mqttResource,
                          String mqttUUID,
                          String mqttDataSendTopic,
                          String mqttDataReceiveTopic,
                          Boolean mqttOnline,
                          LocalDateTime mqttLastPing,
                          Boolean agent,
                          Long departmentId,
                          Long resourceGroupId,
                          Integer occupiedSlots) {

    /** Maps an entity to its DTO. Reads the lazy department/group ids only (safe under open-in-view). */
    public static ResourceDTO from(Resource resource) {
        return new ResourceDTO(
                resource.getId(),
                resource.getName(),
                resource.getDescription(),
                resource.getStationary(),
                resource.getRemote(),
                resource.getIp(),
                resource.getPort(),
                resource.getBusy(),
                resource.getMaxSlots(),
                resource.getLatitude(),
                resource.getLongitude(),
                resource.getMqttResource(),
                resource.getMqttUUID(),
                resource.getMqttDataSendTopic(),
                resource.getMqttDataReceiveTopic(),
                resource.getMqttOnline(),
                resource.getMqttLastPing(),
                resource.getAgent(),
                resource.getDepartment() != null ? resource.getDepartment().getId() : null,
                resource.getResourceGroup() != null ? resource.getResourceGroup().getId() : null,
                resource.getCurrentOccupiedSlots());
    }
}
