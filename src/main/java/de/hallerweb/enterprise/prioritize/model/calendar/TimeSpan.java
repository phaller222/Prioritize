package de.hallerweb.enterprise.prioritize.model.calendar;

import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.security.PAuthorizedObject;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
/**
 * Time span that is reserved for a specific resource or used for other purposes.
 */
public class TimeSpan implements PAuthorizedObject {

    public enum TimeSpanType {
        RESOURCE_RESERVATION, VACATION, ILLNESS, TIME_TRACKER, OTHER, ALL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Implementiert PAuthorizedObject.getId() via Lombok @Getter

    private String title;
    private String description;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "timespan_resources",
        joinColumns = @JoinColumn(name = "timespan_id"),
        inverseJoinColumns = @JoinColumn(name = "resource_id")
    )
    private Set<Resource> involvedResources = new HashSet<>();

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "timespan_users",
        joinColumns = @JoinColumn(name = "timespan_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<PUser> involvedUsers = new HashSet<>();

    private Instant dateFrom;
    private Instant dateUntil;

    @Enumerated(EnumType.STRING)
    private TimeSpanType type;

    // --- Business Logik ---

    /**
     * Checks whether this time span overlaps with another time span.
     *
     * @param other The other time span to check against
     * @return true if the time spans overlap, false otherwise
     */
    public boolean intersects(TimeSpan other) {
        if (other == null || other.getDateFrom() == null || other.getDateUntil() == null) {
            return false;
        }
        return !dateFrom.isAfter(other.getDateUntil()) && !dateUntil.isBefore(other.getDateFrom());
    }

    @Override
    public Long getId() {
        return this.id;
    }
}