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

import de.hallerweb.enterprise.prioritize.model.nfc.NfcUnit.NfcUnitType;

import java.time.Instant;

/**
 * Wire format of an NFC scan broadcast on the MQTT status channel. The {@code type} discriminator
 * ({@value #TYPE}) mirrors the inbound convention (STATUS/DISCOVERY/VALUE), so consumers can route
 * on it the same way.
 * <p>
 * {@code trackingForScanner} is the clock state of the person named in {@code scannedBy}, not of the
 * task: several people can be clocked in on one task at once. Named for the scanner rather than "me"
 * because a broadcast is read by somebody else entirely.
 *
 * @author peter haller
 */
public record NfcScanBroadcast(String type,
                               String uuid,
                               NfcUnitType nfcType,
                               String action,
                               Long resourceId,
                               Long taskId,
                               Boolean trackingForScanner,
                               long sequenceNumber,
                               String scannedBy,
                               Instant timestamp) {

    /** Discriminator identifying an NFC scan event. */
    public static final String TYPE = "NFC_SCAN";

    /** Builds the wire payload from a raised {@link NfcScannedEvent}. */
    public static NfcScanBroadcast from(NfcScannedEvent event) {
        var r = event.result();
        return new NfcScanBroadcast(TYPE, r.uuid(), r.type(), r.action(),
                event.resourceId(), r.taskId(), r.trackingForMe(), r.sequenceNumber(),
                event.scannedBy(), event.scannedAt());
    }
}
