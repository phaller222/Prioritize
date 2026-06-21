package de.hallerweb.enterprise.prioritize.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * PActor is the base for all entities that can actively work on tasks
 * (e.g. persons or machines).
 */
@Entity
@Table(name = "pactor")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "actor_type") // Helps Hibernate with the mapping
@Getter
@Setter
@NoArgsConstructor
public abstract class PActor extends PObject {
    // If you later need fields for all actors (e.g. an internal identifier),
    // they go in here.
}