package de.hallerweb.enterprise.prioritize.process;

import org.flowable.engine.RuntimeService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ProcessStarter implements CommandLineRunner {


    private final RuntimeService runtimeService;

    public ProcessStarter(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @Override
    public void run(String... args) {

        //runtimeService.startProcessInstanceByKey("MyProcess");
    }
}