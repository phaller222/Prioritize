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

package de.hallerweb.enterprise.prioritize.dto.company;

import de.hallerweb.enterprise.prioritize.dto.address.AddressDTO;
import de.hallerweb.enterprise.prioritize.model.company.Company;

/**
 * Flat, transport-safe view of a {@link Company}. Carries the scalar fields plus the eager
 * {@code mainAddress}, but never the lazy {@code departments} collection — so serializing a company
 * never triggers a {@code LazyInitializationException} nor drags the whole department graph onto the
 * wire. A client reads a company's departments via {@code GET /companies/{id}/departments}.
 *
 * @author peter haller
 */
public record CompanyDTO(Long id,
                         String name,
                         String description,
                         String url,
                         String vatNumber,
                         String taxId,
                         AddressDTO mainAddress) {

    /** Maps an entity to its DTO. {@code mainAddress} is eager, so this is safe outside a transaction. */
    public static CompanyDTO from(Company company) {
        return new CompanyDTO(
                company.getId(),
                company.getName(),
                company.getDescription(),
                company.getUrl(),
                company.getVatNumber(),
                company.getTaxId(),
                AddressDTO.from(company.getMainAddress()));
    }
}
