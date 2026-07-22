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

package de.hallerweb.enterprise.prioritize.service.process;

import de.hallerweb.enterprise.prioritize.model.process.ProcessDefinition;
import de.hallerweb.enterprise.prioritize.repository.process.ProcessDefinitionRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.flowable.engine.RepositoryService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reports, once at startup, which process definitions the engine answers to that the registry does not
 * know about.
 * <p>
 * Two ways into the engine exist on purpose: the registry, where a definition is a versioned document
 * with permissions and an explicit activation, and the classpath directory, which stays the trusted
 * root a platform admin deploys from directly. The second one is the bootstrap and the break-glass
 * path — an approval process that could only be activated through a process that must itself be
 * activated would be dead at the first incident.
 * <p>
 * Two ways in are fine; two ways in that nobody can see are not. This log line is the cheapest possible
 * answer to "what is actually deployed here, and where did it come from".
 *
 * @author peter haller
 */
@Component
@RequiredArgsConstructor
@Log4j2
public class ProcessDefinitionStartupReport {

    private final RepositoryService repositoryService;
    private final ProcessDefinitionRepository definitionRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void reportDeploymentsOutsideTheRegistry() {
        Set<String> registered = definitionRepository.findAll().stream()
                .map(ProcessDefinition::getProcessKey)
                .collect(Collectors.toSet());

        List<String> outsideTheRegistry = repositoryService.createProcessDefinitionQuery().latestVersion().list()
                .stream()
                .map(org.flowable.engine.repository.ProcessDefinition::getKey)
                .filter(key -> !registered.contains(key))
                .distinct()
                .toList();

        if (outsideTheRegistry.isEmpty()) {
            log.info("Process definitions: {} registered, none deployed outside the registry.", registered.size());
        } else {
            log.info("Process definitions: {} registered; {} deployed outside the registry (trusted root): {}",
                    registered.size(), outsideTheRegistry.size(), outsideTheRegistry);
        }
    }
}
