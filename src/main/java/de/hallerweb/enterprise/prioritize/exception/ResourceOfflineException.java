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

package de.hallerweb.enterprise.prioritize.exception;

/**
 * Thrown when a control command cannot be delivered because the resource
 * has no reachable transport (e.g. the MQTT resource is offline and has
 * no REST endpoint of its own).
 * <p>
 * Deliberately not a silent failure: the caller receives a clear error message.
 * Mapped to HTTP 503 (Service Unavailable) in the {@code GlobalExceptionHandler}.
 *
 * @author peter haller
 */
public class ResourceOfflineException extends RuntimeException {

    public ResourceOfflineException(String message) {
        super(message);
    }
}
