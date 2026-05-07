package de.hallerweb.enterprise.prioritize.repository.security;

import de.hallerweb.enterprise.prioritize.model.security.PUser;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<PUser, Integer> {
    @QueryHints({@QueryHint(name = "org.hibernate.flushMode", value = "MANUAL")})
    Optional<PUser> findByUsername(String username);
}