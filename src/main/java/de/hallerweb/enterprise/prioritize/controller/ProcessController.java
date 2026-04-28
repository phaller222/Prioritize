package de.hallerweb.enterprise.prioritize.controller;

import org.flowable.engine.RuntimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProcessController {

    @Autowired
    RuntimeService runtimeService;

    @PostMapping("run")
    public void run() {
        runtimeService.startProcessInstanceByKey("MyProcess");
    }


}
