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
import de.hallerweb.enterprise.prioritize.dto.address.AddressRequest;
import de.hallerweb.enterprise.prioritize.model.address.Address;
import de.hallerweb.enterprise.prioritize.model.company.Company;
import de.hallerweb.enterprise.prioritize.model.company.Department;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Plain unit tests (no Spring context) for the request/response DTO mapping of the company subsystem.
 * They pin the two properties the DTO layer exists to guarantee: every scalar field is carried across,
 * and a request never introduces a JPA {@code id}.
 */
class CompanyDtoMappingTest {

    private static Address sampleAddress() {
        Address a = Address.builder()
                .street("Main St").housenumber("7").floor("2").zipCode("12345")
                .city("Springfield").country("US").phone("555-1").fax("555-2").mobile("555-3")
                .build();
        a.setId(99L);
        return a;
    }

    @Test
    @DisplayName("AddressDTO.from carries every scalar; null in -> null out")
    void addressDtoFrom() {
        assertNull(AddressDTO.from(null));

        AddressDTO dto = AddressDTO.from(sampleAddress());
        assertEquals(99L, dto.id());
        assertEquals("Main St", dto.street());
        assertEquals("7", dto.housenumber());
        assertEquals("2", dto.floor());
        assertEquals("12345", dto.zipCode());
        assertEquals("Springfield", dto.city());
        assertEquals("US", dto.country());
        assertEquals("555-1", dto.phone());
        assertEquals("555-2", dto.fax());
        assertEquals("555-3", dto.mobile());
    }

    @Test
    @DisplayName("AddressRequest.toAddress carries scalars but never an id")
    void addressRequestToAddress() {
        AddressRequest req = new AddressRequest("Main St", "7", "2", "12345",
                "Springfield", "US", "555-1", "555-2", "555-3");
        Address a = req.toAddress();
        assertNull(a.getId(), "a request must not carry a JPA id");
        assertEquals("Main St", a.getStreet());
        assertEquals("Springfield", a.getCity());
        assertEquals("US", a.getCountry());
        assertEquals("555-3", a.getMobile());
    }

    @Test
    @DisplayName("CompanyDTO.from carries scalars and the eager address")
    void companyDtoFrom() {
        Company c = Company.builder()
                .name("Acme").description("desc").url("https://acme.example")
                .vatNumber("VAT1").taxId("TAX1").mainAddress(sampleAddress())
                .build();
        c.setId(5L);

        CompanyDTO dto = CompanyDTO.from(c);
        assertEquals(5L, dto.id());
        assertEquals("Acme", dto.name());
        assertEquals("desc", dto.description());
        assertEquals("https://acme.example", dto.url());
        assertEquals("VAT1", dto.vatNumber());
        assertEquals("TAX1", dto.taxId());
        assertEquals("Springfield", dto.mainAddress().city());
    }

    @Test
    @DisplayName("CompanyRequest.toCompany maps base data + address, leaves id null")
    void companyRequestToCompany() {
        CompanyRequest req = new CompanyRequest("Acme", "desc", "https://acme.example", "VAT1", "TAX1",
                new AddressRequest("Main St", "7", null, "12345", "Springfield", "US", null, null, null));
        Company c = req.toCompany();
        assertNull(c.getId());
        assertEquals("Acme", c.getName());
        assertEquals("TAX1", c.getTaxId());
        assertEquals("Springfield", c.getMainAddress().getCity());
        assertNull(c.getMainAddress().getId());
    }

    @Test
    @DisplayName("CompanyRequest.toCompany tolerates a missing address")
    void companyRequestNullAddress() {
        CompanyRequest req = new CompanyRequest("Acme", null, null, null, null, null);
        assertNull(req.toCompany().getMainAddress());
    }

    @Test
    @DisplayName("DepartmentDTO.from carries the owning company id and is null-safe")
    void departmentDtoFrom() {
        Company parent = Company.builder().name("Acme").build();
        parent.setId(5L);
        Department d = Department.builder().name("R&D").description("dep").address(sampleAddress()).build();
        d.setId(11L);
        d.setCompany(parent);

        DepartmentDTO dto = DepartmentDTO.from(d);
        assertEquals(11L, dto.id());
        assertEquals("R&D", dto.name());
        assertEquals("dep", dto.description());
        assertEquals(5L, dto.companyId());
        assertEquals("Springfield", dto.address().city());

        // no company set -> companyId null, no NPE
        Department orphan = Department.builder().name("Loose").build();
        assertNull(DepartmentDTO.from(orphan).companyId());
        assertNull(DepartmentDTO.from(orphan).address());
    }

    @Test
    @DisplayName("DepartmentRequest.toDepartment maps base data + address, leaves id null")
    void departmentRequestToDepartment() {
        DepartmentRequest req = new DepartmentRequest("R&D", "dep",
                new AddressRequest("Main St", "7", null, "12345", "Springfield", "US", null, null, null));
        Department d = req.toDepartment();
        assertNull(d.getId());
        assertEquals("R&D", d.getName());
        assertEquals("dep", d.getDescription());
        assertEquals("Springfield", d.getAddress().getCity());
        assertNull(d.getAddress().getId());
    }
}
