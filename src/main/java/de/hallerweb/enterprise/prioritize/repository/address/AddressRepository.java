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

package de.hallerweb.enterprise.prioritize.repository.address;

import de.hallerweb.enterprise.prioritize.model.address.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByCityEqualsIgnoreCase(String city);

    List<Address> findByZipCodeEqualsIgnoreCase(String zipCode);

    @Query("SELECT a FROM Address a " +
            "WHERE (CAST(:zipCode AS string) IS NULL OR a.zipCode = :zipCode) " +
            "  AND (CAST(:city AS string) IS NULL OR LOWER(a.city) = LOWER(CAST(:city AS string))) " +
            "  AND (CAST(:street AS string) IS NULL OR LOWER(a.street) LIKE LOWER(CONCAT('%', CAST(:street AS string), '%'))) " +
            "  AND (CAST(:country AS string) IS NULL OR a.country = :country) " +
            "  AND (CAST(:floor AS string) IS NULL OR a.floor = :floor) " +
            "  AND (CAST(:housenumber AS string) IS NULL OR a.housenumber = :housenumber)")
    Collection<Address> findByFilter(
            @Param("city") String city,
            @Param("zipCode") String zipCode,
            @Param("street") String street,
            @Param("housenumber") String housenumber,
            @Param("floor") String floor,
            @Param("country") String country);
}