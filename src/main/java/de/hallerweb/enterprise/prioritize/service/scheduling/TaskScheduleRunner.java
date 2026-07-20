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

package de.hallerweb.enterprise.prioritize.service.scheduling;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background poller that drives {@link TaskScheduleService#runDueSchedules(LocalDateTime)} on a fixed
 * cadence, turning the schedule store into actual task generation.
 * <p>
 * Uses {@code fixedDelay} (not {@code fixedRate}) so a slow run never overlaps the next one. It fires
 * against {@link LocalDateTime#now()} in the server zone — exactly the zone {@code nextFireAt} is
 * normalized to. The effective resolution equals the poll interval: a schedule can only fire on a
 * poll tick, so sub-interval cron granularity is not honored (minute-level cadence is the intent).
 * <p>
 * Gated by {@code prioritize.scheduling.enabled} (default on): this single annotation carries both
 * {@link EnableScheduling} and the {@code @Scheduled} method, so switching the property off (as the
 * test resources do) disables the scheduling infrastructure and this poller together — no background
 * thread runs in tests.
 *
 * @author peter haller
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "prioritize.scheduling.enabled", matchIfMissing = true)
@RequiredArgsConstructor
public class TaskScheduleRunner {

    private final TaskScheduleService taskScheduleService;

    /** Fires every schedule that has come due since the last tick. */
    @Scheduled(fixedDelayString = "${prioritize.scheduling.poll-interval-ms:60000}")
    void poll() {
        taskScheduleService.runDueSchedules(LocalDateTime.now());
    }
}
