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

package de.hallerweb.enterprise.prioritize.service.telemetry;

import de.hallerweb.enterprise.prioritize.dto.telemetry.TelemetrySeriesDTO;
import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.resource.ResourceGroup;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.company.DepartmentRepository;
import de.hallerweb.enterprise.prioritize.repository.telemetry.TelemetrySampleRepository;
import de.hallerweb.enterprise.prioritize.service.resource.ResourceService;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link TelemetrySeriesService}: that the existing ingest feeds the series,
 * the window and baseline queries, and that readings go away with their resource.
 */
@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
class TelemetrySeriesServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");

    @Autowired private TelemetrySeriesService seriesService;
    @Autowired private TelemetrySampleRepository sampleRepository;
    @Autowired private ResourceService resourceService;
    @Autowired private UserService userService;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private EntityManager entityManager;

    private PUser admin;
    private Resource resource;

    @BeforeEach
    void setUp() {
        admin = userService.findUserByUsername("admin");
        Department dept = departmentRepository.findAll().stream().findFirst().orElseThrow();
        ResourceGroup group = resourceService.createResourceGroup("Series-Test-Gruppe", dept, admin);
        resource = resourceService.createResource(Resource.builder()
                .name("Series-Test-Zaehler")
                .description("Ressource für Messreihen-Tests")
                .maxSlots(1)
                .build(), group.getId(), admin);
    }

    @Test
    @DisplayName("The REST ingest feeds the series with numeric readings only")
    void ingest_storesNumericReadingsOnly() {
        resourceService.recordMqttValue(resource.getId(), "power", "377", admin);
        resourceService.recordMqttValue(resource.getId(), "power", "-12.5", admin);
        resourceService.recordMqttValue(resource.getId(), "power", "ON", admin);

        TelemetrySeriesDTO series = seriesService.getSeries(resource.getId(), "power",
                null, null, null, null, null, admin);

        assertEquals(2, series.points().size(), () -> "'ON' stays out of the series. DIAG now=" + Instant.now()
                + " window=" + series.from() + ".." + series.to() + " all=" + seriesService.getSeries(resource.getId(),
                "power", Instant.EPOCH, Instant.now().plusSeconds(86400 * 2), null, null, null, admin).points()
                + " jvmZone=" + java.time.ZoneId.systemDefault());
        assertEquals(377.0, series.points().get(0).value());
        assertEquals(-12.5, series.points().get(1).value());
        assertEquals(SeriesBucket.NONE, series.bucket(), "defaults are echoed");
        assertEquals("UTC", series.zone());
    }

    @Test
    @DisplayName("Only readings of the data point inside [from, to) are returned, oldest first")
    void window_isHalfOpenAndPerDatapoint() {
        seriesService.record(resource, "power", "1", T0.minusSeconds(1));
        seriesService.record(resource, "power", "3", T0.plusSeconds(120));
        seriesService.record(resource, "power", "2", T0);
        seriesService.record(resource, "power", "4", T0.plusSeconds(3600));
        seriesService.record(resource, "other", "9", T0.plusSeconds(60));

        TelemetrySeriesDTO series = seriesService.getSeries(resource.getId(), "power",
                T0, T0.plusSeconds(3600), SeriesBucket.NONE, null, null, admin);

        assertEquals(2, series.points().size());
        assertEquals(2.0, series.points().get(0).value());
        assertEquals(3.0, series.points().get(1).value());
    }

    @Test
    @DisplayName("DELTA takes its baseline from the newest reading before the window")
    void delta_usesReadingBeforeWindowAsBaseline() {
        seriesService.record(resource, "MT681.Total_in", "15119.0", T0.minusSeconds(600));
        seriesService.record(resource, "MT681.Total_in", "15119.4", T0.plusSeconds(600));
        seriesService.record(resource, "MT681.Total_in", "15120.0", T0.plusSeconds(3000));

        TelemetrySeriesDTO series = seriesService.getSeries(resource.getId(), "MT681.Total_in",
                T0, T0.plusSeconds(3600), SeriesBucket.HOUR, SeriesAggregation.DELTA, "Europe/Berlin", admin);

        assertEquals(1, series.points().size());
        assertEquals(1.0, series.points().get(0).value(), 1e-9);
        assertEquals(2, series.points().get(0).samples());
    }

    @Test
    @DisplayName("An inverted window, an unknown zone and an unknown resource are rejected")
    void invalidRequests_areRejected() {
        assertThrows(IllegalArgumentException.class, () -> seriesService.getSeries(resource.getId(), "power",
                T0, T0, null, null, null, admin));
        assertThrows(IllegalArgumentException.class, () -> seriesService.getSeries(resource.getId(), "power",
                null, null, null, null, "Mars/Olympus", admin));
        assertThrows(NoSuchElementException.class, () -> seriesService.getSeries(999999L, "power",
                null, null, null, null, null, admin));
    }

    @Test
    @DisplayName("Readings disappear with their resource (database-level cascade)")
    void deletingTheResource_removesItsReadings() {
        seriesService.record(resource, "power", "1", T0);
        seriesService.record(resource, "power", "2", T0.plusSeconds(60));
        entityManager.flush();
        assertEquals(2, sampleRepository.countSeries(resource.getId(), "power", T0, T0.plusSeconds(3600)));

        resourceService.deleteResource(resource.getId(), admin);
        entityManager.flush();
        entityManager.clear();

        assertEquals(0, sampleRepository.countSeries(resource.getId(), "power", T0, T0.plusSeconds(3600)));
    }

    @Test
    @DisplayName("The retention purge removes only readings older than the cutoff")
    void purge_removesOnlyOlderReadings() {
        seriesService.record(resource, "power", "1", T0.minusSeconds(7200));
        seriesService.record(resource, "power", "2", T0);
        entityManager.flush();

        assertTrue(seriesService.purgeRecordedBefore(T0) >= 1);
        assertEquals(1, sampleRepository.countSeries(resource.getId(), "power", Instant.EPOCH, T0.plusSeconds(1)));
    }
}
