package de.hallerweb.enterprise.prioritize.repository.resource;

import de.hallerweb.enterprise.prioritize.model.resource.ResourceReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ResourceReservationRepository extends JpaRepository<ResourceReservation, Integer> {

    // Findet alle Reservierungen für eine bestimmte Ressource
    List<ResourceReservation> findByResourceId(int resourceId);

    // Findet alle Reservierungen eines bestimmten Benutzers
    List<ResourceReservation> findByReservedBy_Id(int userId);

    // Findet Reservierungen innerhalb einer Abteilung (über die Ressource)
    List<ResourceReservation> findByResource_Department_Id(int departmentId);

    // Findet alle Reservierungen, die in der Zukunft enden (aktive und kommende)
    List<ResourceReservation> findByTimespan_DateUntilAfter(Instant now);

    /**
     * Findet alle Reservierungen einer Ressource, die sich mit einem Zeitraum überschneiden.
     * Das ist die Datenbank-Entsprechung zu deiner intersects() Methode.
     */
    @Query("SELECT r FROM ResourceReservation r WHERE r.resource.id = :resId " +
            "AND r.timespan.dateFrom < :until AND r.timespan.dateUntil > :from")
    List<ResourceReservation> findOverlappingReservations(
            @Param("resId") Long resourceId,
            @Param("from") Instant from,
            @Param("until") Instant until);
}