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

package de.hallerweb.enterprise.prioritize.service.resource.control;

/**
 * JSON wire format for a control command sent to a resource.
 * <p>
 * Replaces the old colon-delimited text format ({@code COMMAND;PARAM:SLOT}) with
 * an extensible, self-describing JSON object. New fields can be added
 * without breaking existing device parsers.
 * <p>
 * Example payload:
 * <pre>{ "command": "SET_TEMP", "param": "21", "slot": 1 }</pre>
 *
 * @param command command identifier
 * @param param   optional free parameter value (may be {@code null})
 * @param slot    the caller's reserved slot (0 if not slot-bound)
 */
public record ResourceCommandMessage(String command, String param, int slot) {
}
