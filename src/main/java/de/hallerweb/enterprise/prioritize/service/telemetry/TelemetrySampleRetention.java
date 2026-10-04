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

import java.time.Duration;
import java.time.Instant;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Optional nightly purge of old measurement-series readings.
 * <p>
 * Off unless {@code prioritize.telemetry.retention-days} is set to a positive number: a series is
 * cheap (a sensor every minute is about half a million rows a year) and old readings are exactly what
 * a year-over-year comparison needs, so nothing is thrown away by default. Like the task-schedule
 * poller it is also switched off by {@code prioritize.scheduling.enabled=false}, so no background
 * thread runs in tests.
 *
 * @author peter haller
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "prioritize.scheduling.enabled", matchIfMissing = true)
@Log4j2
public class TelemetrySampleRetention {

    private final TelemetrySeriesService seriesService;
    private final int retentionDays;

    public TelemetrySampleRetention(TelemetrySeriesService seriesService,
                                    @Value("${prioritize.telemetry.retention-days:0}") int retentionDays) {
        this.seriesService = seriesService;
        this.retentionDays = retentionDays;
    }

    /** Deletes readings older than the retention period; a no-op while retention is off. */
    @Scheduled(cron = "${prioritize.telemetry.retention-cron:0 30 3 * * *}")
    void purge() {
        if (retentionDays <= 0) {
            return;
        }
        int deleted = seriesService.purgeRecordedBefore(Instant.now().minus(Duration.ofDays(retentionDays)));
        log.info("Telemetry retention: {} readings older than {} days deleted.", deleted, retentionDays);
    }
}
