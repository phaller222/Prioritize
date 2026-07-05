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
 * Thrown when a control command could be delivered to a device,
 * but the device was unreachable or responded with an error
 * (connection refused, timeout, HTTP 4xx/5xx).
 * <p>
 * Mapped to HTTP 502 (Bad Gateway) in the GlobalExceptionHandler: the backend
 * itself works, but the downstream device did not accept the control command.
 */
public class ResourceCommandFailedException extends RuntimeException {

    public ResourceCommandFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}