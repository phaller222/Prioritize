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

import de.hallerweb.enterprise.prioritize.dto.address.AddressRequest;
import de.hallerweb.enterprise.prioritize.model.company.Company;

/**
 * Request body for creating, updating or filtering a {@link Company}. Carries only the writable base
 * data — never an {@code id} or the {@code departments} collection, so a client cannot inject a JPA
 * identity or mutate relationships through the company payload. On update the service copies these
 * scalar fields onto the managed entity; on filter the same shape is matched against existing companies.
 *
 * @author peter haller
 */
public record CompanyRequest(String name,
                             String description,
                             String url,
                             String vatNumber,
                             String taxId,
                             AddressRequest mainAddress) {

    /** Builds a fresh, id-less {@link Company} (with its address) from this request. */
    public Company toCompany() {
        return Company.builder()
                .name(name)
                .description(description)
                .url(url)
                .vatNumber(vatNumber)
                .taxId(taxId)
                .mainAddress(mainAddress != null ? mainAddress.toAddress() : null)
                .build();
    }
}
