package de.hallerweb.enterprise.prioritize.repository.address;

import de.hallerweb.enterprise.prioritize.model.company.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface AddressRepository extends JpaRepository<Address, Integer> {

    List<Address> findByCityEqualsIgnoreCase(String city);

    List<Address> findByZipCodeEqualsIgnoreCase(String zipCode);

    @Query("SELECT a FROM Address a " +
            "WHERE (:zipCode IS NULL OR a.zipCode = :zipCode) " +
            "  AND (:city IS NULL OR LOWER(a.city) = LOWER(:city)) " +
            "  AND (:street IS NULL OR LOWER(a.street) LIKE LOWER(CONCAT('%', :street, '%'))) " +
            "  AND (:country IS NULL OR a.country = :country) " +
            "  AND (:housenumber IS NULL OR a.housenumber = :housenumber)")
    Collection<Address> findByFilter(
            @Param("city") String city,
            @Param("zipCode") String zipCode,
            @Param("street") String street,
            @Param("housenumber") String housenumber,
            @Param("country") String country);
}