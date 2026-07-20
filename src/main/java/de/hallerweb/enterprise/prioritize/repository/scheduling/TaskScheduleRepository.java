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

package de.hallerweb.enterprise.prioritize.repository.scheduling;

import de.hallerweb.enterprise.prioritize.model.scheduling.TaskSchedule;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link TaskSchedule}s.
 *
 * @author peter haller
 */
public interface TaskScheduleRepository extends JpaRepository<TaskSchedule, Long> {

    /**
     * The enabled schedules that are due at or before {@code now} — the exact set the poller fires.
     * Schedules with a {@code null} {@code nextFireAt} (cron exhausted) are excluded by the
     * comparison.
     */
    List<TaskSchedule> findByEnabledTrueAndNextFireAtLessThanEqual(LocalDateTime now);

    /** All schedules targeting a given project (enabled or not); used by admin/inspection paths. */
    List<TaskSchedule> findByProject_Id(Long projectId);
}
