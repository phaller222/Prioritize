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
 * Thrown when the platform's trusted control path targets a resource slot that currently holds an
 * active reservation held by someone else.
 * <p>
 * A reservation is the promise that its slot is under the holder's exclusive control for the reserved
 * window. A BPMN process, acting under the system principal, supplies a slot explicitly instead of
 * holding a reservation of its own; it is therefore only allowed onto <em>free</em> slots. Letting it
 * command a reserved slot would let a process seize a human's reserved control window — deliberately
 * refused rather than silently overriding.
 * <p>
 * Distinct from {@link SlotNotReservedException} (which is the user path missing a slot of their own).
 * Mapped to HTTP 409 (Conflict) in the {@code GlobalExceptionHandler}.
 *
 * @author peter haller
 */
public class SlotOccupiedException extends RuntimeException {

    public SlotOccupiedException(String message) {
        super(message);
    }
}
