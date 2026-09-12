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

package de.hallerweb.enterprise.prioritize.model.skill;

import de.hallerweb.enterprise.prioritize.model.PObject;
import de.hallerweb.enterprise.prioritize.model.cost.CostRateUnit;
import de.hallerweb.enterprise.prioritize.model.security.PAuthorizedObject;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * What an hour of somebody's work is costed at, held by qualification rather than by person — a
 * journeyman's hour, a master's hour, an apprentice's hour.
 * <p>
 * <b>The rate belongs to the level, not to the person.</b> That is the whole point of the entity. An
 * hourly rate written next to a name is a salary figure, and a system that keeps one invites exactly
 * the performance monitoring this platform decided against; the same number written next to
 * "Journeyman" is an operational figure that costs a job out. It is also the only unambiguous place
 * for it: a {@code Role} carries permissions and a person has several, and a {@link SkillRecord} is
 * far too fine-grained — neither could answer "which rate applies to this person" with one number.
 * <p>
 * Deliberately not a payroll model. There is no gross/net, no employer's contribution, no pay grade
 * progression and no collective agreement; those belong to whatever vertical needs them. What stays
 * here is one rate per unit, the same three fields and the same {@link CostRateUnit} a
 * {@code Resource} uses, so that equipment cost and labour cost can be added up in one currency.
 * <p>
 * Master data of the installation, not of a department: the same qualifications usually apply across
 * a company, and scoping them per department would force a duplicate of every level in every one.
 *
 * @author peter haller
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Builder
public class QualificationLevel extends PObject implements PAuthorizedObject {

    /** What the level is called, e.g. {@code Geselle}. Unique, since it is how people refer to it. */
    @Column(nullable = false, unique = true)
    private String name;

    /** What the level means in this business — who counts as this, and what they are allowed to do. */
    @Column(length = 1024)
    private String description;

    /**
     * What an hour (or day, or use) of this qualification is costed at, or {@code null} when no rate
     * is kept. A level without a rate is perfectly usable — it still groups people for an evaluation,
     * the cost report just reports that it could not be priced instead of quietly counting it as free.
     * <p>
     * The three cost fields are set together or not at all; see {@code CostRateRules}.
     */
    @Column(precision = 12, scale = 2)
    private BigDecimal costRate;

    /** ISO-4217 code of {@link #costRate}, e.g. {@code EUR}. */
    private String costCurrency;

    /** Whether {@link #costRate} is per hour, per day or per use. */
    @Enumerated(EnumType.STRING)
    private CostRateUnit costRateUnit;

    @Override
    public String toString() {
        return name;
    }
}
