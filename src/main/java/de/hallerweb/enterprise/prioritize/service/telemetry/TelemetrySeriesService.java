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
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.security.Action;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.telemetry.TelemetrySample;
import de.hallerweb.enterprise.prioritize.repository.resource.ResourceRepository;
import de.hallerweb.enterprise.prioritize.repository.telemetry.TelemetrySampleRepository;
import de.hallerweb.enterprise.prioritize.service.security.AuthorizationService;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Measurement series of telemetry data points: stores every numeric reading with the time it arrived
 * and serves it back for a time window, raw or grouped into calendar slots.
 * <p>
 * Writing is fed by the existing ingest in {@code ResourceService} (MQTT and REST alike), so a device
 * does not have to do anything new to get a series. Non-numeric readings ({@code ON}, a meter id) are
 * not series material and stay in the latest-value history only.
 *
 * @author peter haller
 */
@Service
@RequiredArgsConstructor
@Transactional
@Log4j2
public class TelemetrySeriesService {

    /** Upper bound for an unaggregated read; beyond it the caller has to pick a bucket or a shorter window. */
    static final int MAX_RAW_POINTS = 10_000;

    /** Window used when the caller gives no {@code from}. */
    static final Duration DEFAULT_WINDOW = Duration.ofHours(24);

    private final TelemetrySampleRepository sampleRepository;
    private final ResourceRepository resourceRepository;
    private final AuthorizationService authService;

    /**
     * Stores {@code rawValue} as a reading of {@code datapoint} if it is a finite number; anything else
     * is skipped silently (it is still kept in the latest-value history by the caller).
     *
     * @param resource   the reporting resource (must be persistent)
     * @param datapoint  the data point name
     * @param rawValue   the value as received
     * @param receivedAt when the platform received it
     * @return whether a reading was stored
     */
    public boolean record(Resource resource, String datapoint, String rawValue, Instant receivedAt) {
        Double value = parseNumber(rawValue);
        if (value == null) {
            log.debug("Non-numeric reading '{}' of '{}' on resource {} not added to the series.",
                    rawValue, datapoint, resource.getId());
            return false;
        }
        sampleRepository.save(TelemetrySample.builder()
                .resource(resource)
                .datapoint(datapoint)
                .recordedAt(receivedAt)
                .numericValue(value)
                .build());
        return true;
    }

    /**
     * Returns the series of one data point of a resource. Requires {@link Action#READ} on the resource.
     *
     * @param resourceId  the resource
     * @param datapoint   the data point name
     * @param from        window start (inclusive); {@code null} = {@link #DEFAULT_WINDOW} before {@code to}
     * @param to          window end (exclusive); {@code null} = now
     * @param bucket      slot size; {@code null} = {@link SeriesBucket#NONE}
     * @param aggregation slot reduction; {@code null} = {@link SeriesAggregation#AVG}
     * @param zone        zone id the slots are cut in; {@code null} = {@code UTC}
     * @param user        the caller
     * @throws NoSuchElementException   if the resource does not exist
     * @throws AccessDeniedException    if the caller may not read the resource
     * @throws IllegalArgumentException for an empty or inverted window, an unknown zone, or a raw read
     *                                  above {@link #MAX_RAW_POINTS}
     */
    @Transactional(readOnly = true)
    public TelemetrySeriesDTO getSeries(Long resourceId, String datapoint, Instant from, Instant to,
                                        SeriesBucket bucket, SeriesAggregation aggregation, String zone,
                                        PUser user) {
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new NoSuchElementException("Resource not found"));
        if (!authService.hasPermission(user, resource, Action.READ)) {
            throw new AccessDeniedException("No read permission for this resource.");
        }

        Instant end = to != null ? to : Instant.now();
        Instant start = from != null ? from : end.minus(DEFAULT_WINDOW);
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("'from' must be before 'to'.");
        }
        SeriesBucket effectiveBucket = bucket != null ? bucket : SeriesBucket.NONE;
        SeriesAggregation effectiveAggregation = aggregation != null ? aggregation : SeriesAggregation.AVG;
        ZoneId zoneId = parseZone(zone);

        if (effectiveBucket == SeriesBucket.NONE) {
            long count = sampleRepository.countSeries(resourceId, datapoint, start, end);
            if (count > MAX_RAW_POINTS) {
                throw new IllegalArgumentException("The window holds " + count + " readings, more than the "
                        + MAX_RAW_POINTS + " a raw read returns; choose a bucket or a shorter window.");
            }
        }

        Double baseline = effectiveAggregation == SeriesAggregation.DELTA && effectiveBucket != SeriesBucket.NONE
                ? sampleRepository
                        .findFirstByResource_IdAndDatapointAndRecordedAtBeforeOrderByRecordedAtDesc(
                                resourceId, datapoint, start)
                        .map(TelemetrySample::getNumericValue)
                        .orElse(null)
                : null;

        return new TelemetrySeriesDTO(resourceId, datapoint, start, end, effectiveBucket, effectiveAggregation,
                zoneId.getId(),
                TelemetrySeriesAggregator.aggregate(
                        sampleRepository.findSeries(resourceId, datapoint, start, end),
                        baseline, effectiveBucket, effectiveAggregation, zoneId));
    }

    /**
     * Deletes every reading recorded before {@code cutoff}.
     *
     * @return the number of deleted readings
     */
    public int purgeRecordedBefore(Instant cutoff) {
        return sampleRepository.deleteRecordedBefore(cutoff);
    }

    /** Parses a finite decimal number; {@code null} for anything else (text, NaN, infinity). */
    static Double parseNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            double value = Double.parseDouble(raw.trim());
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static ZoneId parseZone(String zone) {
        if (zone == null || zone.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(zone.trim());
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Unknown time zone '" + zone + "'.");
        }
    }
}
