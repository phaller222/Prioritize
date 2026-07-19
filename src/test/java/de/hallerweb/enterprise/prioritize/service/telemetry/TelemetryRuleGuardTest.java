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

package de.hallerweb.enterprise.prioritize.service.telemetry;

import de.hallerweb.enterprise.prioritize.repository.telemetry.TelemetryRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link TelemetryRuleGuard} — seeding and per-resource refresh. No Spring.
 *
 * @author peter haller
 */
class TelemetryRuleGuardTest {

    private TelemetryRuleRepository repo;
    private TelemetryRuleGuard guard;

    @BeforeEach
    void setUp() {
        repo = mock(TelemetryRuleRepository.class);
        guard = new TelemetryRuleGuard(repo);
    }

    @Test
    @DisplayName("seed: übernimmt die Resource-Ids mit aktiven Regeln")
    void seed_populatesFromRepository() {
        when(repo.findResourceIdsWithEnabledRules()).thenReturn(List.of(7L, 9L));

        guard.seed();

        assertTrue(guard.mightHaveRules(7L));
        assertTrue(guard.mightHaveRules(9L));
        assertFalse(guard.mightHaveRules(1L));
    }

    @Test
    @DisplayName("refresh: nimmt Resource auf, wenn sie jetzt eine aktive Regel hat")
    void refresh_addsWhenNowHasEnabledRule() {
        when(repo.existsByResource_IdAndEnabledTrue(7L)).thenReturn(true);

        guard.refresh(7L);

        assertTrue(guard.mightHaveRules(7L));
    }

    @Test
    @DisplayName("refresh: entfernt Resource, wenn keine aktive Regel mehr existiert")
    void refresh_removesWhenNoEnabledRuleLeft() {
        when(repo.findResourceIdsWithEnabledRules()).thenReturn(List.of(7L));
        guard.seed();
        assertTrue(guard.mightHaveRules(7L));

        when(repo.existsByResource_IdAndEnabledTrue(7L)).thenReturn(false);
        guard.refresh(7L);

        assertFalse(guard.mightHaveRules(7L));
    }
}
