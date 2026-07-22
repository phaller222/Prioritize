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

package de.hallerweb.enterprise.prioritize.process.tasks;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Test fixture: the second delegate of {@code processes/TestProcess.bpmn}. Kept in the test sources
 * so no example process ships with the application.
 */
@Component("TestTask2")
public class TestTask2 implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(TestTask.class);

    @Override
    public void execute(DelegateExecution execution) {
        log.info("TestTask2 executed, processInstance={}",
                execution.getProcessInstanceId());
        log.info("Received Value: --- " + execution.getVariableLocal("squareRoot"));
    }
}