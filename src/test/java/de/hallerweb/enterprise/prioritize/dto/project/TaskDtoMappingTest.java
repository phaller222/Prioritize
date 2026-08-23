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

package de.hallerweb.enterprise.prioritize.dto.project;

import de.hallerweb.enterprise.prioritize.model.calendar.TimeSpan;
import de.hallerweb.enterprise.prioritize.model.project.Task;
import de.hallerweb.enterprise.prioritize.model.project.TaskStatus;
import de.hallerweb.enterprise.prioritize.model.project.goal.ProjectGoal;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit tests (no Spring context) for {@link TaskDTO#from(Task, PUser)}: the scalar task state maps
 * across, the polymorphic assignee and the goal flatten to their ids, the two tracking fields answer for
 * the viewer and for the task respectively, and both relations are null-safe.
 */
class TaskDtoMappingTest {

    @Test
    @DisplayName("TaskDTO.from carries scalars + assignee/goal ids")
    void taskDtoFrom() {
        PUser assignee = new PUser();
        assignee.setId(5L);
        ProjectGoal goal = new ProjectGoal();
        goal.setId(8L);

        Task task = new Task();
        task.setId(3L);
        task.setName("Design");
        task.setDescription("d");
        task.setPriority(2);
        task.setTaskStatus(TaskStatus.STARTED);
        task.setAssignee(assignee);
        task.setGoal(goal);
        task.setProcessInstanceId("proc-1");

        TaskDTO dto = TaskDTO.from(task, null);
        assertEquals(3L, dto.id());
        assertEquals("Design", dto.name());
        assertEquals("d", dto.description());
        assertEquals(2, dto.priority());
        assertEquals(TaskStatus.STARTED, dto.taskStatus());
        assertEquals(5L, dto.assigneeId());
        assertEquals(8L, dto.goalId());
        assertEquals("proc-1", dto.processInstanceId());
        assertFalse(dto.trackingForMe(), "no active span -> not tracking");
    }

    @Test
    @DisplayName("TaskDTO.from is null-safe for an unassigned, goal-less task")
    void taskDtoFromNullSafe() {
        Task bare = new Task();
        bare.setId(1L);

        TaskDTO dto = TaskDTO.from(bare, null);
        assertEquals(1L, dto.id());
        assertNull(dto.assigneeId());
        assertNull(dto.goalId());
        assertNull(dto.processInstanceId());
        assertFalse(dto.trackingForMe());
        assertEquals(0, dto.runningCount(), "nobody is clocked in on a bare task");
    }

    /**
     * The two tracking fields answer different questions and must not be conflated: {@code trackingForMe}
     * is about the viewer, {@code runningCount} is about the task. A colleague looking at a task somebody
     * else is working on has to see that the work is under way, without that turning into "my clock is
     * running" — that conflation was the bug the per-person clocks fixed.
     */
    @Test
    @DisplayName("trackingForMe answers for the viewer, runningCount for the whole crew")
    void trackingFieldsAnswerDifferentQuestions() {
        PUser worker = new PUser();
        worker.setId(11L);
        PUser colleague = new PUser();
        colleague.setId(12L);

        Task task = new Task();
        task.setId(4L);
        task.getActiveTimeSpans().add(clockOf(worker));
        task.getActiveTimeSpans().add(clockOf(colleague));

        TaskDTO forWorker = TaskDTO.from(task, worker);
        assertTrue(forWorker.trackingForMe(), "the worker's own clock is running");
        assertEquals(2, forWorker.runningCount(), "both people on the task are counted");

        PUser stranger = new PUser();
        stranger.setId(13L);
        TaskDTO forStranger = TaskDTO.from(task, stranger);
        assertFalse(forStranger.trackingForMe(), "a bystander's clock is not running");
        assertEquals(2, forStranger.runningCount(), "the crew is the same whoever is looking");
    }

    /** An open span belonging to one person, the shape {@code startTracking} produces. */
    private static TimeSpan clockOf(PUser user) {
        TimeSpan span = new TimeSpan();
        span.setDateFrom(Instant.now());
        span.getInvolvedUsers().add(user);
        return span;
    }
}
