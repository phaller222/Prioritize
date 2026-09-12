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

package de.hallerweb.enterprise.prioritize.model.cost;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;

/**
 * The rule that makes an amount, a currency and a {@link CostRateUnit} mean something together.
 * <p>
 * Two things in the platform carry a cost rate — a {@code Resource} and a {@code QualificationLevel} —
 * and they have to agree on what a valid rate is. Kept here rather than in either service, because two
 * copies of a validation rule drift apart silently: the day one of them starts accepting {@code Euro}
 * while the other insists on {@code EUR}, no sum across the two is trustworthy any more.
 *
 * @author peter haller
 */
public final class CostRateRules {

    private CostRateRules() {
        // Utility class
    }

    /**
     * Checks that a cost rate is either complete or absent, and returns the currency in its canonical
     * spelling so the caller can store it back.
     * <p>
     * A rate is only meaningful complete: an amount without a unit cannot be interpreted, and without a
     * currency it cannot be added up across whatever carries it. A negative rate is rejected outright;
     * a rate of zero is allowed and means "free of charge", which is a real answer and different from
     * "not recorded".
     * <p>
     * The currency must be an ISO 4217 code, checked against the JDK's table and stored upper case. A
     * free-form string looks harmless until the data exists: {@code EUR}, {@code eur} and {@code Euro}
     * are three currencies as far as any sum is concerned, and tightening the rule afterwards would
     * reject rows that are already in the database.
     *
     * @param rate     the amount, or {@code null} when no rate is kept
     * @param currency the ISO 4217 code, or {@code null} when no rate is kept
     * @param unit     what the rate is charged per, or {@code null} when no rate is kept
     * @return the normalized currency code, or {@code null} when no rate is kept at all
     * @throws IllegalArgumentException if the three fields are partly set, the rate is negative or the
     *                                  currency is not an ISO 4217 code
     */
    public static String requireConsistent(BigDecimal rate, String currency, CostRateUnit unit) {
        boolean any = rate != null || currency != null || unit != null;
        if (!any) {
            return null;
        }
        if (rate == null || currency == null || unit == null) {
            throw new IllegalArgumentException(
                    "A cost rate needs an amount, a currency and a unit — set all three or none.");
        }
        if (rate.signum() < 0) {
            throw new IllegalArgumentException("A cost rate cannot be negative.");
        }
        return normalizedCurrency(currency);
    }

    /** The ISO 4217 code in its canonical spelling, or an {@link IllegalArgumentException}. */
    public static String normalizedCurrency(String currency) {
        String code = currency.trim().toUpperCase(Locale.ROOT);
        try {
            return Currency.getInstance(code).getCurrencyCode();
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new IllegalArgumentException(
                    "'" + currency + "' is not an ISO 4217 currency code — use three letters, for instance EUR.");
        }
    }
}
