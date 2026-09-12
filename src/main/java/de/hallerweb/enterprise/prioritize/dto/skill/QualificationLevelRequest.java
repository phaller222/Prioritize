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
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Request body for creating or updating a {@link QualificationLevel}. The three cost fields are written
 * as a unit — sending all three sets the rate, sending none leaves the level unpriced, and sending some
 * of them is rejected. On an update the body is the new state of the level, so omitting the cost fields
 * clears the rate: this is a PUT, not a patch, and a level that should stop being costed has to have
 * some way of saying so.
 *
 * @author peter haller
 */
public record QualificationLevelRequest(
        @Schema(description = "What the level is called, e.g. Geselle", example = "Geselle")
        String name,

        @Schema(description = "What the level means in this business")
        String description,

        @Schema(description = "What an hour, day or use of this qualification costs. Set with currency and unit, or omit all three.",
                example = "48.00")
        BigDecimal costRate,

        @Schema(description = "ISO 4217 code of the cost rate", example = "EUR")
        String costCurrency,

        @Schema(description = "What the cost rate is charged per")
        CostRateUnit costRateUnit) {

    /** Builds a fresh, id-less {@link QualificationLevel} from the request. */
    public QualificationLevel toQualificationLevel() {
        return QualificationLevel.builder()
                .name(name)
                .description(description)
                .costRate(costRate)
                .costCurrency(costCurrency)
                .costRateUnit(costRateUnit)
                .build();
    }
}
