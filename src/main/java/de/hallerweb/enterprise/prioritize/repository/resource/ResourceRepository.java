package de.hallerweb.enterprise.prioritize.repository.resource;

import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, Integer> {

    // Standard-Suche nach Namen
    List<Resource> findByNameContainingIgnoreCase(String name);

    // Findet alle Ressourcen einer bestimmten Abteilung
    List<Resource> findByDepartment_Id(Integer departmentId);

    // Findet Ressourcen, die aktuell nicht belegt sind (busy = false)
    List<Resource> findByBusyFalse();

    // Spezialsuche für MQTT-Geräte, die online sind
    List<Resource> findByMqttResourceTrueAndMqttOnlineTrue();

    // Die "große" Suche mit Filtern (ähnlich wie bei der Company)
    @Query("SELECT r FROM Resource r WHERE " +
            "(:name IS NULL OR LOWER(r.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
            "(:description IS NULL OR LOWER(r.description) LIKE LOWER(CONCAT('%', :description, '%'))) AND " +
            "(:departmentId IS NULL OR r.department.id = :departmentId) AND " +
            "(:mqttResource IS NULL OR r.mqttResource = :mqttResource)")
    List<Resource> findResourcesByFilter(
            @Param("name") String name,
            @Param("description") String description,
            @Param("departmentId") Integer departmentId,
            @Param("mqttResource") Boolean mqttResource
    );

    // Hilfreich für MQTT-Updates
    Optional<Resource> findByMqttUUID(String mqttUUID);
}