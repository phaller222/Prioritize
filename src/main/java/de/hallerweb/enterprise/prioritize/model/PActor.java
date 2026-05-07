package de.hallerweb.enterprise.prioritize.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * PActor ist die Basis für alle Einheiten, die aktiv an Aufgaben (Tasks)
 * arbeiten können (z.B. Personen oder Maschinen).
 */
@Entity
@Table(name = "pactor")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "actor_type") // Hilft Hibernate beim Mapping
@Getter
@Setter
@NoArgsConstructor
public abstract class PActor extends PObject {
    // Falls du später Felder für alle Aktoren brauchst (z.B. eine interne Kennung),
    // kommen sie hier rein.
}