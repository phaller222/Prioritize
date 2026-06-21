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

    // Finds all reservations for a specific resource
    List<ResourceReservation> findByResourceId(int resourceId);

    // Findet alle Reservierungen eines bestimmten Benutzers
    List<ResourceReservation> findByReservedBy_Id(int userId);

    // Finds reservations within a department (via the resource)
    List<ResourceReservation> findByResource_Department_Id(int departmentId);

    // Finds all reservations that end in the future (active and upcoming)
    List<ResourceReservation> findByTimespan_DateUntilAfter(Instant now);

    /**
     * Finds all reservations of a resource that overlap with a time span.
     * This is the database equivalent of your intersects() method.
     */
    @Query("SELECT r FROM ResourceReservation r WHERE r.resource.id = :resId " +
        "AND r.timespan.dateFrom < :until AND r.timespan.dateUntil > :from")
    List<ResourceReservation> findOverlappingReservations(
        @Param("resId") Long resourceId,
        @Param("from") Instant from,
        @Param("until") Instant until);

    /**
     * Finds all reservations of a resource active at the point in time {@code now},
     * held by the given user. "Active" = the point in time lies within the
     * reserved window ({@code dateFrom <= now < dateUntil}).
     * <p>
     * Basis for slot derivation when sending control commands: the slot
     * is not supplied by the client but determined from the user's own active
     * reservation.
     *
     * @param resourceId resource the reservation refers to
     * @param userId     the user holding the reservation
     * @param now        reference point in time (usually {@code Instant.now()})
     * @return active reservations of this user on this resource
     */
    @Query("SELECT r FROM ResourceReservation r WHERE r.resource.id = :resId " +
        "AND r.reservedBy.id = :userId " +
        "AND r.timespan.dateFrom <= :now AND r.timespan.dateUntil > :now")
    List<ResourceReservation> findActiveReservationsByUser(
        @Param("resId") Long resourceId,
        @Param("userId") Long userId,
        @Param("now") Instant now);
}