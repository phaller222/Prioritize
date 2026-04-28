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