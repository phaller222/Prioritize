package de.hallerweb.enterprise.prioritize.repository;

import de.hallerweb.enterprise.prioritize.model.PActor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Polymorphic repository over the {@link PActor} hierarchy. Thanks to JOINED inheritance a
 * {@code findById} resolves to the concrete actor subtype (e.g. {@code PUser} or
 * {@code Resource}), which is useful when an entity references an actor abstractly (such as a
 * task assignee).
 */
@Repository
public interface PActorRepository extends JpaRepository<PActor, Long> {
}
