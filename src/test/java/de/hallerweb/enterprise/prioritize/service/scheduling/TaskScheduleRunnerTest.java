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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit test for {@link TaskScheduleRunner}: confirms the poll tick delegates to the service with a
 * current server-zone timestamp. The gating and cadence are Spring concerns, not exercised here.
 *
 * @author peter haller
 */
class TaskScheduleRunnerTest {

    @Test
    @DisplayName("poll delegates to runDueSchedules with the current time")
    void poll_delegatesToService() {
        TaskScheduleService service = mock(TaskScheduleService.class);
        TaskScheduleRunner runner = new TaskScheduleRunner(service);

        runner.poll();

        verify(service).runDueSchedules(any(LocalDateTime.class));
    }
}
