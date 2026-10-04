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

package de.hallerweb.enterprise.prioritize.service.resource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for the latest-value history cap in {@link ResourceService#appendCapped}: it must
 * always fit the {@code varchar(255)} column, keep the newest values, and still keep up to 100 short
 * ones.
 */
class ValueHistoryCapTest {

    @Test
    @DisplayName("Long values: the history never exceeds the column and ends with the newest value")
    void longValues_stayWithinColumn() {
        String history = null;
        for (int i = 0; i < 40; i++) {
            history = ResourceService.appendCapped(history, String.format("090149534b%010d", i));
        }

        assertTrue(history.length() <= ResourceService.MAX_HISTORY_CHARS, history.length() + " chars");
        assertTrue(history.endsWith("090149534b0000000039"), history);
        assertEquals(12, history.split(",").length, "12 × 20 chars + 11 commas = 251 fit, a 13th does not");
    }

    @Test
    @DisplayName("Short values: still capped at 100 entries")
    void shortValues_cappedAtHundred() {
        String history = null;
        for (int i = 0; i < 150; i++) {
            history = ResourceService.appendCapped(history, String.valueOf(i % 2));
        }

        assertEquals(100, history.split(",").length);
        assertTrue(history.length() <= ResourceService.MAX_HISTORY_CHARS);
    }

    @Test
    @DisplayName("A single value longer than the column is cut to fit and replaces the history")
    void oversizedValue_isTruncated() {
        String history = ResourceService.appendCapped("1,2,3", "x".repeat(300));

        assertEquals(ResourceService.MAX_HISTORY_CHARS, history.length());
        assertTrue(history.chars().allMatch(c -> c == 'x'));
    }

    @Test
    @DisplayName("First value starts the history")
    void firstValue() {
        assertEquals("21", ResourceService.appendCapped(null, "21"));
        assertEquals("21,22", ResourceService.appendCapped("21", "22"));
    }
}
