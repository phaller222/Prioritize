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

package de.hallerweb.enterprise.prioritize.controller.nfc;

import de.hallerweb.enterprise.prioritize.config.CurrentUserResolver;
import de.hallerweb.enterprise.prioritize.controller.nfc.NfcUnitController.NfcUnitRequest;
import de.hallerweb.enterprise.prioritize.model.nfc.NfcUnit;
import de.hallerweb.enterprise.prioritize.model.nfc.NfcUnit.NfcUnitType;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.nfc.NfcUnitService;
import de.hallerweb.enterprise.prioritize.service.nfc.NfcUnitService.ScanResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NfcUnitController}: delegation, status codes and request validation.
 * Plain Mockito (no Spring context).
 */
class NfcUnitControllerTest {

    private NfcUnitService nfcUnitService;
    private NfcUnitController controller;

    private final Authentication auth = mock(Authentication.class);
    private final PUser user = new PUser();

    @BeforeEach
    void setUp() {
        nfcUnitService = mock(NfcUnitService.class);
        CurrentUserResolver resolver = mock(CurrentUserResolver.class);
        controller = new NfcUnitController(nfcUnitService, resolver);
        when(resolver.resolve(auth)).thenReturn(user);
    }

    @Test
    @DisplayName("registerNfcUnit: delegates and answers 201 Created")
    void registerNfcUnit_created() {
        when(nfcUnitService.registerNfcUnit(eq(7L), any(), eq(user))).thenReturn(new NfcUnit());

        ResponseEntity<NfcUnit> response = controller.registerNfcUnit(
                7L, new NfcUnitRequest("uuid-1", "Tag", "d", NfcUnitType.TIMETRACKER, null), auth);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(nfcUnitService).registerNfcUnit(eq(7L), any(), eq(user));
    }

    @Test
    @DisplayName("registerNfcUnit: blank uuid is rejected before delegation")
    void registerNfcUnit_blankUuid_throws() {
        assertThrows(IllegalArgumentException.class, () -> controller.registerNfcUnit(
                7L, new NfcUnitRequest("", "Tag", "d", NfcUnitType.COUNTER, null), auth));
        verifyNoInteractions(nfcUnitService);
    }

    @Test
    @DisplayName("registerNfcUnit: missing type is rejected before delegation")
    void registerNfcUnit_missingType_throws() {
        assertThrows(IllegalArgumentException.class, () -> controller.registerNfcUnit(
                7L, new NfcUnitRequest("uuid-1", "Tag", "d", null, null), auth));
        verifyNoInteractions(nfcUnitService);
    }

    @Test
    @DisplayName("bindTask: delegates to the service")
    void bindTask_delegates() {
        when(nfcUnitService.bindTask(eq(3L), eq(9L), eq(user))).thenReturn(new NfcUnit());
        controller.bindTask(3L, 9L, auth);
        verify(nfcUnitService).bindTask(eq(3L), eq(9L), eq(user));
    }

    @Test
    @DisplayName("scan: delegates and returns the scan result")
    void scan_delegates() {
        ScanResult result = new ScanResult("uuid-1", NfcUnitType.TIMETRACKER, "TRACKING_STARTED", 9L, true, 0);
        when(nfcUnitService.scan(eq("uuid-1"), eq(user))).thenReturn(result);

        ResponseEntity<ScanResult> response = controller.scan("uuid-1", auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(result, response.getBody());
        verify(nfcUnitService).scan(eq("uuid-1"), eq(user));
    }

    @Test
    @DisplayName("deleteNfcUnit: delegates and answers 204 No Content")
    void deleteNfcUnit_noContent() {
        ResponseEntity<Void> response = controller.deleteNfcUnit(3L, auth);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(nfcUnitService).deleteNfcUnit(eq(3L), eq(user));
    }
}
