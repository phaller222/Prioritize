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

package de.hallerweb.enterprise.prioritize.dto.address;

import de.hallerweb.enterprise.prioritize.model.address.Address;

/**
 * Request body for an {@link Address}. Deliberately carries no {@code id}: a client supplies address
 * data, never a JPA identity — this makes the "manual id assignment not allowed" guard structurally
 * impossible to violate. Shared by the request DTOs of every entity that owns an address.
 *
 * @author peter haller
 */
public record AddressRequest(String street,
                             String housenumber,
                             String floor,
                             String zipCode,
                             String city,
                             String country,
                             String phone,
                             String fax,
                             String mobile) {

    /** Builds a fresh, id-less {@link Address} from this request. */
    public Address toAddress() {
        return Address.builder()
                .street(street)
                .housenumber(housenumber)
                .floor(floor)
                .zipCode(zipCode)
                .city(city)
                .country(country)
                .phone(phone)
                .fax(fax)
                .mobile(mobile)
                .build();
    }
}
