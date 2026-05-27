package de.hallerweb.enterprise.prioritize.repository.company;

import de.hallerweb.enterprise.prioritize.model.company.Company;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    @EntityGraph(attributePaths = {"departments", "departments.resourceGroups"})
    List<Company> findAll();

    List<Company> findByNameEqualsIgnoreCase(String name);

    @Query("SELECT c FROM Company c " +
            "WHERE (:name IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%')))" +
            "  AND (:vatNumber IS NULL OR c.vatNumber = :vatNumber)" +
            "  AND (:taxId IS NULL OR c.taxId = :taxId)" +
            "  AND (:country IS NULL OR c.mainAddress.country = :country)" +
            "  AND (:housenumber IS NULL OR c.mainAddress.housenumber = :housenumber)" +
            "  AND (:street IS NULL OR LOWER(c.mainAddress.street) LIKE LOWER(CONCAT('%', CAST(:street AS string), '%')))" + // NEU
            "  AND (:description IS NULL OR LOWER(c.description) LIKE LOWER(CONCAT('%', CAST(:description AS string), '%')))" +
            "  AND (:city IS NULL OR LOWER(c.mainAddress.city) LIKE LOWER(CONCAT('%', CAST(:city AS string), '%')))")
    Collection<Company> findCompaniesByFilter(
            @Param("name") String name,
            @Param("vatNumber") String vatNumber,
            @Param("taxId") String taxId,
            @Param("country") String country,
            @Param("housenumber") String housenumber,
            @Param("street") String street, // Parameter hinzufügen!
            @Param("description") String description,
            @Param("city") String city);


}

