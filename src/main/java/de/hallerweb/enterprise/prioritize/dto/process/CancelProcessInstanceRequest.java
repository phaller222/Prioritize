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

/**
 * Why a process instance was cancelled. The reason is optional but asked for on purpose: it ends up
 * in the engine's history, where it is the only explanation anybody will find later.
 *
 * @param reason free text; when absent, the platform records who cancelled
 *
 * @author peter haller
 */
public record CancelProcessInstanceRequest(String reason) {
}
