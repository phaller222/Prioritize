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
import de.hallerweb.enterprise.prioritize.model.nfc.NfcUnit;
import de.hallerweb.enterprise.prioritize.model.nfc.NfcUnit.NfcUnitType;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.nfc.NfcUnitService;
import de.hallerweb.enterprise.prioritize.service.nfc.NfcUnitService.NfcUnitData;
import de.hallerweb.enterprise.prioritize.service.nfc.NfcUnitService.ScanResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for {@link NfcUnit NFC tags} and scans. Tags are managed under their owning
 * resource (management requires resource UPDATE); a scan is a resource-independent physical event.
 *
 * @author peter haller
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NfcUnitController {

    private final NfcUnitService nfcUnitService;
    private final CurrentUserResolver currentUserResolver;

    private PUser getCurrentUser(Authentication auth) {
        return currentUserResolver.resolve(auth);
    }

    @PostMapping("/resources/{resourceId}/nfc-units")
    public ResponseEntity<NfcUnit> registerNfcUnit(
        @PathVariable Long resourceId, @RequestBody NfcUnitRequest request, Authentication auth) {
        NfcUnit unit = nfcUnitService.registerNfcUnit(resourceId, request.toData(), getCurrentUser(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(unit);
    }

    @GetMapping("/resources/{resourceId}/nfc-units")
    public ResponseEntity<List<NfcUnit>> getNfcUnits(@PathVariable Long resourceId, Authentication auth) {
        return ResponseEntity.ok(nfcUnitService.getNfcUnitsForResource(resourceId, getCurrentUser(auth)));
    }

    @PutMapping("/nfc-units/{id}/task/{taskId}")
    public ResponseEntity<NfcUnit> bindTask(
        @PathVariable Long id, @PathVariable Long taskId, Authentication auth) {
        return ResponseEntity.ok(nfcUnitService.bindTask(id, taskId, getCurrentUser(auth)));
    }

    @DeleteMapping("/nfc-units/{id}/task")
    public ResponseEntity<NfcUnit> unbindTask(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(nfcUnitService.unbindTask(id, getCurrentUser(auth)));
    }

    @DeleteMapping("/nfc-units/{id}")
    public ResponseEntity<Void> deleteNfcUnit(@PathVariable Long id, Authentication auth) {
        nfcUnitService.deleteNfcUnit(id, getCurrentUser(auth));
        return ResponseEntity.noContent().build();
    }

    /**
     * Processes a scan of the tag with the given uuid (e.g. from a reader device). For a
     * TIMETRACKER this toggles the bound task's time tracking.
     */
    @PostMapping("/nfc/scan/{uuid}")
    public ResponseEntity<ScanResult> scan(@PathVariable String uuid, Authentication auth) {
        return ResponseEntity.ok(nfcUnitService.scan(uuid, getCurrentUser(auth)));
    }

    /**
     * Request body for registering a tag. {@code uuid} and {@code type} are mandatory.
     */
    public record NfcUnitRequest(String uuid, String name, String description,
                                 NfcUnitType type, String payload) {
        NfcUnitData toData() {
            if (uuid == null || uuid.isBlank()) {
                throw new IllegalArgumentException("uuid is required.");
            }
            if (type == null) {
                throw new IllegalArgumentException("type is required.");
            }
            return new NfcUnitData(uuid, name, description, type, payload);
        }
    }
}
