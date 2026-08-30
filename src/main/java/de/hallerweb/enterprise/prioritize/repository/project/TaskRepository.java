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

package de.hallerweb.enterprise.prioritize.repository.project;

import de.hallerweb.enterprise.prioritize.model.project.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByBlackboard_Id(Long blackboardId);

    List<Task> findByAssignee_Id(Long assigneeId);

    List<Task> findByBlackboard_IdAndAssigneeIsNull(Long blackboardId);

    List<Task> findByGoal_Id(Long goalId);

    /**
     * The tasks linked to a BPMN process instance. Normally at most one, but queried as a list so a
     * stale duplicate answers the question instead of throwing (same reasoning as the unique-username
     * lookup).
     */
    List<Task> findByProcessInstanceId(String processInstanceId);

    /**
     * The tasks that currently have the given resource clocked in. A piece of equipment can only be
     * in one place, so this is expected to hold at most one task — it answers "where is this device
     * booked right now?" before it can be clocked in somewhere else. Returned as a list so a stale
     * duplicate can be reported rather than thrown at, same reasoning as
     * {@link #findByProcessInstanceId}.
     */
    @Query("SELECT t FROM Task t JOIN t.activeEquipmentSpans s JOIN s.involvedResources r "
            + "WHERE r.id = :resourceId")
    List<Task> findByEquipmentRunning(@Param("resourceId") Long resourceId);
}
