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

package de.hallerweb.enterprise.prioritize.dto.skill;

import de.hallerweb.enterprise.prioritize.model.cost.CostRateUnit;
import de.hallerweb.enterprise.prioritize.model.skill.QualificationLevel;

import java.math.BigDecimal;

/**
 * Flat view of a {@link QualificationLevel}, including its cost rate. This is the only place the rate
 * is published: it is not carried on a user payload, so reading it goes through the permission check on
 * the qualification-level endpoints rather than falling out of every {@code GET /users}.
 *
 * @author peter haller
 */
public record QualificationLevelDTO(Long id,
                                    String name,
                                    String description,
                                    BigDecimal costRate,
                                    String costCurrency,
                                    CostRateUnit costRateUnit) {

    /** Maps an entity to its DTO. */
    public static QualificationLevelDTO from(QualificationLevel level) {
        if (level == null) {
            return null;
        }
        return new QualificationLevelDTO(
                level.getId(),
                level.getName(),
                level.getDescription(),
                level.getCostRate(),
                level.getCostCurrency(),
                level.getCostRateUnit());
    }
}
