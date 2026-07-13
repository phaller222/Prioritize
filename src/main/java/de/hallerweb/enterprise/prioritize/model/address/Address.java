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

package de.hallerweb.enterprise.prioritize.model.address;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.hallerweb.enterprise.prioritize.model.PObject;
import jakarta.persistence.Entity;
import lombok.*;


/**
 * Address.java - JPA class to represent an {@link Address}.
 *
 * @author peter
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Builder
@ToString(onlyExplicitlyIncluded = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Address extends PObject {

    @ToString.Include
    @EqualsAndHashCode.Include
    private String street;
    @ToString.Include
    @EqualsAndHashCode.Include
    private String housenumber;
    @ToString.Include
    private String floor; // Floor (if needed)
    @ToString.Include
    @EqualsAndHashCode.Include
    private String zipCode;
    @ToString.Include
    @EqualsAndHashCode.Include
    private String city;
    @ToString.Include
    private String country;

    // Contact info usually not needed in toString, to keep logs compact
    private String phone;
    private String fax;
    private String mobile;

    /**
     * Returns a fresh, detached {@link Address} carrying only the scalar fields of {@code src} (no id, no
     * JPA identity), or {@code null} if {@code src} is {@code null}. Used to hand address data out of a
     * service transaction into a Vaadin view (whose thread has no open session, so a lazy proxy could not
     * be read there) and, on the way back, to keep the form bound to a plain bean rather than a managed
     * entity.
     */
    public static Address copyOf(Address src) {
        if (src == null) {
            return null;
        }
        Address copy = new Address();
        copy.setStreet(src.getStreet());
        copy.setHousenumber(src.getHousenumber());
        copy.setFloor(src.getFloor());
        copy.setZipCode(src.getZipCode());
        copy.setCity(src.getCity());
        copy.setCountry(src.getCountry());
        copy.setPhone(src.getPhone());
        copy.setFax(src.getFax());
        copy.setMobile(src.getMobile());
        return copy;
    }
}
