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


@Component("TestTask")
public class TestTask implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(TestTask.class);

    private String sourceValue;

    @Override
    public void execute(DelegateExecution execution) {
        log.info("TestTask executed, processInstance={}", execution.getProcessInstanceId());

        execution.setVariableLocal("sourceValue", "16");

        sourceValue = (String) execution.getVariableLocal("sourceValue");
        System.out.println("Source Value: " + sourceValue);

        double d = Math.sqrt(Double.parseDouble(sourceValue));
        String result = String.valueOf(d);
        System.out.println("Square root calculates: " + result);
        execution.setVariableLocal("squareRoot", result);

    }

    public String getSourceValue() {
        return sourceValue;
    }

    public void setSourceValue(String sourceValue) {
        this.sourceValue = sourceValue;
    }


}