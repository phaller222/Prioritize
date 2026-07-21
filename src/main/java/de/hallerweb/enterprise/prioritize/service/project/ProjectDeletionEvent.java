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

package de.hallerweb.enterprise.prioritize.service.project;

/**
 * Domain event raised by {@link ProjectService} <b>before</b> a project row is removed, so that
 * everything referencing it can be cleaned up first.
 * <p>
 * Unlike {@code TelemetryThresholdEvent} and the NFC events, this one must <b>not</b> be observed
 * {@code AFTER_COMMIT}: listeners are part of the deletion itself and run synchronously inside the
 * deleting transaction — after the commit the foreign keys would already have failed. A plain
 * {@code @EventListener} is therefore the correct (and only) way to handle it.
 * <p>
 * The point of the indirection is dependency direction: satellite concerns may depend on
 * {@code project}, but {@code project} must not learn about each of them. A package that attaches
 * its own entities to a project (as {@code scheduling} does) subscribes here instead of having
 * {@link ProjectService} call into it.
 *
 * @author peter haller
 */
public record ProjectDeletionEvent(Long projectId) {
}
