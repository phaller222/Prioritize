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

package de.hallerweb.enterprise.prioritize.model.process;

/**
 * Lifecycle of a {@link ProcessDefinition}. Registering a definition never deploys it — activation is
 * a separate, deliberate act, so that editing a diagram cannot break production instantly.
 *
 * @author peter haller
 */
public enum ProcessDefinitionState {

    /** Registered and readable, but not deployed to the engine. No instance can be started. */
    DRAFT,

    /** Deployed and startable. */
    ACTIVE,

    /**
     * Deployed but suspended: no new instances can be started, while instances that are already
     * running continue. Deactivating never deletes a deployment — running instances are real work.
     */
    SUSPENDED
}
