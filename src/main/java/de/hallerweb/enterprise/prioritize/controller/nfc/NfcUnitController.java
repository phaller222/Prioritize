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

import de.hallerweb.enterprise.prioritize.config.AuthenticatedUser;
import de.hallerweb.enterprise.prioritize.dto.nfc.NfcUnitDTO;
import de.hallerweb.enterprise.prioritize.model.nfc.NfcUnit;
import de.hallerweb.enterprise.prioritize.model.nfc.NfcUnit.NfcUnitType;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.nfc.NfcUnitService;
import de.hallerweb.enterprise.prioritize.service.nfc.NfcUnitService.NfcUnitData;
import de.hallerweb.enterprise.prioritize.service.nfc.NfcUnitService.ScanResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

/**
 * REST endpoints for {@link NfcUnit NFC tags} and scans. Tags are managed under their owning
 * resource (management requires resource UPDATE); a scan is a resource-independent physical event.
 *
 * @author peter haller
 */
@Tag(name = "NFC", description = "Register NFC tags on resources, bind them to tasks, and record scans.")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NfcUnitController {

    private final NfcUnitService nfcUnitService;

    @Operation(summary = "Register an NFC unit")
    @PostMapping("/resources/{resourceId}/nfc-units")
    public ResponseEntity<NfcUnitDTO> registerNfcUnit(
        @PathVariable Long resourceId, @RequestBody NfcUnitRequest request, @AuthenticatedUser PUser currentUser) {
        NfcUnit unit = nfcUnitService.registerNfcUnit(resourceId, request.toData(), currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(NfcUnitDTO.from(unit));
    }

    @Operation(summary = "Get the NFC units")
    @GetMapping("/resources/{resourceId}/nfc-units")
    public ResponseEntity<List<NfcUnitDTO>> getNfcUnits(@PathVariable Long resourceId, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(nfcUnitService.getNfcUnitsForResource(resourceId, currentUser)
                .stream().map(NfcUnitDTO::from).toList());
    }

    @Operation(summary = "Bind task")
    @PutMapping("/nfc-units/{id}/task/{taskId}")
    public ResponseEntity<NfcUnitDTO> bindTask(
        @PathVariable Long id, @PathVariable Long taskId, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(NfcUnitDTO.from(nfcUnitService.bindTask(id, taskId, currentUser)));
    }

    @Operation(summary = "Unbind task")
    @DeleteMapping("/nfc-units/{id}/task")
    public ResponseEntity<NfcUnitDTO> unbindTask(@PathVariable Long id, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(NfcUnitDTO.from(nfcUnitService.unbindTask(id, currentUser)));
    }

    @Operation(summary = "Delete an NFC unit")
    @DeleteMapping("/nfc-units/{id}")
    public ResponseEntity<Void> deleteNfcUnit(@PathVariable Long id, @AuthenticatedUser PUser currentUser) {
        nfcUnitService.deleteNfcUnit(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    /**
     * Processes a scan of the tag with the given uuid (e.g. from a reader device). For a
     * TIMETRACKER this toggles the bound task's time tracking.
     */
    @Operation(summary = "Process an NFC tag scan by its uuid")
    @PostMapping("/nfc/scan/{uuid}")
    public ResponseEntity<ScanResult> scan(@PathVariable String uuid, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(nfcUnitService.scan(uuid, currentUser));
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
