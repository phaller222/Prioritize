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

package de.hallerweb.enterprise.prioritize.service.resource.control.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.hallerweb.enterprise.prioritize.exception.ResourceCommandFailedException;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises the real REST roundtrip of {@link RestResourceControlAdapter} against a
 * local in-process HTTP listener (no mocked HTTP client, no external device).
 * This makes the positive path reproducible and pins the JSON wire format
 * ({@code command}, {@code param}, {@code slot}).
 */
class RestResourceControlAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestResourceControlAdapter adapter = new RestResourceControlAdapter();

    private LocalRestCommandListener listener;
    private Resource resource;

    @BeforeEach
    void setUp() throws IOException {
        listener = LocalRestCommandListener.start();

        resource = new Resource();
        resource.setId(703L);
        resource.setIp("127.0.0.1");
        resource.setPort(listener.getPort());
    }

    @AfterEach
    void tearDown() {
        listener.close();
    }

    @Test
    @DisplayName("Positive roundtrip: command is POSTed to /command with the reserved slot")
    void sendCommand_postsJsonToCommandEndpoint() throws Exception {
        adapter.sendCommand(resource, "ON", "1", 2);

        List<LocalRestCommandListener.ReceivedRequest> requests = listener.getReceivedRequests();
        assertEquals(1, requests.size(), "exactly one request expected");

        LocalRestCommandListener.ReceivedRequest req = requests.get(0);
        assertEquals("POST", req.method());
        assertEquals("/command", req.path());

        JsonNode body = objectMapper.readTree(req.body());
        assertEquals("ON", body.path("command").asText());
        assertEquals("1", body.path("param").asText());
        assertEquals(2, body.path("slot").asInt());
    }

    @Test
    @DisplayName("Device rejects the command (HTTP 500) -> ResourceCommandFailedException")
    void sendCommand_serverError_throwsCommandFailed() {
        listener.respondWithStatus(500);

        assertThrows(ResourceCommandFailedException.class,
            () -> adapter.sendCommand(resource, "ON", null, 1));
    }
}
