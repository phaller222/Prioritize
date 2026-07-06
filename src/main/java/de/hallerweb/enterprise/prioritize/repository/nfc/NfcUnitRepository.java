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

package de.hallerweb.enterprise.prioritize.repository.nfc;

import de.hallerweb.enterprise.prioritize.model.nfc.NfcUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NfcUnitRepository extends JpaRepository<NfcUnit, Long> {

    /** Resolves a tag by the UUID a scan arrives with. */
    Optional<NfcUnit> findByUuid(String uuid);

    /** All tags mounted on a given resource. */
    List<NfcUnit> findByResource_Id(Long resourceId);

    /** Tags bound to a given task (a TIMETRACKER tag); used to detach on task deletion. */
    List<NfcUnit> findByTask_Id(Long taskId);
}
