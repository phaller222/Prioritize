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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.hallerweb.enterprise.prioritize.service.process.BpmnDefinitionReader.BpmnDefinitionInfo;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BpmnDefinitionReader} — plain parsing, no Spring context.
 *
 * @author peter haller
 */
class BpmnDefinitionReaderTest {

    private final BpmnDefinitionReader reader = new BpmnDefinitionReader();

    private static byte[] bpmn(String processes) {
        return ("""
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             targetNamespace="http://prioritize.test">
                """ + processes + "</definitions>").getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("reads the process key and name of a well-formed definition")
    void readsKeyAndName() {
        BpmnDefinitionInfo info = reader.read(bpmn("""
                  <process id="orderHandling" name="Order handling" isExecutable="true">
                    <startEvent id="start"/>
                  </process>
                """));

        assertEquals("orderHandling", info.processKey());
        assertEquals("Order handling", info.processName());
    }

    @Test
    @DisplayName("reports a missing process name as null rather than as an empty string")
    void missingNameBecomesNull() {
        BpmnDefinitionInfo info = reader.read(bpmn("""
                  <process id="unnamed" name="" isExecutable="true">
                    <startEvent id="start"/>
                  </process>
                """));

        assertEquals("unnamed", info.processKey());
        assertNull(info.processName());
    }

    @Test
    @DisplayName("reads the real Camunda-exported test process, vendor extensions and all")
    void readsTheCamundaExportedFixture() throws IOException {
        byte[] content;
        try (InputStream in = getClass().getResourceAsStream("/processes/TestProcess.bpmn")) {
            assertNotNull(in, "test fixture /processes/TestProcess.bpmn is missing");
            content = in.readAllBytes();
        }

        // Proves the deliberate choice not to validate against the schema: this file carries Camunda
        // extension elements a strict check would reject, while Flowable parses and runs it fine.
        assertEquals("MyProcess", reader.read(content).processKey());
    }

    @Test
    @DisplayName("rejects empty content")
    void rejectsEmptyContent() {
        assertThrows(IllegalArgumentException.class, () -> reader.read(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> reader.read(null));
    }

    @Test
    @DisplayName("rejects content that is not BPMN at all")
    void rejectsGarbage() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> reader.read("this is not a diagram".getBytes(StandardCharsets.UTF_8)));

        assertTrue(ex.getMessage().contains("BPMN"), ex.getMessage());
    }

    @Test
    @DisplayName("rejects a file that defines no process")
    void rejectsFileWithoutProcess() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> reader.read(bpmn("")));

        assertTrue(ex.getMessage().contains("no BPMN process"), ex.getMessage());
    }

    @Test
    @DisplayName("rejects a file defining more than one process — one document is one definition")
    void rejectsSeveralProcesses() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> reader.read(bpmn("""
                  <process id="first" isExecutable="true">
                    <startEvent id="s1"/>
                  </process>
                  <process id="second" isExecutable="true">
                    <startEvent id="s2"/>
                  </process>
                """)));

        assertTrue(ex.getMessage().contains("2 BPMN processes"), ex.getMessage());
    }

    @Test
    @DisplayName("rejects a non-executable process instead of letting it deploy undeployable")
    void rejectsNonExecutableProcess() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> reader.read(bpmn("""
                  <process id="documentationOnly" isExecutable="false">
                    <startEvent id="start"/>
                  </process>
                """)));

        assertTrue(ex.getMessage().contains("not executable"), ex.getMessage());
    }

    @Test
    @DisplayName("does not resolve external entities — uploaded content must not read the server's files")
    void doesNotResolveExternalEntities() {
        byte[] xxe = ("""
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE definitions [ <!ENTITY secret SYSTEM "file:///etc/passwd"> ]>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             targetNamespace="http://prioritize.test">
                  <process id="xxe&secret;" isExecutable="true">
                    <startEvent id="start"/>
                  </process>
                </definitions>
                """).getBytes(StandardCharsets.UTF_8);

        // Either the parser refuses the DOCTYPE outright — which is what actually happens today, at the
        // XML-reader level and independently of Flowable's safe-XML flag — or it leaves the entity
        // unresolved. What must never happen is a definition whose key carries a server file's content.
        try {
            BpmnDefinitionInfo info = reader.read(xxe);
            assertEquals("xxe", info.processKey(), "external entity was resolved into the process key");
        } catch (IllegalArgumentException expected) {
            // refused outright — equally fine
        }
    }
}
