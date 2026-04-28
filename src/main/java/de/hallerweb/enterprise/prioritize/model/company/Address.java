/*
 * Copyright 2015-2024 Peter Michael Haller and contributors
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

package de.hallerweb.enterprise.prioritize.model.company;

import de.hallerweb.enterprise.prioritize.model.PObject;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.*;


/**
 * Address.java - JPA class to represent an {@link Address}.
 *
 *
 * <p>
 * Copyright: (c) 2026
 * </p>
 * <p>
 * Peter Haller
 * </p>
 *
 * @author peter
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class Address extends PObject {

    @ToString.Include
    private String street;
    @ToString.Include
    private String housenumber;
    @ToString.Include
    private String zipCode;
    @ToString.Include
    private String city;
    @ToString.Include
    private String country;

    // Kontaktinfos meist nicht im ToString nötig, um Logs kompakt zu halten
    private String phone;
    private String fax;
    private String mobile;
}
