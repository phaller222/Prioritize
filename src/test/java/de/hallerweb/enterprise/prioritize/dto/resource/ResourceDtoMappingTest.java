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

import de.hallerweb.enterprise.prioritize.model.calendar.TimeSpan;
import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.resource.NameValueEntry;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.resource.ResourceGroup;
import de.hallerweb.enterprise.prioritize.model.resource.ResourceReservation;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Plain unit tests (no Spring context) for the request/response DTO mapping of the resource subsystem. The
 * central property pinned here is that {@link ResourceRequest#toResource()} leaves unsupplied fields
 * {@code null} (so the service's PATCH "null = unchanged" semantics survive), rather than filling them with
 * the entity's {@code @Builder.Default} values.
 */
class ResourceDtoMappingTest {

    @Test
    @DisplayName("ResourceRequest.toResource leaves unsupplied fields null (PATCH semantics preserved)")
    void toResourcePreservesNulls() {
        // only two fields supplied; everything else must stay null
        Resource r = new ResourceRequest("Printer", null, null, 8080, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null).toResource();

        assertEquals("Printer", r.getName());
        assertEquals(8080, r.getPort());
        assertNull(r.getMaxSlots(), "unsupplied -> null, not the builder default 1");
        assertNull(r.getStationary(), "unsupplied -> null, not the builder default true");
        assertNull(r.getRemote());
        assertNull(r.getMqttResource(), "unsupplied -> null, not the builder default false");
        assertNull(r.getMqttOnline());
        assertNull(r.getDescription());
    }

    @Test
    @DisplayName("ResourceRequest.toResource maps every supplied field")
    void toResourceFull() {
        Resource r = new ResourceRequest("Robot", "arm", "10.0.0.5", 1883, 3, true, false, true,
                "50.1", "8.6", true, "uuid-1", "send/topic", "recv/topic", false,
                null, null, null).toResource();

        assertNull(r.getId());
        assertEquals("Robot", r.getName());
        assertEquals("arm", r.getDescription());
        assertEquals("10.0.0.5", r.getIp());
        assertEquals(1883, r.getPort());
        assertEquals(3, r.getMaxSlots());
        assertEquals(true, r.getStationary());
        assertEquals(false, r.getRemote());
        assertEquals(true, r.getAgent());
        assertEquals("50.1", r.getLatitude());
        assertEquals("8.6", r.getLongitude());
        assertEquals(true, r.getMqttResource());
        assertEquals("uuid-1", r.getMqttUUID());
        assertEquals("send/topic", r.getMqttDataSendTopic());
        assertEquals("recv/topic", r.getMqttDataReceiveTopic());
        assertEquals(false, r.getMqttOnline());
    }

    @Test
    @DisplayName("ResourceDTO.from carries scalars + department/group ids, null-safe")
    void resourceDtoFrom() {
        Department dept = new Department();
        dept.setId(4L);
        ResourceGroup group = ResourceGroup.builder().name("Line A").build();
        group.setId(6L);

        Resource r = new Resource();
        r.setId(9L);
        r.setName("Robot");
        r.setMaxSlots(3);
        r.setMqttOnline(true);
        r.setDepartment(dept);
        r.setResourceGroup(group);
        r.setCurrentOccupiedSlots(2);

        ResourceDTO dto = ResourceDTO.from(r);
        assertEquals(9L, dto.id());
        assertEquals("Robot", dto.name());
        assertEquals(3, dto.maxSlots());
        assertEquals(true, dto.mqttOnline());
        assertEquals(4L, dto.departmentId());
        assertEquals(6L, dto.resourceGroupId());
        assertEquals(2, dto.occupiedSlots());

        Resource loose = new Resource();
        loose.setId(1L);
        assertNull(ResourceDTO.from(loose).departmentId());
        assertNull(ResourceDTO.from(loose).resourceGroupId());
    }

    @Test
    @DisplayName("ResourceGroupDTO.from carries name + department id, null-safe")
    void resourceGroupDtoFrom() {
        Department dept = new Department();
        dept.setId(4L);
        ResourceGroup group = ResourceGroup.builder().name("Line A").build();
        group.setId(6L);
        group.setDepartment(dept);

        ResourceGroupDTO dto = ResourceGroupDTO.from(group);
        assertEquals(6L, dto.id());
        assertEquals("Line A", dto.name());
        assertEquals(4L, dto.departmentId());

        ResourceGroup orphan = ResourceGroup.builder().name("Loose").build();
        orphan.setId(7L);
        assertNull(ResourceGroupDTO.from(orphan).departmentId());
    }

    @Test
    @DisplayName("ResourceValueDTO.from exposes only the newest entry of the capped history, null-safe")
    void resourceValueDtoFrom() {
        NameValueEntry entry = new NameValueEntry();
        entry.setMqttName("temperature");
        entry.setMqttValues("21.0,21.5,22.3");

        ResourceValueDTO dto = ResourceValueDTO.from(entry);
        assertEquals("temperature", dto.name());
        assertEquals("22.3", dto.value(), "newest (last) token of the comma history");

        NameValueEntry single = new NameValueEntry();
        single.setMqttName("humidity");
        single.setMqttValues("55");
        assertEquals("55", ResourceValueDTO.from(single).value());

        NameValueEntry empty = new NameValueEntry();
        empty.setMqttName("pressure");
        assertNull(ResourceValueDTO.from(empty).value(), "empty history -> null value");
    }

    @Test
    @DisplayName("ResourceReservationDTO.from flattens user + timespan, null-safe")
    void reservationDtoFrom() {
        Instant from = Instant.parse("2026-05-15T14:00:00Z");
        Instant until = Instant.parse("2026-05-15T16:00:00Z");

        PUser user = new PUser();
        user.setUsername("bob");
        TimeSpan span = new TimeSpan();
        span.setDateFrom(from);
        span.setDateUntil(until);

        ResourceReservation res = new ResourceReservation();
        res.setId(3);
        res.setReservedBy(user);
        res.setTimespan(span);
        res.setSlotNumber(2);

        ResourceReservationDTO dto = ResourceReservationDTO.from(res);
        assertEquals(3, dto.getId());
        assertEquals("bob", dto.getReservedBy());
        assertEquals(from, dto.getFrom());
        assertEquals(until, dto.getUntil());
        assertEquals(2, dto.getSlotNumber());

        // null-safe: no user, no timespan
        ResourceReservation bare = new ResourceReservation();
        bare.setId(4);
        bare.setSlotNumber(1);
        ResourceReservationDTO bareDto = ResourceReservationDTO.from(bare);
        assertNull(bareDto.getReservedBy());
        assertNull(bareDto.getFrom());
        assertNull(bareDto.getUntil());
    }
}
