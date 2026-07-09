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

package de.hallerweb.enterprise.prioritize.service.nfc;

import de.hallerweb.enterprise.prioritize.service.nfc.NfcUnitService.ScanResult;

import java.time.Instant;

/**
 * Domain event raised by {@link NfcUnitService#scan} once a tag scan has been processed. It carries
 * the {@link ScanResult} plus the scan context (which resource the tag sits on, who scanned it and
 * when).
 * <p>
 * Publishing this event keeps {@link NfcUnitService} free of any transport dependency: any interested
 * component may listen (e.g. {@link NfcScanMqttBridge} broadcasts it over MQTT). Listeners should
 * observe it {@code AFTER_COMMIT} so that only actually persisted scans are propagated.
 *
 * @author peter haller
 */
public record NfcScannedEvent(ScanResult result,
                              Long resourceId,
                              String scannedBy,
                              Instant scannedAt) {
}
