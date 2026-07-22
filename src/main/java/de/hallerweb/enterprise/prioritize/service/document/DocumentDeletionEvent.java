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

package de.hallerweb.enterprise.prioritize.service.document;

/**
 * Domain event raised by {@link DocumentService} <b>before</b> a document is removed, so that
 * everything referencing it can clean up — or object.
 * <p>
 * A plain {@code @EventListener}, never {@code AFTER_COMMIT}: listeners run synchronously inside the
 * deleting transaction, which is what makes both possible outcomes work. A listener that removes its
 * own rows keeps the foreign keys satisfied; a listener that throws vetoes the whole deletion, and it
 * rolls back as one.
 * <p>
 * The point of the indirection is dependency direction, exactly as with {@code ProjectDeletionEvent}:
 * satellite packages may depend on {@code document}, but {@code document} must not learn about each of
 * them.
 *
 * @author peter haller
 */
public record DocumentDeletionEvent(Long documentInfoId) {
}
