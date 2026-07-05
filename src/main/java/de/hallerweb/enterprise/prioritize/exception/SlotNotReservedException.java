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
 * Thrown when a control command is to be sent to a resource,
 * but the executing user has no active reservation
 * (and thus no assigned slot) on that resource at the current time.
 * <p>
 * A command's slot is derived exclusively from the user's active
 * reservation; without a reservation there is no valid slot and therefore
 * no deliverable command. Deliberately not a silent failure.
 * <p>
 * Mapped to HTTP 409 (Conflict) in the {@code GlobalExceptionHandler}.
 *
 * @author peter haller
 */
public class SlotNotReservedException extends RuntimeException {

    public SlotNotReservedException(String message) {
        super(message);
    }
}