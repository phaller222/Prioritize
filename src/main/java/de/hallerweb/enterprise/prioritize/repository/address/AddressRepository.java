package de.hallerweb.enterprise.prioritize.repository.address;

import de.hallerweb.enterprise.prioritize.model.company.Address;
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