package de.hallerweb.enterprise.prioritize.model.resource;


import com.fasterxml.jackson.annotation.JsonBackReference;
import de.hallerweb.enterprise.prioritize.model.calendar.TimeSpan;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_id")
    @JsonBackReference(value = "resource-reservations")
    private Resource resource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private PUser reservedBy; // Nutzt dein neues PUser Modell

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    private TimeSpan timespan;

    private int slotNumber;
}
