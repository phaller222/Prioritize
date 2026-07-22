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

package de.hallerweb.enterprise.prioritize.dto.process;

import java.util.Map;

/**
 * What a caller has to say to start a process: which registered definition, and optionally the
 * initial process variables the diagram needs.
 * <p>
 * There is deliberately no business key here. What an instance is about is derived from the project
 * or task it is started for — see {@code ProcessInstanceService}.
 *
 * @param definitionId the registered, active definition to start
 * @param variables    initial process variables, or {@code null}; the platform's own
 *                     ({@code projectId}, {@code taskId}, {@code startedBy}) always win
 *
 * @author peter haller
 */
public record StartProcessInstanceRequest(Long definitionId, Map<String, Object> variables) {
}
