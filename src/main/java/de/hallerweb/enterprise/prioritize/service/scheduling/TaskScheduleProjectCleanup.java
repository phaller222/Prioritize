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

import de.hallerweb.enterprise.prioritize.repository.scheduling.TaskScheduleRepository;
import de.hallerweb.enterprise.prioritize.service.project.ProjectDeletionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Removes a project's {@link de.hallerweb.enterprise.prioritize.model.scheduling.TaskSchedule}s when the
 * project itself is deleted.
 * <p>
 * A schedule points at its project, but a project knows nothing about its schedules — deliberately, so the
 * {@code scheduling} package stays an additive satellite of {@code project} rather than the other way round.
 * That also means no JPA cascade can reach them, and the leftover rows would make the delete fail on the
 * foreign key. Subscribing to {@link ProjectDeletionEvent} keeps the cleanup on this side of the dependency.
 * <p>
 * A plain {@code @EventListener} on purpose: it runs synchronously, before the project row is removed and
 * within the deleting transaction, so the delete either succeeds as a whole or rolls back as a whole. An
 * {@code AFTER_COMMIT} listener would fire too late to be of any use here.
 *
 * @author peter haller
 */
@Component
@RequiredArgsConstructor
@Log4j2
public class TaskScheduleProjectCleanup {

    private final TaskScheduleRepository scheduleRepository;

    @EventListener
    public void onProjectDeletion(ProjectDeletionEvent event) {
        long removed = scheduleRepository.deleteByProject_Id(event.projectId());
        if (removed > 0) {
            log.info("Removed {} task schedule(s) of deleted project {}.", removed, event.projectId());
        }
    }
}
