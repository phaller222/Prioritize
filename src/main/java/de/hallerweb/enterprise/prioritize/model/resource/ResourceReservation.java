package de.hallerweb.enterprise.prioritize.model.resource;


import de.hallerweb.enterprise.prioritize.model.PObject;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class ResourceReservation extends PObject {


    @ManyToOne(fetch = FetchType.LAZY)
    private Resource resource;

}
