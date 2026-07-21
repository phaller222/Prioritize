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

package de.hallerweb.enterprise.prioritize.controller;

import de.hallerweb.enterprise.prioritize.dto.ApiError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for the two mappings that exist because their absence produced a <b>misleading 403</b>:
 * an unreadable request body and a rejected database constraint. Both used to escape this advice, and
 * the resulting error dispatch was turned into a permission error by the security chain. Plain JUnit,
 * no Spring context — the advice is a plain object.
 *
 * @author peter haller
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("handleUnreadableBody: 400 statt der irreführenden 403, ohne interne Details")
    void unreadableBody_isBadRequest() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error: Cannot map `null` into type `boolean`",
                new RuntimeException("de.hallerweb.enterprise.prioritize.model.security.PUser[\"active\"]"),
                null);

        ResponseEntity<ApiError> response = handler.handleUnreadableBody(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiError body = response.getBody();
        assertNotNull(body);
        assertEquals(HttpStatus.BAD_REQUEST.value(), body.status());
        assertFalse(body.message().contains("de.hallerweb"),
                "die Antwort darf keine internen Klassen-/Feldnamen preisgeben");
    }

    @Test
    @DisplayName("handleDataIntegrityViolation: 409, ohne die DB-Meldung durchzureichen")
    void dataIntegrityViolation_isConflict() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "Referential integrity constraint violation: FKxyz TASK_SCHEDULE FOREIGN KEY(PROJECT_ID)");

        ResponseEntity<ApiError> response = handler.handleDataIntegrityViolation(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        ApiError body = response.getBody();
        assertNotNull(body);
        assertEquals(HttpStatus.CONFLICT.value(), body.status());
        assertFalse(body.message().contains("FOREIGN KEY"),
                "die Antwort darf die rohe DB-Meldung nicht durchreichen");
    }
}
