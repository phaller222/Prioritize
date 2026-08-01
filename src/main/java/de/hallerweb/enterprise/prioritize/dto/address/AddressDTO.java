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
 * Flat, transport-safe view of an {@link Address}. Carries the plain scalar fields (all eagerly loaded),
 * so it can be mapped outside a transaction. Shared by the DTOs of every entity that owns an address.
 *
 * @author peter haller
 */
public record AddressDTO(Long id,
                         String street,
                         String housenumber,
                         String floor,
                         String zipCode,
                         String city,
                         String country,
                         String phone,
                         String fax,
                         String mobile) {

    /** Maps an entity to its DTO, or {@code null} if {@code address} is {@code null}. */
    public static AddressDTO from(Address address) {
        if (address == null) {
            return null;
        }
        return new AddressDTO(
                address.getId(),
                address.getStreet(),
                address.getHousenumber(),
                address.getFloor(),
                address.getZipCode(),
                address.getCity(),
                address.getCountry(),
                address.getPhone(),
                address.getFax(),
                address.getMobile());
    }
}
