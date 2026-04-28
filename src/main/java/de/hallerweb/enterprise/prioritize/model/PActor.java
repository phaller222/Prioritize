package de.hallerweb.enterprise.prioritize.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * PActor ist die Basis für alle Einheiten, die aktiv an Aufgaben (Tasks)
 * arbeiten können (z.B. Personen oder Maschinen).
 */
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@Setter
@NoArgsConstructor
public abstract class PActor extends PObject {
    // Falls du später Felder für alle Aktoren brauchst (z.B. eine interne Kennung),
    // kommen sie hier rein.
}