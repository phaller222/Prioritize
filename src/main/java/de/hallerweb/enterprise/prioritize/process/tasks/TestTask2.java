package de.hallerweb.enterprise.prioritize.process.tasks;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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