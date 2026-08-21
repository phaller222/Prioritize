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

package de.hallerweb.enterprise.prioritize.model.resource;

/**
 * What a resource's cost rate is charged per. Three units cover the cases a neutral platform should
 * know about: an hourly rate, a daily rate as rental equipment is billed, and a flat charge per use.
 * <p>
 * Deliberately not a tariff model. The platform stores <em>a</em> rate per unit and can multiply it by
 * a duration; how that rate came about — surcharges, VAT, time-of-day pricing, framework agreements —
 * belongs to whatever vertical sits on top. The same line runs through the equipment itself: there is
 * no field for power draw in kW, because that would force the platform to model an electricity price.
 * A rate in currency per hour that already includes the power is neutral; kilowatts are not.
 *
 * @author peter haller
 */
public enum CostRateUnit {

    /** Charged per hour of use — the usual case for machines and workshop equipment. */
    HOUR,

    /** Charged per calendar day, the way rental equipment is normally billed. */
    DAY,

    /** A flat charge each time the resource is used, regardless of how long. */
    USAGE
}
