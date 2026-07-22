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

import java.util.List;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Process;
import org.flowable.common.engine.impl.util.io.BytesStreamSource;
import org.springframework.stereotype.Component;

/**
 * Reads what the platform needs to know out of a BPMN file: its process key and name. Pure parsing —
 * no engine, no database, no deployment.
 * <p>
 * This runs <b>before</b> anything is registered, so a file that cannot become a working definition is
 * rejected while a human is still looking at the error, instead of failing later at deployment or,
 * worse, silently sitting there as a definition nobody can start.
 *
 * @author peter haller
 */
@Component
public class BpmnDefinitionReader {

    /**
     * Flowable's safe-XML mode, its documented switch against XXE. The content is uploaded by users,
     * so this stays on — although a negative check (2026-07-22) showed the protection does not
     * actually depend on it: the XML reader underneath already refuses a document carrying a DTD,
     * with or without this flag. Kept as defence in depth, not as the only line of it.
     */
    private static final boolean SAFE_XML = true;

    /**
     * Schema validation is deliberately off: real-world diagrams carry vendor extension elements
     * (Camunda's, for one) that a strict BPMN 2.0 schema check rejects although Flowable deploys and
     * runs them fine. The checks below cover what actually matters.
     */
    private static final boolean VALIDATE_SCHEMA = false;

    /** What a BPMN file tells us about the process it defines. */
    public record BpmnDefinitionInfo(String processKey, String processName) {}

    /**
     * Extracts the single process defined by {@code content}.
     *
     * @param content the raw BPMN 2.0 XML
     * @return the process key (the {@code <process id="…">}) and its name, the name being {@code null}
     *         when the diagram does not carry one
     * @throws IllegalArgumentException if the content is not readable BPMN, defines no process,
     *                                  defines more than one, or defines one that could never be
     *                                  started
     */
    public BpmnDefinitionInfo read(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("The process definition is empty.");
        }

        BpmnModel model;
        try {
            model = new BpmnXMLConverter()
                    .convertToBpmnModel(new BytesStreamSource(content), VALIDATE_SCHEMA, SAFE_XML, "UTF-8");
        } catch (Exception e) {
            throw new IllegalArgumentException("Not a readable BPMN 2.0 file: " + e.getMessage());
        }

        List<Process> processes = model.getProcesses();
        if (processes.isEmpty()) {
            throw new IllegalArgumentException("The file contains no BPMN process.");
        }
        if (processes.size() > 1) {
            // One document = one definition keeps the mapping to the document history comprehensible.
            // Collaborations with several pools can be allowed later if a real case turns up.
            throw new IllegalArgumentException(
                    "The file contains " + processes.size() + " BPMN processes; exactly one is expected.");
        }

        Process process = processes.getFirst();
        String key = process.getId();
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("The BPMN process has no id to use as its key.");
        }
        if (!process.isExecutable()) {
            // Flowable deploys these happily and then refuses to start them, which reads like a bug.
            throw new IllegalArgumentException(
                    "The BPMN process '" + key + "' is not executable and could never be started.");
        }

        String name = process.getName();
        return new BpmnDefinitionInfo(key, name == null || name.isBlank() ? null : name);
    }
}
