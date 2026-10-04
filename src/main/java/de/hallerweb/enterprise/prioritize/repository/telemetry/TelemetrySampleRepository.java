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

package de.hallerweb.enterprise.prioritize.repository.telemetry;

import de.hallerweb.enterprise.prioritize.model.telemetry.TelemetrySample;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for {@link TelemetrySample}s.
 *
 * @author peter haller
 */
public interface TelemetrySampleRepository extends JpaRepository<TelemetrySample, Long> {

    /**
     * The readings of one data point in {@code [from, to)}, oldest first, as bare time/value pairs —
     * a series can span hundreds of thousands of rows, so no entity (and no resource proxy) is built.
     */
    @Query("select new de.hallerweb.enterprise.prioritize.repository.telemetry.TelemetrySampleValue("
            + "s.recordedAt, s.numericValue) from TelemetrySample s "
            + "where s.resource.id = :resourceId and s.datapoint = :datapoint "
            + "and s.recordedAt >= :from and s.recordedAt < :to order by s.recordedAt")
    List<TelemetrySampleValue> findSeries(@Param("resourceId") Long resourceId,
                                          @Param("datapoint") String datapoint,
                                          @Param("from") Instant from,
                                          @Param("to") Instant to);

    /** How many readings {@link #findSeries} would return; guards the unaggregated read. */
    @Query("select count(s) from TelemetrySample s "
            + "where s.resource.id = :resourceId and s.datapoint = :datapoint "
            + "and s.recordedAt >= :from and s.recordedAt < :to")
    long countSeries(@Param("resourceId") Long resourceId,
                     @Param("datapoint") String datapoint,
                     @Param("from") Instant from,
                     @Param("to") Instant to);

    /**
     * The newest reading strictly before {@code before} — the baseline a counter delta needs for the
     * first bucket of a window.
     */
    Optional<TelemetrySample> findFirstByResource_IdAndDatapointAndRecordedAtBeforeOrderByRecordedAtDesc(
            Long resourceId, String datapoint, Instant before);

    /** Removes every reading older than {@code cutoff}; backs the retention purge. */
    @Modifying
    @Query("delete from TelemetrySample s where s.recordedAt < :cutoff")
    int deleteRecordedBefore(@Param("cutoff") Instant cutoff);
}
